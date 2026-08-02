package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Compact indicator used when an item is being rendered from the local Room cache. */
@Composable
fun CachedBadge() {
    Badge(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text("Cached", style = MaterialTheme.typography.labelSmall)
    }
}
