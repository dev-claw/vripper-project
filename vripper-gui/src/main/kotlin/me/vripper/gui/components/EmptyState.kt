package me.vripper.gui.components

import atlantafx.base.theme.Styles
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.javafx.FontIcon

/** Centred icon + message shown when a list or table has nothing to display. */
object EmptyState {

    fun create(icon: Ikon, message: String): Region = create(icon, SimpleStringProperty(message))

    fun create(icon: Ikon, message: ObservableValue<String>): Region = HBox(12.0).apply {
        alignment = Pos.CENTER
        isMouseTransparent = true
        style = "-fx-padding: 24;"
        val iconNode = FontIcon.of(icon).apply {
            styleClass.addAll(Styles.TEXT_MUTED, "vripper-icon-empty")
        }
        val label = Label().apply {
            textProperty().bind(message)
            styleClass.add(Styles.TEXT_MUTED)
        }
        children.addAll(iconNode, label)
    }
}
