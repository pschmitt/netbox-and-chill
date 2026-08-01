package dev.pschmitt.netboxandchill.data.repository

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateObjectTest {
    @Test
    fun `parses writable POST metadata and skips read only fields`() {
        val fields =
            parseCreateFieldDefinitions(
                buildJsonObject {
                    put(
                        "actions",
                        buildJsonObject {
                            put(
                                "POST",
                                buildJsonObject {
                                    put(
                                        "name",
                                        buildJsonObject {
                                            put("type", "string")
                                            put("label", "Name")
                                        },
                                    )
                                    put(
                                        "site",
                                        buildJsonObject {
                                            put("type", "nested object")
                                            put("required", true)
                                        },
                                    )
                                    put(
                                        "id",
                                        buildJsonObject {
                                            put("type", "integer")
                                            put("read_only", true)
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            )

        assertEquals(listOf("name", "site"), fields.map { it.key })
        assertTrue(fields.first { it.key == "site" }.required)
        assertEquals("api/dcim/sites/", fields.first { it.key == "site" }.referenceEndpointPath)
    }

    @Test
    fun `falls back to PUT metadata when the server omits POST`() {
        val fields =
            parseCreateFieldDefinitions(
                buildJsonObject {
                    put(
                        "actions",
                        buildJsonObject {
                            put(
                                "PUT",
                                buildJsonObject {
                                    put(
                                        "name",
                                        buildJsonObject {
                                            put("type", "string")
                                            put("read_only", false)
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            )
        assertEquals(listOf("name"), fields.map { it.key })
    }

    @Test
    fun `builds typed values and rejects missing required fields`() {
        val fields =
            listOf(
                CreateFieldDefinition("name", "Name", "string", false, null, emptyList(), null),
                CreateFieldDefinition(
                    "site",
                    "Site",
                    "nested object",
                    true,
                    null,
                    emptyList(),
                    "api/dcim/sites/",
                ),
                CreateFieldDefinition(
                    "active",
                    "Active",
                    "boolean",
                    false,
                    null,
                    emptyList(),
                    null,
                ),
            )
        assertTrue(buildCreateBody(fields, mapOf("name" to "Router", "active" to "true")).isFailure)
        val body =
            buildCreateBody(fields, mapOf("name" to "Router", "site" to "3", "active" to "true"))
                .getOrThrow()
        assertEquals(JsonPrimitive("Router"), body["name"])
        assertEquals(JsonPrimitive(3), body["site"])
        assertEquals(JsonPrimitive(true), body["active"])
    }
}
