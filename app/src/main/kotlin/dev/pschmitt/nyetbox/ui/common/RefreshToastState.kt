package dev.pschmitt.nyetbox.ui.common

import androidx.work.WorkInfo

internal const val REFRESH_QUEUED_TOAST = "Sync queued"

internal fun shouldShowRefreshQueuedToast(
    showConfirmation: Boolean,
    offlineMode: Boolean,
): Boolean = showConfirmation && !offlineMode

/** Returns the terminal toast for a sync job, or null while it is still running. */
internal fun refreshCompletionToast(state: WorkInfo.State): String? =
    when {
        !state.isFinished -> null
        state == WorkInfo.State.SUCCEEDED -> "Sync complete"
        else -> "Sync failed"
    }
