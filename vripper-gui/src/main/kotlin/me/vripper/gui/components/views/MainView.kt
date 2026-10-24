package me.vripper.gui.components.views

import javafx.scene.control.TabPane
import javafx.scene.layout.Priority
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon
import tornadofx.View
import tornadofx.tab
import tornadofx.tabpane
import tornadofx.vgrow

class MainView : View("Center view") {

    private val threadTableView: ThreadTableView by inject()
    private val logTableView: LogTableView by inject()
    private val taskTableView: TaskTableView by inject()
    private val postsView: PostsView by inject()

    override val root = tabpane {
        tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
        vgrow = Priority.ALWAYS
        tab(postsView) {
            graphic = FontIcon(Feather.DOWNLOAD)
            id = "download-tab"
        }
        tab(threadTableView) {
            graphic = FontIcon(Feather.FILE_TEXT)
            id = "thread-tab"
        }
        tab(taskTableView) {
            graphic = FontIcon(Feather.ACTIVITY)
            id = "task-tab"
        }
        tab(logTableView) {
            graphic = FontIcon(Feather.DATABASE)
            id = "log-tab"
        }
    }
}