package me.vripper.gui.components.fragments

import kotlinx.coroutines.flow.Flow
import me.vripper.model.ImageChunk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

interface ImageSource {
    suspend fun inputStream(): InputStream?
    fun fileName(): String
}

data class BytesImageSource(
    val fileName: String,
    val downloadFunction: () -> Flow<ImageChunk>
) : ImageSource {

    override suspend fun inputStream(): InputStream? {
        val bos = ByteArrayOutputStream()
        var missing = false
        downloadFunction().collect {
            if (it.missing) {
                missing = true
            } else {
                bos.write(it.data)
            }
        }
        val bytes = bos.toByteArray()
        return if (missing) null else ByteArrayInputStream(bytes)
    }

    override fun fileName(): String {
        return fileName
    }
}