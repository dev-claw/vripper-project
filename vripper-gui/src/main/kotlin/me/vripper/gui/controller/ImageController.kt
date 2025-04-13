package me.vripper.gui.controller

import me.vripper.gui.model.ImageModel
import me.vripper.model.Image
import me.vripper.services.IAppEndpointService
import tornadofx.Controller

class ImageController : Controller() {
    lateinit var appEndpointService: IAppEndpointService
    suspend fun findImages(postId: Long): List<ImageModel> {
        return appEndpointService.findImagesByPostId(postId).map(::mapper)
    }

    private fun mapper(it: Image): ImageModel {
        return ImageModel(
            it.id,
            it.index + 1,
            it.url,
            progress(it.size, it.downloaded),
            it.status.name,
            it.size,
            it.downloaded,
            it.filename,
            it.thumbUrl
        )
    }

    fun progress(size: Long, downloaded: Long): Double {
        return if (downloaded == 0L && size == 0L) {
            0.0
        } else {
            downloaded.toDouble() / size
        }
    }

    fun onUpdateImages(postId: Long) =
        appEndpointService.onUpdateImagesByPostId(postId)

    fun onStopped() = appEndpointService.onStopped()

}