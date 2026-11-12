package be.reststop

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import be.reststop.data.SoldOutException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

data class LatLon(val lat: Double, val lon: Double)

/** Antwerpen, als er nog geen locatie is. */
val DEFAULT_CENTER = LatLon(51.2194, 4.4025)

val CATEGORIES = listOf("bakery", "supermarket", "restaurant", "other")

@StringRes
fun categoryRes(key: String): Int = when (key) {
    "bakery" -> R.string.cat_bakery
    "supermarket" -> R.string.cat_supermarket
    "restaurant" -> R.string.cat_restaurant
    else -> R.string.cat_other
}

@StringRes
fun statusRes(key: String): Int = when (key) {
    "collected" -> R.string.status_collected
    "cancelled" -> R.string.status_cancelled
    else -> R.string.status_reserved
}

val Context.container: AppContainer get() = (applicationContext as ReststopApp).container

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun Timestamp.hm(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(toDate())
fun Long.hm(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(this))

/** Maakt een ViewModelProvider.Factory zonder boilerplate. */
inline fun <reified VM : ViewModel> vmFactory(crossinline build: CreationExtras.() -> VM) =
    viewModelFactory { initializer { this.build() } }

/** Zet een exception om naar een duidelijke, vertaalbare foutmelding (A3, G1). */
@StringRes
fun Throwable.toMessageRes(): Int = when {
    this is SoldOutException || cause is SoldOutException -> R.string.err_sold_out
    this is FirebaseNetworkException || this is IOException -> R.string.err_network
    this is FirebaseAuthUserCollisionException -> R.string.err_email_in_use
    this is FirebaseAuthWeakPasswordException -> R.string.err_weak_password
    this is FirebaseAuthInvalidUserException || this is FirebaseAuthInvalidCredentialsException ->
        R.string.err_wrong_credentials
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE ->
        R.string.err_network
    else -> R.string.err_generic
}
