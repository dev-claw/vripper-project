package me.vripper.gui.utils

import me.vripper.utilities.LoggerDelegate
import org.bytedeco.leptonica.PIX
import org.bytedeco.leptonica.global.leptonica
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.math.min

/**
 * Fast JPEG decoding backed by libjpeg-turbo.
 *
 * The Leptonica JavaCPP preset bundles libjpeg-turbo natives for Windows,
 * Linux and macOS (x86_64 and arm64), so the same code path works on every
 * desktop platform the application ships for. libjpeg-turbo can decode a JPEG
 * directly at 1/2, 1/4 or 1/8 of its resolution (DCT scaling) instead of
 * decoding the full bitmap and shrinking it afterwards, which skips most of
 * the IDCT work and is several times faster than ImageIO.
 *
 * Everything here is best-effort: if the data is not a JPEG, the native
 * library cannot be loaded on the current platform, or decoding fails for any
 * reason, [decode] returns null and the caller falls back to ImageIO.
 */
object FastJpegDecoder {

    private val logger by LoggerDelegate()

    class Decoded(
        val image: BufferedImage,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val reduction: Int
    )

    /**
     * libjpeg-turbo only supports 1/1, 1/2, 1/4 and 1/8 DCT scaling.
     */
    private const val MAX_REDUCTION = 8

    @Volatile
    private var nativeUnavailable = false

    fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
                (bytes[0].toInt() and 0xff) == 0xFF &&
                (bytes[1].toInt() and 0xff) == 0xD8 &&
                (bytes[2].toInt() and 0xff) == 0xFF

    /**
     * Decodes [bytes] at the smallest power-of-two reduction still at least
     * [targetWidth] x [targetHeight]. Returns null to signal a fallback.
     */
    fun decode(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Decoded? {
        if (nativeUnavailable || !isJpeg(bytes)) return null
        return try {
            decodeNative(bytes, targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1))
        } catch (t: Throwable) {
            // UnsatisfiedLinkError/ExceptionInInitializerError on unsupported
            // platforms: disable the native path once and keep using ImageIO.
            nativeUnavailable = true
            logger.warn("FastJpegDecoder: native libjpeg-turbo unavailable, falling back to ImageIO", t)
            null
        }
    }

    private fun decodeNative(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Decoded? {
        val width = intArrayOf(0)
        val height = intArrayOf(0)
        val samplesPerPixel = intArrayOf(0)
        val ycck = intArrayOf(0)
        val cmyk = intArrayOf(0)
        val header = leptonica.readHeaderMemJpeg(
            bytes, bytes.size.toLong(), width, height, samplesPerPixel, ycck, cmyk
        )
        if (header != 0 || width[0] <= 0 || height[0] <= 0) return null

        val sourceWidth = width[0]
        val sourceHeight = height[0]
        val wantedWidth = min(targetWidth, sourceWidth)
        val wantedHeight = min(targetHeight, sourceHeight)
        val reduction = chooseReduction(sourceWidth, sourceHeight, wantedWidth, wantedHeight)

        // cmflag = 0 keeps RGB output, reduction is 1, 2, 4 or 8, no warning
        // buffer and no special hint flags.
        var pix = leptonica.pixReadMemJpeg(
            bytes, bytes.size.toLong(), 0, reduction, intArrayOf(0), 0
        ) ?: return null
        try {
            if (leptonica.pixGetDepth(pix) != 32) {
                // Grayscale/1bpp JPEGs are not packed as RGB(A) words.
                val converted = leptonica.pixConvertTo32(pix) ?: return null
                leptonica.pixDestroy(pix)
                pix = converted
            }
            return Decoded(pixToBufferedImage(pix), sourceWidth, sourceHeight, reduction)
        } finally {
            leptonica.pixDestroy(pix)
        }
    }

    private fun chooseReduction(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        var reduction = 1
        var candidate = 2
        while (candidate <= MAX_REDUCTION) {
            if (sourceWidth / candidate >= targetWidth && sourceHeight / candidate >= targetHeight) {
                reduction = candidate
            } else {
                break
            }
            candidate *= 2
        }
        return reduction
    }

    /**
     * Leptonica stores 32bpp pixels as 0xRRGGBBAA words regardless of the host
     * endianness, with [wordsPerLine] words per row (rows can be padded).
     */
    private fun pixToBufferedImage(pix: PIX): BufferedImage {
        val width = leptonica.pixGetWidth(pix)
        val height = leptonica.pixGetHeight(pix)
        val wordsPerLine = leptonica.pixGetWpl(pix)
        val data = leptonica.pixGetData(pix)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val pixels = (image.raster.dataBuffer as DataBufferInt).data
        var out = 0
        for (y in 0 until height) {
            val row = y.toLong() * wordsPerLine
            for (x in 0 until width) {
                val word = data.get(row + x)
                val r = (word ushr 24) and 0xff
                val g = (word ushr 16) and 0xff
                val b = (word ushr 8) and 0xff
                pixels[out++] = (r shl 16) or (g shl 8) or b
            }
        }
        return image
    }
}
