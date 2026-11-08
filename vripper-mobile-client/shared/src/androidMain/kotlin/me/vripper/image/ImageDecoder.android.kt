package me.vripper.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max

/**
 * Android decoder that downsamples while decoding instead of loading the full
 * resolution bitmap into memory first. This is the main safeguard against
 * `OutOfMemoryError` when opening large photos on low-end devices.
 */
actual fun decodeImage(bytes: ByteArray, maxDimension: Int): ImageBitmap {
    require(bytes.isNotEmpty()) { "Image data is empty" }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image format" }

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: error("Unsupported image format")
    return bitmap.asImageBitmap()
}

private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    if (maxDimension <= 0) return 1
    val longestEdge = max(width, height)
    var sampleSize = 1
    while (longestEdge / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}
