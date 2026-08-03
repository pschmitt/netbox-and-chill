package dev.pschmitt.nyetbox.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentTypeBadgeTest {
    @Test
    fun knownDocumentTypesHaveDistinctBadgeColors() {
        val colors =
            listOf("manual", "purchaseorder", "floorplan", "other")
                .map { documentTypeBadgeColors(it).container }
                .toSet()

        assertEquals(4, colors.size)
    }
}
