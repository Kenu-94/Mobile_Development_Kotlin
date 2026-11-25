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

// middleen dat hergebuikbaar zijn wordne hier verzameld omt egebruiken bij eht app
//zoals locaties en categiers,
// omzetten naar tekst,
// we gaan vanuit heir de appcontainer en activity ophalen wnr nodig
//tijdstippe
//viewmodels makeen
//fouten in tekst



//een data class waar de geografische coordinatin wordne bij gehouden,
// lat is de breedtegraad en lon de lengtegraad,
// zo kunenn we makkelijk ene cloatie doorgeven aan een obejct
data class LatLon(val lat: Double, val lon: Double)

/** Antwerpen, als er nog geen locatie is. */
//dat zijn de coorrdinatien van antwerpen, voor als de gebruiker geen locatie instelt
val DEFAULT_CENTER = LatLon(51.2194, 4.4025)
//lisjt van categories dat de winkels kunnen gebruiken
val CATEGORIES = listOf("bakery", "supermarket", "restaurant", "other")
//methode, dat de  cateogries omzet naar string en,
// ook de behorderen rescources erbij zet dat bij het tekst

@StringRes
fun categoryRes(key: String): Int = when (key) {
    "bakery" -> R.string.cat_bakery
    "supermarket" -> R.string.cat_supermarket
    "restaurant" -> R.string.cat_restaurant
    else -> R.string.cat_other
}

//hetzelfde als hierboven maar voor de status van een reservatie, opgehaald, geannuleerd, gereserveerd
@StringRes
fun statusRes(key: String): Int = when (key) {
    "collected" -> R.string.status_collected
    "cancelled" -> R.string.status_cancelled
    else -> R.string.status_reserved
}
//vanuit de current context wordt de appcontainer gebruikt dat in de tresstop)app gemaakt werd
//zodat de rest van de app die firebase , room enz kan gebruiken
val Context.container: AppContainer get() = (applicationContext as ReststopApp).container

//methode dat de activiteit van een context zoekt, null als het neit lukt
fun Context.findActivity(): Activity? {
    //we beginnen bij deze context van nu
    var c = this
    //zoekt zolang de context een contextwrapper is,
    while (c is ContextWrapper) {
        //als de gevonde gegeven een activity is, dan is het gevonden
        if (c is Activity) return c
        //als het geen activity is, dus else gaat die verder kijken bij het volgend econtext in onze context
        c = c.baseContext
    }
    return null

    //want sommige andorid functionaliteint hebben een activity nodig, en bij jetpakc compose heb je soms alleen een context en soms heb je dus die activity nodig dus je moet het zelf gaan zoeken
}

//een functie maken bij fireback timestamp, gwn firebase tijftip converteren naar tekst
//todat zorgt gwn dat het omgezet wordt naar java date
fun Timestamp.hm(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(toDate())
//zelfde maar dan voor long (tijstip in ms)
fun Long.hm(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(this))
//zodat de app de tijdstippen juist op een uniforme manier tonen

// Maakt een ViewModelProvider.Factory zonder boilerplate.
//G functie om die viewmodel factory te maken, VM is de type viewmodel dat we willen maken
inline fun <reified VM : ViewModel> vmFactory(crossinline build: CreationExtras.() -> VM) =
    //factory maken die de build functie uitvoert zodat die het viewmodel kan maken
    viewModelFactory { initializer { this.build() } }
//zodat we niet teleksn dezelfe code moeten doen om een viewmodel te maken

//Zet een exception om naar een duidelijke, vertaalbare foutmelding (A3, G1).
@StringRes

fun Throwable.toMessageRes(): Int = when {
    //
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
