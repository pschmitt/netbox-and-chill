package dev.pschmitt.nyetbox.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TabSwipeTest {
    @Test
    fun `swiping left selects the next tab`() {
        assertEquals(2, itemTabTargetIndex(selectedTab = 1, tabCount = 4, swipeLeft = true))
    }

    @Test
    fun `swiping right selects the previous tab`() {
        assertEquals(1, itemTabTargetIndex(selectedTab = 2, tabCount = 4, swipeLeft = false))
    }

    @Test
    fun `swipes at either edge stay on the edge`() {
        assertEquals(0, itemTabTargetIndex(selectedTab = 0, tabCount = 4, swipeLeft = false))
        assertEquals(3, itemTabTargetIndex(selectedTab = 3, tabCount = 4, swipeLeft = true))
    }

    @Test
    fun `invalid selection is normalized and empty tab lists are safe`() {
        assertEquals(1, itemTabTargetIndex(selectedTab = -2, tabCount = 3, swipeLeft = true))
        assertEquals(1, itemTabTargetIndex(selectedTab = 8, tabCount = 3, swipeLeft = false))
        assertEquals(0, itemTabTargetIndex(selectedTab = 0, tabCount = 0, swipeLeft = true))
    }
}
