package me.vripper.gui.controller

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import me.vripper.gui.model.ImageModel
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.model.Image
import tornadofx.Controller

class ImageController : Controller() {

    suspend fun findImages(postEntityId: Long): List<ImageModel> {
        return runCatching {
            currentAppEndpointService().findImagesByPostEntityId(postEntityId).map(::mapper)
        }.getOrDefault(
            emptyList()
        )
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
            it.thumbUrl,
            it.postEntityId
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
        currentAppEndpointService().onUpdateImagesByPostEntityId(postId).retryWhen { _, _ -> delay(1000); true }

    fun onStopped() = currentAppEndpointService().onStopped().retryWhen { _, _ -> delay(1000);true }
}