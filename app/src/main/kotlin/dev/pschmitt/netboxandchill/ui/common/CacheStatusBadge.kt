package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Compact indicator used when an item is being rendered from the local Room cache. */
@Composable
fun CachedBadge() {
    Badge(
        modifier = Modifier.height(32.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text("Cached", style = MaterialTheme.typography.labelSmall)
    }
}
