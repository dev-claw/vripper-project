package me.vripper.gui.components.views

import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.control.Button
import javafx.scene.control.TabPane
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import me.vripper.entities.CustomField
import me.vripper.gui.controller.PostController
import me.vripper.gui.model.PostModel
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

class PostInfoView : View() {
    private val postController: PostController by inject()
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imagesTableView: ImagesTableView by inject()
    private val postModel: PostModel = PostModel(
        -1,
        -1,
        "",
        0.0,
        "",
        "",
        0,
        0,
        "",
        "",
        -1,
        "",
        "",
        "",
        emptyList(),
        emptyList(),
        "",
        0,
        emptyList()
    )
    val selectedId = SimpleLongProperty(-1)
    val notSelected: ObservableValue<Boolean> = selectedId.map { it as Long == -1L }
    val customFieldsProperty: SimpleListProperty<CustomField> = SimpleListProperty(FXCollections.observableArrayList())

    override val root = tabpane()
    lateinit var vbox: VBox

    init {
        with(root) {
            this.id = "postinfo_panel"
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            tab("General") {
                graphic = FontIcon(Feather.INFO)
                scrollpane {
                    form {
                        fieldset("Post Details") {
                            field("Post Link:") {
                                textfield(postModel.urlProperty) {
                                    isEditable = false
                                    visibleWhen(postModel.urlProperty.isNotEmpty)
                                }
                            }
                            field("Posted By:") {
                                label(postModel.postedByProperty)
                            }
                            field("Title:") {
                                label(postModel.titleProperty)
                            }
                            field("More titles:") {
                                label(postModel.altTitlesProperty.map { it.joinToString(", ") })
                            }
                            field("Status:") {
                                label(postModel.statusProperty.map { it ->
                                    it.lowercase().replaceFirstChar { it.uppercase() }
                                })
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
                        fieldset("Custom Fields") {
                            vbox {
                                hiddenWhen { notSelected }
                                padding = Insets(5.0)
                                spacing = 5.0
                                hbox {
                                    spacing = 5.0
                                    label("Field").apply { padding = Insets(0.0, 5.0, 0.0, 5.0); prefWidth = 200.0 }
                                    label("Value").apply { padding = Insets(0.0, 5.0, 0.0, 5.0); prefWidth = 200.0 }
                                }
                                val fields = vbox fields@{
                                    spacing = 5.0
                                    this@fields.bindChildren(postModel.customFieldsProperty) {
                                        HBox(5.0).apply {
                                            this.children.add(TextField(it.name).apply { prefWidth = 200.0 })
                                            this.children.add(TextField(it.value).apply { prefWidth = 200.0 })
                                            this.children.add(Button("Remove").apply {
                                                action {
                                                    postModel.customFields.remove(it)
                                                }
                                            })
                                        }
                                    }
                                }
                                hbox {
                                    spacing = 5.0
                                    button("Add").apply {
                                        action {
                                            postModel.customFields.add(CustomField("", ""))
                                        }
                                    }
                                    button("Save").apply {
                                        action {
                                            coroutineScope.launch {
                                                postController.updateCustomFields(
                                                    postModel.id,
                                                    fields.children.map {
                                                        val hbox = (it as HBox)
                                                        val field = (hbox.children[0] as TextField).text
                                                        val value = (hbox.children[1] as TextField).text

                                                        CustomField(field, value)
                                                    })
                                            }
                                        }
                                    }
                                }
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
        selectedId.set(id ?: -1)
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
                this.customFieldsProperty.clear()
            }
            return
        }
        coroutineScope.launch {
            val model: PostModel? = postController.find(id)
            if (model == null) {
                return@launch
            }
            runLater {
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
                    this.customFields = model.customFields
                }
            }
        }

        postController.updatePostsFlow.let { flow ->
            coroutineScope.launch {
                flow.filter {
                    it.id == postModel.id
                }.collect { post ->
                    runLater {
                        postModel.status =
                            post.status.stringValue.lowercase().replaceFirstChar { it.uppercase() }
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
        }

        postController.updateMetadataFlow.let { flow ->
            coroutineScope.launch {
                flow.filter {
                    it.postIdRef == postModel.id
                }.collect {
                    runLater {
                        postModel.altTitles = FXCollections.observableArrayList(it.data.resolvedNames)
                        postModel.postedBy = it.data.postedBy
                    }
                }
            }
        }
    }
}

