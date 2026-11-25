package me.vripper.gui.model

import javafx.beans.property.SimpleStringProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class LogModel(
    val sequence: Long,
    timestamp: String,
    threadName: String,
    loggerName: String,
    levelString: String,
    formattedMessage: String,
    throwable: String,
    val threadId: Long? = null,
    val postId: Long? = null,
) {
    val timestampProperty = SimpleStringProperty(timestamp)
    var timestamp: String by timestampProperty

    val threadNameProperty = SimpleStringProperty(threadName)
    var threadName: String by threadNameProperty

    val loggerNameProperty = SimpleStringProperty(loggerName)
    var loggerName: String by loggerNameProperty

    val levelStringProperty = SimpleStringProperty(levelString)
    var levelString: String by levelStringProperty

    val formattedMessageProperty = SimpleStringProperty(formattedMessage)
    var formattedMessage: String by formattedMessageProperty

    val throwableProperty = SimpleStringProperty(throwable)
    var throwable: String by throwableProperty
}
