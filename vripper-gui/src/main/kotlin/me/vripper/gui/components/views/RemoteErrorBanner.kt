package me.vripper.gui.components.views

import atlantafx.base.controls.Message
import atlantafx.base.theme.Styles
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.EventHandler
import kotlinx.coroutines.*
import me.vripper.gui.event.GuiEventBus
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2OutlinedAL
import tornadofx.View
import tornadofx.runLater

class RemoteErrorBanner : View() {

    private val visible = SimpleBooleanProperty(false)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var hideJob: Job? = null

    private val message = Message(
        "Error",
        "",
        FontIcon(Material2OutlinedAL.ERROR_OUTLINE)
    ).apply {
        styleClass.add(Styles.DANGER)
        maxWidth = Double.MAX_VALUE
        onClose = EventHandler { hide() }
    }

    override val root = message

    init {
        root.visibleProperty().bind(visible)
        root.managedProperty().bind(visible)
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
        if (visible.get() && message.description == text) {
            scheduleHide()
            return
        }
        message.description = text
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
