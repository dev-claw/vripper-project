package me.vripper.gui.model.settings

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import me.vripper.model.AutomationMode
import me.vripper.model.TriggerAction
import me.vripper.model.WebhookMethod
import tornadofx.getValue
import tornadofx.setValue

class AutomationSettingsModel {
    val enabledProperty = SimpleBooleanProperty()
    var enabled: Boolean by enabledProperty

    val modeProperty = SimpleObjectProperty<AutomationMode>(AutomationMode.REVIEW)
    var mode: AutomationMode by modeProperty

    val compressProperty = SimpleBooleanProperty()
    var compress: Boolean by compressProperty

    val actionProperty = SimpleObjectProperty<TriggerAction>(TriggerAction.NONE)
    var action: TriggerAction by actionProperty

    val moveDestinationProperty = SimpleStringProperty()
    var moveDestination: String by moveDestinationProperty

    val moveOverrideProperty = SimpleBooleanProperty()
    var moveOverride: Boolean by moveOverrideProperty

    val webhookUrlProperty = SimpleStringProperty()
    var webhookUrl: String by webhookUrlProperty

    val webhookMethodProperty = SimpleObjectProperty<WebhookMethod>(WebhookMethod.POST)
    var webhookMethod: WebhookMethod by webhookMethodProperty

    val webhookPayloadProperty = SimpleStringProperty()
    var webhookPayload: String by webhookPayloadProperty

    val scriptPathProperty = SimpleStringProperty()
    var scriptPath: String by scriptPathProperty

    val scriptArgumentsProperty = SimpleStringProperty()
    var scriptArguments: String by scriptArgumentsProperty

    val requiredCustomFieldsProperty = SimpleStringProperty()
    var requiredCustomFields: String by requiredCustomFieldsProperty
}
