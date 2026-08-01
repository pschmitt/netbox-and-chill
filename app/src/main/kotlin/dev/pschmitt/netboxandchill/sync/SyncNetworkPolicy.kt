package dev.pschmitt.netboxandchill.sync

import androidx.work.NetworkType

/** Maps the user-facing network preferences to WorkManager's available network constraints. */
object SyncNetworkPolicy {
    fun requiredNetworkType(syncOnlyOnWifi: Boolean, syncWhileRoaming: Boolean): NetworkType =
        when {
            syncOnlyOnWifi -> NetworkType.UNMETERED
            !syncWhileRoaming -> NetworkType.NOT_ROAMING
            else -> NetworkType.CONNECTED
        }
}
