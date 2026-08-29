package me.vripper.gui.components.fragments.settings

import atlantafx.base.controls.ToggleSwitch
import javafx.geometry.Insets
import me.vripper.gui.model.settings.AutomationSettingsModel
import me.vripper.model.AutomationSettings
import me.vripper.model.TriggerAction
import tornadofx.*

class AutomationSettingsFragment : Fragment("Automation") {

    val automationSettings: AutomationSettings by param()
    val automationSettingsModel = AutomationSettingsModel()

    override val root = scrollpane {}

    init {
        automationSettingsModel.compress = automationSettings.compress
        automationSettingsModel.trigger = automationSettings.trigger
        automationSettingsModel.collect = automationSettings.collect
        automationSettingsModel.triggerAction = automationSettings.triggerAction.name
        automationSettingsModel.moveDestination = automationSettings.moveDestination
        automationSettingsModel.moveOverride = automationSettings.moveOverride
        automationSettingsModel.webhookUrl = automationSettings.webhookUrl
        automationSettingsModel.webhookMethod = automationSettings.webhookMethod
        automationSettingsModel.webhookPayload = automationSettings.webhookPayload
        automationSettingsModel.scriptPath = automationSettings.scriptPath
        automationSettingsModel.scriptArguments = automationSettings.scriptArguments
        with(root) {
            form {
                fieldset("File Processing") {
                    field("Compress files into a ZIP archive") {
                        checkbox(property = automationSettingsModel.compressProperty) {}
                    }
                }
                fieldset("Automation Triggers") {
                    field("Execute an action when task finishes successfully") {
                        add(ToggleSwitch().apply {
                            isSelected = automationSettings.trigger
                            automationSettingsModel.triggerProperty.bind(selectedProperty())
                        })
                    }
                    fieldset {
                        padding = Insets(5.0)
                        disableProperty().bind(automationSettingsModel.triggerProperty.not())
                        field("Hold tasks for configuration") {
                            checkbox(property = automationSettingsModel.collectProperty) {
                                tooltip("Prevent new tasks from starting automatically. This allows you to fill in custom fields needed for your automation before any files are downloaded. Tasks must be manually released once ready.") {
                                    isWrapText = true
                                    prefWidth = 200.0
                                }
                            }
                        }
                        field("Action Type") {
                            combobox<String>(
                                property = automationSettingsModel.triggerActionProperty,
                                values = TriggerAction.entries.map { it.name }
                            )
                        }

                        stackpane {
                            fieldset("Move Action") {
                                padding = Insets(5.0)
                                visibleWhen(automationSettingsModel.triggerActionProperty.eq(TriggerAction.Move.name))
                                field("Destination Folder") {
                                    textfield(automationSettingsModel.moveDestinationProperty)
                                }
                                field("Overwrite existing files/folders at destination") {
                                    checkbox(property = automationSettingsModel.moveOverrideProperty)
                                }
                            }

                            fieldset("Webhook Action") {
                                padding = Insets(5.0)
                                visibleWhen(automationSettingsModel.triggerActionProperty.eq(TriggerAction.Webhook.name))
                                field("URL") {
                                    textfield(automationSettingsModel.webhookUrlProperty)
                                }
                                field("Method") {
                                    combobox(
                                        property = automationSettingsModel.webhookMethodProperty,
                                        values = listOf("GET", "POST")
                                    )
                                }
                                field("Payload") {
                                    visibleProperty().bind(automationSettingsModel.webhookMethodProperty.eq("POST"))
                                    textarea(automationSettingsModel.webhookPayloadProperty)
                                }
                            }

                            fieldset("Script Action") {
                                padding = Insets(5.0)
                                visibleWhen(automationSettingsModel.triggerActionProperty.eq(TriggerAction.Script.name))
                                field("Script Path") {
                                    textfield(automationSettingsModel.scriptPathProperty)
                                }
                                field("Arguments") {
                                    textfield(automationSettingsModel.scriptArgumentsProperty)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}