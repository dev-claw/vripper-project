package me.vripper.gui.model.settings

import javafx.beans.property.Property
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import me.vripper.model.HostName
import me.vripper.model.HostSettingKey
import me.vripper.model.SettingType

class HostSettingsModel(
    val host: HostName,
    val settingKey: HostSettingKey,
    initialValue: String,
) {
    val valueProperty: Property<*> = when (settingKey.type) {
        SettingType.STRING -> SimpleStringProperty(initialValue)
        SettingType.BOOLEAN -> SimpleBooleanProperty(initialValue.toBoolean())
        SettingType.INT -> SimpleIntegerProperty(initialValue.toIntOrNull() ?: 0)
    }

    fun getValue(): String = when (settingKey.type) {
        SettingType.STRING -> (valueProperty as SimpleStringProperty).value
        SettingType.BOOLEAN -> (valueProperty as SimpleBooleanProperty).value.toString()
        SettingType.INT -> (valueProperty as SimpleIntegerProperty).value.toString()
    }
}
