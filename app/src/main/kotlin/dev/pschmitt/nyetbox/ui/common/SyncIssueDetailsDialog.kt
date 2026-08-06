package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.BuildConfig
import dev.pschmitt.nyetbox.data.repository.ServerProfile
import dev.pschmitt.nyetbox.data.repository.SyncIssue
import dev.pschmitt.nyetbox.data.repository.syncIssueReasons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Everything useful for a bug report about a sync failure, not just the one-line summary shown on
 * the card - app build info, which server was involved, and every distinct failure reason (not only
 * the primary one the card truncates to).
 */
fun buildSyncIssueReport(
    issue: SyncIssue,
    server: ServerProfile?,
    offlineMode: Boolean,
): String {
    val occurredAt =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(issue.occurredAt))
    val reasons = syncIssueReasons(issue.details)
    return buildString {
        appendLine("Nyetbox sync issue report")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_REVISION})")
        appendLine("Built: ${BuildConfig.BUILD_DATE}")
        appendLine("Server: ${server?.displayName ?: "unknown"} (${server?.baseUrl ?: "unknown"})")
        appendLine("Occurred: $occurredAt")
        appendLine("Offline mode: $offlineMode")
        appendLine()
        appendLine("Reasons (${reasons.size}):")
        reasons.forEachIndexed { index, reason -> appendLine("${index + 1}. $reason") }
    }
}

/**
 * The full breakdown behind a [SyncIssueCard]'s one-line summary - [SyncIssue.details] is the raw,
 * pre-summarization diagnostic text, one failure per line, which [syncIssueReasons] turns back into
 * the same distinct, per-item reasons the summary was derived from.
 */
@Composable
fun SyncIssueDetailsDialog(issue: SyncIssue, onDismiss: () -> Unit, onCopyLogs: () -> Unit) {
    val reasons = remember(issue.details) { syncIssueReasons(issue.details) }
    val occurredAt =
        remember(issue.occurredAt) {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(issue.occurredAt))
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = { Text("Sync issue details") },
        text = {
            Column {
                Text(
                    occurredAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(reasons) { index, reason ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                reason,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = onCopyLogs) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Copy logs")
            }
        },
    )
}
