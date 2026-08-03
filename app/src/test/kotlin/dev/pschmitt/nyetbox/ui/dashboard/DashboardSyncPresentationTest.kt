package dev.pschmitt.nyetbox.ui.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSyncPresentationTest {
    @Test
    fun `offline mode hides stale sync issue details`() {
        assertFalse(shouldShowSyncIssue(offlineMode = true))
    }

    @Test
    fun `online mode keeps sync issue details visible`() {
        assertTrue(shouldShowSyncIssue(offlineMode = false))
    }
}
