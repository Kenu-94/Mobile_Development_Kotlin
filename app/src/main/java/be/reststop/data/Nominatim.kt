package be.reststop.data

//de app ontvangt coordinaten
//controleert eerst als het adress bestaat in room
//bestaat nog neit? dan opvragen bij nominatim
//zodat we niet altijd aanvragen meoten doen als de adres al ooit gebruik werd
//limiet van maximaal 1 aanvraag (aan nominatim)  per seconde
//results van deze api aanvragen worden opgeslagen in room
//adres wordt gegeven aan de rest van de app

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale

//data class voor wat een adress heeft, want nomanitm antwoord met json, dus we gana die json met deze dataclass kopelen
data class NominatimAddress(
    val road: String? = null,
    val house_number: String? = null,
    val postcode: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val municipality: String? = null
)

//data class voor hoe een antwoord van nominatie is, dus een nomniatie adress
data class NominatimResponse(val address: NominatimAddress? = null)

//onze nomiatim api interface, dat een get doet met de queries voor de georeverse
interface NominatimApi {
    @GET("reverse")
    //doet dus een get aanvraga bij de eindpoint reverse, bij de basis url dat we in een andere ebstand hadden gedaan (https://nominatim.openstreetmap.org/reverse)
    suspend fun reverse(
        //om een coordinat om te zetten naar een adress
        @Query("lat") lat: Double,
        //de lat en lon wordt meegegven aan de api
        @Query("lon") lon: Double,
        @Query("zoom") zoom: Int,
        //zoomniveau ook
        @Query("format") format: String = "json",
        //we willen van nominatie de antwoor din json krijgen
        @Query("addressdetails") addressDetails: Int = 1,
        //we vragen ook dan de afzonderlijke adres zelfs
        @Query("accept-language") language: String = "nl"
        //liefst in het nl
    ): NominatimResponse
    //dit is dus een nominatimrepsinse, en wordt dan een nominatimadress (dataclassen van hierboven)
}


 // Reverse geocoding met Nominatim. Voldoet aan de gebruiksvoorwaarden:
 // - eigen User-Agent (zie AppContainer), (is kenuja)
 // - maximaal 1 aanvraag per seconde (Mutex + delay),
 // - resultaten worden in Room (CachedAddress) bewaard met afgeronde coördinaten als sleutel.

//api aanvragen worden hier gedaan en deze resultaten wordne dan opgeslagen in room lokaal
class AddressRepository(private val dao: AddressDao, private val api: NominatimApi) {
    //een repository dat een dao en api krijgt,
    // dao om adressgegeven uit room op te halen en op te slaan
    //api om aanvragen te doen bij nomnatim

    //maakt een mutex die zal zogren dat meerdere aanvragen niet tegelijk kan worden uitgevoerd
    private val mutex = Mutex()
    // wnr de laatse nomniatim aanvraag werd gedaan, 0L is de beginwaarde als Long
    private var lastCall = 0L

    //lookup functie, een fucntie die adress opzoek van de coordinaten, geeft chachedaddress of null terug
    suspend fun lookup(lat: Double, lon: Double, zoom: Int): CachedAddress? {
        //een unique sleutek wordt gemaakt van deze coordianten en zoomniveau,
        //deze key wordt gebruikt om te checken het het adres al in de room is opgeslagen
        val key = String.format(Locale.US, "%.4f,%.4f,z%d", lat, lon, zoom)
        //als het gevondne is dan reutnr it, de opgelsagen adress dus
        //of anders gwn null
        dao.get(key)?.let { return it }
        //de mutex wordt gelocked dan voert de code uit in de {},
        // zodat er maar 1 aanvraga tegelijekrtijd gedaan wordt
        return mutex.withLock {
            //nogseens checken als de adress in room staat
            //want het kan de een andere coroutine het adress heeft opgeslagen toen onze couritne
            // op de mutex wachte bij de eerste controle
            dao.get(key)?.let { return@withLock it }
            //berkent hvl ms sec de app moet w8 voordat die een niuewe aavraag meot uitvoeren
            val wait = 1100 - (System.currentTimeMillis() - lastCall)
            //als het nog niet genoeg tijd is w8 die verder
            if (wait > 0) delay(wait)
            try {
                //in deze try, doen we die nominatil aanvragen met die coordindatie en halen we adressgegevens uit de antwoordne
                val a = api.reverse(lat, lon, zoom).address ?: return@withLock null
                //een cachedadress object maken met deze opgehaalde adresgegevens
                val cached = CachedAddress(
                    //de sleutel die we hebben gemaakt wordt opgeslagen bij het adress
                    key = key,
                    //de straat en adress wordt 1 tekst samen
                    displayName = listOfNotNull(a.road, a.house_number).joinToString(" "),
                    city = a.city ?: a.town ?: a.village ?: a.municipality ?: "",
                    postcode = a.postcode ?: "",
                    //tijdtsip wnr de adress opgehaald werd
                    fetchedAt = System.currentTimeMillis()
                )
                //nieuwe adres wordt dan opgeslagen in room,
                // upsert=invoegen als het nog niet bestand, of bestaande gegevns bijwerken
                dao.upsert(cached)
                cached //aangemaake adres wordt terugveven als reultaat van het try block
            } finally {
                //dan op het einde van de try wordt die lstcal angepast zodat de app weet
                //wnr de poging is gebeurt, zo dat de volgende anavrag genoeg kan wachten
                lastCall = System.currentTimeMillis()
            }
        }
    }
}
