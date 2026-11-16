package me.vripper.gui.components.fragments

import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import me.vripper.gui.model.LogModel
import tornadofx.*

class LogMessageFragment : Fragment("Log message") {

    val logModel: LogModel by param()
    private val textAreaProperty = SimpleStringProperty(buildString {
        append("Time: ").append(logModel.timestamp).append('\n')
        append("Level: ").append(logModel.levelString).append('\n')
        append("Logger: ").append(logModel.loggerName).append('\n')
        append("Thread: ").append(logModel.threadName)
        logModel.threadId?.let { append(" (threadId=").append(it).append(')') }
        logModel.postId?.let { append(" (postId=").append(it).append(')') }
        append('\n')
        append("Message: ").append(logModel.formattedMessage)
        if (logModel.throwable.isNotBlank()) {
            append("\n\n").append(logModel.throwable.trimEnd())
        }
    })

    override val root = vbox(alignment = Pos.CENTER_RIGHT) {
        padding = insets(all = 5)
        spacing = 5.0
        textarea {
            isEditable = false
            isWrapText = true
            style = "-fx-font-family: monospace;"
            VBox.setVgrow(this, Priority.ALWAYS)
            bind(textAreaProperty)
        }
    }
}