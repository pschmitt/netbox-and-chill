package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Compact indicator used when an item is available from the local Room cache. */
@Composable
fun DownloadedIndicator(modifier: Modifier = Modifier) {
    ContentSaveCheckIcon(
        tint = ContentSaveCheckGreen,
        contentDescription = "Downloaded",
        modifier = modifier.size(24.dp),
    )
}
