package me.vripper.gui.model

import javafx.beans.property.SimpleDoubleProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class LogsColumnsWidthModel(
    time: Double,
    threadName: Double,
    loggerName: Double,
    levelString: Double,
    message: Double,
) {
    val timeProperty = SimpleDoubleProperty(time)
    var time: Double by timeProperty

    val threadNameProperty = SimpleDoubleProperty(threadName)
    var threadName: Double by threadNameProperty

    val loggerNameProperty = SimpleDoubleProperty(loggerName)
    var loggerName: Double by loggerNameProperty

    val levelStringProperty = SimpleDoubleProperty(levelString)
    var levelString: Double by levelStringProperty

    val messageProperty = SimpleDoubleProperty(message)
    var message: Double by messageProperty
}