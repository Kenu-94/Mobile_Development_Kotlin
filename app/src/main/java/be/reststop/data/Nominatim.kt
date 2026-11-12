package be.reststop.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale

data class NominatimAddress(
    val road: String? = null,
    val house_number: String? = null,
    val postcode: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val municipality: String? = null
)

data class NominatimResponse(val address: NominatimAddress? = null)

interface NominatimApi {
    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("zoom") zoom: Int,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("accept-language") language: String = "nl"
    ): NominatimResponse
}

/**
 * Reverse geocoding met Nominatim. Voldoet aan de gebruiksvoorwaarden:
 * - eigen User-Agent (zie AppContainer),
 * - maximaal 1 aanvraag per seconde (Mutex + delay),
 * - resultaten worden in Room (CachedAddress) bewaard met afgeronde coördinaten als sleutel.
 */
class AddressRepository(private val dao: AddressDao, private val api: NominatimApi) {
    private val mutex = Mutex()
    private var lastCall = 0L

    suspend fun lookup(lat: Double, lon: Double, zoom: Int): CachedAddress? {
        val key = String.format(Locale.US, "%.4f,%.4f,z%d", lat, lon, zoom)
        dao.get(key)?.let { return it }
        return mutex.withLock {
            dao.get(key)?.let { return@withLock it }
            val wait = 1100 - (System.currentTimeMillis() - lastCall)
            if (wait > 0) delay(wait)
            try {
                val a = api.reverse(lat, lon, zoom).address ?: return@withLock null
                val cached = CachedAddress(
                    key = key,
                    displayName = listOfNotNull(a.road, a.house_number).joinToString(" "),
                    city = a.city ?: a.town ?: a.village ?: a.municipality ?: "",
                    postcode = a.postcode ?: "",
                    fetchedAt = System.currentTimeMillis()
                )
                dao.upsert(cached)
                cached
            } finally {
                lastCall = System.currentTimeMillis()
            }
        }
    }
}
