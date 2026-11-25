package me.vripper.gui.fx

import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Window
import java.io.File

private fun Alert.configure(
    header: String,
    content: String,
    owner: Window?,
    title: String?,
) {
    this.title = title
    headerText = header
    contentText = content
    if (owner != null) initOwner(owner)
}

fun confirm(
    header: String,
    content: String = "",
    confirmButton: ButtonType = ButtonType.OK,
    cancelButton: ButtonType = ButtonType.CANCEL,
    owner: Window? = null,
    title: String? = null,
    actionFn: () -> Unit,
) {
    val alert = Alert(Alert.AlertType.CONFIRMATION)
    alert.configure(header, content, owner, title)
    alert.buttonTypes.setAll(confirmButton, cancelButton)
    alert.showAndWait().ifPresent { button ->
        if (button == confirmButton) actionFn()
    }
}

fun information(
    header: String,
    content: String = "",
    vararg buttons: ButtonType,
    owner: Window? = null,
    title: String? = null,
    actionFn: Alert.(ButtonType) -> Unit = {},
) {
    val alert = Alert(Alert.AlertType.INFORMATION)
    alert.configure(header, content, owner, title)
    if (buttons.isEmpty()) {
        alert.buttonTypes.setAll(ButtonType.OK)
    } else {
        alert.buttonTypes.setAll(*buttons)
    }
    alert.showAndWait().ifPresent { button -> actionFn(alert, button) }
}

fun alert(
    type: Alert.AlertType,
    title: String? = null,
    content: String? = null,
    op: Alert.(ButtonType?) -> Unit = {},
) {
    val alert = Alert(type)
    alert.title = title
    alert.contentText = content
    alert.showAndWait().ifPresent { button -> op(alert, button) }
}

fun chooseDirectory(
    title: String? = null,
    initialDirectory: File? = null,
    owner: Window? = null,
    op: DirectoryChooser.() -> Unit = {},
): File? {
    val chooser = DirectoryChooser()
    if (title != null) chooser.title = title
    if (initialDirectory != null) chooser.initialDirectory = initialDirectory
    op(chooser)
    return chooser.showDialog(owner)
}

fun chooseFile(
    title: String? = null,
    filters: Array<FileChooser.ExtensionFilter> = arrayOf(),
    owner: Window? = null,
    op: FileChooser.() -> Unit = {},
): List<File> {
    val chooser = FileChooser()
    if (title != null) chooser.title = title
    if (filters.isNotEmpty()) chooser.extensionFilters.addAll(filters)
    op(chooser)
    return chooser.showOpenMultipleDialog(owner) ?: emptyList()
}
