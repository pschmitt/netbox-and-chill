package dev.pschmitt.nyetbox.sync

import javax.inject.Inject
import javax.inject.Singleton

/** Collects non-fatal sync warnings until the complete sync can persist them as one issue. */
@Singleton
class SyncIssueReporter @Inject constructor() {
    private val warnings = linkedSetOf<String>()

    @Synchronized
    fun report(message: String) {
        if (message.isNotBlank()) warnings += message
    }

    @Synchronized fun drain(): List<String> = warnings.toList().also { warnings.clear() }
}
