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
    private const val TEXT_WIDTH = 64

    fun render(objectUrl: String, labelText: String): BrotherLabelRaster {
        require(objectUrl.isNotBlank()) { "A device URL is required for the label QR code" }
        val source = Bitmap.createBitmap(QR_SIZE + TEXT_WIDTH, LABEL_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(source).apply {
            drawColor(Color.WHITE)
            val qr = QrBitmap.encode(objectUrl, QR_SIZE)
            drawBitmap(qr, 0f, 0f, null)
            qr.recycle()

            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    typeface = Typeface.DEFAULT
                    textAlign = Paint.Align.CENTER
                }
            val text = labelText.replace(Regex("[\\r\\n\\t]+"), " ").trim()
            var textSize = 20f
            val bounds = Rect()
            while (textSize >= 8f) {
                paint.textSize = textSize
                paint.getTextBounds(text, 0, text.length, bounds)
                if (bounds.height() <= LABEL_HEIGHT - 8) break
                textSize -= 1f
            }
            val x = QR_SIZE + TEXT_WIDTH / 2f
            val y = LABEL_HEIGHT / 2f - (paint.ascent() + paint.descent()) / 2f
            drawText(text, x, y, paint)
        }

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
                val isWhite = Color.red(pixel) > 127
                if (isWhite) raster[y * bytesPerLine + x / 8] =
                    (raster[y * bytesPerLine + x / 8].toInt() or (0x80 shr (x % 8))).toByte()
            }
        }
        padded.recycle()
        return BrotherLabelRaster(raster, raster.size / bytesPerLine)
    }
}
