package dev.pschmitt.nyetbox.image

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Keeps thumbnails from making a small foreground object look tiny because of a large transparent
 * canvas around it. This is deliberately a thumbnail-only Coil transformation; full-size image
 * viewing continues to show the original pixels.
 */
class TransparentPaddingTransformation : Transformation() {
    override val cacheKey: String = "transparent-padding-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!input.hasAlpha() || input.width < 4 || input.height < 4) return input

        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        val bounds = visibleAlphaBounds(input.width, input.height, pixels) ?: return input
        val paddingX = (bounds.width * PADDING_FRACTION).roundToInt().coerceAtLeast(2)
        val paddingY = (bounds.height * PADDING_FRACTION).roundToInt().coerceAtLeast(2)
        var left = (bounds.left - paddingX).coerceAtLeast(0)
        var top = (bounds.top - paddingY).coerceAtLeast(0)
        var right = (bounds.right + paddingX).coerceAtMost(input.width)
        var bottom = (bounds.bottom + paddingY).coerceAtMost(input.height)

        val minWidth = ceil(input.width * MIN_VISIBLE_FRACTION).roundToInt()
        val minHeight = ceil(input.height * MIN_VISIBLE_FRACTION).roundToInt()
        val widthRange = expandToMinimum(left, right, minWidth, input.width)
        val heightRange = expandToMinimum(top, bottom, minHeight, input.height)
        left = widthRange.first
        right = widthRange.second
        top = heightRange.first
        bottom = heightRange.second

        val croppedArea = (right - left).toLong() * (bottom - top)
        val originalArea = input.width.toLong() * input.height
        if (croppedArea >= originalArea * (1f - MIN_REMOVED_FRACTION)) return input

        return Bitmap.createBitmap(input, left, top, right - left, bottom - top)
    }

    private companion object {
        private const val ALPHA_THRESHOLD = 8
        private const val MIN_VISIBLE_FRACTION = 0.2f
        private const val MIN_REMOVED_FRACTION = 0.10f
        private const val PADDING_FRACTION = 0.04f
    }
}

internal data class AlphaBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Returns the right/bottom-exclusive bounds of pixels whose alpha is above the noise threshold. */
internal fun visibleAlphaBounds(
    width: Int,
    height: Int,
    pixels: IntArray,
    alphaThreshold: Int = 8,
): AlphaBounds? {
    require(width > 0 && height > 0 && pixels.size == width * height)
    var left = width
    var top = height
    var right = 0
    var bottom = 0
    pixels.forEachIndexed { index, pixel ->
        if ((pixel ushr 24) <= alphaThreshold) return@forEachIndexed
        val x = index % width
        val y = index / width
        left = minOf(left, x)
        top = minOf(top, y)
        right = maxOf(right, x + 1)
        bottom = maxOf(bottom, y + 1)
    }
    return if (right == 0) null else AlphaBounds(left, top, right, bottom)
}

private fun expandToMinimum(start: Int, end: Int, minimum: Int, limit: Int): Pair<Int, Int> {
    if (end - start >= minimum) return start to end
    val center = (start + end) / 2
    var expandedStart = center - minimum / 2
    var expandedEnd = expandedStart + minimum
    if (expandedStart < 0) {
        expandedStart = 0
        expandedEnd = minimum
    }
    if (expandedEnd > limit) {
        expandedEnd = limit
        expandedStart = limit - minimum
    }
    return expandedStart to expandedEnd
}
