package dev.pschmitt.nyetbox.qrsetup

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/** Android-only QR rendering wrapper around the already bundled ZXing core library. */
object QrBitmap {
    fun encode(payload: String, size: Int = 768): Bitmap {
        val matrix =
            MultiFormatWriter()
                .encode(
                    payload,
                    BarcodeFormat.QR_CODE,
                    size,
                    size,
                    mapOf(EncodeHintType.MARGIN to 1),
                )
        return createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (y in 0 until size) {
                for (x in 0 until size) {
                    bitmap[x, y] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
        }
    }
}
