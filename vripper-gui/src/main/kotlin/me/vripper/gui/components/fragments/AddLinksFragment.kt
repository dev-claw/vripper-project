package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.scene.control.TextArea
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.*
import me.vripper.gui.controller.PostController
import tornadofx.*

class AddLinksFragment : Fragment("Add thread links") {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val textAreaProperty = SimpleStringProperty()
    private val postController: PostController by inject()
    lateinit var input: TextArea

    override val root = vbox(alignment = Pos.CENTER_RIGHT) {
        padding = insets(all = 5)
        spacing = 5.0
        input = textarea {
            VBox.setVgrow(this, Priority.ALWAYS)
            bind(textAreaProperty)
        }
        button("Scan") {
            isDefaultButton
            addClass(Styles.ACCENT)
            disableWhen(textAreaProperty.isEmpty)
            action {
                coroutineScope.launch {
                    postController.scan(textAreaProperty.value)
                }
                close()
            }
        }
    }

    override fun onUndock() {
        coroutineScope.cancel()
    }
}