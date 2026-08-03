package dev.pschmitt.nyetbox.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransparentPaddingTransformationTest {
    @Test
    fun findsVisibleBoundsAndUsesExclusiveRightBottom() {
        val pixels = IntArray(8 * 6)
        for (y in 1..4) {
            for (x in 2..5) pixels[y * 8 + x] = 0xFF112233.toInt()
        }

        assertEquals(AlphaBounds(2, 1, 6, 5), visibleAlphaBounds(8, 6, pixels))
    }

    @Test
    fun ignoresNearlyTransparentNoise() {
        val pixels = IntArray(4 * 4)
        pixels[0] = 0x08112233
        pixels[15] = 0x09112233

        assertEquals(AlphaBounds(3, 3, 4, 4), visibleAlphaBounds(4, 4, pixels))
    }

    @Test
    fun returnsNullWhenBitmapIsFullyTransparent() {
        assertNull(visibleAlphaBounds(3, 2, IntArray(6)))
    }
}
