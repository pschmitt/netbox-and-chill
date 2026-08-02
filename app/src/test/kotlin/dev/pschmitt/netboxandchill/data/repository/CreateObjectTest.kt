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

    @Test
    fun `parses custom field administration metadata into typed controls`() {
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
                                        "object_types",
                                        buildJsonObject {
                                            put("type", "field")
                                            put("required", true)
                                        },
                                    )
                                    put(
                                        "type",
                                        buildJsonObject {
                                            put("type", "field")
                                            put(
                                                "choices",
                                                kotlinx.serialization.json.buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            put("value", "text")
                                                            put("display_name", "Text")
                                                        }
                                                    )
                                                },
                                            )
                                        },
                                    )
                                    put(
                                        "default",
                                        buildJsonObject {
                                            put("type", "field")
                                            put("help_text", "Enter JSON")
                                        },
                                    )
                                    put(
                                        "choice_set",
                                        buildJsonObject {
                                            put("type", "nested object")
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            )

        assertTrue(fields.first { it.key == "object_types" }.multiple)
        assertEquals("json", fields.first { it.key == "default" }.type)
        assertEquals(
            "api/extras/custom-field-choice-sets/",
            fields.first { it.key == "choice_set" }.referenceEndpointPath,
        )
        assertEquals("Enter JSON", fields.first { it.key == "default" }.helpText)
    }

    @Test
    fun `builds custom field object types and JSON defaults`() {
        val fields =
            listOf(
                CreateFieldDefinition(
                    "object_types",
                    "Object types",
                    "field",
                    true,
                    null,
                    emptyList(),
                    null,
                    multiple = true,
                ),
                CreateFieldDefinition(
                    "default",
                    "Default",
                    "json",
                    false,
                    null,
                    emptyList(),
                    null,
                ),
            )
        val body =
            buildCreateBody(
                    fields,
                    mapOf("object_types" to """["dcim.device","dcim.rack"]""", "default" to "false"),
                )
                    .getOrThrow()

        assertEquals(
            kotlinx.serialization.json.JsonArray(
                listOf(JsonPrimitive("dcim.device"), JsonPrimitive("dcim.rack"))
            ),
            body["object_types"],
        )
        assertEquals(JsonPrimitive(false), body["default"])
    }
}
