package me.vripper.gui.utils

import javafx.scene.image.Image
import javafx.scene.image.PixelFormat
import javafx.scene.image.WritableImage
import me.vripper.gui.utils.ImageDecodeUtils.scaleDownHighQuality
import me.vripper.utilities.LoggerDelegate
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayInputStream
import java.util.*
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Stateless image decoding helpers used by the photo viewer. Kept free of any
 * viewer/UI state so they can be unit-tested and reused independently of the
 * fragment that drives navigation and zoom.
 */
internal object ImageDecodeUtils {

    private val logger by LoggerDelegate()

    // Largest source-subsampling factor used while decoding (all ImageIO
    // readers support at least 1/2/4/8).
    private const val MAX_SUBSAMPLING = 8

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
     * extension), falling back to magic-byte sniffing for formats ImageIO does
     * not know (e.g. WebP).
     */
    fun detectFormat(bytes: ByteArray): String = runCatching {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes))?.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (readers.hasNext()) {
                val reader = readers.next()
                try {
                    return@runCatching reader.formatName.uppercase()
                } finally {
                    reader.dispose()
                }
            }
        }
        formatFromMagic(bytes)
    }.getOrElse { formatFromMagic(bytes) }

    private fun formatFromMagic(bytes: ByteArray): String {
        fun at(i: Int): Int = bytes[i].toInt() and 0xff
        return when {
            bytes.size >= 3 && at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "JPEG"
            bytes.size >= 8 && at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "PNG"
            bytes.size >= 6 && at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 -> "GIF"
            bytes.size >= 2 && at(0) == 0x42 && at(1) == 0x4D -> "BMP"
            bytes.size >= 12 && at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
                    at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50 -> "WEBP"

            else -> "?"
        }
    }

    /**
     * Decodes and resizes an image to the given display size.
     *
     * JavaFX's own scaling is not used on purpose: its minifier is a fixed
     * integer box filter centered on `floor(dx * scale)`, which is phase
     * misaligned and leaves stair/moiré artifacts on fine detail such as hair
     * when downscaling (and nearest-neighbour when upscaling, JDK-8090523).
     * Instead the image is decoded with ImageIO and resized with a progressive
     * high-quality bilinear filter (repeated halving), which is a good
     * approximation of an area-average low-pass filter and stays crisp.
     *
     * Falls back to JavaFX decoding for formats ImageIO cannot read (e.g. WebP).
     */
    fun decodeScaled(bytes: ByteArray, targetWidth: Double, targetHeight: Double): Image {
        decodeWithLibJpegTurbo(bytes, targetWidth, targetHeight)?.let { return it }
        val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        if (stream != null) {
            stream.use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (readers.hasNext()) {
                    val reader = readers.next()
                    try {
                        reader.input = input
                        val sourceWidth = reader.getWidth(0)
                        val sourceHeight = reader.getHeight(0)
                        if (sourceWidth > 0 && sourceHeight > 0) {
                            // Never upscale: clamp the target to the intrinsic size.
                            val width = min(targetWidth, sourceWidth.toDouble()).roundToInt().coerceAtLeast(1)
                            val height = min(targetHeight, sourceHeight.toDouble()).roundToInt().coerceAtLeast(1)
                            val buffered =
                                readSubsampled(reader, sourceWidth, sourceHeight, width, height)
                            if (buffered != null) {
                                val scaled = scaleDownHighQuality(buffered, width, height)
                                return toFxImage(scaled)
                            }
                            logger.debug("Subsampled image read failed, using the JavaFX fallback")
                        }
                    } catch (e: Exception) {
                        logger.debug("ImageIO decode failed, using the JavaFX fallback", e)
                    } finally {
                        runCatching { reader.dispose() }
                    }
                } else {
                    logger.debug("No ImageIO reader found, using the JavaFX fallback")
                }
            }
        }
        return Image(ByteArrayInputStream(bytes), targetWidth, targetHeight, true, true)
    }

    /**
     * JPEG fast path: libjpeg-turbo decodes at 1/2, 1/4 or 1/8 resolution,
     * skipping most of the IDCT work. Returns null for non-JPEG data, when the
     * native library is unavailable, or when decoding fails, so the caller can
     * fall back to the generic ImageIO path below.
     */
    private fun decodeWithLibJpegTurbo(
        bytes: ByteArray,
        targetWidth: Double,
        targetHeight: Double
    ): Image? {
        if (!FastJpegDecoder.isJpeg(bytes)) return null
        val result = FastJpegDecoder.decode(
            bytes,
            targetWidth.roundToInt(),
            targetHeight.roundToInt()
        ) ?: return null
        val width = min(targetWidth, result.image.width.toDouble()).roundToInt().coerceAtLeast(1)
        val height = min(targetHeight, result.image.height.toDouble()).roundToInt().coerceAtLeast(1)
        return toFxImage(scaleDownHighQuality(result.image, width, height))
    }

    /**
     * Decodes [reader]'s first image at a reduced resolution directly, instead
     * of decoding the full-resolution bitmap and then downscaling it. The
     * subsampling step is a power of two (the JPEG/PNG/GIF readers all support
     * at least 1/2/4/8) chosen so the decoded bitmap is still at least the
     * target size, and the small remainder is removed by [scaleDownHighQuality].
     * This is the dominant win for large images: a 6000x4000 JPEG targeting a
     * 2048px viewport decodes ~4x fewer pixels and allocates no full-size
     * intermediate buffers.
     */
    private fun readSubsampled(
        reader: ImageReader,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): BufferedImage? {
        val step = subsamplingStep(sourceWidth, sourceHeight, targetWidth, targetHeight)
        return if (step > 1) {
            runCatching {
                val param = reader.defaultReadParam ?: ImageReadParam()
                param.setSourceSubsampling(step, step, 0, 0)
                reader.read(0, param)
            }.getOrNull()
        } else {
            runCatching { reader.read(0) }.getOrNull()
        }
    }

    private fun subsamplingStep(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        var step = 1
        while (step < MAX_SUBSAMPLING &&
            sourceWidth / (step * 2) >= targetWidth &&
            sourceHeight / (step * 2) >= targetHeight
        ) {
            step *= 2
        }
        return step
    }

    /**
     * Downsamples [source] to [targetWidth] x [targetHeight] by repeatedly
     * halving with bilinear interpolation before a final step. Each step works
     * on at most a 2x reduction, which avoids the aliasing a single large-scale
     * bilinear step would produce. The source is usually already close to the
     * target because of source subsampling, so only one or two steps run.
     */
    private fun scaleDownHighQuality(
        source: BufferedImage,
        targetWidth: Int,
        targetHeight: Int
    ): BufferedImage {
        if (targetWidth >= source.width && targetHeight >= source.height) return source

        val hasAlpha = source.colorModel.hasAlpha()
        val type = if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB

        var current = source
        var width = source.width
        var height = source.height
        while (width / 2 >= targetWidth && height / 2 >= targetHeight) {
            width /= 2
            height /= 2
            current = drawScaled(current, width, height, type)
        }
        if (width != targetWidth || height != targetHeight) {
            current = drawScaled(current, targetWidth, targetHeight, type)
        }
        return current
    }

    private fun drawScaled(source: BufferedImage, width: Int, height: Int, type: Int): BufferedImage {
        val dest = BufferedImage(width, height, type)
        val graphics = dest.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
            )
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(source, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return dest
    }

    private fun toFxImage(source: BufferedImage): Image {
        val width = source.width
        val height = source.height
        val image = WritableImage(width, height)
        // TYPE_INT_ARGB stores exactly the non-premultiplied ARGB words the
        // pixel writer wants, so hand over the backing array directly instead
        // of copying it through an intermediate IntArray.
        val data = (source.raster.dataBuffer as? DataBufferInt)?.data
        if (source.type == BufferedImage.TYPE_INT_ARGB && data != null && data.size >= width * height) {
            image.pixelWriter.setPixels(
                0, 0, width, height, PixelFormat.getIntArgbInstance(), data, 0, width
            )
        } else {
            val pixels = IntArray(width * height)
            source.getRGB(0, 0, width, height, pixels, 0, width)
            image.pixelWriter.setPixels(
                0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width
            )
        }
        return image
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
    fun readSourceSize(bytes: ByteArray): Pair<Int, Int>? = runCatching {
        val imageInputStream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
            ?: return@runCatching null
        imageInputStream.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return@runCatching null
            val reader = readers.next()
            try {
                reader.input = stream
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()
}
