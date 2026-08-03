package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryVisualTransformationTest {
    @Test
    fun `styles recognized field token while preserving text offsets`() {
        val transformed =
            SearchQueryVisualTransformation(Color.Magenta)
                .filter(AnnotatedString("manufacturer:Shelly router"))
                .text

        assertEquals("manufacturer:Shelly router", transformed.text)
        assertTrue(transformed.spanStyles.any { it.start == 0 && it.end == 19 })
        assertTrue(
            transformed.spanStyles.any {
                it.start == 0 && it.end == 12 && it.item.fontWeight == FontWeight.SemiBold
            }
        )
    }

    @Test
    fun `leaves ordinary free text unstyled`() {
        val transformed =
            SearchQueryVisualTransformation(Color.Magenta)
                .filter(AnnotatedString("router office"))
                .text

        assertEquals("router office", transformed.text)
        assertTrue(transformed.spanStyles.isEmpty())
    }
}
