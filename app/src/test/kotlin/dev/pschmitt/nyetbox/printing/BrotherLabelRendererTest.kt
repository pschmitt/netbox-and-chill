package dev.pschmitt.nyetbox.printing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrotherLabelRendererTest {
    @Test
    fun `normal raster keeps source white as printer white`() {
        assertTrue(BrotherLabelRenderer.printerWhiteBit(sourcePixelIsWhite = true, invert = false))
        assertFalse(
            BrotherLabelRenderer.printerWhiteBit(sourcePixelIsWhite = false, invert = false)
        )
    }

    @Test
    fun `inverted raster swaps source black and white`() {
        assertFalse(BrotherLabelRenderer.printerWhiteBit(sourcePixelIsWhite = true, invert = true))
        assertTrue(BrotherLabelRenderer.printerWhiteBit(sourcePixelIsWhite = false, invert = true))
    }
}
