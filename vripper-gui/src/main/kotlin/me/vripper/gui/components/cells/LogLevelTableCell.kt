package me.vripper.gui.components.cells

import atlantafx.base.theme.Styles
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.TableCell
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.javafx.FontIcon

/**
 * Level column for the log table. Each level gets a matching icon and colour so
 * warnings and errors stand out while scanning the list.
 */
class LogLevelTableCell<T> : TableCell<T, String>() {

    private val icon = FontIcon()
    private val label = Label().apply {
        graphic = icon
        graphicTextGap = 6.0
        alignment = Pos.CENTER_LEFT
    }
    private val levelStyles = listOf(
        Styles.ACCENT,
        Styles.SUCCESS,
        Styles.WARNING,
        Styles.DANGER,
        Styles.TEXT_MUTED,
    )

    override fun updateItem(item: String?, empty: Boolean) {
        super.updateItem(item, empty)
        if (empty || item == null) {
            graphic = null
            text = null
            return
        }
        val level = item.uppercase()
        val (iconCode, style) = when (level) {
            "ERROR", "FATAL" -> Feather.ALERT_OCTAGON to Styles.DANGER
            "WARN", "WARNING" -> Feather.ALERT_TRIANGLE to Styles.WARNING
            "INFO" -> Feather.INFO to Styles.ACCENT
            "DEBUG", "TRACE" -> Feather.CODE to Styles.TEXT_MUTED
            else -> Feather.MESSAGE_SQUARE to Styles.TEXT_MUTED
        }
        icon.iconCode = iconCode
        icon.styleClass.removeAll(levelStyles)
        icon.styleClass.add(style)
        label.text = item
        label.styleClass.removeAll(levelStyles)
        label.styleClass.add(style)
        graphic = label
        text = null
    }
}
