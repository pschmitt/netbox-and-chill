package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.db.RecentVisitEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalSearchRankingTest {
    @Test
    fun `recent visits become navigable search hits in visit order`() {
        val hits =
            recentVisitsToSearchHits(
                listOf(
                    RecentVisitEntity("api/dcim/devices/", 1, "Router", "active", 2),
                    RecentVisitEntity("api/dcim/sites/", 4, "HQ", null, 1),
                )
            )

        assertEquals(listOf("Router", "HQ"), hits.map { it.display })
        assertEquals("api/dcim/sites/", hits[1].endpointPath)
    }

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
