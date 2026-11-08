package me.vripper.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap

@Suppress("UNUSED_PARAMETER")
actual fun decodeImage(bytes: ByteArray, maxDimension: Int): ImageBitmap =
    bytes.decodeToImageBitmap()
