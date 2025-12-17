package com.steamdeck.mobile.core.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QRコード生成ユーティリティ
 *
 * Best Practice: ZXing coreライブラリを使用
 * Reference:
 * - https://github.com/zxing/zxing
 * - https://dev.to/devniiaddy/qr-code-with-jetpack-compose-47e
 * - https://blog.simonsickle.com/drawing-qr-codes-in-jetpack-compose
 */
object QrCodeGenerator {

    /**
     * テキストからQRコードBitmapを生成
     *
     * Best Practice:
     * - Error Correction Level = H (30%エラー訂正)
     * - Character Set = UTF-8
     * - Margin = 1 (パディング最小化)
     *
     * @param content QRコードに埋め込むテキスト
     * @param size Bitmapサイズ（幅=高さ）
     * @param foregroundColor QRコード色（デフォルト: 黒）
     * @param backgroundColor 背景色（デフォルト: 白）
     * @return QRコードBitmap
     */
    fun generateQrCodeBitmap(
        content: String,
        size: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val hints = hashMapOf<EncodeHintType, Any>().apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H) // 高エラー訂正
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1) // パディング最小化
        }

        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) foregroundColor else backgroundColor)
            }
        }

        return bitmap
    }

    /**
     * Steam Challenge URLをQRコードに変換
     *
     * Steam Challenge URL形式:
     * steammobile://mobileconf/v1/login?client_id=XXX&request_id=YYY
     *
     * @param challengeUrl Steam APIから取得したチャレンジURL
     * @param size Bitmapサイズ
     * @return QRコードBitmap
     */
    fun generateSteamQrCode(
        challengeUrl: String,
        size: Int = 512
    ): Bitmap {
        return generateQrCodeBitmap(
            content = challengeUrl,
            size = size,
            foregroundColor = Color.BLACK,
            backgroundColor = Color.WHITE
        )
    }

    /**
     * QRコード生成が可能かバリデーション
     *
     * @param content 生成するコンテンツ
     * @return 生成可能ならtrue
     */
    fun isValidContent(content: String): Boolean {
        if (content.isBlank()) return false

        // QRコードの最大容量チェック（Alphanumericで約4296文字）
        if (content.length > 4000) return false

        return true
    }
}
