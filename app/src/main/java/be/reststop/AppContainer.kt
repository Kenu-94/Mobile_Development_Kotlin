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

/** TODO: vervang door je eigen e-mailadres (Nominatim-voorwaarden: een identificeerbare User-Agent). */
const val USER_AGENT = "Reststop/1.0 (Android studentenproject; contact: kenuja.satheeskumar@student.ap.be)"

class LocationProvider(private val ctx: Context) {
    @SuppressLint("MissingPermission")
    suspend fun current(): LatLon? {
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val loc = client.getCurrentLocation(priority, null).await() ?: client.lastLocation.await()
        return loc?.let { LatLon(it.latitude, it.longitude) }
    }
}

/** Eenvoudige dependency-container (geen Hilt nodig). */
class AppContainer(val appContext: Context) {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    val room: AppDatabase = AppDatabase.get(appContext)

    val authRepo = AuthRepository(auth, firestore)
    val data = DataRepository(firestore)
    val locations = LocationProvider(appContext)

    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }.build()

    private val nominatim: NominatimApi = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NominatimApi::class.java)

    val addresses = AddressRepository(room.addressDao(), nominatim)
}
