package dev.pschmitt.nyetbox.ui.generic

import dev.pschmitt.nyetbox.data.repository.CreateChoice
import dev.pschmitt.nyetbox.data.repository.choiceSearchHint
import dev.pschmitt.nyetbox.data.repository.compactSearchMatchValue
import dev.pschmitt.nyetbox.data.repository.createChoiceSearchFields
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
        assertEquals(
            listOf(deviceTypes[1]),
            filterCreateChoices(deviceTypes, "manufacturer d-link"),
        )
        assertEquals(deviceTypes, filterCreateChoices(deviceTypes, "manufacturer "))
    }

    @Test
    fun `extracts generic nested relation values for linked filters`() {
        val objectJson =
            Json.parseToJsonElement(
                    """{"display":"Turris Omnia","manufacturer":{"display":"CZ.NIC"}}"""
                )
                .jsonObject

        val fields = objectJson.createChoiceSearchFields()
        assertEquals("Turris Omnia", fields["display"])
        assertEquals("CZ.NIC", fields["manufacturer"])
        assertEquals("CZ.NIC", fields["manufacturer.display"])
    }

    @Test
    fun `matches recursively nested relation values and explains the match`() {
        val deviceTypes =
            listOf(
                CreateChoice(
                    "1",
                    "1PM Mini Gen4",
                    searchFields =
                        mapOf("manufacturer" to "Shelly", "manufacturer.name" to "Shelly"),
                ),
                CreateChoice("2", "Other type"),
            )

        assertEquals(listOf(deviceTypes[0]), filterCreateChoices(deviceTypes, "shelly"))
        assertEquals(
            "Manufacturer: Shelly",
            choiceSearchHint(
                deviceTypes[0].label,
                deviceTypes[0].value,
                deviceTypes[0].searchFields,
                "shelly",
            ),
        )
    }

    @Test
    fun `removes repeated case-insensitive words from match hints`() {
        assertEquals("Shelly", compactSearchMatchValue("Shelly shelly"))
        assertEquals(
            "Shelly Plus 1PM shelly-plus-1pm",
            compactSearchMatchValue("Shelly Plus 1PM shelly-plus-1pm"),
        )
    }
}
