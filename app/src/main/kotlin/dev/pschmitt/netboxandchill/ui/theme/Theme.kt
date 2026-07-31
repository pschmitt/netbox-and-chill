package dev.pschmitt.netboxandchill.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors =
    lightColorScheme(primary = NetBoxTeal, secondary = NetBoxTealDark, tertiary = NetBoxTealLight)

private val DarkColors =
    darkColorScheme(primary = NetBoxTealLight, secondary = NetBoxTeal, tertiary = NetBoxTealDark)

/** Material You dynamic color on Android 12+, a teal NetBox-flavored fallback scheme below that. */
@Composable
fun NetBoxAndChillTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkTheme -> DarkColors
            else -> LightColors
        }

    MaterialTheme(colorScheme = colorScheme, typography = NetBoxTypography, content = content)
}
