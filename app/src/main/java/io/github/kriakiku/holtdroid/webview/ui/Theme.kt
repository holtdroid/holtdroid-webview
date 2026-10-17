package io.github.kriakiku.holtdroid.webview.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val Light = lightColorScheme(
    primary = Color(0xFF8A5A3C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC9),
    onPrimaryContainer = Color(0xFF331200),
    secondary = Color(0xFF006D3B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9BF6B8),
    onSecondaryContainer = Color(0xFF00210E),
    tertiary = Color(0xFF5C6300),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E98A),
    onTertiaryContainer = Color(0xFF1A1D00),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A16),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A16),
    surfaceVariant = Color(0xFFF4DED4),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF85746B),
    outlineVariant = Color(0xFFD7C2B8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFCEBE3),
    surfaceContainerHigh = Color(0xFFF6E5DE),
    surfaceContainerHighest = Color(0xFFF0DFD8),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF2BC98),
    onPrimary = Color(0xFF4A2810),
    primaryContainer = Color(0xFF6A4026),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFF3DDC84),
    onSecondary = Color(0xFF003919),
    secondaryContainer = Color(0xFF005229),
    onSecondaryContainer = Color(0xFF9BF6B8),
    tertiary = Color(0xFFC4CD71),
    onTertiary = Color(0xFF2F3300),
    tertiaryContainer = Color(0xFF454A00),
    onTertiaryContainer = Color(0xFFE0E98A),
    background = Color(0xFF1A1210),
    onBackground = Color(0xFFF0DFD8),
    surface = Color(0xFF1A1210),
    onSurface = Color(0xFFF0DFD8),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFFA08D84),
    outlineVariant = Color(0xFF52443C),
    surfaceContainerLowest = Color(0xFF140D0B),
    surfaceContainerLow = Color(0xFF221A17),
    surfaceContainer = Color(0xFF271E1B),
    surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D3330),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val Base = Typography()

private val AppTypography = Base.copy(
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

val Mono = FontFamily.Monospace

@Composable
fun HoltdroidTheme(dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
