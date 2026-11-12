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

object ImageUtils {
    /** Verkleint (max 800 px), comprimeert (JPEG ±70) en zet om naar Base64 — op een achtergrondthread. */
    suspend fun encode(context: android.content.Context, uri: Uri): String = withContext(Dispatchers.Default) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longest = maxOf(w, h)
            if (longest > 800) {
                val f = 800f / longest
                decoder.setTargetSize((w * f).toInt().coerceAtLeast(1), (h * f).toInt().coerceAtLeast(1))
            }
        }
        var quality = 70
        var text: String
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            text = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            quality -= 15
        } while (text.length > 900_000 && quality > 10) // Firestore-document max 1 MiB
        text
    }

    suspend fun decode(base64: String, sample: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        if (base64.isEmpty()) return@withContext null
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

/** Toont een Base64-foto; decodeert op een achtergrondthread en onthoudt het resultaat. */
@Composable
fun Base64Image(base64: String, modifier: Modifier = Modifier, sample: Int = 1) {
    val bitmap by produceState<ImageBitmap?>(null, base64, sample) { value = ImageUtils.decode(base64, sample) }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
