package dev.pschmitt.nyetbox.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RackElevationRepositoryTest {

    private fun parse(rawJson: String): JsonObject =
        Json.decodeFromString(JsonObject.serializer(), rawJson)

    @Test
    fun `parses occupied rack slots and nested device display`() {
        val slot =
            parse(
                    """{
                    "id": 7.5,
                    "name": "U7.5",
                    "occupied": true,
                    "device": {"id": 15, "display": "tesmart-kvm-switch"}
                }"""
                )
                .toRackElevationEntity(1, "front")

        assertEquals(7.5, slot!!.position, 0.0)
        assertEquals("U7.5", slot.slotName)
        assertEquals(15, slot.deviceId)
        assertEquals("tesmart-kvm-switch", slot.deviceDisplay)
        assertEquals("front", slot.face)
    }

    @Test
    fun `parses empty slots and falls back to slot name position`() {
        val slot =
            parse(
                    """{
                    "name": "U3.0",
                    "occupied": false,
                    "device": null
                }"""
                )
                .toRackElevationEntity(4, "rear")

        assertEquals(3.0, slot!!.position, 0.0)
        assertEquals(false, slot.occupied)
        assertNull(slot.deviceId)
        assertNull(slot.deviceDisplay)
    }
}
