package me.vripper.gui.fx

import javafx.beans.property.Property
import javafx.beans.value.ObservableValue
import javafx.scene.control.CheckBox
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ComboBoxBase
import javafx.scene.control.TextInputControl

private fun <T> Property<T>.internalBind(property: ObservableValue<T>, readonly: Boolean) {
    if (readonly || property !is Property<*>) {
        bind(property)
    } else {
        @Suppress("UNCHECKED_CAST")
        bindBidirectional(property as Property<T>)
    }
}

fun <T> ComboBoxBase<T>.bind(property: ObservableValue<T>, readonly: Boolean = false) =
    valueProperty().internalBind(property, readonly)

fun CheckBox.bind(property: ObservableValue<Boolean>, readonly: Boolean = false) =
    selectedProperty().internalBind(property, readonly)

fun CheckMenuItem.bind(property: ObservableValue<Boolean>, readonly: Boolean = false) =
    selectedProperty().internalBind(property, readonly)

fun TextInputControl.bind(property: ObservableValue<String>, readonly: Boolean = false) =
    textProperty().internalBind(property, readonly)
