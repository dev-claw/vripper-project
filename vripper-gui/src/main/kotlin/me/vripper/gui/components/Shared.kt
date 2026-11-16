package me.vripper.gui.components

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty

object Shared {
    val postsSearchInput = SimpleStringProperty("")
    val threadsSearchInput = SimpleStringProperty("")
    val subscriptionsSearchInput = SimpleStringProperty("")
    val tasksSearchInput = SimpleStringProperty("")
    val logsSearchInput = SimpleStringProperty("")
    val logsThreadIdInput = SimpleStringProperty("")
    val logsPostIdInput = SimpleStringProperty("")
    val taskQuickFilter = SimpleObjectProperty(TaskQuickFilter.ALL)
    val taskGroupMode = SimpleObjectProperty(TaskGroupMode.NONE)
    val logQuickFilter = SimpleObjectProperty(LogQuickFilter.ALL)
}
