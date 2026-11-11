package me.vripper.gui.components.views

import atlantafx.base.controls.Notification
import atlantafx.base.theme.Styles
import atlantafx.base.util.Animations
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.vripper.event.ThreadNewPostsEvent
import me.vripper.gui.components.fragments.ThreadSelectionTableFragment
import me.vripper.gui.controller.ThreadController
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import tornadofx.*

class NotificationHost : View() {

    private val threadController: ThreadController by inject()
    private val mainView: MainView by inject()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val container = VBox().apply {
        spacing = 10.0
        alignment = Pos.TOP_RIGHT
        isFillWidth = false
        isPickOnBounds = false
        maxWidth = Region.USE_PREF_SIZE
        maxHeight = Region.USE_PREF_SIZE
    }
    private val notifications = mutableMapOf<Long, Notification>()

    override val root = StackPane().apply {
        isPickOnBounds = false
        alignment = Pos.TOP_RIGHT
        children.add(container)
        StackPane.setMargin(container, Insets(10.0, 10.0, 0.0, 0.0))
    }

    init {
        coroutineScope.launch {
            threadController.newThreadPosts.collect { event ->
                runLater { show(event) }
            }
        }
    }

    private fun show(event: ThreadNewPostsEvent) {
        notifications[event.threadId]?.let { existing ->
            existing.message = message(event)
            return
        }

        val notification = Notification(message(event), FontIcon(Material2OutlinedMZ.NOTIFICATIONS)).apply {
            styleClass.addAll(Styles.ACCENT, Styles.ELEVATED_1)
            prefHeight = Region.USE_PREF_SIZE
            maxHeight = Region.USE_PREF_SIZE
            onClose = EventHandler {
                val out = Animations.slideOutUp(this, Duration.millis(250.0))
                out.setOnFinished {
                    container.children.remove(this)
                    notifications.remove(event.threadId)
                }
                out.playFromStart()
            }
            setPrimaryActions(
                Button("Open").apply {
                    setOnAction { openThread(event) }
                }
            )
        }

        notifications[event.threadId] = notification
        container.children.add(notification)
        Animations.slideInDown(notification, Duration.millis(250.0)).playFromStart()
    }

    private fun openThread(event: ThreadNewPostsEvent) {
        val tabId = if (event.subscription) "subscription-tab" else "thread-tab"
        mainView.root.tabs.firstOrNull { it.id == tabId }?.let {
            mainView.root.selectionModel.select(it)
        }
        find<ThreadSelectionTableFragment>(
            mapOf(ThreadSelectionTableFragment::threadId to event.threadId)
        ).openModal()
    }

    private fun message(event: ThreadNewPostsEvent): String {
        val posts = if (event.newPosts == 1) "1 new post" else "${event.newPosts} new posts"
        return "$posts in ${event.title}"
    }
}
