package be.reststop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2E7D32), onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9), onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFFEF6C00), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2), onSecondaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF9FBF6), surface = Color(0xFFF9FBF6)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF81C784), onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF1B5E20), onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFFFB74D), onSecondary = Color(0xFF442B00),
    secondaryContainer = Color(0xFF5D3300), onSecondaryContainer = Color(0xFFFFE0B2),
    background = Color(0xFF10150F), surface = Color(0xFF10150F)
)

@Composable
fun ReststopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
