package me.vripper.gui.fx

import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

class Field(label: String? = null) : HBox() {
    val labelContainer = HBox()
    val inputContainer = HBox()

    init {
        alignment = Pos.CENTER_LEFT
        spacing = 8.0
        labelContainer.alignment = Pos.CENTER_LEFT
        inputContainer.alignment = Pos.CENTER_LEFT
        HBox.setHgrow(inputContainer, Priority.ALWAYS)
        children.addAll(labelContainer, inputContainer)
        if (label != null) {
            labelContainer.children.add(Label(label))
        }
    }
}

class Fieldset(title: String? = null) : VBox() {
    init {
        spacing = 8.0
        if (title != null) {
            children.add(Label(title).apply { styleClass.add("fieldset-title") })
        }
    }
}

class Form : VBox() {
    init {
        spacing = 12.0
    }
}

fun EventTarget.field(label: String? = null, op: Field.() -> Unit = {}): Field {
    val field = Field(label)
    addChildIfPossible(field)
    op(field)
    return field
}

fun EventTarget.fieldset(title: String? = null, op: Fieldset.() -> Unit = {}): Fieldset {
    val fieldset = Fieldset(title)
    addChildIfPossible(fieldset)
    op(fieldset)
    return fieldset
}

fun EventTarget.form(op: Form.() -> Unit = {}): Form {
    val form = Form()
    addChildIfPossible(form)
    op(form)
    return form
}
