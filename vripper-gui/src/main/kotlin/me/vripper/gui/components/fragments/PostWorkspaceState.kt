package me.vripper.gui.components.fragments

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import me.vripper.entities.Status
import me.vripper.gui.controller.ImageController
import me.vripper.gui.controller.PostController
import me.vripper.gui.model.ImageModel
import me.vripper.gui.model.PostModel
import tornadofx.runLater

/**
 * Shared state for a single post workspace: the post itself and its images,
 * kept live-updated from the endpoint. Owned by the workspace fragment; all
 * mutation of the observable models happens on the FX thread.
 */
class PostWorkspaceState(
    val postEntityId: Long,
    private val imageController: ImageController,
    private val postController: PostController,
    initialIndex: Int = 0,
) {

    val images: ObservableList<ImageModel> = FXCollections.observableArrayList()
    val selectedIndex = SimpleIntegerProperty(initialIndex.coerceAtLeast(0))
    val postModel: ObjectProperty<PostModel> = SimpleObjectProperty()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableListOf<Job>()

    fun start() {
        jobs += scope.launch {
            val list = imageController.findImages(postEntityId)
            runLater {
                images.setAll(list)
                selectedIndex.set(selectedIndex.get().coerceIn(0, (images.size - 1).coerceAtLeast(0)))
            }
        }

        jobs += scope.launch {
            imageController.onUpdateImages(postEntityId).collect { image ->
                runLater {
                    val model = images.find { it.id == image.id } ?: return@runLater
                    model.size = image.size
                    model.status = image.status.name
                    model.filename = image.filename
                    model.downloaded = image.downloaded
                    model.progress = imageController.progress(image.size, image.downloaded)
                }
            }
        }

        jobs += scope.launch {
            imageController.onStopped().collect {
                runLater {
                    images.forEach { model ->
                        if (model.status != Status.FINISHED.name) {
                            model.status = Status.STOPPED.name
                        }
                    }
                }
            }
        }

        jobs += scope.launch {
            val model = postController.find(postEntityId)
            runLater {
                // A live update may have installed a fresher model already.
                if (model != null && postModel.get() == null) postModel.set(model)
            }
        }

        jobs += scope.launch {
            postController.updatePostsFlow.filter { it.id == postEntityId }.collect { post ->
                runLater {
                    val model = postModel.get()
                    if (model == null) {
                        // The initial fetch has not landed yet: use this live
                        // snapshot so an update is never dropped.
                        postModel.set(postController.toModel(post))
                        return@runLater
                    }
                    model.apply {
                        status = post.status.stringValue.lowercase().replaceFirstChar { it.uppercase() }
                        progressCount = postController.progressCount(post.total, post.done, post.downloaded)
                        done = post.done
                        total = post.total
                        progress = postController.progress(post.total, post.done)
                        path = post.getDownloadFolder()
                        folderName = post.folderName
                    }
                }
            }
        }

        jobs += scope.launch {
            postController.updateMetadataFlow.filter { it.postIdRef == postEntityId }.collect { metadata ->
                runLater {
                    postModel.get()?.apply {
                        altTitles = FXCollections.observableArrayList(metadata.data.resolvedNames)
                        postedBy = metadata.data.postedBy
                    }
                }
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        scope.cancel()
    }
}
