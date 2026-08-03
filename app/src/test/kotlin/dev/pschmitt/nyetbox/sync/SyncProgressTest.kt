package dev.pschmitt.nyetbox.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncProgressTest {
    @Test
    fun `formats attachment progress for the notification`() {
        val progress =
            SyncProgress(
                message = "Downloading cached images and documents…",
                step = 8,
                totalSteps = 12,
                itemLabel = "images/documents",
                itemCompleted = 7,
                itemTotal = 19,
            )

        assertEquals("7 of 19 images/documents", progress.itemProgressText())
        assertEquals("Step 8 of 12 · 7 of 19 images/documents", progress.notificationSubText())
        assertEquals(
            "Downloading cached images and documents…\n7 of 19 images/documents",
            progress.notificationText(),
        )
    }

    @Test
    fun `formats stage progress without an item count`() {
        val progress = SyncProgress("Syncing devices…", step = 3, totalSteps = 10)

        assertEquals("Step 3 of 10", progress.notificationSubText())
        assertEquals("Syncing devices…", progress.notificationText())
    }
}
