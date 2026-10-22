package me.vripper.gui.components

import atlantafx.base.theme.Styles
import javafx.beans.property.DoubleProperty
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import tornadofx.addClass
import tornadofx.onChange

fun gridTileSizeSelector(property: DoubleProperty, min: Double, max: Double): Region {
    val current = property.get().coerceIn(min, max)
    property.set(current)
    val valueLabel = Label("${current.toInt()} px").apply {
        addClass(Styles.TEXT_MUTED)
    }
    val slider = Slider(min, max, current).apply {
        prefWidth = 220.0
        blockIncrement = 10.0
        isShowTickMarks = true
        majorTickUnit = 40.0
        minorTickCount = 3
    }
    slider.valueProperty().onChange { value ->
        val size = value.toDouble().coerceIn(min, max)
        valueLabel.text = "${size.toInt()} px"
        if (!slider.isValueChanging) {
            property.set(size)
        }
    }
    slider.valueChangingProperty().onChange { changing ->
        if (!changing) {
            property.set(slider.value.coerceIn(min, max))
        }
    }
    return HBox(8.0, slider, valueLabel).apply {
        alignment = Pos.CENTER_LEFT
    }
}
