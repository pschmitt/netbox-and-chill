package dev.pschmitt.netboxandchill.ui.generic

import dev.pschmitt.netboxandchill.data.repository.CreateChoice
import dev.pschmitt.netboxandchill.data.repository.createChoiceSearchFields
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GenericCreatePickerTest {
    private val choices =
        listOf(
            CreateChoice("1", "Turris Omnia"),
            CreateChoice("2", "Shelly 1PM Mini Gen4"),
        )

    @Test
    fun `filters choices by display label and id`() {
        assertEquals(listOf(choices[1]), filterCreateChoices(choices, "shelly"))
        assertEquals(listOf(choices[1]), filterCreateChoices(choices, "2"))
    }

    @Test
    fun `blank query keeps cached order`() {
        assertEquals(choices, filterCreateChoices(choices, "  "))
    }

    @Test
    fun `suggests and applies a related field filter`() {
        val deviceTypes =
            listOf(
                CreateChoice(
                    "1",
                    "Turris Omnia",
                    searchFields = mapOf("manufacturer" to "CZ.NIC"),
                ),
                CreateChoice(
                    "2",
                    "DGS-1100-24PV2",
                    searchFields = mapOf("manufacturer" to "D-Link"),
                ),
            )

        assertEquals(
            listOf(CreateChoiceFieldSuggestion("manufacturer", "Manufacturer")),
            createChoiceFieldSuggestions(deviceTypes, "manu"),
        )
        assertEquals(listOf(deviceTypes[1]), filterCreateChoices(deviceTypes, "manufacturer d-link"))
        assertEquals(deviceTypes, filterCreateChoices(deviceTypes, "manufacturer "))
    }

    @Test
    fun `extracts generic nested relation values for linked filters`() {
        val objectJson =
            Json.parseToJsonElement(
                """{"display":"Turris Omnia","manufacturer":{"display":"CZ.NIC"}}"""
            ).jsonObject

        assertEquals(
            mapOf("display" to "Turris Omnia", "manufacturer" to "CZ.NIC"),
            objectJson.createChoiceSearchFields(),
        )
    }
}
