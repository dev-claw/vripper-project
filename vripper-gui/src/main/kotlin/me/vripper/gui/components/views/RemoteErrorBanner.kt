package me.vripper.gui.components.views

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.paint.Color
import kotlinx.coroutines.*
import me.vripper.gui.event.GuiEventBus
import tornadofx.View
import tornadofx.action
import tornadofx.button
import tornadofx.hbox
import tornadofx.insets
import tornadofx.label
import tornadofx.runLater

class RemoteErrorBanner : View() {

    private val message = SimpleStringProperty("")
    private val visible = SimpleBooleanProperty(false)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var hideJob: Job? = null

    override val root = hbox {
        id = "remote_error_banner"
        spacing = 8.0
        padding = insets(6.0, 10.0, 6.0, 10.0)
        style = "-fx-background-color: #b00020; -fx-background-radius: 0;"
        visibleProperty().bind(visible)
        managedProperty().bind(visible)
        label(message) {
            textFill = Color.WHITE
            maxWidth = Double.MAX_VALUE
        }
        button("Dismiss") {
            action { hide() }
        }
    }

    init {
        coroutineScope.launch {
            GuiEventBus.events.collect { event ->
                if (event is GuiEventBus.RemoteError) {
                    runLater { show(event) }
                }
            }
        }
    }

    override fun onUndock() {
        coroutineScope.cancel()
        super.onUndock()
    }

    private fun show(event: GuiEventBus.RemoteError) {
        val text = "${event.source}: ${event.message}"
        if (visible.get() && message.get() == text) {
            scheduleHide()
            return
        }
        message.set(text)
        visible.set(true)
        scheduleHide()
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        hideJob = coroutineScope.launch {
            delay(15_000)
            runLater { hide() }
        }
    }

    private fun hide() {
        visible.set(false)
        hideJob?.cancel()
    }
}
