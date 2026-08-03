package dev.pschmitt.nyetbox.data.repository

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeNotificationTest {
    @Test
    fun defaultAllFilterMatchesEveryActionAndObjectType() {
        val event = event(action = "delete", objectType = "dcim.cable")

        assertTrue(ChangeNotificationFilter.All.matches(event))
        assertEquals(ChangeNotificationFilter.All, ChangeNotificationFilter.fromStorage("all"))
    }

    @Test
    fun specificFiltersMatchBothActionAndObjectType() {
        val deletedCable = event(action = "delete", objectType = "dcim.cable")
        val createdCable = event(action = "create", objectType = "dcim.cable")
        val deletedDevice = event(action = "delete", objectType = "dcim.device")

        assertEquals(
            listOf(deletedCable),
            matchingChangeNotificationEvents(
                listOf(deletedCable, createdCable, deletedDevice),
                setOf(ChangeNotificationFilter.CableDeleted),
            ),
        )
    }

    @Test
    fun rawObjectChangeDataUsesChangedObjectTypeAndSupportsDeletes() {
        val raw =
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive(14831),
                    "action" to
                        JsonObject(
                            mapOf(
                                "value" to JsonPrimitive("delete"),
                                "label" to JsonPrimitive("Deleted"),
                            )
                        ),
                    "changed_object_type" to JsonPrimitive("dcim.device"),
                    "object_repr" to JsonPrimitive("Disposable device"),
                )
            )

        val event = raw.toChangeNotificationEvent()

        assertEquals("dcim.device", event?.objectType)
        assertEquals("delete", event?.actionValue)
        assertEquals("Disposable device", event?.objectRepr)
        assertNull(JsonObject(mapOf("id" to JsonPrimitive(1))).toChangeNotificationEvent())
    }

    private fun event(action: String, objectType: String) =
        ChangeNotificationEvent(
            id = 1,
            actionValue = action,
            actionLabel = action,
            objectType = objectType,
            objectRepr = "Fixture",
        )
}
