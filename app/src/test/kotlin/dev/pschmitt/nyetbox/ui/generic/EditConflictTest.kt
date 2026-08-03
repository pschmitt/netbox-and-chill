package dev.pschmitt.nyetbox.ui.generic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditConflictTest {

    private fun parse(rawJson: String): JsonObject =
        Json.decodeFromString(JsonObject.serializer(), rawJson)

    @Test
    fun `builds field level three way diff and skips metadata`() {
        val fields =
            buildConflictFields(
                parse("""{"name":"old","serial":"same","last_updated":"one","id":1}"""),
                parse("""{"name":"local","serial":"same","last_updated":"one","id":1}"""),
                parse("""{"name":"server","serial":"same","last_updated":"two","id":1}"""),
            )

        assertEquals(listOf(ConflictField("name", "Name", "old", "local", "server")), fields)
    }

    @Test
    fun `reports fields added or removed on either side`() {
        val fields =
            buildConflictFields(
                parse("""{"name":"same"}"""),
                parse("""{"name":"same","serial":"ABC"}"""),
                parse("""{"name":"same","asset_tag":"TAG-1"}"""),
            )

        assertEquals(listOf("Asset Tag", "Serial"), fields.map { it.label })
        assertTrue(fields.first().local == "—")
        assertTrue(fields.last().server == "—")
    }
}
