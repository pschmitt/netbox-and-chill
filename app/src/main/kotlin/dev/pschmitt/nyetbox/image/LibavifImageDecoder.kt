package dev.pschmitt.nyetbox.image

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.util.component1
import coil3.util.component2
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.BufferedSource
import org.aomedia.avif.android.AvifDecoder

/**
 * Decodes AVIF through libavif instead of the platform decoder.
 *
 * Android's platform AVIF decoder has produced a fully opaque bitmap for some valid AVIF files
 * whose alpha is stored in an auxiliary image item. NetBox device-type images use exactly that
 * representation, so using libavif here keeps the alpha plane intact on every supported Android
 * version and device.
 */
@OptIn(ExperimentalCoilApi::class)
class LibavifImageDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val parallelismLock: Semaphore,
) : Decoder {

    override suspend fun decode(): DecodeResult = parallelismLock.withPermit {
        val encoded = source.source().readByteArray()
        val encodedBuffer = ByteBuffer.allocateDirect(encoded.size).apply { put(encoded).flip() }
        val info = AvifDecoder.Info()
        check(AvifDecoder.getInfo(encodedBuffer, encoded.size, info)) {
            "libavif could not read the AVIF image header"
        }
        require(info.width > 0 && info.height > 0) {
            "libavif returned invalid AVIF dimensions ${info.width}x${info.height}"
        }

        val decoded = createBitmap(info.width, info.height, Bitmap.Config.ARGB_8888)
        encodedBuffer.rewind()
        check(AvifDecoder.decode(encodedBuffer, encoded.size, decoded)) {
            "libavif could not decode the AVIF image"
        }

        val (output, isSampled) = scaleToRequest(decoded, info.width, info.height)
        DecodeResult(image = output.asImage(), isSampled = isSampled)
    }

    private fun scaleToRequest(
        bitmap: Bitmap,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Pair<Bitmap, Boolean> {
        val (targetWidth, targetHeight) =
            DecodeUtils.computeDstSize(
                srcWidth = sourceWidth,
                srcHeight = sourceHeight,
                targetSize = options.size,
                scale = options.scale,
                maxSize = options.maxBitmapSize,
            )
        var multiplier =
            DecodeUtils.computeSizeMultiplier(
                srcWidth = sourceWidth,
                srcHeight = sourceHeight,
                dstWidth = targetWidth,
                dstHeight = targetHeight,
                scale = options.scale,
                maxSize = options.maxBitmapSize,
            )
        if (options.precision == Precision.INEXACT) multiplier = multiplier.coerceAtMost(1.0)

        val outputWidth = (sourceWidth * multiplier).roundToInt().coerceAtLeast(1)
        val outputHeight = (sourceHeight * multiplier).roundToInt().coerceAtLeast(1)
        if (outputWidth == sourceWidth && outputHeight == sourceHeight) {
            return bitmap to false
        }

        val scaled = bitmap.scale(outputWidth, outputHeight, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled to true
    }

    class Factory(private val sourceLock: Semaphore = decodeLock) : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            val source = result.source.source()
            if (!isAvif(result.mimeType, source)) return null
            return LibavifImageDecoder(result.source, options, sourceLock)
        }
    }

    private companion object {
        private val decodeLock = Semaphore(2)
    }
}

internal fun isAvif(mimeType: String?, source: BufferedSource): Boolean {
    if (mimeType?.startsWith("image/avif", ignoreCase = true) == true) return true

    val header = source.peek()
    if (!header.request(12)) return false
    return isAvifHeader(header.readByteArray(12))
}

internal fun isAvifHeader(header: ByteArray): Boolean {
    if (header.size < 12) return false
    return header.ascii(4, 4) == "ftyp" && header.ascii(8, 4) in setOf("avif", "avis")
}

private fun ByteArray.ascii(offset: Int, length: Int): String =
    copyOfRange(offset, offset + length).decodeToString()
