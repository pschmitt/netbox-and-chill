package dev.pschmitt.netboxandchill.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardJsonAdaptersTest {
    @Test
    fun `bookmark adapter projects nested object reference`() {
        val payload =
            Json.parseToJsonElement(
                """
                {
                  "id": 4,
                  "object_type": "dcim.device",
                  "created": "2026-08-03T10:00:00Z",
                  "object": {
                    "id": 19,
                    "url": "https://netbox.test/api/dcim/devices/19/",
                    "display": "edge-19"
                  }
                }
                """
            ) as JsonObject

        val bookmark = payload.toBookmarkEntity(syncedAt = 99L)

        requireNotNull(bookmark)
        assertEquals("edge-19", bookmark.display)
        assertEquals("dcim.device", bookmark.objectType)
        assertEquals("api/dcim/devices/", bookmark.targetEndpointPath)
        assertEquals(19, bookmark.targetId)
        assertEquals(99L, bookmark.syncedAt)
    }

    @Test
    fun `change adapter preserves delete rows without a target`() {
        val payload =
            Json.parseToJsonElement(
                """
                {
                  "id": 8,
                  "time": "2026-08-03T10:00:00Z",
                  "user": {"id": 2, "display": "Ada"},
                  "action": {"value": "delete", "label": "Deleted"},
                  "object_repr": "old-device"
                }
                """
            ) as JsonObject

        val change = payload.toObjectChangeEntity(syncedAt = 101L)

        requireNotNull(change)
        assertEquals("Ada", change.userDisplay)
        assertEquals("Deleted", change.actionLabel)
        assertEquals("old-device", change.objectRepr)
        assertNull(change.targetEndpointPath)
        assertNull(change.targetId)
    }

    @Test
    fun `change adapter projects the cache query key for a live object`() {
        val payload =
            Json.parseToJsonElement(
                """
                {
                  "id": 9,
                  "time": "2026-08-03T10:00:00Z",
                  "changed_object": {
                    "id": 19,
                    "url": "https://netbox.test/api/dcim/devices/19/"
                  },
                  "action": {"value": "update", "label": "Updated"}
                }
                """
            ) as JsonObject

        val change = payload.toObjectChangeEntity(syncedAt = 101L)

        requireNotNull(change)
        assertEquals("api/dcim/devices/", change.targetEndpointPath)
        assertEquals(19, change.targetId)
    }

    @Test
    fun `adapters reject records without numeric ids`() {
        val payload = Json.parseToJsonElement("""{"id":"unknown"}""") as JsonObject

        assertNull(payload.toBookmarkEntity(syncedAt = 1L))
        assertNull(payload.toObjectChangeEntity(syncedAt = 1L))
    }
}
