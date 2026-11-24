package be.reststop

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import be.reststop.data.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

//LocationProvider: haalt d elcoatie op
//AppContainer : maakt alle diensten dat nodig is
// de repositries hiervan verbind de app met firebase, room en nominatim

//deze voleldige bestand, doet d emeeste belangerijke dingen die locatioe van de gebruiker/winkel ophalen, vebrindingen met de database, datacloud en lokale room db, en ook mogelijkheid om addressen opteaoeken via retrofit om nominatim (via coordinatien, reverse geococing)

/** (Nominatim-voorwaarden: een identificeerbare User-Agent). */
//kenuja is de contact persoon, voor als user agent voor Nominatim server
const val USER_AGENT = "Reststop/1.0 (Android studentenproject; contact: kenuja.satheeskumar@student.ap.be)"

//classe om de hudige locatie optehalen, de context is voor dus de situatie nu en de loactie nu
class LocationProvider(private val ctx: Context) {
    //android studio geeft meestal een waarschwuing voor dat er geen lcoatiepermissie is, we zetten deze waarschuwing weg want we gaan van uit dat de gebruikte toestemming gaat geven
    @SuppressLint("MissingPermission")
    //vraagt huidige locatie op en geeft terug een LatLon (?als er geen is is het gwn null)
    suspend fun current(): LatLon? {
        //een locatie klanten majen waar de app de laatse gegevn locatie van eht apparaat zal ophalen via de context weeral
        val client = LocationServices.getFusedLocationProviderClient(ctx)
       //checken als de gebruiker toestemming geeft voor d elocatie, boolean
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
       //gwn meeste nauwkerigheid locatie ophalen
        val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
      //nu wordt de locatie van nu opgehaald, als dez enull is doen we die priority adress
        val loc = client.getCurrentLocation(priority, null).await() ?: client.lastLocation.await()
       //gevonden locatie wordt dan een latlon object
        return loc?.let { LatLon(it.latitude, it.longitude) }
    }
}

// simpele dependency-container, geen Hilt nodig
//deze container gaat alle belangeirjke obejcten en
// , dependenene van de app aanmaken en beschikbaar stellen
//ook hier maakt die gebruik van de context




class AppContainer(val appContext: Context) {
    //we halen de firebase authneticatie object om te registeren, inloggen en uitloggen
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    //hetzelfde maar van firestore cloud zodat we hierin alles gaan opslaan
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    //om lokaal gegevens bij de gebruikers te kunnen oplsaan,
    // halen we de lokale room db op (gebruik maken van de current context)
   val room: AppDatabase = AppDatabase.get(appContext)
//een authentiatierepository aanmaken door de firestore en firebase mee te gebruiken
    //hier zal dus de logica zijn van het gebruikersbeheer
    val authRepo = AuthRepository(auth, firestore)
    //een reporitoy voor data maken (met hulp van de firestore cloud)
    //hier gaan de gegevns (winkels, pakketen en reservaties)
    // beheerd worden dat firestore opgeslagen heeft
    val data = DataRepository(firestore)
    //die locationproivder klas van hierboven, maken we een object van
    //zo kan de volledige app weten wat de hudige locatie is van de gebruiker
    val locations = LocationProvider(appContext)
//http client maken om aanvragen te sturen naar externe servers,
// we passen die eerst aan voordat we die maken











    private val http = OkHttpClient.Builder()
        //een interceptor die elke http aanvraag kan aanpassen voordat die verstruurd wordt
        .addInterceptor { chain ->
            //og aanvrag , voegt daar de user agent toe (kenuja) en stuurt de aangepaste versie naar d eserver
            //zo wordt de app met de identifictia van onze app meegestuurd
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }.build()

    //een retrofit config maken voor die nominatim api, de results gaat naar onze interface
    private val nominatim: NominatimApi = Retrofit.Builder()
        //adress instellen van de nomniaitm api, dus alle api aanvragen komt van hier
        //dit is de openstreetmap, gwn een map zoals google map
        .baseUrl("https://nominatim.openstreetmap.org/")
        //de cliente dat we eerder hadden gemaakt wordt nu gekopeld aan retrofit
        //de user agent (kenuja) wordt automatishce toegevoegd ana de aanvrag dan
        .client(http)
        //gwn josn converter toevoegen die de json results van nominatim kan omzetten naar kotlin objecten
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        //en gemaakt maar wordt gemaakt als een class van ons dat we hebben gemaakt, zodat die dan ook die methodes dat we hebben gemakat gebruiken
        .create(NominatimApi::class.java)

    //we maken hier een address repostiroy aan die 2 dingens geeft
    //toegang tot die room db (dao) , hier worden er gecachte addressen opegslaan en kunenn we het ophaeln enz en ook toevoegen
    //er wordt ook toegang tot die nominatim api geven, zodat we nieuwe aadressen kunnen krijgen
    val addresses = AddressRepository(room.addressDao(), nominatim)
}
