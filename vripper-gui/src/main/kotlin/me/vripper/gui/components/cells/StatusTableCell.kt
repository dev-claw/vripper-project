package me.vripper.gui.components.cells

import atlantafx.base.theme.Styles
import javafx.geometry.Pos
import javafx.scene.control.TableCell
import javafx.scene.control.Tooltip
import me.vripper.entities.Status
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon

class StatusTableCell<T> : TableCell<T, String>() {

    init {
        alignment = Pos.CENTER
    }

    override fun updateItem(item: String?, empty: Boolean) {
        super.updateItem(item, empty)
        if (empty || item == null) {
            graphic = null
            tooltip = null
            return
        }
        val status = Status.valueOf(item)
        tooltip = Tooltip(status.stringValue)
        graphic = when (status) {
            Status.ON_HOLD -> FontIcon.of(Feather.INFO).apply { styleClass.add(Styles.WARNING) }
            Status.AUTOMATION -> FontIcon.of(Feather.CPU).apply { styleClass.add(Styles.ACCENT) }
            Status.PENDING -> FontIcon.of(Feather.CLOCK)
            Status.DOWNLOADING -> FontIcon.of(Feather.DOWNLOAD).apply { styleClass.add(Styles.ACCENT) }
            Status.FINISHED -> FontIcon.of(Feather.CHECK).apply { styleClass.add(Styles.SUCCESS) }
            Status.ERROR -> FontIcon.of(Feather.ALERT_TRIANGLE).apply { styleClass.add(Styles.DANGER) }
            Status.STOPPED -> FontIcon.of(Feather.PAUSE)
        }
    }
}