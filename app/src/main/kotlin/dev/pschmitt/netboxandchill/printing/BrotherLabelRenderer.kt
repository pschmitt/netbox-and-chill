package dev.pschmitt.netboxandchill.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.set
import dev.pschmitt.netboxandchill.qrsetup.QrBitmap
import kotlin.math.ceil

data class BrotherLabelRaster(val bytes: ByteArray, val rasterLines: Int)

private data class LabelTextLayout(
    val lines: List<String>,
    val width: Int,
    val height: Int,
    val lineHeight: Int,
)

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
        qrSize: Int = QR_SIZE,
    ): BrotherLabelRaster {
        val source = renderSource(objectUrl, labelText, vertical, qrSize)

        // Match printlabel's rotate(-90) + mirror operation without bitmap filtering. The
        // printer's 1-bit head cannot represent anti-aliased interpolation; filtering makes
        // small glyphs lose their stems and produces the garbled right-side text seen on paper.
        val padded =
            createBitmap(BrotherPtcBp.RASTER_WIDTH, source.width, Bitmap.Config.ARGB_8888)
        Canvas(padded).drawColor(Color.WHITE)
        val horizontalPadding = (BrotherPtcBp.RASTER_WIDTH - source.height) / 2
        for (sourceY in 0 until source.height) {
            for (sourceX in 0 until source.width) {
                padded[horizontalPadding + sourceY, sourceX] = source[sourceX, sourceY]
            }
        }
        source.recycle()

        val bytesPerLine = BrotherPtcBp.RASTER_WIDTH / 8
        val raster = ByteArray(padded.height * bytesPerLine)
        for (y in 0 until padded.height) {
            for (x in 0 until BrotherPtcBp.RASTER_WIDTH) {
                val pixel = padded[x, y]
                val sourcePixelIsWhite = Color.red(pixel) > 127
                if (printerWhiteBit(sourcePixelIsWhite, invert))
                    raster[y * bytesPerLine + x / 8] =
                        (raster[y * bytesPerLine + x / 8].toInt() or (0x80 shr (x % 8))).toByte()
            }
        }
        padded.recycle()
        return BrotherLabelRaster(raster, raster.size / bytesPerLine)
    }

    /**
     * Creates the human-readable label image before the printer protocol's head rotation. This
     * deliberately shares the QR/text sizing and layout path with [render], so the preview never
     * drifts from the label that will be sent to the printer.
     */
    fun preview(
        objectUrl: String,
        labelText: String,
        invert: Boolean = false,
        vertical: Boolean = false,
        qrSize: Int = QR_SIZE,
    ): Bitmap {
        val source = renderSource(objectUrl, labelText, vertical, qrSize)
        if (!invert) return source

        return createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
            bitmap ->
            for (y in 0 until source.height) {
                for (x in 0 until source.width) {
                    val pixel = source[x, y]
                    bitmap[x, y] =
                        Color.rgb(255 - Color.red(pixel), 255 - Color.green(pixel), 255 - Color.blue(pixel))
                }
            }
            source.recycle()
        }
    }

    private fun renderSource(
        objectUrl: String,
        labelText: String,
        vertical: Boolean,
        qrSize: Int,
    ): Bitmap {
        require(objectUrl.isNotBlank()) { "A device URL is required for the label QR code" }
        require(qrSize in 16..LABEL_HEIGHT) {
            "QR size must be between 16 and $LABEL_HEIGHT pixels"
        }
        val textPaint = labelTextPaint(labelText, fitWidth = vertical)
        val textLayout = measureText(textPaint, labelText)
        val qr = QrBitmap.encode(objectUrl, qrSize)
        val source =
            if (vertical) {
                renderVerticalSource(qr, textPaint, textLayout)
            } else {
                renderHorizontalSource(qr, textPaint, textLayout)
            }
        qr.recycle()
        return source
    }

    private fun labelTextPaint(text: String, fitWidth: Boolean): Paint {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textScaleX = 1f
                isAntiAlias = false
                isSubpixelText = false
                isLinearText = false
            }
        var textSize = 20f
        while (textSize >= 8f) {
            paint.textSize = textSize
            val layout = measureText(paint, text)
            if (
                layout.height <= LABEL_HEIGHT - TEXT_PADDING * 2 &&
                    (!fitWidth || layout.width <= LABEL_HEIGHT - TEXT_PADDING * 2)
            )
                break
            textSize -= 1f
        }
        return paint
    }

    private fun measureText(paint: Paint, text: String): LabelTextLayout {
        val lines =
            text
                .lines()
                .map { it.replace(Regex("[\\r\\t]+"), " ").trim() }
                .filter(String::isNotEmpty)
                .ifEmpty { listOf("") }
        val lineHeight =
            ceil(paint.fontMetrics.descent - paint.fontMetrics.ascent).toInt().coerceAtLeast(1)
        val width = lines.maxOf { ceil(paint.measureText(it)).toInt() }.coerceAtLeast(1)
        return LabelTextLayout(lines, width, lineHeight * lines.size, lineHeight)
    }

    private fun drawTextBlock(
        canvas: Canvas,
        paint: Paint,
        layout: LabelTextLayout,
        centerX: Float,
        centerY: Float,
    ) {
        val firstBaseline = centerY - layout.height / 2f - paint.ascent()
        layout.lines.forEachIndexed { index, line ->
            canvas.drawText(line, centerX, firstBaseline + index * layout.lineHeight, paint)
        }
    }

    private fun renderHorizontalSource(
        qr: Bitmap,
        paint: Paint,
        layout: LabelTextLayout,
    ): Bitmap {
        val textStart = LABEL_END_PADDING + qr.width + QR_TEXT_GAP
        val sourceWidth = textStart + layout.width + TEXT_PADDING
        return createBitmap(sourceWidth, LABEL_HEIGHT, Bitmap.Config.ARGB_8888).also { bitmap
            ->
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawBitmap(qr, LABEL_END_PADDING.toFloat(), (LABEL_HEIGHT - qr.height) / 2f, null)
                drawTextBlock(this, paint, layout, textStart + layout.width / 2f, LABEL_HEIGHT / 2f)
            }
        }
    }

    private fun renderVerticalSource(
        qr: Bitmap,
        paint: Paint,
        layout: LabelTextLayout,
    ): Bitmap {
        val textSourceWidth = layout.width + TEXT_PADDING * 2
        val textSourceHeight = layout.height + TEXT_PADDING * 2
        val textSource =
            createBitmap(textSourceWidth, textSourceHeight, Bitmap.Config.ARGB_8888)
        Canvas(textSource).apply {
            drawColor(Color.WHITE)
            drawTextBlock(this, paint, layout, textSourceWidth / 2f, textSourceHeight / 2f)
        }
        val rotation = Matrix().apply { setRotate(-90f) }
        val textColumn =
            Bitmap.createBitmap(
                textSource,
                0,
                0,
                textSource.width,
                textSource.height,
                rotation,
                false,
            )
        textSource.recycle()

        val textStart = LABEL_END_PADDING + qr.width + QR_TEXT_GAP
        val sourceWidth = textStart + textColumn.width + TEXT_PADDING
        return createBitmap(sourceWidth, LABEL_HEIGHT, Bitmap.Config.ARGB_8888).also { bitmap
            ->
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawBitmap(qr, LABEL_END_PADDING.toFloat(), (LABEL_HEIGHT - qr.height) / 2f, null)
                drawBitmap(
                    textColumn,
                    textStart.toFloat(),
                    (LABEL_HEIGHT - textColumn.height) / 2f,
                    null,
                )
            }
            textColumn.recycle()
        }
    }

    /** PTCBP uses a set bit for tape white space and a clear bit for a printed dot. */
    internal fun printerWhiteBit(sourcePixelIsWhite: Boolean, invert: Boolean): Boolean =
        if (invert) !sourcePixelIsWhite else sourcePixelIsWhite
}
