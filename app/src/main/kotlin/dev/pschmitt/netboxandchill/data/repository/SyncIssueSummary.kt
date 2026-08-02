package dev.pschmitt.netboxandchill.data.repository

private const val MAX_SYNC_REASON_LENGTH = 140

/** Turns verbose per-model sync diagnostics into one useful message for the UI. */
internal fun summarizeSyncIssueMessage(message: String): String {
    val lines =
        message
            .lineSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter(String::isNotBlank)
            .toList()
    if (lines.isEmpty()) return "Sync failed."

    val reasons = lines.map(::extractSyncReason).distinct()
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
