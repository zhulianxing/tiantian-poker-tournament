package com.pokernight.tvdisplay.ui.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Generates a QR code bitmap from text using ZXing.
 */
fun generateQrCode(text: String, size: Int = 300): Bitmap? {
    return try {
        val hints = hashMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 1
        val bitMatrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints,
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
