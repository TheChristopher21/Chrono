package ch.chronologisch.chrono.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ChronoWebDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF5B6DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF26315F),
    onPrimaryContainer = Color(0xFFE7EAFF),
    secondary = Color(0xFF65D6C7),
    onSecondary = Color(0xFF062A27),
    secondaryContainer = Color(0xFF123D3A),
    onSecondaryContainer = Color(0xFFD9FFFA),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF422006),
    tertiaryContainer = Color(0xFF4B3413),
    onTertiaryContainer = Color(0xFFFFE8B4),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF4A1F25),
    onErrorContainer = Color(0xFFFFD7DC),
    background = Color(0xFF15171C),
    onBackground = Color(0xFFE5E7EC),
    surface = Color(0xFF1F2127),
    onSurface = Color(0xFFE5E7EC),
    surfaceVariant = Color(0xFF252830),
    onSurfaceVariant = Color(0xFFA0A4B4),
    outline = Color(0xFF373B46),
)

@Composable
fun ChronoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChronoWebDarkColors,
        content = content,
    )
}
