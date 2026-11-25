package be.reststop.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
//1) foto selecteren
//2) foto verkleinene en comprimeren
//3) foto omzetten naar base64 en opslana in firestore
//4) base64 (foto) wordt teurg omgezet naar foto en wordt getoont in compose

//zorgt dat fotos in de app verwerkt kan worden,
//en bij firestore als base64 is opgeslagen

//ImageUitls: fotos verkleinen, comprimeren, naar bse64 zetten en terug decoderen
//Base64Image:gedecodeert foto tonen in jetpackcompose
object ImageUtils {
    // Verkleint (max 800 px), comprimeert (JPEG ±70) en zet om naar Base64 — op een achtergrondthread.


    //fotos omzeten naar base64
    suspend fun encode(context: android.content.Context, uri: Uri): String = withContext(Dispatchers.Default) {
        //uri is verwijzigen naar de foto, zogezged url

        //afbeleeding wordt geladen,basis van de url wordt een source gemaakt,
        // de contentresolver zorgt dat aundeoid echt effectief die img kan openen
        val source = ImageDecoder.createSource(context.contentResolver, uri)

        //een img source wordt omgezet naar een bitmap
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
           //de bitmap wordt via software verwerkt inplaats van hardware allocatie,
            //dit is nodig als we willen bewerken en compressen
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            //foto verkleinen
            val w = info.size.width
            val h = info.size.height
            val longest = maxOf(w, h)

            //checken als breedthe of hoogte groter is
            //dan de maximum size checken als die groter is dan 800pixels
            if (longest > 800) {
                val f = 800f / longest
                //berkekenen hoeveel kleiner de foto moet zijn
                decoder.setTargetSize((w * f).toInt().coerceAtLeast(1), (h * f).toInt().coerceAtLeast(1))
           //nieuwe grote geven, dus verkleint zonder het te vervormen
            }
        }
        //de foto nu comprimenen en base64 maken

        //we starten met een JPEG compress kwaliteit van 70%
        var quality = 70
        //dit zal de base64 tekst bijhouden
        var text: String

        do {
            //tijdelijke een geheuengestream maken voor de gecomprimeerde img bytes
            val out = ByteArrayOutputStream()
            //wordt eindelijke die bitmap omgezet naar JPEG , de results wordt gescherven in out
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            //de gecompirereed img data opgehaald als byte
            //dan die bytes omzetten naar string
            //en no wrap is om inspirngen of witte regels te voorkomen
            text = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            quality -= 15 //compresskwaliteit verlagen voor de volgende keer

            //deze while blijft zolang de base64 tekst groter is dan 900 000tekens
            //de quality groter is dna 10
        } while (text.length > 900_000 && quality > 10) // Firestore-document max 1 MiB
        text //wordt returned
    }

    //de base64 teurg naar img omzetten
    //base64 bevat de img data ,
    // sample:hoeveel de img verkleint wordt,
    // imagebitmap:geeft een img terug
    //dispatched.default doet de decodeerwerk op de background thread
    suspend fun decode(base64: String, sample: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        if (base64.isEmpty()) return@withContext null
        //eerst checken dat we wel een base64 data hebben
        try {
          //base64 omzeetten naar de og img bytes
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            //opties voor het ladne van img
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
           //img bytes werd gedecodeer tot bitmap
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

//Toont een Base64-foto; decodeert op een achtergrondthread en onthoudt het resultaat.
@Composable
fun Base64Image(base64: String, modifier: Modifier = Modifier, sample: Int = 1) {
    val bitmap by produceState<ImageBitmap?>(null, base64, sample) { value = ImageUtils.decode(base64, sample) }
    //huidige waarde va bitmap wordt opgeslagen bij bmp
    val bmp = bitmap
    //eerst checken natuurlijk dat de img wel bestaat
    if (bmp != null) {
        //de image wordt getoont op het shcerm, compose
        Image(bmp, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else { //als er toch een probleem is of gwn geen foto is dan passen we de layout aan
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
          //er wordt dan inplaats van een img ene icon gedaan
            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
