package dev.pschmitt.nyetbox.ui.common

import androidx.work.WorkInfo

internal const val REFRESH_QUEUED_TOAST = "Refresh queued"

internal fun shouldShowRefreshQueuedToast(showConfirmation: Boolean, offlineMode: Boolean): Boolean =
    showConfirmation && !offlineMode

/** Returns the terminal toast for a refresh job, or null while it is still running. */
internal fun refreshCompletionToast(state: WorkInfo.State): String? =
    when {
        !state.isFinished -> null
        state == WorkInfo.State.SUCCEEDED -> "Refresh complete"
        else -> "Refresh failed"
    }
