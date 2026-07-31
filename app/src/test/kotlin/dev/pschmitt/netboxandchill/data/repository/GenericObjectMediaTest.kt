package dev.pschmitt.netboxandchill.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GenericObjectMediaTest {

    private fun parse(rawJson: String): JsonObject = Json.decodeFromString(JsonObject.serializer(), rawJson)

    @Test
    fun `extracts media URLs with sibling filename and deduplicates repeated URLs`() {
        val attachments =
            parse(
                """{"file":"https://netbox.example/media/docs/manual.pdf","filename":"manual.pdf","preview":"https://netbox.example/media/docs/manual.pdf"}"""
            ).mediaAttachments()

        assertEquals(
            listOf(OfflineAttachment("https://netbox.example/media/docs/manual.pdf", "manual.pdf")),
            attachments,
        )
    }

    @Test
    fun `extracts nested media URL with a stable fallback filename`() {
        val attachments =
            parse("""{"image":{"url":"https://netbox.example/media/images/front.jpg"}}""").mediaAttachments()

        assertEquals(
            listOf(OfflineAttachment("https://netbox.example/media/images/front.jpg", "front.jpg")),
            attachments,
        )
    }
}
