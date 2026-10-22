package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.beans.binding.Bindings
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import me.vripper.gui.model.PostModel
import me.vripper.gui.utils.displayStatusLabel

/**
 * Read-only panel showing the details of the post currently open in the
 * workspace (title, status, path, host, ...). Binds directly to a [PostModel]
 * so it follows live updates; pass `null` to clear it.
 *
 * Values use read-only text fields so their content can be selected and
 * copied, unlike plain labels.
 */
class PostDetailsPane {

    private val link = valueField()
    private val postedBy = valueField()
    private val title = valueField()
    private val altTitles = valueField()
    private val status = valueField()
    private val path = valueField()
    private val total = valueField()
    private val hosts = valueField()
    private val addedOn = valueField()

    val root: Region = ScrollPane(
        GridPane().apply {
            padding = Insets(12.0)
            hgap = 14.0
            vgap = 8.0
            addRow(0, fieldLabel("Post Link:"), link)
            addRow(1, fieldLabel("Posted By:"), postedBy)
            addRow(2, fieldLabel("Title:"), title)
            addRow(3, fieldLabel("More titles:"), altTitles)
            addRow(4, fieldLabel("Status:"), status)
            addRow(5, fieldLabel("Path:"), path)
            addRow(6, fieldLabel("Total:"), total)
            addRow(7, fieldLabel("Hosts:"), hosts)
            addRow(8, fieldLabel("Added On:"), addedOn)
            columnConstraints.addAll(
                ColumnConstraints().apply { minWidth = Region.USE_PREF_SIZE },
                ColumnConstraints().apply {
                    minWidth = 0.0
                    hgrow = Priority.ALWAYS
                },
            )
        }
    ).apply {
        id = "post_details_panel"
        isFitToWidth = true
        hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        style = "-fx-background-color: transparent;"
    }

    private var model: PostModel? = null

    fun setPostModel(model: PostModel?) {
        if (this.model === model) return
        clearBindings()
        this.model = model
        if (model == null) {
            listOf(link, postedBy, title, altTitles, status, path, total, hosts, addedOn).forEach {
                it.text = ""
            }
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
    }

    private fun clearBindings() {
        listOf(link, postedBy, title, altTitles, status, path, total, hosts, addedOn).forEach {
            it.textProperty().unbind()
        }
    }

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

    private fun GridPane.addRow(row: Int, name: Label, value: TextField) {
        add(name, 0, row)
        add(value, 1, row)
        GridPane.setHgrow(value, Priority.ALWAYS)
        GridPane.setFillWidth(value, true)
    }
}
