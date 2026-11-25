package me.vripper.gui.fx

import javafx.beans.binding.BooleanBinding
import javafx.beans.binding.Bindings
import javafx.beans.value.ObservableValue
import javafx.css.Styleable
import javafx.scene.Node
import javafx.scene.control.ButtonBase
import javafx.scene.control.MenuItem
import javafx.scene.control.TextField
import javafx.scene.control.TextInputControl
import javafx.scene.control.ToolBar
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

fun <T : Styleable> T.addClass(vararg styleClasses: String): T {
    styleClass.addAll(styleClasses)
    return this
}

fun Node.tooltip(text: String): Tooltip {
    val tooltip = Tooltip(text)
    Tooltip.install(this, tooltip)
    return tooltip
}

fun Node.visibleWhen(observable: ObservableValue<Boolean>) = apply {
    visibleProperty().bind(observable)
}

fun Node.visibleWhen(op: () -> ObservableValue<Boolean>) = visibleWhen(op())

fun Node.managedWhen(observable: ObservableValue<Boolean>) = apply {
    managedProperty().bind(observable)
}

fun Node.managedWhen(op: () -> ObservableValue<Boolean>) = managedWhen(op())

fun Node.disableWhen(observable: ObservableValue<Boolean>) = apply {
    disableProperty().bind(observable)
}

fun Node.disableWhen(op: () -> ObservableValue<Boolean>) = disableWhen(op())

fun Node.enableWhen(observable: ObservableValue<Boolean>) = apply {
    disableProperty().bind(observable.negate())
}

fun Node.enableWhen(op: () -> ObservableValue<Boolean>) = enableWhen(op())

fun TextInputControl.editableWhen(observable: ObservableValue<Boolean>) = apply {
    editableProperty().bind(observable)
}

fun TextInputControl.editableWhen(op: () -> ObservableValue<Boolean>) = editableWhen(op())

fun MenuItem.disableWhen(observable: ObservableValue<Boolean>) = apply {
    disableProperty().bind(observable)
}

fun MenuItem.visibleWhen(observable: ObservableValue<Boolean>) = apply {
    visibleProperty().bind(observable)
}

fun MenuItem.visibleWhen(op: () -> ObservableValue<Boolean>) = visibleWhen(op())

fun MenuItem.disableWhen(op: () -> ObservableValue<Boolean>) = disableWhen(op())

private fun ObservableValue<Boolean>.negate(): BooleanBinding =
    Bindings.createBooleanBinding({ value != true }, this)

fun ButtonBase.action(op: () -> Unit) = setOnAction { op() }

fun MenuItem.action(op: () -> Unit) = setOnAction { op() }

fun TextField.action(op: () -> Unit) = setOnAction { op() }

var Node.vgrow: Priority?
    get() = VBox.getVgrow(this)
    set(value) = VBox.setVgrow(this, value)

var Node.hgrow: Priority?
    get() = HBox.getHgrow(this)
    set(value) = HBox.setHgrow(this, value)

fun HBox.spacer(prio: Priority = Priority.ALWAYS, op: Pane.() -> Unit = {}): Pane {
    val pane = Pane()
    HBox.setHgrow(pane, prio)
    addChildIfPossible(pane)
    op(pane)
    return pane
}

fun VBox.spacer(prio: Priority = Priority.ALWAYS, op: Pane.() -> Unit = {}): Pane {
    val pane = Pane()
    VBox.setVgrow(pane, prio)
    addChildIfPossible(pane)
    op(pane)
    return pane
}

fun ToolBar.spacer(prio: Priority = Priority.ALWAYS, op: Region.() -> Unit = {}): Region {
    val region = Region()
    HBox.setHgrow(region, prio)
    items.add(region)
    op(region)
    return region
}
