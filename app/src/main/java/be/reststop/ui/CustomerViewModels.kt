package be.reststop.ui

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.reststop.*
import be.reststop.R
import be.reststop.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint as OsmGeoPoint

data class BagItem(val bag: Bag, val distanceKm: Double?)

data class HomeUiState(
    val loading: Boolean = true,
    val location: LatLon? = null,
    val city: String? = null,
    val locating: Boolean = false,
    val items: List<BagItem> = emptyList(),
    val maxKm: Int? = null,
    val category: String? = null,
    @StringRes val error: Int? = null
)

/** Gedeeld door de tabbladen Ontdekken en Kaart. */
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private data class Meta(val city: String? = null, val locating: Boolean = false, val error: Int? = null)
    private data class Filters(val maxKm: Int? = null, val category: String? = null)

    private val meta = MutableStateFlow(Meta())
    private val location = MutableStateFlow<LatLon?>(null)
    private val filters = MutableStateFlow(Filters())

    // null = nog aan het laden. Realtime via Firestore snapshots (K5).
    private val bags: StateFlow<List<Bag>?> = c.data.availableBags()
        .catch { e -> meta.update { it.copy(error = e.toMessageRes()) }; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<HomeUiState> = combine(bags, location, filters, meta) { list, loc, f, m ->
        val items = list.orEmpty().mapNotNull { bag ->
            val km = loc?.let {
                OsmGeoPoint(it.lat, it.lon)
                    .distanceToAsDouble(OsmGeoPoint(bag.location.latitude, bag.location.longitude)) / 1000.0
            }
            when {
                f.maxKm != null && km != null && km > f.maxKm -> null
                f.category != null && bag.category != f.category -> null
                else -> BagItem(bag, km)
            }
        }.sortedWith(compareBy<BagItem, Double?>(nullsLast<Double>()) { it.distanceKm })
        HomeUiState(
            loading = list == null, location = loc, city = m.city, locating = m.locating,
            items = items, maxKm = f.maxKm, category = f.category, error = m.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun setMaxKm(km: Int?) = filters.update { it.copy(maxKm = km) }
    fun setCategory(cat: String?) = filters.update { it.copy(category = cat) }

    /** Huidige GPS-locatie ophalen (na toestemming). */
    fun fetchLocation() {
        viewModelScope.launch {
            meta.update { it.copy(locating = true, error = null) }
            try {
                val l = c.locations.current()
                if (l == null) meta.update { it.copy(error = R.string.err_no_location) } else applyLocation(l)
            } catch (e: Exception) {
                meta.update { it.copy(error = R.string.err_no_location) }
            } finally {
                meta.update { it.copy(locating = false) }
            }
        }
    }

    /** Locatie gekozen via een pin op de kaart (K1). */
    fun setLocation(lat: Double, lon: Double) {
        viewModelScope.launch { applyLocation(LatLon(lat, lon)) }
    }

    private suspend fun applyLocation(l: LatLon) {
        location.value = l
        meta.update { it.copy(city = null, error = null) }
        try {
            val a = c.addresses.lookup(l.lat, l.lon, 10) // zoom 10 = gemeente (K2)
            meta.update { it.copy(city = a?.city?.ifBlank { null }) }
        } catch (e: Exception) {
            meta.update { it.copy(error = e.toMessageRes()) }
        }
    }
}

data class DetailUiState(
    val loading: Boolean = true,
    val bag: Bag? = null,
    val store: Store? = null,
    val favIds: Set<String> = emptySet(),
    val quantity: Int = 1,
    val reserving: Boolean = false,
    @StringRes val error: Int? = null,
    val code: String? = null
) {
    val isFavorite: Boolean get() = bag != null && bag.storeId in favIds
}

class DetailViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val bagId: String = checkNotNull(handle["bagId"])
    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()
    private var loadingStore = false

    init {
        viewModelScope.launch {
            c.data.bag(bagId)
                .catch { e -> _ui.update { it.copy(loading = false, error = e.toMessageRes()) } }
                .collect { bag ->
                    _ui.update {
                        it.copy(
                            loading = false, bag = bag,
                            quantity = it.quantity.coerceIn(1, maxOf(1, bag?.quantityLeft ?: 1))
                        )
                    }
                    if (bag != null && _ui.value.store == null) loadStore(bag.storeId)
                }
        }
        viewModelScope.launch {
            c.room.favoriteDao().all().collect { list ->
                _ui.update { it.copy(favIds = list.map { f -> f.storeId }.toSet()) }
            }
        }
    }

    private fun loadStore(id: String) {
        if (loadingStore) return
        loadingStore = true
        viewModelScope.launch {
            try {
                _ui.update { it.copy(store = c.data.store(id)) }
            } catch (_: Exception) {
            } finally {
                loadingStore = false
            }
        }
    }

    fun increase() = _ui.update {
        val max = it.bag?.quantityLeft ?: 1
        it.copy(quantity = (it.quantity + 1).coerceAtMost(maxOf(1, max)))
    }

    fun decrease() = _ui.update { it.copy(quantity = (it.quantity - 1).coerceAtLeast(1)) }
    fun dismissCode() = _ui.update { it.copy(code = null) }

    fun toggleFavorite() {
        val st = _ui.value
        val bag = st.bag ?: return
        viewModelScope.launch {
            if (st.isFavorite) c.room.favoriteDao().delete(bag.storeId)
            else c.room.favoriteDao().upsert(
                FavoriteStore(
                    bag.storeId, bag.storeName,
                    st.store?.let { "${it.address}, ${it.postcode} ${it.city}" } ?: "",
                    System.currentTimeMillis()
                )
            )
        }
    }

    fun reserve() {
        val st = _ui.value
        val bag = st.bag ?: return
        val uid = c.auth.currentUser?.uid ?: return
        if (st.reserving) return
        viewModelScope.launch {
            _ui.update { it.copy(reserving = true, error = null) }
            try {
                val code = c.data.reserve(bag, st.quantity, uid)
                _ui.update { it.copy(code = code) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.toMessageRes()) }
            } finally {
                _ui.update { it.copy(reserving = false) }
            }
        }
    }
}

/** Spiegelt de reservaties uit Firestore naar Room; het scherm leest enkel uit Room (K10). */
class OrdersViewModel(private val c: AppContainer) : ViewModel() {
    val orders: StateFlow<List<CachedReservation>?> = c.room.reservationDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    init {
        val uid = c.auth.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                c.data.reservationsOfCustomer(uid)
                    .catch { e -> _error.value = e.toMessageRes() }
                    .collect { list -> c.room.reservationDao().replaceAll(list.map { it.toCached() }) }
            }
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch {
            try {
                c.data.cancel(id)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.toMessageRes()
            }
        }
    }
}

class FavoritesViewModel(private val c: AppContainer) : ViewModel() {
    val favorites: StateFlow<List<FavoriteStore>> = c.room.favoriteDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun remove(storeId: String) {
        viewModelScope.launch { c.room.favoriteDao().delete(storeId) }
    }
}
