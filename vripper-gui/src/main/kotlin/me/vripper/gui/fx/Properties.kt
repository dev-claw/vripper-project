package me.vripper.gui.fx

import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanExpression
import javafx.beans.property.Property
import javafx.beans.value.ObservableBooleanValue
import javafx.beans.value.ObservableDoubleValue
import javafx.beans.value.ObservableFloatValue
import javafx.beans.value.ObservableIntegerValue
import javafx.beans.value.ObservableLongValue
import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import kotlin.reflect.KProperty

operator fun <T> ObservableValue<T>.getValue(thisRef: Any, property: KProperty<*>) = value

operator fun ObservableIntegerValue.getValue(thisRef: Any, property: KProperty<*>): Int = intValue()

operator fun ObservableLongValue.getValue(thisRef: Any, property: KProperty<*>): Long = longValue()

operator fun ObservableFloatValue.getValue(thisRef: Any, property: KProperty<*>): Float = floatValue()

operator fun ObservableDoubleValue.getValue(thisRef: Any, property: KProperty<*>): Double = doubleValue()

fun ObservableValue<String>.isBlank(): BooleanExpression =
    Bindings.createBooleanBinding({ value?.isBlank() ?: true }, this)

fun ObservableValue<String>.isNotBlank(): BooleanExpression =
    Bindings.createBooleanBinding({ value?.isNotBlank() ?: false }, this)

operator fun <T> Property<T>.setValue(thisRef: Any, property: KProperty<*>, value: T) {
    this.value = value
}

fun <T> ObservableValue<T>.onChange(op: (T?) -> Unit) = apply {
    addListener { _, _, newValue -> op(newValue) }
}

fun ObservableBooleanValue.onChange(op: (Boolean) -> Unit) = apply {
    addListener { _, _, newValue -> op(newValue ?: false) }
}

fun ObservableIntegerValue.onChange(op: (Int) -> Unit) = apply {
    addListener { _, _, newValue -> op((newValue ?: 0).toInt()) }
}

fun ObservableLongValue.onChange(op: (Long) -> Unit) = apply {
    addListener { _, _, newValue -> op((newValue ?: 0L).toLong()) }
}

fun ObservableFloatValue.onChange(op: (Float) -> Unit) = apply {
    addListener { _, _, newValue -> op((newValue ?: 0f).toFloat()) }
}

fun ObservableDoubleValue.onChange(op: (Double) -> Unit) = apply {
    addListener { _, _, newValue -> op((newValue ?: 0.0).toDouble()) }
}

fun <T> ObservableList<T>.onChange(op: (ListChangeListener.Change<out T>) -> Unit) = apply {
    addListener(ListChangeListener<T> { change -> op(change) })
}
