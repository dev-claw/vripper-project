package me.vripper.gui.components.fragments

import atlantafx.base.theme.Styles
import javafx.collections.FXCollections
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.stage.FileChooser
import me.vripper.gui.model.settings.AutomationSettingsModel
import me.vripper.model.AutomationMode
import me.vripper.model.AutomationPlaceholders
import me.vripper.model.AutomationSettings
import me.vripper.model.TriggerAction
import me.vripper.model.WebhookMethod
import tornadofx.*

class AutomationSettingsFragment : Fragment("Automation Settings") {

    val automationSettings: AutomationSettings by param()
    val automationSettingsModel = AutomationSettingsModel()

    override val root = vbox {}

    init {
        automationSettingsModel.enabled = automationSettings.enabled
        automationSettingsModel.mode = automationSettings.mode
        automationSettingsModel.compress = automationSettings.compress
        automationSettingsModel.action = automationSettings.action
        automationSettingsModel.moveDestination = automationSettings.moveDestination
        automationSettingsModel.moveOverride = automationSettings.moveOverride
        automationSettingsModel.webhookUrl = automationSettings.webhookUrl
        automationSettingsModel.webhookMethod = automationSettings.webhookMethod
        automationSettingsModel.webhookPayload = automationSettings.webhookPayload
        automationSettingsModel.scriptPath = automationSettings.scriptPath
        automationSettingsModel.scriptArguments = automationSettings.scriptArguments
        automationSettingsModel.requiredCustomFields = automationSettings.requiredCustomFields.joinToString(", ")

        with(root) {
            form {
                fieldset("Automation") {
                    field("Enable post automation") {
                        checkbox { bind(automationSettingsModel.enabledProperty) }
                    }
                    field("Mode") {
                        combobox<AutomationMode> {
                            items = FXCollections.observableArrayList(AutomationMode.AUTO, AutomationMode.REVIEW)
                            valueProperty().bindBidirectional(automationSettingsModel.modeProperty)
                        }
                    }
                    field("Required custom fields") {
                        textfield(automationSettingsModel.requiredCustomFieldsProperty) {
                            promptText = "Comma-separated field names"
                        }
                    }
                }

                fieldset("Post-processing") {
                    field("Compress post folder") {
                        checkbox { bind(automationSettingsModel.compressProperty) }
                    }
                }

                fieldset("Trigger action") {
                    label("Only the selected action runs. Compression is applied first when enabled.") {
                        styleClass.add(Styles.TEXT_MUTED)
                    }
                    field("Action") {
                        combobox<TriggerAction> {
                            items = FXCollections.observableArrayList(TriggerAction.entries)
                            valueProperty().bindBidirectional(automationSettingsModel.actionProperty)
                        }
                    }
                    val moveDestinationField = field("Move destination") {
                        textfield(automationSettingsModel.moveDestinationProperty)
                        button("Browse") {
                            action {
                                val directory = chooseDirectory(title = "Select move destination")
                                if (directory != null) {
                                    automationSettingsModel.moveDestinationProperty.set(directory.path)
                                }
                            }
                        }
                    }
                    moveDestinationField.visibleWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.MOVE))
                    moveDestinationField.managedWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.MOVE))

                    val moveOverrideField = field("Overwrite move destination") {
                        checkbox { bind(automationSettingsModel.moveOverrideProperty) }
                    }
                    moveOverrideField.visibleWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.MOVE))
                    moveOverrideField.managedWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.MOVE))

                    val webhookUrlField = field("Webhook URL") {
                        textfield(automationSettingsModel.webhookUrlProperty)
                    }
                    webhookUrlField.visibleWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.WEBHOOK))
                    webhookUrlField.managedWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.WEBHOOK))

                    val webhookMethodField = field("Webhook method") {
                        combobox<WebhookMethod> {
                            items = FXCollections.observableArrayList(WebhookMethod.entries)
                            valueProperty().bindBidirectional(automationSettingsModel.webhookMethodProperty)
                        }
                    }
                    webhookMethodField.visibleWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.WEBHOOK))
                    webhookMethodField.managedWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.WEBHOOK))

                    val webhookPayloadField = field("Webhook payload") {
                        textarea(automationSettingsModel.webhookPayloadProperty) {
                            prefRowCount = 4
                        }
                    }
                    webhookPayloadField.visibleWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.WEBHOOK))
                    webhookPayloadField.managedWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.WEBHOOK))

                    val scriptPathField = field("Script path") {
                        textfield(automationSettingsModel.scriptPathProperty)
                        button("Browse") {
                            action {
                                val file = chooseFile(
                                    title = "Select script",
                                    filters = arrayOf(FileChooser.ExtensionFilter("Scripts", "*"))
                                )
                                file.firstOrNull()?.let {
                                    automationSettingsModel.scriptPathProperty.set(it.absolutePath)
                                }
                            }
                        }
                    }
                    scriptPathField.visibleWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.SCRIPT))
                    scriptPathField.managedWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.SCRIPT))

                    val scriptArgumentsField = field("Script arguments") {
                        textfield(automationSettingsModel.scriptArgumentsProperty)
                    }
                    scriptArgumentsField.visibleWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.SCRIPT))
                    scriptArgumentsField.managedWhen(automationSettingsModel.actionProperty.isEqualTo(TriggerAction.SCRIPT))
                }

                fieldset("Available attributes") {
                    label("Use these tokens in the move destination, webhook URL/payload and script path/arguments.") {
                        styleClass.add(Styles.TEXT_MUTED)
                        isWrapText = true
                    }
                    vbox(4.0) {
                        AutomationPlaceholders.all.forEach { placeholder ->
                            hbox(8.0) {
                                label("\${${placeholder.key}}") {
                                    style = "-fx-font-family: monospace;"
                                    minWidth = 190.0
                                }
                                label(placeholder.description) {
                                    styleClass.add(Styles.TEXT_MUTED)
                                    isWrapText = true
                                    HBox.setHgrow(this, Priority.ALWAYS)
                                }
                            }
                        }
                    }
                    label("Custom fields are not listed here because their names are user-defined. Use \${cf:<field_name>} for a field from the post metadata.") {
                        styleClass.add(Styles.TEXT_MUTED)
                        isWrapText = true
                    }
                }
            }
        }
    }
}
