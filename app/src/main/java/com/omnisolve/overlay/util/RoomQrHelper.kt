package com.omnisolve.overlay.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.omnisolve.overlay.data.LiveClassRoomRepository

/**
 * Generates crisp offline Room Invite QR bitmaps (`prism://room/<CODE>`)
 * and launches the zero-permission Google Code Scanner to join rooms in 1 tap.
 */
object RoomQrHelper {

    fun buildRoomDeepLink(roomCode: String): String {
        val clean = LiveClassRoomRepository.normalizeRoomCode(roomCode)
        return "prism://room/$clean"
    }

    /**
     * Generates a high-contrast black-on-white scannable QR code Bitmap for a Live Class Room.
     */
    fun generateRoomQrBitmap(roomCode: String, sizePx: Int = 640): Bitmap? {
        val clean = LiveClassRoomRepository.normalizeRoomCode(roomCode)
        if (!LiveClassRoomRepository.isValidRoomCode(clean)) return null
        return try {
            val payload = buildRoomDeepLink(clean)
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts a valid 6-char room code from any scanned QR payload:
     * - `prism://room/K7M29Q`
     * - `https://.../room/K7M-29Q`
     * - `K7M-29Q` or `K7M29Q`
     */
    fun extractRoomCodeFromQrPayload(rawValue: String): String? {
        val trimmed = rawValue.trim()
        if (trimmed.isBlank()) return null

        val deepLinkMatch = Regex("""(?:prism://room/|/room/)([A-Za-z0-9\-]{6,10})""", RegexOption.IGNORE_CASE)
            .find(trimmed)
        if (deepLinkMatch != null) {
            val candidate = LiveClassRoomRepository.normalizeRoomCode(deepLinkMatch.groupValues[1])
            if (LiveClassRoomRepository.isValidRoomCode(candidate)) return candidate
        }

        val direct = LiveClassRoomRepository.normalizeRoomCode(trimmed)
        if (LiveClassRoomRepository.isValidRoomCode(direct)) return direct

        return null
    }

    /**
     * Launches Google Play Services Code Scanner (zero camera permission needed in manifest).
     */
    fun launchGoogleQrScanner(
        context: Context,
        onCodeScanned: (String) -> Unit,
        onMessage: (String) -> Unit
    ) {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val raw = barcode.rawValue.orEmpty()
                    val code = extractRoomCodeFromQrPayload(raw)
                    if (code != null) {
                        onCodeScanned(code)
                    } else {
                        onMessage("That QR code is not a valid Prism Room invite.")
                    }
                }
                .addOnCanceledListener {
                    // User closed scanner — stay quiet
                }
                .addOnFailureListener { e ->
                    val msg = e.localizedMessage.orEmpty()
                    if (msg.contains("module", ignoreCase = true) || msg.contains("waiting", ignoreCase = true)) {
                        onMessage("QR scanner is preparing on this device — try again in a few seconds or type the 6-letter code.")
                    } else {
                        onMessage("Could not open QR scanner here. Type or paste the 6-letter room code instead.")
                    }
                }
        } catch (_: Exception) {
            onMessage("QR scanner is unavailable on this device. Enter the 6-letter room code instead.")
        }
    }
}
