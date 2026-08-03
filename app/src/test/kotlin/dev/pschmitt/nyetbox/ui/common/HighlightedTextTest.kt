package dev.pschmitt.nyetbox.ui.common

import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightedTextTest {
    @Test
    fun highlightsAllCaseInsensitiveOccurrences() {
        val result = highlightedSearchText("Router ROUTER", "rou", SpanStyle())

        assertEquals(2, result.spanStyles.size)
        assertEquals(0, result.spanStyles[0].start)
        assertEquals(3, result.spanStyles[0].end)
        assertEquals(7, result.spanStyles[1].start)
        assertEquals(10, result.spanStyles[1].end)
    }

    @Test
    fun highlightsEachSearchTermAndMergesOverlappingRanges() {
        val result = highlightedSearchText("Shelly router", "hel router", SpanStyle())

        assertEquals(2, result.spanStyles.size)
        assertEquals("hel", result.text.substring(result.spanStyles[0].start, result.spanStyles[0].end))
        assertEquals("router", result.text.substring(result.spanStyles[1].start, result.spanStyles[1].end))
    }

    @Test
    fun leavesTextUnstyledWhenThereIsNoMatchOrQuery() {
        assertTrue(highlightedSearchText("Router", "switch", SpanStyle()).spanStyles.isEmpty())
        assertTrue(highlightedSearchText("Router", "", SpanStyle()).spanStyles.isEmpty())
    }
}
