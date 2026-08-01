package dev.pschmitt.netboxandchill.ui.generic

import dev.pschmitt.netboxandchill.data.repository.CustomFieldDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableFieldTest {

    private fun parse(rawJson: String): JsonObject =
        Json.decodeFromString(JsonObject.serializer(), rawJson)

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
                )
                .single()
        assertEquals(EditFieldKind.REFERENCE, field.kind)
        assertEquals("7", field.value)
        assertEquals("api/dcim/sites/", field.referenceEndpointPath)
        assertEquals("Berlin", field.currentDisplay)
    }

    @Test
    fun `resolves linked IDs to labels in an edit review`() {
        val field =
            EditableField(
                key = "role",
                label = "Role",
                kind = EditFieldKind.REFERENCE,
                value = "7",
                referenceEndpointPath = "api/dcim/device-roles/",
                currentDisplay = "Access point",
            )
        val options =
            mapOf(
                "role" to
                    listOf(
                        EditOption("7", "Access point"),
                        EditOption("9", "Router"),
                    )
            )

        assertEquals("Access point", displayEditValue(field, field.value, options))
        assertEquals("Router", displayEditValue(field, "9", options))
    }

    @Test
    fun `resolves multi-linked IDs to labels in an edit review`() {
        val field =
            EditableField(
                key = "sites",
                label = "Sites",
                kind = EditFieldKind.MULTI_REFERENCE,
                value = "[7]",
                currentDisplay = "Berlin",
            )
        val options = mapOf("sites" to listOf(EditOption("7", "Berlin"), EditOption("9", "Paris")))

        assertEquals("Berlin", displayEditValue(field, "[7]", options))
        assertEquals("Berlin, Paris", displayEditValue(field, "[7,9]", options))
    }

    @Test
    fun `detects a choice field and keeps its wire value`() {
        val field =
            buildEditableFields(parse("""{"status":{"value":"active","label":"Active"}}"""))
                .single()
        assertEquals(EditFieldKind.CHOICE, field.kind)
        assertEquals("active", field.value)
        assertEquals("Active", field.currentDisplay)
    }

    @Test
    fun `detects string fields`() {
        val fields = buildEditableFields(parse("""{"serial":"ABC123"}"""))
        assertEquals(
            listOf(EditableField("serial", "Serial", EditFieldKind.STRING, "ABC123")),
            fields,
        )
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
    fun `detects type-aware custom fields`() {
        val fields =
            buildEditableFields(
                parse(
                    """{"custom_fields":{"purchase_notes":"- inspected","purchase_price":12.5,"purchase_date":"2026-01-01","purchase_currency":"EUR","is_managed":true}}"""
                ),
                listOf(
                    CustomFieldDefinition("purchase_notes", "longtext", "Notes", null, 1),
                    CustomFieldDefinition("purchase_price", "decimal", "Price", null, 2),
                    CustomFieldDefinition("purchase_date", "date", "Date", null, 3),
                    CustomFieldDefinition("purchase_currency", "select", "Currency", null, 4),
                    CustomFieldDefinition("is_managed", "boolean", "Managed", null, 5),
                ),
            )

        assertEquals(
            listOf(
                EditFieldKind.LONG_TEXT,
                EditFieldKind.NUMBER,
                EditFieldKind.STRING,
                EditFieldKind.CHOICE,
                EditFieldKind.BOOLEAN,
            ),
            fields.map { it.kind },
        )
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
        assertEquals(
            mapOf("status" to listOf(EditOption("active", "Active"))),
            parseChoiceOptions(response),
        )
    }

    @Test
    fun `reads custom choice set pairs`() {
        val response =
            parse(
                """{"base_choices":[["EUR","Euro"]],"extra_choices":[["USD","US Dollar"],["GBP","Pound"]]}"""
            )
        assertEquals(
            listOf(
                EditOption("EUR", "Euro"),
                EditOption("USD", "US Dollar"),
                EditOption("GBP", "Pound"),
            ),
            parseCustomChoiceOptions(response),
        )
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

    @Test
    fun `buildPatchBody nests custom field edits`() {
        val body =
            buildPatchBody(
                mapOf(
                    "custom_fields.purchase_currency" to (EditFieldKind.CHOICE to "USD"),
                    "custom_fields.purchase_notes" to (EditFieldKind.LONG_TEXT to "received"),
                    "custom_fields.related" to
                        (EditFieldKind.MULTI_REFERENCE to selectedValuesToJson(listOf("4", "9"))),
                )
            )
        val customFields = body["custom_fields"] as JsonObject
        assertEquals(JsonPrimitive("USD"), customFields["purchase_currency"])
        assertEquals(JsonPrimitive("received"), customFields["purchase_notes"])
        assertEquals(JsonArray(listOf(JsonPrimitive(4), JsonPrimitive(9))), customFields["related"])
    }
}
