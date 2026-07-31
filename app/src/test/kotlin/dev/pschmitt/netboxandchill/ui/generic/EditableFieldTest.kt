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
    fun `excludes unrecognized nested objects and arrays`() {
        val fields = buildEditableFields(parse("""{"weight":{"value":5,"unit":"kg"},"tags":[]}"""))
        assertTrue(fields.isEmpty())
    }

    @Test
    fun `detects a reference field with its endpoint and display`() {
        val field =
            buildEditableFields(
                parse(
                    """{"site":{"id":7,"url":"https://x/api/dcim/sites/7/","display":"Berlin"}}"""
                )
            ).single()
        assertEquals(EditFieldKind.REFERENCE, field.kind)
        assertEquals("7", field.value)
        assertEquals("api/dcim/sites/", field.referenceEndpointPath)
        assertEquals("Berlin", field.currentDisplay)
    }

    @Test
    fun `detects a choice field and keeps its wire value`() {
        val field = buildEditableFields(parse("""{"status":{"value":"active","label":"Active"}}""")).single()
        assertEquals(EditFieldKind.CHOICE, field.kind)
        assertEquals("active", field.value)
        assertEquals("Active", field.currentDisplay)
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
    fun `reference and choice values use NetBox PATCH wire formats`() {
        assertEquals(JsonPrimitive(7), EditFieldKind.REFERENCE.toJsonElement("7"))
        assertEquals(JsonPrimitive("active"), EditFieldKind.CHOICE.toJsonElement("active"))
        assertEquals(kotlinx.serialization.json.JsonNull, EditFieldKind.REFERENCE.toJsonElement(""))
    }

    @Test
    fun `reads choice metadata from either PATCH or PUT OPTIONS actions`() {
        val response =
            parse(
                """{"actions":{"PUT":{"status":{"choices":[{"value":"active","display_name":"Active"}]}}}}"""
            )
        assertEquals(mapOf("status" to listOf(EditOption("active", "Active"))), parseChoiceOptions(response))
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
