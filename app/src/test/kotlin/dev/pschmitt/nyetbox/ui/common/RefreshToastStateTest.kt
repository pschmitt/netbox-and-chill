package dev.pschmitt.nyetbox.ui.common

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshToastStateTest {
    @Test
    fun `running refresh has no terminal toast`() {
        assertNull(refreshCompletionToast(WorkInfo.State.RUNNING))
    }

    @Test
    fun `successful refresh reports completion`() {
        assertEquals("Sync complete", refreshCompletionToast(WorkInfo.State.SUCCEEDED))
    }

    @Test
    fun `failed refresh reports failure`() {
        assertEquals("Sync failed", refreshCompletionToast(WorkInfo.State.FAILED))
        assertEquals("Sync failed", refreshCompletionToast(WorkInfo.State.CANCELLED))
    }

    @Test
    fun offlineRefreshNeverReportsQueued() {
        assertEquals(
            false,
            shouldShowRefreshQueuedToast(showConfirmation = true, offlineMode = true),
        )
        assertEquals(
            true,
            shouldShowRefreshQueuedToast(showConfirmation = true, offlineMode = false),
        )
    }
}
