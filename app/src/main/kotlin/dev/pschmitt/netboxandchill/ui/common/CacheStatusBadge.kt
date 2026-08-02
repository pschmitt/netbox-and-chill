package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Compact indicator used when an item is available from the local Room cache. */
@Composable
fun DownloadedIndicator(modifier: Modifier = Modifier) {
    Icon(
        Icons.Default.Download,
        contentDescription = "Downloaded",
        modifier = modifier.size(22.dp),
    )
}
