package dev.pschmitt.netboxandchill.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageViewerNavigationTest {
    @Test
    fun `left and right navigation move one image`() {
        assertEquals(1, imageViewerTargetIndex(currentPage = 2, pageCount = 4, step = -1))
        assertEquals(3, imageViewerTargetIndex(currentPage = 2, pageCount = 4, step = 1))
    }

    @Test
    fun `navigation stops at both edges`() {
        assertNull(imageViewerTargetIndex(currentPage = 0, pageCount = 4, step = -1))
        assertNull(imageViewerTargetIndex(currentPage = 3, pageCount = 4, step = 1))
    }

    @Test
    fun `navigation is safe for empty and single-image viewers`() {
        assertNull(imageViewerTargetIndex(currentPage = 0, pageCount = 0, step = 1))
        assertNull(imageViewerTargetIndex(currentPage = 0, pageCount = 1, step = -1))
        assertNull(imageViewerTargetIndex(currentPage = 0, pageCount = 1, step = 1))
    }
}
