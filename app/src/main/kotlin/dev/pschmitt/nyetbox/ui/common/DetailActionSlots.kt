package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Fixed two-slot action area shared by typed and generic detail rows. */
@Composable
fun DetailTrailingActions(
    copyLabel: String? = null,
    onCopy: (() -> Unit)? = null,
    openLabel: String? = null,
    onOpen: (() -> Unit)? = null,
    openIcon: ImageVector = Icons.AutoMirrored.Filled.OpenInNew,
) {
    Row(
        modifier = Modifier.width(96.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (copyLabel != null && onCopy != null) {
            IconButton(modifier = Modifier.size(48.dp), onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy $copyLabel")
            }
        }
        if (openLabel != null && onOpen != null) {
            IconButton(modifier = Modifier.size(48.dp), onClick = onOpen) {
                Icon(openIcon, contentDescription = "Open $openLabel")
            }
        }
    }
}
