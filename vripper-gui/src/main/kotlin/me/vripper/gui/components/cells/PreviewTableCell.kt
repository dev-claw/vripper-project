package me.vripper.gui.components.cells

import javafx.geometry.Pos
import javafx.scene.control.TableCell
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon

class PreviewTableCell<T, V> : TableCell<T, V>() {
    init {
        alignment = Pos.CENTER
    }

    override fun updateItem(item: V, empty: Boolean) {
        super.updateItem(item, empty)
        graphic = if (!empty) {
            FontIcon.of(Feather.IMAGE)
        } else {
            null
        }
    }
}