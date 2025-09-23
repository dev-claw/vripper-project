package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import kotlinx.coroutines.*
import me.vripper.gui.controller.PostController
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.*

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
        padding = insets(all = 5)
        spacing = 5.0
        form {
            fieldset {
                field("Name") {
                    comboBox = combobox(textInputProperty, altTitles.ifEmpty { listOf(name) }) {
                        useMaxSize = true
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