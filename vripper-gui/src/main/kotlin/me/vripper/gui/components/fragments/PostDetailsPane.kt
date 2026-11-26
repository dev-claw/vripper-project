package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.beans.binding.Bindings
import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.vripper.entities.CustomField
import me.vripper.gui.controller.PostController
import me.vripper.gui.fx.FormGrid
import me.vripper.gui.model.PostModel
import me.vripper.gui.utils.displayActionState
import me.vripper.gui.utils.displayStatusLabel
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon

/**
 * Details panel for the post currently open in the workspace. The upper part is
 * read-only post metadata; the lower part is the post automation review area
 * where custom fields can be completed and actions run, retried, or skipped.
 */
class PostDetailsPane(
    private val postController: PostController,
    private val scope: CoroutineScope,
) {

    private val link = valueField()
    private val postedBy = valueField()
    private val title = valueField()
    private val altTitles = valueField()
    private val status = valueField()
    private val path = valueField()
    private val total = valueField()
    private val hosts = valueField()
    private val addedOn = valueField()
    private val automation = valueField()
    private val missingFields = valueField()
    private val actionError = valueField()
    private val customFieldsEditor = TextArea().apply {
        prefRowCount = 4
        promptText = "name=value, one per line"
    }
    private val startButton = Button("Start", FontIcon.of(Feather.PLAY))
    private val stopButton = Button("Stop", FontIcon.of(Feather.SQUARE))
    private val saveFieldsButton = Button("Save fields", FontIcon.of(Feather.SAVE))
    private val runButton = Button("Run action", FontIcon.of(Feather.PLAY))
    private val retryButton = Button("Retry action", FontIcon.of(Feather.REFRESH_CW))
    private val skipButton = Button("Skip action", FontIcon.of(Feather.SKIP_FORWARD))

    private val detailsGrid = FormGrid().apply {
        padding = Insets(6.0, 12.0, 12.0, 12.0)
        addRow(
            fieldLabel("Downloads:"),
            HBox(6.0, startButton, stopButton).apply { alignment = Pos.CENTER_LEFT },
        )
        addRow(fieldLabel("Post Link:"), link)
        addRow(fieldLabel("Posted By:"), postedBy)
        addRow(fieldLabel("Title:"), title)
        addRow(fieldLabel("More titles:"), altTitles)
        addRow(fieldLabel("Status:"), status)
        addRow(fieldLabel("Path:"), path)
        addRow(fieldLabel("Total:"), total)
        addRow(fieldLabel("Hosts:"), hosts)
        addRow(fieldLabel("Added On:"), addedOn)
    }

    private val automationGrid = FormGrid().apply {
        padding = Insets(6.0, 12.0, 12.0, 12.0)
        addRow(fieldLabel("Automation:"), automation)
        addRow(fieldLabel("Missing fields:"), missingFields)
        addRow(fieldLabel("Action error:"), actionError)
        addRow(fieldLabel("Custom fields:"), customFieldsEditor)
        addRow(null, HBox(6.0, saveFieldsButton, runButton, retryButton, skipButton))
    }

    val root: Region = ScrollPane(
        VBox(
            12.0,
            section("General", detailsGrid),
            Separator(),
            section("Automation", automationGrid),
        ).apply {
            padding = Insets(0.0, 0.0, 12.0, 0.0)
        }
    ).apply {
        id = "post_details_panel"
        isFitToWidth = true
        hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        style = "-fx-background-color: transparent;"
    }

    private var model: PostModel? = null
    private val customFieldsListener = ListChangeListener<CustomField> {
        updateCustomFieldsText()
    }

    init {
        startButton.setOnAction {
            model?.let { scope.launch { postController.start(listOf(it.id)) } }
        }
        stopButton.setOnAction {
            model?.let { scope.launch { postController.stop(listOf(it.id)) } }
        }
        saveFieldsButton.setOnAction {
            val current = model ?: return@setOnAction
            val fields = customFieldsEditor.text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains("=") }
                .map {
                    val name = it.substringBefore("=").trim()
                    val value = it.substringAfter("=").trim()
                    CustomField(name, value)
                }
                .toList()
            scope.launch { postController.updateCustomFields(current.id, fields) }
        }
        runButton.setOnAction {
            model?.let { scope.launch { postController.runAction(it.id) } }
        }
        retryButton.setOnAction {
            model?.let { scope.launch { postController.retryAction(it.id) } }
        }
        skipButton.setOnAction {
            model?.let { scope.launch { postController.skipAction(it.id) } }
        }
        updateActionButtons()
    }

    fun setPostModel(model: PostModel?) {
        if (this.model === model) return
        clearBindings()
        this.model = model
        if (model == null) {
            listOf(
                link, postedBy, title, altTitles, status, path, total, hosts, addedOn,
                automation, missingFields, actionError,
            ).forEach {
                it.text = ""
            }
            customFieldsEditor.text = ""
            updateActionButtons()
            return
        }
        link.textProperty().bind(model.urlProperty)
        postedBy.textProperty().bind(model.postedByProperty)
        title.textProperty().bind(model.titleProperty)
        altTitles.textProperty().bind(
            Bindings.createStringBinding(
                { model.altTitles.joinToString(", ") },
                model.altTitlesProperty
            )
        )
        status.textProperty().bind(
            Bindings.createStringBinding(
                { model.status.displayStatusLabel() },
                model.statusProperty
            )
        )
        path.textProperty().bind(model.pathProperty)
        total.textProperty().bind(model.progressCountProperty)
        hosts.textProperty().bind(model.hostsProperty)
        addedOn.textProperty().bind(model.addedOnProperty)
        automation.textProperty().bind(
            Bindings.createStringBinding(
                {
                    model.actionState.displayActionState()
                },
                model.actionStateProperty
            )
        )
        missingFields.textProperty().bind(
            Bindings.createStringBinding(
                { if (model.missingFields.isEmpty()) "None" else model.missingFields.joinToString(", ") },
                model.missingFieldsProperty
            )
        )
        actionError.textProperty().bind(model.actionErrorProperty)
        model.customFieldsProperty.addListener(customFieldsListener)
        updateCustomFieldsText()
        updateActionButtons()
    }

    private fun updateCustomFieldsText() {
        customFieldsEditor.text = model?.customFields?.joinToString("\n") { "${it.name}=${it.value}" }.orEmpty()
    }

    private fun updateActionButtons() {
        val disabled = model == null
        startButton.isDisable = disabled
        stopButton.isDisable = disabled
        saveFieldsButton.isDisable = disabled
        runButton.isDisable = disabled
        retryButton.isDisable = disabled
        skipButton.isDisable = disabled
    }

    private fun clearBindings() {
        listOf(
            link, postedBy, title, altTitles, status, path, total, hosts, addedOn,
            automation, missingFields, actionError,
        ).forEach {
            it.textProperty().unbind()
        }
        model?.customFieldsProperty?.removeListener(customFieldsListener)
    }

    private fun section(title: String, content: Region) = VBox(
        4.0,
        Label(title).apply {
            styleClass.add(Styles.TITLE_4)
            padding = Insets(10.0, 12.0, 0.0, 12.0)
        },
        content,
    )

    private fun fieldLabel(text: String) = Label(text).apply {
        styleClass.add(Styles.TEXT_MUTED)
        alignment = Pos.CENTER_LEFT
        minWidth = Region.USE_PREF_SIZE
    }

    private fun valueField() = TextField().apply {
        isEditable = false
        alignment = Pos.CENTER_LEFT
        minWidth = 0.0
        prefWidth = 0.0
        maxWidth = Double.MAX_VALUE
        // Look like a label but keep text selection/copy support.
        style = "-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;"
    }

}
