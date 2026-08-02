package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val ContentSaveCheckGreen = Color(0xFF4CAF50)

/** A close Material icon equivalent to Material Design's content-save-check glyph. */
@Composable
fun ContentSaveCheckIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.Save,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(44.dp),
        )
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).size(20.dp),
        )
    }
}
