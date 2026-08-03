package dev.pschmitt.nyetbox.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncIssueSummaryTest {
    @Test
    fun `repeated cancellation messages become one short message`() {
        val message =
            """
            Job was cancelled
            Device type 3 sync: Job was cancelled
            Device type 7 sync: Job was cancelled
            Device type 20 sync: Job was cancelled
            """.trimIndent()

        assertEquals("Sync was cancelled.", summarizeSyncIssueMessage(message))
    }

    @Test
    fun `different failures retain one useful reason and count the rest`() {
        val message =
            """
            Device sync: Read timed out
            Device type 3 sync: HTTP 500 Server Error
            """.trimIndent()

        assertEquals(
            "Sync failed: Read timed out (+1 other issue).",
            summarizeSyncIssueMessage(message),
        )
    }

    @Test
    fun `old persisted verbose message is shortened too`() {
        assertEquals(
            "Sync failed: Read timed out.",
            summarizeSyncIssueMessage("Device sync: Read timed out"),
        )
    }
}
