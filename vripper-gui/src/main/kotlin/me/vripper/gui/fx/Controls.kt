package me.vripper.gui.fx

import javafx.beans.property.Property
import javafx.beans.value.ObservableValue
import javafx.event.EventTarget
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.control.PasswordField
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.RadioButton
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.ToggleGroup
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.text.Text

fun EventTarget.label(
    text: String = "",
    graphic: Node? = null,
    op: Label.() -> Unit = {},
): Label {
    val label = Label(text)
    if (graphic != null) label.graphic = graphic
    return label.attachTo(this, op)
}

fun EventTarget.label(
    observable: ObservableValue<String>,
    graphic: Node? = null,
    op: Label.() -> Unit = {},
): Label {
    val label = Label()
    label.textProperty().bind(observable)
    if (graphic != null) label.graphic = graphic
    return label.attachTo(this, op)
}

fun EventTarget.text(value: String? = null, op: Text.() -> Unit = {}): Text =
    Text(value).attachTo(this, op)

fun EventTarget.text(observable: ObservableValue<String>, op: Text.() -> Unit = {}): Text {
    val text = Text()
    text.textProperty().bind(observable)
    return text.attachTo(this, op)
}

fun EventTarget.textfield(value: String? = null, op: TextField.() -> Unit = {}): TextField {
    val textField = TextField()
    if (value != null) textField.text = value
    return textField.attachTo(this, op)
}

fun EventTarget.textfield(
    property: ObservableValue<String>,
    op: TextField.() -> Unit = {},
): TextField {
    val textField = TextField()
    textField.bind(property)
    return textField.attachTo(this, op)
}

fun EventTarget.passwordfield(
    value: String? = null,
    op: PasswordField.() -> Unit = {},
): PasswordField {
    val passwordField = PasswordField()
    if (value != null) passwordField.text = value
    return passwordField.attachTo(this, op)
}

fun EventTarget.passwordfield(
    property: ObservableValue<String>,
    op: PasswordField.() -> Unit = {},
): PasswordField {
    val passwordField = PasswordField()
    passwordField.bind(property)
    return passwordField.attachTo(this, op)
}

fun EventTarget.textarea(value: String? = null, op: TextArea.() -> Unit = {}): TextArea {
    val textArea = TextArea()
    if (value != null) textArea.text = value
    return textArea.attachTo(this, op)
}

fun EventTarget.textarea(
    property: ObservableValue<String>,
    op: TextArea.() -> Unit = {},
): TextArea {
    val textArea = TextArea()
    textArea.bind(property)
    return textArea.attachTo(this, op)
}

fun EventTarget.button(
    text: String = "",
    graphic: Node? = null,
    op: Button.() -> Unit = {},
): Button {
    val button = Button(text)
    if (graphic != null) button.graphic = graphic
    return button.attachTo(this, op)
}

fun EventTarget.button(
    text: ObservableValue<String>,
    graphic: Node? = null,
    op: Button.() -> Unit = {},
): Button {
    val button = Button()
    button.textProperty().bind(text)
    if (graphic != null) button.graphic = graphic
    return button.attachTo(this, op)
}

fun EventTarget.hyperlink(
    text: String = "",
    graphic: Node? = null,
    op: Hyperlink.() -> Unit = {},
): Hyperlink {
    val hyperlink = Hyperlink(text, graphic)
    return hyperlink.attachTo(this, op)
}

fun EventTarget.imageview(
    url: String? = null,
    lazyload: Boolean = true,
    op: ImageView.() -> Unit = {},
): ImageView = (if (url == null) ImageView() else ImageView(Image(url, lazyload))).attachTo(this, op)

fun EventTarget.progressindicator(
    op: ProgressIndicator.() -> Unit = {},
): ProgressIndicator = ProgressIndicator().attachTo(this, op)

fun EventTarget.checkbox(
    text: String? = null,
    property: Property<Boolean>? = null,
    op: CheckBox.() -> Unit = {},
): CheckBox {
    val checkBox = CheckBox(text)
    if (property != null) checkBox.bind(property)
    return checkBox.attachTo(this, op)
}

fun <T> EventTarget.combobox(op: ComboBox<T>.() -> Unit = {}): ComboBox<T> =
    ComboBox<T>().attachTo(this, op)

fun <T> EventTarget.combobox(
    property: ObservableValue<T>,
    values: List<T>,
    op: ComboBox<T>.() -> Unit = {},
): ComboBox<T> {
    val comboBox = ComboBox<T>()
    comboBox.items.addAll(values)
    comboBox.bind(property)
    return comboBox.attachTo(this, op)
}

fun EventTarget.radiobutton(
    text: String? = null,
    group: ToggleGroup? = null,
    value: Any? = null,
    op: RadioButton.() -> Unit = {},
): RadioButton {
    val radioButton = RadioButton()
    radioButton.text = if (value != null && text == null) value.toString() else text ?: ""
    radioButton.properties["me.vripper.fx.toggleValue"] = value ?: text
    if (group != null) radioButton.toggleGroup = group
    return radioButton.attachTo(this, op)
}
