package dev.pschmitt.netboxandchill.ui.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** State shared by screens whose cached content must remain usable during a refresh failure. */
data class CacheFirstRefreshState(
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Run a best-effort refresh without making the network the source of truth for the screen.
 *
 * The caller owns the cached data and only applies a successful result. Failures become a
 * user-facing message, while cancellation is propagated so collectLatest can replace an old
 * query cleanly. The helper also prevents overlapping refreshes for a single state holder.
 */
suspend fun <T> MutableStateFlow<CacheFirstRefreshState>.runCacheFirstRefresh(
    operation: suspend () -> Result<T>,
    errorMessage: (Throwable) -> String,
): Result<T>? {
    if (value.isRefreshing) return null
    update { it.copy(isRefreshing = true, errorMessage = null) }

    val result =
        try {
            operation()
        } catch (cancelled: CancellationException) {
            update { it.copy(isRefreshing = false) }
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }

    result.fold(
        onSuccess = { update { it.copy(isRefreshing = false, errorMessage = null) } },
        onFailure = { error ->
            update { it.copy(isRefreshing = false, errorMessage = errorMessage(error)) }
        },
    )
    return result
}
