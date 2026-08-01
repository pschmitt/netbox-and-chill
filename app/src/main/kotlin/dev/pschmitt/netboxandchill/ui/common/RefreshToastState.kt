package dev.pschmitt.netboxandchill.ui.common

import androidx.work.WorkInfo

internal const val REFRESH_QUEUED_TOAST = "Refresh queued"

/** Returns the terminal toast for a refresh job, or null while it is still running. */
internal fun refreshCompletionToast(state: WorkInfo.State): String? =
    when {
        !state.isFinished -> null
        state == WorkInfo.State.SUCCEEDED -> "Refresh complete"
        else -> "Refresh failed"
    }
