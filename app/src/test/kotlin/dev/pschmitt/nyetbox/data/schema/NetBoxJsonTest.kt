package dev.pschmitt.nyetbox.data.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetBoxJsonTest {
    @Test
    fun `reference accepts display and falls back to name`() {
        val withDisplay =
            Json.parseToJsonElement(
                """{"device":{"id":7,"url":"/api/dcim/devices/7/","display":"edge-7"}}"""
            ) as JsonObject
        val withName =
            Json.parseToJsonElement("""{"manufacturer":{"id":3,"name":"Shelly"}}""") as JsonObject

        assertEquals(
            NetBoxJsonReference(7, "/api/dcim/devices/7/", "edge-7"),
            withDisplay.jsonReference("device"),
        )
        assertEquals(
            NetBoxJsonReference(3, display = "Shelly"),
            withName.jsonReference("manufacturer"),
        )
    }

    @Test
    fun `common projections tolerate missing and non primitive fields`() {
        val value =
            Json.parseToJsonElement("""{"id":"bad","status":{"label":"Active"}}""") as JsonObject

        assertNull(value.jsonInt("id"))
        assertNull(value.jsonReference("status"))
        assertEquals("Active", (value["status"] as JsonObject).jsonString("label"))
    }
}
