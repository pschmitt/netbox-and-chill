package dev.pschmitt.netboxandchill.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlobalSearchAssetTagTest {
    @Test
    fun `extracts asset tags from cached object json`() {
        val objectJson = Json.parseToJsonElement("""{"asset_tag":"NBC-123"}""").jsonObject

        assertEquals("NBC-123", objectJson.assetTag())
    }

    @Test
    fun `ignores missing and blank asset tags`() {
        assertNull(Json.parseToJsonElement("{}" ).jsonObject.assetTag())
        assertNull(
            Json.parseToJsonElement("""{"asset_tag":"  "}""").jsonObject.assetTag()
        )
    }
}
