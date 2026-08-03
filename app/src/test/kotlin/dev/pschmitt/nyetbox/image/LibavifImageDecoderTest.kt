package dev.pschmitt.nyetbox.image

import okio.Buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibavifImageDecoderTest {

    @Test
    fun recognizesStillAvifFileTypeHeader() {
        assertTrue(isAvifHeader(ftypHeader("avif")))
    }

    @Test
    fun recognizesAnimatedAvifFileTypeHeader() {
        assertTrue(isAvifHeader(ftypHeader("avis")))
    }

    @Test
    fun rejectsOtherIsoBaseMediaFiles() {
        assertFalse(isAvifHeader(ftypHeader("isom")))
        assertFalse(isAvifHeader(byteArrayOf(0, 0, 0, 0)))
    }

    @Test
    fun mimeTypeAllowsDetectionWhenNetworkOmitsFileHeaderFromPeek() {
        assertTrue(isAvif("image/avif", Buffer().writeUtf8("not a header")))
    }

    private fun ftypHeader(brand: String): ByteArray =
        byteArrayOf(0, 0, 0, 28) + "ftyp$brand".encodeToByteArray()
}
