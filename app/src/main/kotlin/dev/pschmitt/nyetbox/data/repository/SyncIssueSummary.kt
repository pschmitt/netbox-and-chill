package dev.pschmitt.nyetbox.data.repository

private const val MAX_SYNC_REASON_LENGTH = 140

/** The distinct, per-item failure reasons behind a raw multi-line sync diagnostic message. */
internal fun syncIssueReasons(message: String): List<String> =
    message
        .lineSequence()
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter(String::isNotBlank)
        .map(::extractSyncReason)
        .distinct()
        .toList()

/** Turns verbose per-model sync diagnostics into one useful message for the UI. */
internal fun summarizeSyncIssueMessage(message: String): String {
    val reasons = syncIssueReasons(message)
    if (reasons.isEmpty()) return "Sync failed."
    if (reasons.all(::isCancellation)) return "Sync was cancelled."

    val primaryReason = reasons.first().trimEnd('.', ' ')
    val suffix =
        if (reasons.size > 1) {
            " (+${reasons.size - 1} other issue${if (reasons.size == 2) "" else "s"})"
        } else {
            ""
        }
    return "Sync failed: ${primaryReason.take(MAX_SYNC_REASON_LENGTH)}$suffix."
}

private fun extractSyncReason(line: String): String {
    val separator = line.indexOf(": ")
    if (separator > 0) {
        val scope = line.substring(0, separator).lowercase()
        if (scope.contains("sync") || scope.endsWith("failed") || scope.endsWith("failure")) {
            return line.substring(separator + 2).trim()
        }
    }
    return line
}

private fun isCancellation(reason: String): Boolean =
    reason.contains("cancel", ignoreCase = true) ||
        reason.contains("cancellation", ignoreCase = true)
