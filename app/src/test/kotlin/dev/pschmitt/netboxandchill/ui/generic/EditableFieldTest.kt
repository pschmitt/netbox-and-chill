package dev.pschmitt.netboxandchill.ui.generic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableFieldTest {

    private fun parse(rawJson: String): JsonObject = Json.decodeFromString(JsonObject.serializer(), rawJson)

    @Test
    fun `excludes blocklisted meta fields`() {
        val fields =
            buildEditableFields(
                parse(
                    """{"id":1,"url":"https://x/api/dcim/racks/1/","display":"Rack 1",
                        "created":"2026-01-01","last_updated":"2026-01-01","custom_fields":{}}"""
                )
            )
        assertTrue(fields.isEmpty())
    }

    @Test
    fun `excludes nested objects and arrays`() {
        val fields = buildEditableFields(parse("""{"status":{"value":"active","label":"Active"},"tags":[]}"""))
        assertTrue(fields.isEmpty())
    }

    @Test
    fun `detects string fields`() {
        val fields = buildEditableFields(parse("""{"serial":"ABC123"}"""))
        assertEquals(listOf(EditableField("serial", "Serial", EditFieldKind.STRING, "ABC123")), fields)
    }

    @Test
    fun `detects a numeric-looking string as STRING not NUMBER`() {
        val fields = buildEditableFields(parse("""{"serial":"12345"}"""))
        assertEquals(EditFieldKind.STRING, fields.single().kind)
    }

    @Test
    fun `detects genuine JSON numbers as NUMBER`() {
        val fields = buildEditableFields(parse("""{"position":5.5}"""))
        assertEquals(EditFieldKind.NUMBER, fields.single().kind)
        assertEquals("5.5", fields.single().value)
    }

    @Test
    fun `detects genuine JSON booleans as BOOLEAN`() {
        val fields = buildEditableFields(parse("""{"is_full_depth":true}"""))
        assertEquals(EditFieldKind.BOOLEAN, fields.single().kind)
    }

    @Test
    fun `toJsonPrimitive round-trips each kind`() {
        assertEquals(JsonPrimitive("hello"), EditFieldKind.STRING.toJsonPrimitive("hello"))
        assertEquals(JsonPrimitive(5.5), EditFieldKind.NUMBER.toJsonPrimitive("5.5"))
        assertEquals(JsonPrimitive(true), EditFieldKind.BOOLEAN.toJsonPrimitive("true"))
        assertEquals(JsonPrimitive(false), EditFieldKind.BOOLEAN.toJsonPrimitive("nonsense"))
    }

    @Test
    fun `buildPatchBody produces a flat JsonObject of the edited fields`() {
        val body =
            buildPatchBody(
                mapOf(
                    "serial" to (EditFieldKind.STRING to "XYZ"),
                    "position" to (EditFieldKind.NUMBER to "3"),
                )
            )
        assertEquals(JsonPrimitive("XYZ"), body["serial"])
        assertEquals(JsonPrimitive(3.0), body["position"])
    }
}
