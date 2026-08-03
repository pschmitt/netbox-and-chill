package dev.pschmitt.nyetbox.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import dev.pschmitt.nyetbox.data.repository.ThemeAccent
import dev.pschmitt.nyetbox.data.repository.ThemeMode

private val LightColors =
    lightColorScheme(primary = NetBoxTeal, secondary = NetBoxTealDark, tertiary = NetBoxTealLight)

private val DarkColors =
    darkColorScheme(primary = NetBoxTealLight, secondary = NetBoxTeal, tertiary = NetBoxTealDark)

/** Material You dynamic color on Android 12+, a teal NetBox-flavored fallback scheme below that. */
@Composable
fun NyetboxTheme(
    themeMode: ThemeMode = ThemeMode.FollowSystem,
    accent: ThemeAccent = ThemeAccent.System,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme =
        when (themeMode) {
            ThemeMode.FollowSystem -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    val colorScheme =
        when {
            accent == ThemeAccent.System && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            accent == ThemeAccent.System -> if (darkTheme) DarkColors else LightColors
            darkTheme -> customDarkColors(accent)
            else -> customLightColors(accent)
        }

    MaterialTheme(colorScheme = colorScheme, typography = NetBoxTypography, content = content)
}

private fun customLightColors(accent: ThemeAccent): ColorScheme =
    when (accent) {
        ThemeAccent.Teal -> LightColors
        ThemeAccent.Blue -> lightColorScheme(primary = Color(0xFF1565C0), secondary = Color(0xFF0D47A1), tertiary = Color(0xFF00838F))
        ThemeAccent.Purple -> lightColorScheme(primary = Color(0xFF7B1FA2), secondary = Color(0xFF6A1B9A), tertiary = Color(0xFF4527A0))
        ThemeAccent.Orange -> lightColorScheme(primary = Color(0xFFEF6C00), secondary = Color(0xFFE65100), tertiary = Color(0xFF8D6E63))
        ThemeAccent.Pink -> lightColorScheme(primary = Color(0xFFC2185B), secondary = Color(0xFFAD1457), tertiary = Color(0xFF6A1B9A))
        ThemeAccent.Green -> lightColorScheme(primary = Color(0xFF2E7D32), secondary = Color(0xFF1B5E20), tertiary = Color(0xFF00695C))
        ThemeAccent.System -> LightColors
    }

private fun customDarkColors(accent: ThemeAccent): ColorScheme =
    when (accent) {
        ThemeAccent.Teal -> DarkColors
        ThemeAccent.Blue -> darkColorScheme(primary = Color(0xFF90CAF9), secondary = Color(0xFF64B5F6), tertiary = Color(0xFF80DEEA))
        ThemeAccent.Purple -> darkColorScheme(primary = Color(0xFFCE93D8), secondary = Color(0xFFBA68C8), tertiary = Color(0xFFB39DDB))
        ThemeAccent.Orange -> darkColorScheme(primary = Color(0xFFFFB74D), secondary = Color(0xFFFF9800), tertiary = Color(0xFFBCAAA4))
        ThemeAccent.Pink -> darkColorScheme(primary = Color(0xFFF48FB1), secondary = Color(0xFFF06292), tertiary = Color(0xFFCE93D8))
        ThemeAccent.Green -> darkColorScheme(primary = Color(0xFFA5D6A7), secondary = Color(0xFF81C784), tertiary = Color(0xFF80CBC4))
        ThemeAccent.System -> DarkColors
    }
