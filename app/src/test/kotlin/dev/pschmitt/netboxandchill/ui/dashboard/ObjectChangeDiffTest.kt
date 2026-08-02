package dev.pschmitt.netboxandchill.ui.dashboard

import dev.pschmitt.netboxandchill.data.repository.CustomFieldDefinition
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectChangeDiffTest {

    private fun parse(rawJson: String): JsonObject =
        Json.decodeFromString(JsonObject.serializer(), rawJson)

    @Test
    fun `only fields that actually changed appear as diff rows`() {
        val pre = parse("""{"name":"old-name","serial":"ABC123","status":"active"}""")
        val post = parse("""{"name":"new-name","serial":"ABC123","status":"active"}""")
        assertEquals(
            listOf(DiffRow("Name", "old-name", "new-name", fieldKey = "name")),
            buildDiffRows(pre, post),
        )
    }

    @Test
    fun `create has no before side`() {
        val post = parse("""{"name":"new-device"}""")
        assertEquals(
            listOf(DiffRow("Name", null, "new-device", fieldKey = "name")),
            buildDiffRows(null, post),
        )
    }

    @Test
    fun `delete has no after side`() {
        val pre = parse("""{"name":"gone-device"}""")
        assertEquals(
            listOf(DiffRow("Name", "gone-device", null, fieldKey = "name")),
            buildDiffRows(pre, null),
        )
    }

    @Test
    fun `null-to-empty-string is still reported as a change`() {
        val pre = parse("""{"description":null}""")
        val post = parse("""{"description":"now has a description"}""")
        assertEquals(
            listOf(DiffRow("Description", null, "now has a description", fieldKey = "description")),
            buildDiffRows(pre, post),
        )
    }

    @Test
    fun `nested objects fall back to raw JSON since there is no schema to render them richly`() {
        val pre = parse("""{"site":{"id":1,"name":"Old Site"}}""")
        val post = parse("""{"site":{"id":2,"name":"New Site"}}""")
        val rows = buildDiffRows(pre, post)
        assertEquals(1, rows.size)
        assertEquals("Site", rows[0].label)
        assertTrue(rows[0].before!!.contains("Old Site"))
        assertTrue(rows[0].after!!.contains("New Site"))
    }

    @Test
    fun `unchanged fields produce no rows at all`() {
        val pre = parse("""{"name":"same","serial":"same"}""")
        val post = parse("""{"name":"same","serial":"same"}""")
        assertEquals(emptyList<DiffRow>(), buildDiffRows(pre, post))
    }

    @Test
    fun `custom fields become individually labeled and grouped markdown rows`() {
        val pre =
            parse(
                """{"custom_fields":{"purchase_info":"old **details**","enabled":false}}"""
            )
        val post =
            parse(
                """{"custom_fields":{"purchase_info":"new **details**","enabled":true}}"""
            )
        val definitions =
            listOf(
                CustomFieldDefinition("purchase_info", "text", "Purchase info", "Purchase", 10),
                CustomFieldDefinition("enabled", "boolean", "Enabled", "Purchase", 20),
            )

        assertEquals(
            listOf(
                DiffRow(
                    "Purchase info",
                    "old **details**",
                    "new **details**",
                    "Purchase",
                    markdown = true,
                    fieldKey = "custom_fields.purchase_info",
                ),
                DiffRow(
                    "Enabled",
                    "Disabled",
                    "Enabled",
                    "Purchase",
                    fieldKey = "custom_fields.enabled",
                ),
            ),
            buildDiffRows(pre, post, definitions),
        )
    }

    @Test
    fun resolvesRoleIdsFromDeviceChangelogUsingCachedDisplays() = runBlocking {
        val change =
            parse(
                """{
                    "changed_object_type":"dcim.device",
                    "prechange_data":{"role":30,"vc_position":1},
                    "postchange_data":{"role":8,"vc_position":2}
                }"""
            )
        val pre = change["prechange_data"] as JsonObject
        val post = change["postchange_data"] as JsonObject
        val rows = buildDiffRows(pre, post)

        val resolved =
            resolveLinkedDiffRows(change, rows) { endpoint, id ->
                assertEquals("api/dcim/device-roles/", endpoint)
                mapOf(30 to "Old role", 8 to "New role")[id]
            }

        assertEquals("Old role", resolved.first { it.fieldKey == "role" }.before)
        assertEquals("New role", resolved.first { it.fieldKey == "role" }.after)
        assertEquals("1", resolved.first { it.fieldKey == "vc_position" }.before)
        assertEquals("2", resolved.first { it.fieldKey == "vc_position" }.after)
    }

    @Test
    fun resolvesNestedReferencesFromTheirSnapshotUrl() = runBlocking {
        val change =
            parse(
                """{
                    "changed_object_type":"dcim.device",
                    "prechange_data":{"role":{"id":30,"url":"https://netbox.test/api/dcim/device-roles/30/"}},
                    "postchange_data":{"role":{"id":8,"url":"https://netbox.test/api/dcim/device-roles/8/"}}
                }"""
            )
        val pre = change["prechange_data"] as JsonObject
        val post = change["postchange_data"] as JsonObject
        val rows = buildDiffRows(pre, post)
        val resolved =
            resolveLinkedDiffRows(change, rows) { endpoint, id ->
                assertEquals("api/dcim/device-roles/", endpoint)
                "Role $id"
            }

        assertEquals("Role 30", resolved.single().before)
        assertEquals("Role 8", resolved.single().after)
    }

    @Test
    fun keepsUnknownLinkedIdsAndNumericFieldsIntact() = runBlocking {
        val change =
            parse(
                """{
                    "changed_object_type":"dcim.device",
                    "prechange_data":{"role":30,"position":1},
                    "postchange_data":{"role":8,"position":2}
                }"""
            )
        val pre = change["prechange_data"] as JsonObject
        val post = change["postchange_data"] as JsonObject
        val rows = buildDiffRows(pre, post)
        val resolved = resolveLinkedDiffRows(change, rows) { _, _ -> null }

        assertEquals("30", resolved.first { it.fieldKey == "role" }.before)
        assertEquals("8", resolved.first { it.fieldKey == "role" }.after)
        assertEquals("1", resolved.first { it.fieldKey == "position" }.before)
        assertEquals("2", resolved.first { it.fieldKey == "position" }.after)
    }

    @Test
    fun keeps_before_and_after_links_for_cached_device_type_references() = runBlocking {
        val change =
            parse(
                """{
                    "changed_object_type":"dcim.device",
                    "prechange_data":{"device_type":244},
                    "postchange_data":{"device_type":245}
                }"""
            )
        val rows =
            buildDiffRows(
                change["prechange_data"] as JsonObject,
                change["postchange_data"] as JsonObject,
            )

        val resolved = resolveLinkedDiffRows(change, rows) { endpoint, id ->
            assertEquals("api/dcim/device-types/", endpoint)
            "Device type $id"
        }
        val deviceType = resolved.single()

        assertEquals("Device type 244", deviceType.before)
        assertEquals("Device type 245", deviceType.after)
        assertEquals(DiffReference("api/dcim/device-types/", 244), deviceType.beforeReference)
        assertEquals(DiffReference("api/dcim/device-types/", 245), deviceType.afterReference)
    }

    @Test
    fun retains_a_link_when_the_related_display_is_not_cached() = runBlocking {
        val change =
            parse(
                """{
                    "changed_object_type":"dcim.device",
                    "prechange_data":{"device":17},
                    "postchange_data":{"device":18}
                }"""
            )
        val rows =
            buildDiffRows(
                change["prechange_data"] as JsonObject,
                change["postchange_data"] as JsonObject,
            )

        val resolved = resolveLinkedDiffRows(change, rows) { _, _ -> null }.single()

        assertEquals("17", resolved.before)
        assertEquals("18", resolved.after)
        assertEquals(DiffReference("api/dcim/devices/", 17), resolved.beforeReference)
        assertEquals(DiffReference("api/dcim/devices/", 18), resolved.afterReference)
    }

    @Test
    fun inline_diff_highlights_only_the_changed_words() {
        val diff = buildInlineDiff("Shelly old device", "Shelly new device")

        assertEquals(
            listOf("Shelly", " ", "old", " ", "device"),
            diff.before.map(InlineDiffToken::text),
        )
        assertEquals(
            listOf(
                InlineDiffTokenKind.UNCHANGED,
                InlineDiffTokenKind.UNCHANGED,
                InlineDiffTokenKind.REMOVED,
                InlineDiffTokenKind.UNCHANGED,
                InlineDiffTokenKind.UNCHANGED,
            ),
            diff.before.map(InlineDiffToken::kind),
        )
        assertEquals(
            listOf("Shelly", " ", "new", " ", "device"),
            diff.after.map(InlineDiffToken::text),
        )
        assertEquals(
            listOf(
                InlineDiffTokenKind.UNCHANGED,
                InlineDiffTokenKind.UNCHANGED,
                InlineDiffTokenKind.ADDED,
                InlineDiffTokenKind.UNCHANGED,
                InlineDiffTokenKind.UNCHANGED,
            ),
            diff.after.map(InlineDiffToken::kind),
        )
    }

    @Test
    fun inline_diff_keeps_added_and_removed_values_for_create_and_delete() {
        val created = buildInlineDiff(null, "new device")
        val deleted = buildInlineDiff("old device", null)

        assertTrue(created.before.isEmpty())
        assertTrue(created.after.all { it.kind == InlineDiffTokenKind.ADDED })
        assertTrue(deleted.after.isEmpty())
        assertTrue(deleted.before.all { it.kind == InlineDiffTokenKind.REMOVED })
    }
}
