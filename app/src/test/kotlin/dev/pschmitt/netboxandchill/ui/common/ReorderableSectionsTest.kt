package dev.pschmitt.netboxandchill.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderableSectionsTest {
    @Test
    fun `saved keys are first and unknown keys are retained alphabetically`() {
        assertEquals(
            listOf("ipam", "dcim", "extras", "plugins/example"),
            reorderSectionKeys(
                available = listOf("plugins/example", "extras", "dcim", "ipam"),
                savedOrder = listOf("ipam", "dcim"),
            ),
        )
    }

    @Test
    fun `moving a section produces a stable new order`() {
        assertEquals(
            listOf("stats", "news", "search"),
            moveSection(listOf("stats", "search", "news"), "news", 1),
        )
    }
}
