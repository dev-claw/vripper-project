package me.vripper.gui.utils

import javafx.scene.image.Image
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlin.math.min

/**
 * Stateless image decoding helpers used by the photo viewer. Kept free of any
 * viewer/UI state so they can be unit-tested and reused independently of the
 * fragment that drives navigation and zoom.
 */
internal object ImageDecodeUtils {

    fun formatSize(bytes: Long): String {
        val kb = 1024.0
        return when {
            bytes < kb -> "$bytes B"
            bytes < kb * kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            bytes < kb * kb * kb -> String.format(Locale.US, "%.2f MB", bytes / (kb * kb))
            else -> String.format(Locale.US, "%.2f GB", bytes / (kb * kb * kb))
        }
    }

    /**
     * Detects the actual image format from the encoded data (not the file
     * extension) using magic bytes, so it needs no image library.
     */
    fun detectFormat(bytes: ByteArray): String {
        fun at(i: Int): Int = bytes[i].toInt() and 0xff
        return when {
            bytes.size >= 3 && at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "JPEG"
            bytes.size >= 8 && at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "PNG"
            bytes.size >= 6 && at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 -> "GIF"
            bytes.size >= 2 && at(0) == 0x42 && at(1) == 0x4D -> "BMP"
            bytes.size >= 12 && at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
                    at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50 -> "WEBP"
            bytes.size >= 4 && (
                    (at(0) == 0x49 && at(1) == 0x49 && at(2) == 0x2A && at(3) == 0x00) ||
                            (at(0) == 0x4D && at(1) == 0x4D && at(2) == 0x00 && at(3) == 0x2A)
                    ) -> "TIFF"

            else -> "?"
        }
    }

    /**
     * Decodes and scales an image to fit the given display size.
     *
     * Leptonica does the decode and the high-quality downscale natively: JPEG
     * can be decoded at 1/2, 1/4 or 1/8 resolution (skipping most of the IDCT
     * work) and other formats use area-map scaling, which is a good
     * approximation of an area-average low-pass filter and stays crisp.
     *
     * Falls back to JavaFX's decoder when the native path cannot handle the
     * data. Neither path uses AWT, so no X11 toolkit is initialized.
     */
    fun decodeScaled(bytes: ByteArray, targetWidth: Double, targetHeight: Double): Image {
        LeptonicaImageDecoder.decodeScaled(bytes, targetWidth, targetHeight)?.let { return it }
        return Image(ByteArrayInputStream(bytes), targetWidth, targetHeight, true, true)
    }

    /**
     * Computes the size (device pixels) to decode at, never exceeding the
     * intrinsic size of the source image.
     */
    fun decodeSize(
        sourceSize: Pair<Int, Int>?,
        targetWidth: Double,
        targetHeight: Double
    ): Pair<Double, Double> {
        if (sourceSize == null) return targetWidth to targetHeight
        val (sourceWidth, sourceHeight) = sourceSize
        if (sourceWidth <= 0 || sourceHeight <= 0) return targetWidth to targetHeight
        val scale = min(1.0, min(targetWidth / sourceWidth, targetHeight / sourceHeight))
        return (sourceWidth * scale).coerceAtLeast(1.0) to (sourceHeight * scale).coerceAtLeast(1.0)
    }

    /**
     * Reads the intrinsic dimensions from the encoded image header without
     * decoding the full bitmap.
     */
    fun readSourceSize(bytes: ByteArray): Pair<Int, Int>? = LeptonicaImageDecoder.readSize(bytes)
}
