package me.vripper.gui.model

import javafx.beans.property.SimpleBooleanProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class LogsColumnsModel(
    time: Boolean,
    threadName: Boolean,
    loggerName: Boolean,
    levelString: Boolean,
    message: Boolean,
) {
    val timeProperty = SimpleBooleanProperty(time)
    var time: Boolean by timeProperty

    val threadNameProperty = SimpleBooleanProperty(threadName)
    var threadName: Boolean by threadNameProperty

    val loggerNameProperty = SimpleBooleanProperty(loggerName)
    var loggerName: Boolean by loggerNameProperty

    val levelStringProperty = SimpleBooleanProperty(levelString)
    var levelString: Boolean by levelStringProperty

    val messageProperty = SimpleBooleanProperty(message)
    var message: Boolean by messageProperty
}