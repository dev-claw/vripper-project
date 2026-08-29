package me.vripper.gui.components.fragments.settings

import atlantafx.base.util.IntegerStringConverter
import javafx.beans.property.BooleanProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.StringProperty
import javafx.scene.control.Spinner
import me.vripper.gui.model.settings.HostSettingsModel
import me.vripper.model.HostName
import me.vripper.model.HostSettingKey
import me.vripper.model.SettingType
import tornadofx.*

class HostSettingsFragment : Fragment("Image Host") {

    val hostSettings: Map<HostName, Map<HostSettingKey, String>> by param()
    val hostSettingsModels: List<HostSettingsModel>

    override val root = scrollpane {}

    init {
        val mutableHostSettingsModels = mutableListOf<HostSettingsModel>()

        // Build models dynamically from hostSettings map
        hostSettings.forEach { (hostName, settingsMap) ->
            settingsMap.forEach { (settingKey, settingValue) ->
                try {
                    val key = settingKey
                    val model = HostSettingsModel(
                        host = hostName,
                        settingKey = key,
                        initialValue = settingValue
                    )
                    mutableHostSettingsModels.add(model)
                } catch (e: IllegalArgumentException) {
                    // Skip unknown settings
                }
            }
        }

        hostSettingsModels = mutableHostSettingsModels.toList()

        // Build UI dynamically
        with(root) {
            val groupedByHost = hostSettingsModels.groupBy { it.host }

            form {
                groupedByHost.forEach { (hostName, settings) ->
                    fieldset(hostName.name) {
                        settings.forEach { model ->
                            field(
                                model.settingKey.name.replace("_", " ").lowercase()
                                    .replaceFirstChar { it.uppercase() }) {
                                when (model.settingKey.type) {
                                    SettingType.STRING -> textfield(model.valueProperty as StringProperty)
                                    SettingType.BOOLEAN -> checkbox(
                                        "",
                                        model.valueProperty as BooleanProperty
                                    )

                                    SettingType.INT -> {
                                        add(Spinner<Int>(Int.MIN_VALUE, Int.MAX_VALUE, model.getValue().toInt()).apply {
                                            valueProperty().onChange {
                                                (model.valueProperty as IntegerProperty).value =
                                                    it ?: 0
                                            }
                                            isEditable = true
                                            IntegerStringConverter.createFor(this)
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}