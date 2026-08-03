package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pschmitt.netboxandchill.data.repository.SyncIssue

@Composable
fun SyncIssueCard(
    issue: SyncIssue,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    isSyncing: Boolean = false,
) {
    var retryRequested by remember(issue.occurredAt) { mutableStateOf(false) }
    val retryActive = retryRequested || isSyncing
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Sync issue", style = MaterialTheme.typography.titleMedium)
                Text(issue.message, style = MaterialTheme.typography.bodyMedium)
                onRetry?.let { retry ->
                    Button(
                        onClick = {
                            retryRequested = true
                            retry()
                        },
                        enabled = !retryActive,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        if (retryActive) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (retryActive) "Retrying sync…" else "Retry sync")
                    }
                    if (retryActive) {
                        Text(
                            if (isSyncing) "Sync is running…" else "Retry queued…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
