package me.vripper.gui.fx

import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

/**
 * Two-column form grid: labels occupy the first column and values the second.
 *
 * The label column is sized to its widest label, so every value in the same
 * [FormGrid] starts at the same horizontal position. This is the JavaFX
 * equivalent of a CSS grid form with `grid-template-columns: max-content 1fr`.
 */
open class FormGrid : GridPane() {
    private var nextRow = 0
    private val labels = mutableListOf<Node>()
    private val labelWidth = SimpleDoubleProperty(0.0)

    internal val labelColumnWidth: ReadOnlyDoubleProperty
        get() = labelWidth

    init {
        hgap = 12.0
        vgap = 8.0
        columnConstraints.addAll(
            ColumnConstraints().apply { minWidth = Region.USE_PREF_SIZE },
            ColumnConstraints().apply {
                minWidth = 0.0
                hgrow = Priority.ALWAYS
            },
        )
    }

    fun addRow(label: Node?, value: Node) {
        if (label != null) {
            labels.add(label)
            add(label, 0, nextRow)
        }
        add(value, 1, nextRow)
        GridPane.setHgrow(value, Priority.ALWAYS)
        GridPane.setFillWidth(value, true)
        nextRow++
    }

    fun addSpanningRow(node: Node) {
        add(node, 0, nextRow, 2, 1)
        GridPane.setHgrow(node, Priority.ALWAYS)
        GridPane.setFillWidth(node, true)
        nextRow++
    }

    /**
     * Makes this grid's label column as wide as [parent]'s label column.
     * Nested fieldsets then keep their values aligned with the outer fields.
     */
    internal fun alignLabelColumnWith(parent: FormGrid) {
        columnConstraints[0].minWidthProperty().bind(parent.labelColumnWidth)
        columnConstraints[0].prefWidthProperty().bind(parent.labelColumnWidth)
    }

    override fun layoutChildren() {
        super.layoutChildren()
        val width = labels.maxOfOrNull { it.prefWidth(-1.0) } ?: 0.0
        if (labelWidth.get() != width) {
            labelWidth.set(width)
        }
    }
}

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

class Fieldset(title: String? = null) : FormGrid() {
    init {
        if (title != null) {
            addSpanningRow(Label(title).apply { styleClass.add("fieldset-title") })
        }
    }

    internal fun addContent(node: Node) {
        when (node) {
            is Field -> addField(node)
            is Fieldset -> {
                node.alignLabelColumnWith(this)
                addSpanningRow(node)
            }

            else -> addSpanningRow(node)
        }
    }

    private fun addField(field: Field) {
        field.children.removeAll(field.labelContainer, field.inputContainer)
        field.labelContainer.visibleProperty().bind(field.visibleProperty())
        field.labelContainer.managedProperty().bind(field.managedProperty())
        field.inputContainer.visibleProperty().bind(field.visibleProperty())
        field.inputContainer.managedProperty().bind(field.managedProperty())
        addRow(field.labelContainer, field.inputContainer)
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
