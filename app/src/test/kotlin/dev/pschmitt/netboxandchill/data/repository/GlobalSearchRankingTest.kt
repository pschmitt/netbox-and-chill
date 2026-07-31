package dev.pschmitt.netboxandchill.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalSearchRankingTest {
    @Test
    fun `ranks exact and prefix display matches before substring and secondary matches`() {
        val hits =
            listOf(
                SearchHit("api/dcim/devices/", 1, "Router Office", "active"),
                SearchHit("api/dcim/devices/", 2, "Router", "active"),
                SearchHit("api/dcim/devices/", 3, "Main Switch", "router"),
                SearchHit("api/dcim/devices/", 4, "Backup Router", "active"),
            )

        assertEquals(listOf(2, 1, 4, 3), rankSearchHits("router", hits).map { it.id })
    }

    @Test
    fun `deduplicates hits from typed and generic caches`() {
        val hits =
            listOf(
                SearchHit("api/dcim/devices/", 7, "Router", null),
                SearchHit("api/dcim/devices/", 7, "Router", null),
            )

        assertEquals(1, rankSearchHits("router", hits).size)
    }
}
