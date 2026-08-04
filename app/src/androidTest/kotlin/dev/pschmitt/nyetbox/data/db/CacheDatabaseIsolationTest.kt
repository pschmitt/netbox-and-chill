package dev.pschmitt.nyetbox.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pschmitt.nyetbox.data.repository.ServerProfile
import dev.pschmitt.nyetbox.data.repository.SettingsRepository
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheDatabaseIsolationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun switchingProfilesKeepsRowsInTheirOwnRoomFiles() = runTest {
        val manager = CacheDatabaseManager(context, SettingsRepository(context))
        val suffix = UUID.randomUUID().toString()
        val first = testProfile("first-$suffix")
        val second = testProfile("second-$suffix")
        try {
            manager.switchTo(first)
            manager.activeDatabase.value.deviceDao().upsert(device(id = 1, name = "first"))

            manager.switchTo(second)
            assertEquals(
                emptyList<DeviceEntity>(),
                manager.activeDatabase.value.deviceDao().getAll(),
            )
            manager.activeDatabase.value.deviceDao().upsert(device(id = 2, name = "second"))

            manager.switchTo(first)
            assertEquals(
                listOf("first"),
                manager.activeDatabase.value.deviceDao().getAll().map { it.name },
            )
            manager.switchTo(second)
            assertEquals(
                listOf("second"),
                manager.activeDatabase.value.deviceDao().getAll().map { it.name },
            )
        } finally {
            manager.delete(first)
            manager.delete(second)
        }
    }

    private fun testProfile(id: String) =
        ServerProfile(
            id = id,
            displayName = id,
            baseUrl = "https://$id.invalid",
            token = "token",
            cacheDatabaseName = "$id.db",
            cacheNamespace = id,
        )

    private fun device(id: Int, name: String) =
        DeviceEntity(
            id = id,
            name = name,
            url = "",
            statusValue = null,
            statusLabel = null,
            siteName = null,
            siteId = null,
            rackName = null,
            rackId = null,
            position = null,
            roleName = null,
            manufacturerName = null,
            deviceTypeModel = null,
            deviceTypeId = null,
            serial = null,
            assetTag = null,
            primaryIp = null,
            comments = null,
            lastUpdated = null,
            syncedAt = 1L,
        )
}
