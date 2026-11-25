package be.reststop.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.reststop.*
import be.reststop.R
import be.reststop.data.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

// ---------------- Mijn winkel ----------------

data class StoreUiState(
    val loading: Boolean = true,
    val name: String = "",
    val category: String = "bakery",
    val street: String = "",
    val postcode: String = "",
    val city: String = "",
    val email: String = "",
    val phone: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val geocoding: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    @StringRes val error: Int? = null
)

class StoreViewModel(private val c: AppContainer) : ViewModel() {
    private val uid = c.auth.currentUser?.uid.orEmpty()
    private val loginEmail = c.auth.currentUser?.email.orEmpty()
    private val _ui = MutableStateFlow(StoreUiState())
    val ui: StateFlow<StoreUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val s = c.data.store(uid)
                _ui.update {
                    if (s == null) it.copy(loading = false, email = loginEmail)
                    else it.copy(
                        loading = false, name = s.name, category = s.category, street = s.address,
                        postcode = s.postcode, city = s.city,
                        email = if (s.email.isNotBlank()) s.email else loginEmail,
                        phone = s.phone.orEmpty(),
                        lat = s.location.latitude, lon = s.location.longitude
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.toMessageRes()) }
            }
        }
    }

    fun onName(v: String) = _ui.update { it.copy(name = v, saved = false) }
    fun onCategory(v: String) = _ui.update { it.copy(category = v, saved = false) }
    fun onStreet(v: String) = _ui.update { it.copy(street = v, saved = false) }
    fun onPostcode(v: String) = _ui.update { it.copy(postcode = v, saved = false) }
    fun onCity(v: String) = _ui.update { it.copy(city = v, saved = false) }
    fun onEmail(v: String) = _ui.update { it.copy(email = v, saved = false) }
    fun onPhone(v: String) = _ui.update { it.copy(phone = v, saved = false) }

    /** Pin gezet: adres automatisch invullen via reverse geocoding, zoom 18 (H2). */
    fun onPin(lat: Double, lon: Double) {
        _ui.update { it.copy(lat = lat, lon = lon, geocoding = true, error = null, saved = false) }
        viewModelScope.launch {
            try {
                val a = c.addresses.lookup(lat, lon, 18)
                if (a == null) _ui.update { it.copy(error = R.string.err_no_address) }
                else _ui.update { it.copy(street = a.displayName, postcode = a.postcode, city = a.city) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.toMessageRes()) }
            } finally {
                _ui.update { it.copy(geocoding = false) }
            }
        }
    }

    fun save() {
        val s = _ui.value
        val error = when {
            s.name.isBlank() || s.street.isBlank() || s.city.isBlank() -> R.string.err_required
            s.lat == null || s.lon == null -> R.string.err_no_pin
            else -> null
        }
        if (error != null) { _ui.update { it.copy(error = error) }; return }
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, error = null) }
            try {
                c.data.saveStore(
                    Store(
                        ownerId = uid,
                        name = s.name.trim(),
                        category = s.category,
                        location = GeoPoint(s.lat!!, s.lon!!),
                        address = s.street.trim(),
                        postcode = s.postcode.trim(),
                        city = s.city.trim(),
                        email = s.email.trim(),
                        phone = s.phone.trim().ifBlank { null }
                    )
                )
                _ui.update { it.copy(saved = true) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.toMessageRes()) }
            } finally {
                _ui.update { it.copy(saving = false) }
            }
        }
    }
}

// ---------------- Mijn pakketten ----------------

data class BagWithReservations(val bag: Bag, val reservations: List<Reservation>)

data class MyBagsUiState(
    val loading: Boolean = true,
    val hasStore: Boolean = false,
    val items: List<BagWithReservations> = emptyList(),
    @StringRes val error: Int? = null
)

class MyBagsViewModel(private val c: AppContainer) : ViewModel() {
    private val uid = c.auth.currentUser?.uid.orEmpty()
    private val hasStore = MutableStateFlow(false)
    private val error = MutableStateFlow<Int?>(null)

