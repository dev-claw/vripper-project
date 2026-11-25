package me.vripper.gui.components.fragments

import javafx.geometry.Insets

import atlantafx.base.theme.Styles
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import kotlinx.coroutines.*
import me.vripper.gui.controller.PostController
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import me.vripper.gui.fx.*

class RenameFragment : Fragment("Rename download post") {

    val id: Long by param()
    val name: String by param()
    val altTitles: List<String> by param()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val textInputProperty = SimpleStringProperty()
    private val postController: PostController by inject()
    private lateinit var comboBox: ComboBox<String>

    override fun onDock() {
        textInputProperty.value = name
    }

    override val root = vbox(alignment = Pos.CENTER_RIGHT) {
        padding = Insets(5.0, 5.0, 5.0, 5.0)
        spacing = 5.0
        form {
            fieldset {
                field("Name") {
                    comboBox = combobox(textInputProperty, altTitles.ifEmpty { listOf(name) }) {
                        maxWidth = Double.MAX_VALUE
                        maxHeight = Double.MAX_VALUE
                        isEditable = true
                    }
                }
            }
        }
        button("Rename") {
            graphic = FontIcon.of(Feather.EDIT)
            addClass(Styles.ACCENT)
            disableWhen(comboBox.editor.textProperty().isEmpty)
            action {
                coroutineScope.launch {
                    postController.rename(this@RenameFragment.id, comboBox.editor.text.trim())
                }
                close()
            }
        }
    }

    override fun onUndock() {
        coroutineScope.cancel()
    }
}