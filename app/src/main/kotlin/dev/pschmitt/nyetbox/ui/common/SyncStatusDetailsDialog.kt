package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.sync.SyncProgress
import dev.pschmitt.nyetbox.ui.settings.formatBytes

/**
 * Read-only snapshot of local cache size, computed on demand (see
 * `DashboardViewModel.loadCacheSummary`) rather than kept continuously up to date, since it's only
 * ever shown after an explicit tap on [SyncStatusCard] - the same figures Settings' "Cached data"
 * card computes.
 */
data class CacheSummary(
    val deviceCount: Int,
    val objectCount: Int,
    val imageCount: Int,
    val fileCount: Int,
    val bytes: Long,
)

/**
 * The positive counterpart to [SyncIssueDetailsDialog] - shown from [SyncStatusCard] instead of a
 * bare "Synced"/"Syncing…" line, with the same cache figures Settings' "Cached data" card shows
 * (device/object/image counts, downloaded-file count and size) so a tap on the dashboard's status
 * card is useful even when nothing is actually wrong.
 */
@Composable
fun SyncStatusDetailsDialog(
    isSyncing: Boolean,
    syncProgress: SyncProgress?,
    lastSuccessfulSyncAt: Long?,
    cacheSummary: CacheSummary?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        title = { Text(syncStatusHeadline(isSyncing, syncProgress)) },
        text = {
            Column {
                Text(
                    syncStatusSubText(isSyncing, syncProgress, lastSuccessfulSyncAt, System.currentTimeMillis()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Text("Cached data", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                if (cacheSummary == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Calculating…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    CacheSummaryRow("Devices", cacheSummary.deviceCount.toString())
                    CacheSummaryRow("Other objects", cacheSummary.objectCount.toString())
                    CacheSummaryRow("Image records", cacheSummary.imageCount.toString())
                    CacheSummaryRow(
                        "Downloaded files",
                        "${cacheSummary.fileCount} (${formatBytes(cacheSummary.bytes)})",
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun CacheSummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
