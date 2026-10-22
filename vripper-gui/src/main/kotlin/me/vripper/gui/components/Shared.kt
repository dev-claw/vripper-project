package me.vripper.gui.components

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty

object Shared {
    val postsSearchInput = SimpleStringProperty("")
    val threadsSearchInput = SimpleStringProperty("")
    val tasksSearchInput = SimpleStringProperty("")
    val logsSearchInput = SimpleStringProperty("")

    val threadQuickFilter = SimpleObjectProperty(ThreadQuickFilter.ALL)
    val taskQuickFilter = SimpleObjectProperty(TaskQuickFilter.ALL)
    val logQuickFilter = SimpleObjectProperty(LogQuickFilter.ALL)
}
