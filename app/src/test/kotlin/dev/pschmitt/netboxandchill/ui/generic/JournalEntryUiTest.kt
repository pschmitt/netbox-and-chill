package dev.pschmitt.netboxandchill.ui.generic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalEntryUiTest {

    private fun parse(rawJson: String): JsonObject =
        Json.decodeFromString(JsonObject.serializer(), rawJson)

    @Test
    fun `keeps the cached object as the edit base`() {
        val entry =
            parse(
                    """{
                        "id": 7,
                        "created": "2026-08-02T10:00:00Z",
                        "kind": {"value": "warning", "label": "Warning"},
                        "comments": "**check cable**"
                    }"""
                )
                .toJournalEntryUi()!!

        assertEquals(7, entry.id)
        assertEquals("warning", entry.kind)
        assertEquals("Warning", entry.kindLabel)
        assertEquals("**check cable**", entry.comments)
        assertTrue(entry.baseJson.contains("\"id\":7"))
    }

    @Test
    fun `uses safe defaults for incomplete cached entries`() {
        val entry = parse("""{"id": 8, "comments": "note"}""").toJournalEntryUi()!!

        assertEquals("", entry.created)
        assertEquals("info", entry.kind)
        assertEquals("Info", entry.kindLabel)
        assertEquals("note", entry.comments)
    }
}
