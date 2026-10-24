package me.vripper.gui.components

import atlantafx.base.theme.Styles
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.HBox
import tornadofx.*

fun <T> quickFilterPills(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
): HBox {
    require(options.isNotEmpty()) { "Quick filter options must not be empty" }

    val group = ToggleGroup()
    val toggles = options.mapIndexed { index, (value, text) ->
        ToggleButton(text).apply {
            userData = value
            toggleGroup = group
            addClass(Styles.SMALL)
            when (index) {
                0 -> addClass(Styles.LEFT_PILL)
                options.lastIndex -> addClass(Styles.RIGHT_PILL)
                else -> addClass(Styles.CENTER_PILL)
            }
        }
    }

    val first = toggles.first()
    group.selectedToggleProperty().onChange { toggle ->
        if (toggle == null) {
            first.isSelected = true
        } else {
            @Suppress("UNCHECKED_CAST")
            onSelect(toggle.userData as T)
        }
    }
    (toggles.firstOrNull { it.userData == selected } ?: first).isSelected = true

    val labelNode = Label(label).apply {
        addClass(Styles.TEXT_MUTED)
        addClass(Styles.TEXT_SMALL)
        alignment = Pos.CENTER
        maxHeight = Double.MAX_VALUE
    }

    val pillBox = HBox(*toggles.toTypedArray()).apply {
        spacing = 0.0
        alignment = Pos.CENTER_LEFT
    }

    return HBox(6.0, labelNode, pillBox).apply {
        alignment = Pos.CENTER_LEFT
    }
}
