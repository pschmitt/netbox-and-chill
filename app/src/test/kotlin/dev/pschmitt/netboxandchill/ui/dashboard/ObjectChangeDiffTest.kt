package dev.pschmitt.netboxandchill.ui.dashboard

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectChangeDiffTest {

    private fun parse(rawJson: String): JsonObject = Json.decodeFromString(JsonObject.serializer(), rawJson)

    @Test
    fun `only fields that actually changed appear as diff rows`() {
        val pre = parse("""{"name":"old-name","serial":"ABC123","status":"active"}""")
        val post = parse("""{"name":"new-name","serial":"ABC123","status":"active"}""")
        assertEquals(listOf(DiffRow("Name", "old-name", "new-name")), buildDiffRows(pre, post))
    }

    @Test
    fun `create has no before side`() {
        val post = parse("""{"name":"new-device"}""")
        assertEquals(listOf(DiffRow("Name", null, "new-device")), buildDiffRows(null, post))
    }

    @Test
    fun `delete has no after side`() {
        val pre = parse("""{"name":"gone-device"}""")
        assertEquals(listOf(DiffRow("Name", "gone-device", null)), buildDiffRows(pre, null))
    }

    @Test
    fun `null-to-empty-string is still reported as a change`() {
        val pre = parse("""{"description":null}""")
        val post = parse("""{"description":"now has a description"}""")
        assertEquals(listOf(DiffRow("Description", null, "now has a description")), buildDiffRows(pre, post))
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
}
