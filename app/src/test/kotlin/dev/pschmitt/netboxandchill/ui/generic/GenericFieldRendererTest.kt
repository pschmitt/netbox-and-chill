package dev.pschmitt.netboxandchill.ui.generic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericFieldRendererTest {

    private fun parse(rawJson: String): JsonObject = Json.decodeFromString(JsonObject.serializer(), rawJson)

    @Test
    fun `detects a netbox media URL as a FileAttachment using the sibling filename field`() {
        val rows =
            buildFieldRows(
                parse(
                    """{"document":"https://netbox.brkn.lol/media/netbox-documents/188_x.pdf","filename":"x.pdf"}"""
                )
            )
        // "filename" is also its own top-level field and renders as its own PlainText row too -
        // only asserting the FileAttachment row we actually care about here.
        assertTrue(
            rows.contains(
                FieldRow.FileAttachment("Document", "https://netbox.brkn.lol/media/netbox-documents/188_x.pdf", "x.pdf")
            )
        )
    }

    @Test
    fun `falls back to the URL's last path segment when there is no filename field`() {
        val rows = buildFieldRows(parse("""{"image":"https://x/media/image-attachments/foo.png"}"""))
        assertEquals(
            listOf(FieldRow.FileAttachment("Image", "https://x/media/image-attachments/foo.png", "foo.png")),
            rows,
        )
    }

    @Test
    fun `a plain http url field becomes an ExternalLink, not PlainText`() {
        val rows = buildFieldRows(parse("""{"external_url":"https://vendor.example.com/support"}"""))
        assertEquals(listOf(FieldRow.ExternalLink("External URL", "https://vendor.example.com/support")), rows)
    }

    @Test
    fun `a url-shaped custom field also becomes an ExternalLink`() {
        val rows =
            buildFieldRows(parse("""{"custom_fields":{"vendor_support_url":"https://vendor.example.com/x"}}"""))
        assertEquals(listOf(FieldRow.ExternalLink("Vendor Support URL", "https://vendor.example.com/x")), rows)
    }

    @Test
    fun `skips id, url and display`() {
        val rows = buildFieldRows(parse("""{"id":1,"url":"https://x/api/dcim/racks/1/","display":"Rack 1"}"""))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `renders comments as Markdown, not PlainText`() {
        val rows = buildFieldRows(parse("""{"comments":"- Started: `2021-10-01`"}"""))
        assertEquals(listOf(FieldRow.Markdown("Comments", "- Started: `2021-10-01`")), rows)
    }

    @Test
    fun `description is not treated as Markdown`() {
        val rows = buildFieldRows(parse("""{"description":"Not markdown"}"""))
        assertEquals(listOf(FieldRow.PlainText("Description", "Not markdown")), rows)
    }

    @Test
    fun `expands custom_fields into individual rows`() {
        val rows =
            buildFieldRows(
                parse(
                    """{"name":"x","custom_fields":{"warranty_expires":"2027-01-01","internal_owner":"NetOps"}}"""
                )
            )
        assertEquals(
            listOf(
                FieldRow.PlainText("Name", "x"),
                FieldRow.PlainText("Warranty Expires", "2027-01-01"),
                FieldRow.PlainText("Internal Owner", "NetOps"),
            ),
            rows,
        )
    }

    @Test
    fun `custom_fields with only null values contributes nothing`() {
        val rows = buildFieldRows(parse("""{"custom_fields":{"unset_field":null}}"""))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `custom_fields reference values become tappable Reference rows`() {
        val rows =
            buildFieldRows(
                parse(
                    """{"custom_fields":{"owner_contact":{"id":9,"url":"https://x/api/tenancy/contacts/9/","display":"Jane"}}}"""
                )
            )
        assertEquals(listOf(FieldRow.Reference("Owner Contact", RefTarget("Jane", "api/tenancy/contacts/", 9))), rows)
    }

    @Test
    fun `skips null and blank fields`() {
        val rows = buildFieldRows(parse("""{"comments":null,"description":""}"""))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `renders a plain string field`() {
        val rows = buildFieldRows(parse("""{"serial":"ABC123"}"""))
        assertEquals(listOf(FieldRow.PlainText("Serial", "ABC123")), rows)
    }

    @Test
    fun `renders booleans as Yes or No`() {
        val rows = buildFieldRows(parse("""{"is_full_depth":true,"airflow":false}"""))
        assertTrue(rows.contains(FieldRow.PlainText("Is Full Depth", "Yes")))
        assertTrue(rows.contains(FieldRow.PlainText("Airflow", "No")))
    }

    @Test
    fun `renders a choice-style object using its label`() {
        val rows = buildFieldRows(parse("""{"status":{"value":"active","label":"Active"}}"""))
        assertEquals(listOf(FieldRow.PlainText("Status", "Active")), rows)
    }

    @Test
    fun `renders a nested reference object as a tappable Reference`() {
        val rows =
            buildFieldRows(
                parse(
                    """{"site":{"id":3,"url":"https://netbox.brkn.lol/api/dcim/sites/3/","display":"HQ"}}"""
                )
            )
        assertEquals(
            listOf(FieldRow.Reference("Site", RefTarget("HQ", "api/dcim/sites/", 3))),
            rows,
        )
    }

    @Test
    fun `renders an array of references as a ReferenceList`() {
        val rows =
            buildFieldRows(
                parse(
                    """
                    {"tags":[
                        {"id":1,"url":"https://x/api/extras/tags/1/","display":"prod","name":"prod"},
                        {"id":2,"url":"https://x/api/extras/tags/2/","display":"edge","name":"edge"}
                    ]}
                    """
                        .trimIndent()
                )
            )
        assertEquals(
            listOf(
                FieldRow.ReferenceList(
                    "Tags",
                    listOf(RefTarget("prod", "api/extras/tags/", 1), RefTarget("edge", "api/extras/tags/", 2)),
                )
            ),
            rows,
        )
    }

    @Test
    fun `renders an array of plain strings as a ChipList`() {
        val rows = buildFieldRows(parse("""{"aliases":["foo","bar"]}"""))
        assertEquals(listOf(FieldRow.ChipList("Aliases", listOf("foo", "bar"))), rows)
    }

    @Test
    fun `flattens an unrecognized nested object best-effort`() {
        val rows = buildFieldRows(parse("""{"weight":{"value":5.0,"unit":"kg"}}"""))
        assertEquals(listOf(FieldRow.PlainText("Weight", "value: 5.0, unit: kg")), rows)
    }

    @Test
    fun `humanizes ip-style keys with acronym casing`() {
        val rows = buildFieldRows(parse("""{"primary_ip4":"10.0.0.1/24"}"""))
        assertEquals("Primary IP4", rows.single().label)
    }

    @Test
    fun `returns null endpoint for a malformed reference url`() {
        val rows = buildFieldRows(parse("""{"site":{"id":3,"url":"not-a-url","display":"HQ"}}"""))
        // Falls through to the flatten fallback since it's not a usable reference.
        assertNull(rows.filterIsInstance<FieldRow.Reference>().firstOrNull())
    }
}
