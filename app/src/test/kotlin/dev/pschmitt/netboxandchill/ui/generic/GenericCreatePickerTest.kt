package dev.pschmitt.netboxandchill.ui.generic

import dev.pschmitt.netboxandchill.data.repository.CreateChoice
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
}
