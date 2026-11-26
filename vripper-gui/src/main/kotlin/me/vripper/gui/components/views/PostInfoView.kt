package me.vripper.gui.components.views

import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.control.TabPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import me.vripper.gui.controller.PostController
import me.vripper.gui.model.PostModel
import me.vripper.gui.utils.displayStatusLabel
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import me.vripper.gui.fx.*

class PostInfoView : View() {
    private val postController: PostController by inject()
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imagesTableView: ImagesTableView by inject()
    private val postModel: PostModel = PostModel(
        -1, -1, "", 0.0, "", "", 0, 0, "", "", -1, "", "", "", emptyList(), emptyList(), "", 0
    )

    @Volatile
    private var selectionToken = 0L
    private var detailJob: Job? = null
    private var postUpdatesJob: Job? = null
    private var metadataUpdatesJob: Job? = null

    override val root = tabpane()

    init {
        with(root) {
            this.id = "postinfo_panel"
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            tab("General") {
                graphic = FontIcon(Feather.INFO)
                scrollpane(fitToWidth = true) {
                    form {
                        padding = Insets(12.0)
                        fieldset {
                            field("Post Link:") {
                                textfield(postModel.urlProperty) {
                                    isEditable = false
                                    visibleWhen(postModel.urlProperty.isNotEmpty)
                                }
                            }
                            field("Author:") {
                                label(postModel.authorProperty)
                            }
                            field("Posted By:") {
                                label(postModel.postedByProperty)
                            }
                            field("Posted On:") {
                                label(postModel.postDateProperty)
                            }
                            field("Edited:") {
                                label(postModel.editedProperty)
                                visibleWhen(postModel.editedProperty.isNotEmpty)
                            }
                            field("Title:") {
                                label(postModel.titleProperty)
                            }
                            field("More titles:") {
                                label(postModel.altTitlesProperty.map { it.joinToString(", ") })
                            }
                            field("Status:") {
                                label(postModel.statusProperty.map { it.displayStatusLabel() })
                            }

                            field("Path:") {
                                label(postModel.pathProperty)
                            }
                            field("Total:") {
                                label(postModel.progressCountProperty)
                            }
                            field("Hosts:") {
                                label(postModel.hostsProperty)
                            }

                            field("Added On:") {
                                label(postModel.addedOnProperty)
                            }
                        }
                    }
                }
            }
            tab("Photos") {
                graphic = FontIcon(Feather.IMAGE)
                add(imagesTableView)
            }
        }
    }

    fun setPostId(id: Long?) {
        imagesTableView.setPostId(id)
        detailJob?.cancel()
        postUpdatesJob?.cancel()
        metadataUpdatesJob?.cancel()
        detailJob = null
        postUpdatesJob = null
        metadataUpdatesJob = null
        val token = ++selectionToken

        if (id == null) {
            postModel.apply {
                this.title = ""
                this.progress = 0.0
                this.status = ""
                this.url = ""
                this.done = 0
                this.total = 0
                this.hosts = ""
                this.addedOn = ""
                this.order = -1
                this.path = ""
                this.folderName = ""
                this.progressCount = ""
                this.previewList.clear()
                this.altTitles.clear()
                this.postedBy = ""
                this.author = ""
                this.postDate = ""
                this.edited = ""
            }
            return
        }

        detailJob = coroutineScope.launch {
            val model: PostModel? = postController.find(id)
            if (model == null || token != selectionToken) {
                return@launch
            }
            runLater {
                if (token != selectionToken) return@runLater
                postModel.apply {
                    this.id = model.id
                    this.vgPostId = model.vgPostId
                    this.vgThreadId = model.vgThreadId
                    this.title = model.title
                    this.progress = model.progress
                    this.status = model.status.lowercase().replaceFirstChar { it.uppercase() }
                    this.url = model.url
                    this.done = model.done
                    this.total = model.total
                    this.hosts = model.hosts
                    this.addedOn = model.addedOn
                    this.order = model.order
                    this.path = model.path
                    this.folderName = model.folderName
                    this.progressCount = model.progressCount
                    this.previewList = model.previewList
                    this.altTitles = model.altTitles
                    this.postedBy = model.postedBy
                    this.author = model.author
                    this.postDate = model.postDate
                    this.edited = model.edited
                }
            }
        }

        postUpdatesJob = coroutineScope.launch {
            postController.updatePostsFlow.filter { it.id == id }.collect { post ->
                runLater {
                    if (token != selectionToken) return@runLater
                    postModel.status = post.status.stringValue.lowercase().replaceFirstChar { it.uppercase() }
                    postModel.progressCount = postController.progressCount(
                        post.total, post.done, post.downloaded
                    )
                    postModel.done = post.done
                    postModel.progress = postController.progress(
                        post.total, post.done
                    )
                    postModel.path = post.getDownloadFolder()
                    postModel.folderName = post.folderName
                }
            }
        }

        metadataUpdatesJob = coroutineScope.launch {
            postController.updateMetadataFlow.filter { it.postIdRef == id }.collect {
                runLater {
                    if (token != selectionToken) return@runLater
                    postModel.altTitles = FXCollections.observableArrayList(it.data.resolvedNames)
                    postModel.postedBy = it.data.postedBy
                }
            }
        }
    }
}
