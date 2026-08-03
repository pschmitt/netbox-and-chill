package dev.pschmitt.nyetbox.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStartupPolicyTest {
    @Test
    fun startupSyncIsEnabledByDefaultWhenOnline() {
        assertTrue(shouldScheduleStartup(syncOnAppLaunch = true, offlineMode = false))
    }

    @Test
    fun disabledPreferenceDoesNotAffectOtherSyncPaths() {
        assertFalse(shouldScheduleStartup(syncOnAppLaunch = false, offlineMode = false))
    }

    @Test
    fun offlineModeSuppressesLaunchSync() {
        assertFalse(shouldScheduleStartup(syncOnAppLaunch = true, offlineMode = true))
    }
}
