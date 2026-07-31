package dev.pschmitt.netboxandchill.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import dev.pschmitt.netboxandchill.qrsetup.QrBitmap

data class BrotherLabelRaster(val bytes: ByteArray, val rasterLines: Int)

/** Renders the same QR + asset-tag shape used by printlabel for a 128-dot P-touch head. */
object BrotherLabelRenderer {
    private const val QR_SIZE = 64
    private const val LABEL_HEIGHT = 64
    private const val LABEL_END_PADDING = 4
    private const val QR_TEXT_GAP = 4
    private const val TEXT_PADDING = 4

    /** [invert] defaults to the image inversion expected by the physical label workflow. */
    fun render(
        objectUrl: String,
        labelText: String,
        invert: Boolean = true,
        vertical: Boolean = false,
    ): BrotherLabelRaster {
        require(objectUrl.isNotBlank()) { "A device URL is required for the label QR code" }
        val text = labelText.replace(Regex("[\\r\\n\\t]+"), " ").trim()
        val textPaint = labelTextPaint(text, fitWidth = vertical)
        val textBounds = Rect().also { textPaint.getTextBounds(text, 0, text.length, it) }
        val qr = QrBitmap.encode(objectUrl, QR_SIZE)
        val source =
            if (vertical) {
                renderVerticalSource(qr, text, textPaint, textBounds)
            } else {
                renderHorizontalSource(qr, text, textPaint, textBounds)
            }
        qr.recycle()

        // printlabel rotates the horizontal label -90 degrees, mirrors it, then pads the print
        // head to 128 dots. PTCBP treats zero bits as printed dots and one bits as white space.
        val transform = Matrix().apply {
            setRotate(-90f)
            postScale(-1f, 1f)
        }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, transform, true)
        source.recycle()
        val padded = Bitmap.createBitmap(BrotherPtcBp.RASTER_WIDTH, rotated.height, Bitmap.Config.ARGB_8888)
        Canvas(padded).apply {
            drawColor(Color.WHITE)
            drawBitmap(rotated, (BrotherPtcBp.RASTER_WIDTH - rotated.width) / 2f, 0f, null)
        }
        rotated.recycle()

        val bytesPerLine = BrotherPtcBp.RASTER_WIDTH / 8
        val raster = ByteArray(padded.height * bytesPerLine)
        for (y in 0 until padded.height) {
            for (x in 0 until BrotherPtcBp.RASTER_WIDTH) {
                val pixel = padded.getPixel(x, y)
                val sourcePixelIsWhite = Color.red(pixel) > 127
                if (printerWhiteBit(sourcePixelIsWhite, invert)) raster[y * bytesPerLine + x / 8] =
                    (raster[y * bytesPerLine + x / 8].toInt() or (0x80 shr (x % 8))).toByte()
            }
        }
        padded.recycle()
        return BrotherLabelRaster(raster, raster.size / bytesPerLine)
    }

    private fun labelTextPaint(text: String, fitWidth: Boolean): Paint {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.DEFAULT
                textAlign = Paint.Align.CENTER
                textScaleX = 1f
            }
        val bounds = Rect()
        var textSize = 20f
        while (textSize >= 8f) {
            paint.textSize = textSize
            paint.getTextBounds(text, 0, text.length, bounds)
            val fitsHeight = bounds.height() <= LABEL_HEIGHT - TEXT_PADDING * 2
            val fitsWidth = !fitWidth || bounds.width() <= LABEL_HEIGHT - TEXT_PADDING * 2
            if (fitsHeight && fitsWidth) break
            textSize -= 1f
        }
        return paint
    }

    private fun renderHorizontalSource(
        qr: Bitmap,
        text: String,
        paint: Paint,
        bounds: Rect,
    ): Bitmap {
        val textWidth = bounds.width().coerceAtLeast(1)
        val textStart = LABEL_END_PADDING + QR_SIZE + QR_TEXT_GAP
        val sourceWidth = textStart + textWidth + TEXT_PADDING
        return Bitmap.createBitmap(sourceWidth, LABEL_HEIGHT, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawBitmap(qr, LABEL_END_PADDING.toFloat(), 0f, null)
                val x = textStart + textWidth / 2f
                val y = LABEL_HEIGHT / 2f - (paint.ascent() + paint.descent()) / 2f
                drawText(text, x, y, paint)
            }
        }
    }

    private fun renderVerticalSource(
        qr: Bitmap,
        text: String,
        paint: Paint,
        bounds: Rect,
    ): Bitmap {
        val textSourceWidth = bounds.width().coerceAtLeast(1) + TEXT_PADDING * 2
        val textSourceHeight = bounds.height().coerceAtLeast(1) + TEXT_PADDING * 2
        val textSource = Bitmap.createBitmap(textSourceWidth, textSourceHeight, Bitmap.Config.ARGB_8888)
        Canvas(textSource).apply {
            drawColor(Color.WHITE)
            val x = textSourceWidth / 2f
            val y = textSourceHeight / 2f - (paint.ascent() + paint.descent()) / 2f
            drawText(text, x, y, paint)
        }
        val rotation = Matrix().apply { setRotate(-90f) }
        val textColumn = Bitmap.createBitmap(
            textSource,
            0,
            0,
            textSource.width,
            textSource.height,
            rotation,
            true,
        )
        textSource.recycle()

        val textStart = LABEL_END_PADDING + QR_SIZE + QR_TEXT_GAP
        val sourceWidth = textStart + textColumn.width + TEXT_PADDING
        return Bitmap.createBitmap(sourceWidth, LABEL_HEIGHT, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawBitmap(qr, LABEL_END_PADDING.toFloat(), 0f, null)
                drawBitmap(textColumn, textStart.toFloat(), (LABEL_HEIGHT - textColumn.height) / 2f, null)
            }
            textColumn.recycle()
        }
    }

    /** PTCBP uses a set bit for tape white space and a clear bit for a printed dot. */
    internal fun printerWhiteBit(sourcePixelIsWhite: Boolean, invert: Boolean): Boolean =
        if (invert) !sourcePixelIsWhite else sourcePixelIsWhite
}
