package dev.pschmitt.nyetbox.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pschmitt.nyetbox.sync.SyncProgress
import dev.pschmitt.nyetbox.sync.notificationSubText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

/**
 * A persistent, low-key "when did this last sync" indicator for the dashboard - shown in place of
 * [SyncIssueCard] whenever there is no active issue to report (see
 * DashboardScreen.shouldShowSyncStatus), so the two never compete for the same slot.
 *
 * While syncing, renders the same step/message/item-count detail as [SyncNotifier]'s system
 * notification (NBC-370) via [syncProgress], instead of a generic spinner + "Syncing…" - falls back
 * to that generic text only for the brief window before the first [SyncProgress] arrives.
 */
@Composable
fun SyncStatusCard(
    lastSuccessfulSyncAt: Long?,
    isSyncing: Boolean,
    modifier: Modifier = Modifier,
    syncProgress: SyncProgress? = null,
    nowMillis: Long = System.currentTimeMillis(),
) {
    NyetboxCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    syncStatusHeadline(isSyncing, syncProgress),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    syncStatusSubText(isSyncing, syncProgress, lastSuccessfulSyncAt, nowMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The card's headline: the current sync step's message while syncing, else "Synced". */
internal fun syncStatusHeadline(isSyncing: Boolean, syncProgress: SyncProgress?): String =
    when {
        !isSyncing -> "Synced"
        syncProgress != null -> syncProgress.message
        else -> "Syncing…"
    }

/**
 * The card's secondary line: the same "Step X of Y · N of M items" detail shown in the system
 * notification (see [notificationSubText]) while syncing, else the usual last-synced timestamp.
 */
internal fun syncStatusSubText(
    isSyncing: Boolean,
    syncProgress: SyncProgress?,
    lastSuccessfulSyncAt: Long?,
    nowMillis: Long,
): String =
    if (isSyncing && syncProgress != null) {
        syncProgress.notificationSubText()
    } else {
        formatRelativeSyncTime(lastSuccessfulSyncAt, nowMillis)
    }

internal fun formatRelativeSyncTime(lastSuccessfulSyncAt: Long?, nowMillis: Long): String {
    if (lastSuccessfulSyncAt == null) return "Never synced yet"
    val deltaMillis = (nowMillis - lastSuccessfulSyncAt).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(deltaMillis)
    val hours = TimeUnit.MILLISECONDS.toHours(deltaMillis)
    val days = TimeUnit.MILLISECONDS.toDays(deltaMillis)
    return when {
        minutes < 1 -> "Last synced just now"
        minutes < 60 -> "Last synced ${minutes}m ago"
        hours < 24 -> "Last synced ${hours}h ago"
        days < 7 -> "Last synced ${days}d ago"
        else ->
            "Last synced on " +
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(lastSuccessfulSyncAt))
    }
}
