package me.vripper.gui.utils

import javafx.scene.image.Image
import javafx.scene.image.PixelFormat
import javafx.scene.image.WritableImage
import me.vripper.utilities.LoggerDelegate
import org.bytedeco.leptonica.PIX
import org.bytedeco.leptonica.global.leptonica
import kotlin.math.min

/**
 * Decodes and downscales images with Leptonica (already bundled for the JPEG
 * fast path), keeping the viewer pipeline free of AWT. AWT's X11 toolkit is
 * what triggers the "XSetErrorHandler() called with a GDK error trap pushed"
 * warning when it initializes while JavaFX's GTK backend is active.
 *
 * Everything is best-effort: if the data cannot be decoded or the native
 * library is unavailable, [decodeScaled] returns null and the caller falls
 * back to JavaFX's own decoder.
 */
object LeptonicaImageDecoder {

    private val logger by LoggerDelegate()

    private const val MAX_JPEG_REDUCTION = 8

    @Volatile
    private var nativeUnavailable = false

    fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
                (bytes[0].toInt() and 0xff) == 0xFF &&
                (bytes[1].toInt() and 0xff) == 0xD8 &&
                (bytes[2].toInt() and 0xff) == 0xFF

    /**
     * Reads the intrinsic dimensions from the encoded header without decoding
     * the full bitmap.
     */
    fun readSize(bytes: ByteArray): Pair<Int, Int>? {
        if (nativeUnavailable || bytes.isEmpty()) return null
        return try {
            val format = IntArray(1)
            val width = IntArray(1)
            val height = IntArray(1)
            val bitsPerSample = IntArray(1)
            val samplesPerPixel = IntArray(1)
            val hasColormap = IntArray(1)
            val rc = leptonica.pixReadHeaderMem(
                bytes,
                bytes.size.toLong(),
                format,
                width,
                height,
                bitsPerSample,
                samplesPerPixel,
                hasColormap,
            )
            if (rc == 0 && width[0] > 0 && height[0] > 0) width[0] to height[0] else null
        } catch (t: Throwable) {
            handleFailure(t)
            null
        }
    }

    /**
     * Decodes [bytes] and scales it down to fit [targetWidth] x [targetHeight]
     * (never upscales). Returns null when the native path cannot handle the
     * data so the caller can fall back to JavaFX decoding.
     */
    fun decodeScaled(bytes: ByteArray, targetWidth: Double, targetHeight: Double): Image? {
        if (nativeUnavailable || bytes.isEmpty()) return null
        try {
            val targetW = targetWidth.toInt().coerceAtLeast(1)
            val targetH = targetHeight.toInt().coerceAtLeast(1)
            val loaded = if (isJpeg(bytes)) {
                val source = readSize(bytes) ?: return null
                val reduction = jpegReduction(
                    source.first,
                    source.second,
                    min(targetW, source.first),
                    min(targetH, source.second),
                )
                leptonica.pixReadMemJpeg(
                    bytes,
                    bytes.size.toLong(),
                    0,
                    reduction,
                    IntArray(1),
                    0,
                )
            } else {
                leptonica.pixReadMem(bytes, bytes.size.toLong())
            } ?: return null

            var pix: PIX = loaded
            try {
                if (leptonica.pixGetDepth(pix) != 32) {
                    val converted = leptonica.pixConvertTo32(pix) ?: return null
                    leptonica.pixDestroy(pix)
                    pix = converted
                }

                val width = leptonica.pixGetWidth(pix)
                val height = leptonica.pixGetHeight(pix)
                if (width <= 0 || height <= 0) return null

                val widthTarget = min(targetW, width)
                val heightTarget = min(targetH, height)
                if (widthTarget != width || heightTarget != height) {
                    val scaleX = widthTarget.toDouble() / width
                    val scaleY = heightTarget.toDouble() / height
                    val scaled = if (scaleX <= 0.7 || scaleY <= 0.7) {
                        leptonica.pixScaleAreaMapToSize(pix, widthTarget, heightTarget)
                    } else {
                        leptonica.pixScaleLI(pix, scaleX.toFloat(), scaleY.toFloat())
                    } ?: return null
                    leptonica.pixDestroy(pix)
                    pix = scaled
                }
                return toFxImage(pix)
            } finally {
                leptonica.pixDestroy(pix)
            }
        } catch (t: Throwable) {
            handleFailure(t)
            return null
        }
    }

    private fun jpegReduction(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var reduction = 1
        var candidate = 2
        while (candidate <= MAX_JPEG_REDUCTION) {
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
     * endianness, with [wordsPerLine] words per row (rows can be padded). The
     * low byte carries alpha for images that have it and is ignored otherwise.
     */
    private fun toFxImage(pix: PIX): Image {
        val width = leptonica.pixGetWidth(pix)
        val height = leptonica.pixGetHeight(pix)
        val wordsPerLine = leptonica.pixGetWpl(pix)
        val data = leptonica.pixGetData(pix)
        val hasAlpha = leptonica.pixGetSpp(pix) >= 4
        val pixels = IntArray(width * height)
        var out = 0
        for (y in 0 until height) {
            val row = y.toLong() * wordsPerLine
            for (x in 0 until width) {
                val word = data.get(row + x)
                val r = (word ushr 24) and 0xff
                val g = (word ushr 16) and 0xff
                val b = (word ushr 8) and 0xff
                val a = if (hasAlpha) word and 0xff else 0xff
                pixels[out++] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val image = WritableImage(width, height)
        image.pixelWriter.setPixels(
            0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width
        )
        return image
    }

    private fun handleFailure(t: Throwable) {
        if (t is UnsatisfiedLinkError || t is ExceptionInInitializerError || t is NoClassDefFoundError) {
            nativeUnavailable = true
            logger.warn("LeptonicaImageDecoder: native decoding unavailable, falling back to JavaFX", t)
        } else {
            logger.debug("LeptonicaImageDecoder: decode failed, falling back to JavaFX", t)
        }
    }
}
