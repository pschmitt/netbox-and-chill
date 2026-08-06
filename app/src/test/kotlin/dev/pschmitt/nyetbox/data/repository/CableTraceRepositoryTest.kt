package dev.pschmitt.nyetbox.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CableTraceRepositoryTest {

    private fun cable(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `picks the a-side termination when it supports trace`() {
        val cable =
            cable(
                """
                {
                  "a_terminations": [
                    {"object_type": "dcim.interface", "object_id": 238,
                     "object": {"id": 238, "url": "https://netbox.example.com/api/dcim/interfaces/238/"}}
                  ],
                  "b_terminations": []
                }
                """
                    .trimIndent()
            )

        assertEquals("api/dcim/interfaces/" to 238, cableTraceStartTarget(cable))
    }

    @Test
    fun `falls back to the b-side termination when the a side is a rear port`() {
        // Mirrors a real KVM/USB-hub passthrough cable (NBC sync issue: rear-port trace 404s).
        val cable =
            cable(
                """
                {
                  "a_terminations": [
                    {"object_type": "dcim.rearport", "object_id": 56,
                     "object": {"id": 56, "url": "https://netbox.example.com/api/dcim/rear-ports/56/"}}
                  ],
                  "b_terminations": [
                    {"object_type": "dcim.interface", "object_id": 238,
                     "object": {"id": 238, "url": "https://netbox.example.com/api/dcim/interfaces/238/"}}
                  ]
                }
                """
                    .trimIndent()
            )

        assertEquals("api/dcim/interfaces/" to 238, cableTraceStartTarget(cable))
    }

    @Test
    fun `falls back to the b-side termination when the a side is a front port`() {
        val cable =
            cable(
                """
                {
                  "a_terminations": [
                    {"object_type": "dcim.frontport", "object_id": 31,
                     "object": {"id": 31, "url": "https://netbox.example.com/api/dcim/front-ports/31/"}}
                  ],
                  "b_terminations": [
                    {"object_type": "dcim.consoleport", "object_id": 9,
                     "object": {"id": 9, "url": "https://netbox.example.com/api/dcim/console-ports/9/"}}
                  ]
                }
                """
                    .trimIndent()
            )

        assertEquals("api/dcim/console-ports/" to 9, cableTraceStartTarget(cable))
    }

    @Test
    fun `returns null when both sides are passthrough ports`() {
        val cable =
            cable(
                """
                {
                  "a_terminations": [
                    {"object_type": "dcim.frontport", "object_id": 31,
                     "object": {"id": 31, "url": "https://netbox.example.com/api/dcim/front-ports/31/"}}
                  ],
                  "b_terminations": [
                    {"object_type": "dcim.rearport", "object_id": 56,
                     "object": {"id": 56, "url": "https://netbox.example.com/api/dcim/rear-ports/56/"}}
                  ]
                }
                """
                    .trimIndent()
            )

        assertNull(cableTraceStartTarget(cable))
    }
}
