package dev.pschmitt.nyetbox.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardOrderingTest {
    @Test
    fun `news is hidden by default while the remaining sections retain their order`() {
        assertEquals(
            listOf("stats", "search", "recently_viewed", "bookmarks", "recent_changes"),
            orderedDashboardSections(emptyList(), setOf("news")).map { it.key },
        )
    }

    @Test
    fun `saved section order wins and hidden sections stay omitted`() {
        assertEquals(
            listOf(DashboardSection.RecentChanges, DashboardSection.Stats),
            orderedDashboardSections(
                savedOrder = listOf("recent_changes", "stats", "news"),
                hidden = setOf("search", "bookmarks", "news", "recently_viewed"),
            ),
        )
    }
}
