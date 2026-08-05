package dev.pschmitt.nyetbox.ui.dashboard

import dev.pschmitt.nyetbox.ui.common.formatRelativeSyncTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
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

    @Test
    fun `sync status hides while offline`() {
        assertFalse(shouldShowSyncStatus(offlineMode = true, hasSyncIssue = false))
    }

    @Test
    fun `sync status hides when an issue already covers status`() {
        assertFalse(shouldShowSyncStatus(offlineMode = false, hasSyncIssue = true))
    }

    @Test
    fun `sync status shows online with no issue`() {
        assertTrue(shouldShowSyncStatus(offlineMode = false, hasSyncIssue = false))
    }

    @Test
    fun `relative sync time reports never synced`() {
        assertEquals("Never synced yet", formatRelativeSyncTime(null, nowMillis = 1_000L))
    }

    @Test
    fun `relative sync time reports just now under a minute`() {
        val now = 1_000_000L
        assertEquals(
            "Last synced just now",
            formatRelativeSyncTime(now - TimeUnit.SECONDS.toMillis(30), now),
        )
    }

    @Test
    fun `relative sync time reports minutes`() {
        val now = 1_000_000L
        assertEquals(
            "Last synced 5m ago",
            formatRelativeSyncTime(now - TimeUnit.MINUTES.toMillis(5), now),
        )
    }

    @Test
    fun `relative sync time reports hours`() {
        val now = 1_000_000L
        assertEquals(
            "Last synced 3h ago",
            formatRelativeSyncTime(now - TimeUnit.HOURS.toMillis(3), now),
        )
    }

    @Test
    fun `relative sync time reports days`() {
        val now = 1_000_000L
        assertEquals(
            "Last synced 2d ago",
            formatRelativeSyncTime(now - TimeUnit.DAYS.toMillis(2), now),
        )
    }
}
