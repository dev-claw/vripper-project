package me.vripper.gui.controller

import me.vripper.entities.ImageEntity
import me.vripper.entities.Status
import me.vripper.gui.components.fragments.BytesImageSource
import me.vripper.gui.components.fragments.ImageSource
import me.vripper.gui.model.ImageModel
import me.vripper.gui.utils.AppEndpointManager.currentAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.localAppEndpointService
import me.vripper.gui.utils.AppEndpointManager.remoteAppEndpointService
import me.vripper.gui.utils.ChannelFlowBuilder
import me.vripper.gui.utils.GuiErrorReporter
import me.vripper.model.DownloadRequest
import me.vripper.model.Image
import tornadofx.Controller

class ImageController : Controller() {

    suspend fun findImages(postEntityId: Long): List<ImageModel> {
        return GuiErrorReporter.call("images.find") {
            currentAppEndpointService().findImagesByPostEntityId(postEntityId).map(::mapper)
        }.getOrDefault(emptyList())
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

    fun onUpdateImages(postId: Long) = ChannelFlowBuilder.build(
        { localAppEndpointService.onUpdateImagesByPostEntityId(postId) },
        { remoteAppEndpointService.onUpdateImagesByPostEntityId(postId) },
        "images.update",
    )

    fun onStopped() = ChannelFlowBuilder.build(
        localAppEndpointService::onStopped,
        remoteAppEndpointService::onStopped,
        "images.stopped",
    )

    suspend fun getImageSources(item: ImageModel): Map<ImageEntity, ImageSource> {
        val imageEntities = GuiErrorReporter.call("images.find") {
            currentAppEndpointService().findImagesByPostEntityId(item.postEntityId)
        }.getOrDefault(emptyList())
        return imageEntities.associateWith { imageEntity ->
            BytesImageSource(
                imageEntity.id,
                imageEntity.filename,
                imageEntity.thumbUrl,
                imageEntity.postEntityId,
                imageEntity.status == Status.FINISHED
            ) {
                currentAppEndpointService().downloadImage(DownloadRequest(imageEntity.id))
            }
        }
    }
}