    private val items: StateFlow<List<BagWithReservations>?> =
        combine(c.data.bagsOfStore(uid), c.data.reservationsOfStore(uid)) { bags, res ->
            bags.sortedByDescending { it.pickupEnd.toDate().time }
                .map { b -> BagWithReservations(b, res.filter { it.bagId == b.id }) }
        }
            .catch { e -> error.value = e.toMessageRes(); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val ui: StateFlow<MyBagsUiState> = combine(items, hasStore, error) { list, store, err ->
        MyBagsUiState(loading = list == null, hasStore = store, items = list.orEmpty(), error = err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MyBagsUiState())

    fun refreshStore() {
        viewModelScope.launch {
            try { hasStore.value = c.data.store(uid) != null } catch (_: Exception) {}
        }
    }

    fun setActive(bag: Bag, active: Boolean) {
        viewModelScope.launch {
            try { c.data.setBagActive(bag.id, active); error.value = null }
            catch (e: Exception) { error.value = e.toMessageRes() }
        }
    }

    /** Reservatie als opgehaald markeren na controle van de code (H6). */
    fun collect(res: Reservation, enteredCode: String) {
        if (!enteredCode.trim().equals(res.pickupCode, ignoreCase = true)) {
            error.value = R.string.err_wrong_code; return
        }
        viewModelScope.launch {
            try { c.data.markCollected(res.id); error.value = null }
            catch (e: Exception) { error.value = e.toMessageRes() }
        }
    }
}

// ---------------- Pakket aanmaken ----------------

data class NewBagUiState(
    val photoBase64: String = "",
    val photoLoading: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    @StringRes val error: Int? = null
)

class NewBagViewModel(private val c: AppContainer) : ViewModel() {
    private val uid = c.auth.currentUser?.uid.orEmpty()
    private val _ui = MutableStateFlow(NewBagUiState())
    val ui: StateFlow<NewBagUiState> = _ui.asStateFlow()

    fun onPhoto(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(photoLoading = true, error = null) }
            try {
                _ui.update { it.copy(photoBase64 = ImageUtils.encode(c.appContext, uri)) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = R.string.err_photo) }
            } finally {
                _ui.update { it.copy(photoLoading = false) }
            }
        }
    }

    private fun today(minutes: Int): Timestamp {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return Timestamp(cal.time)
    }

    /** startMin / endMin = minuten sinds middernacht. */
    fun save(title: String, description: String, priceText: String, originalText: String, qtyText: String, startMin: Int, endMin: Int) {
        val price = priceText.replace(',', '.').toDoubleOrNull()
        val original = originalText.replace(',', '.').toDoubleOrNull()
        val qty = qtyText.toIntOrNull()
        val start = today(startMin)
        val end = today(endMin)
        val error = when {
            title.isBlank() || description.isBlank() -> R.string.err_required
            price == null || original == null || qty == null || price <= 0 || qty < 1 -> R.string.err_invalid_number
            original < price -> R.string.err_original_lower
            endMin <= startMin -> R.string.err_window_order
            end.toDate().time <= System.currentTimeMillis() -> R.string.err_window_past
            _ui.value.photoBase64.isEmpty() -> R.string.err_photo_required
            else -> null
        }
        if (error != null) { _ui.update { it.copy(error = error) }; return }
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, error = null) }
            try {
                val store = c.data.store(uid)
                if (store == null) {
                    _ui.update { it.copy(error = R.string.err_no_store) }
                } else {
                    c.data.addBag(
                        Bag(
                            storeId = uid, storeName = store.name, category = store.category,
                            title = title.trim(), description = description.trim(),
                            price = price!!, originalValue = original!!,
                            quantityTotal = qty!!, quantityLeft = qty!!,
                            pickupStart = start, pickupEnd = end, location = store.location,
                            active = true, photoBase64 = _ui.value.photoBase64
                        )
                    )
                    _ui.update { it.copy(saved = true) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.toMessageRes()) }
            } finally {
                _ui.update { it.copy(saving = false) }
            }
        }
    }
}
