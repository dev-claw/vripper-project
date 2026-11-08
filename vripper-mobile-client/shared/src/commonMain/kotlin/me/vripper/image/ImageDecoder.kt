package me.vripper.image

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes encoded image bytes into an [ImageBitmap].
 *
 * Implementations should downsample large images so a single photo cannot exhaust
 * the process heap. [maxDimension] is the longest edge, in pixels, of the result.
 */
expect fun decodeImage(bytes: ByteArray, maxDimension: Int): ImageBitmap
