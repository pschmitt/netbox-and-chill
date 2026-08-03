package dev.pschmitt.nyetbox.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchOfflineTest {
    @Test
    fun offlineModeNeverStartsLiveRefresh() {
        assertFalse(shouldRefreshGlobalSearch("router", offlineMode = true))
    }

    @Test
    fun onlineNonEmptyQueriesMayRefreshCache() {
        assertTrue(shouldRefreshGlobalSearch("router", offlineMode = false))
    }

    @Test
    fun blankQueriesNeverRefresh() {
        assertFalse(shouldRefreshGlobalSearch("  ", offlineMode = false))
    }
}
