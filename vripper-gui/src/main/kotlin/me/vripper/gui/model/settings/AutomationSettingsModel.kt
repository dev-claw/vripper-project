package me.vripper.gui.model.settings

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import tornadofx.getValue
import tornadofx.setValue

class AutomationSettingsModel {
    val compressProperty = SimpleBooleanProperty()
    var compress: Boolean by compressProperty

    val triggerProperty = SimpleBooleanProperty()
    var trigger: Boolean by triggerProperty

    val collectProperty = SimpleBooleanProperty()
    var collect: Boolean by collectProperty

    val triggerActionProperty = SimpleStringProperty()
    var triggerAction: String by triggerActionProperty

    val moveDestinationProperty = SimpleStringProperty()
    var moveDestination: String by moveDestinationProperty

    val moveOverrideProperty = SimpleBooleanProperty()
    var moveOverride: Boolean by moveOverrideProperty

    val webhookUrlProperty = SimpleStringProperty()
    var webhookUrl: String by webhookUrlProperty

    val webhookMethodProperty = SimpleStringProperty()
    var webhookMethod: String by webhookMethodProperty

    val webhookPayloadProperty = SimpleStringProperty()
    var webhookPayload: String by webhookPayloadProperty

    val scriptPathProperty = SimpleStringProperty()
    var scriptPath: String by scriptPathProperty

    val scriptArgumentsProperty = SimpleStringProperty()
    var scriptArguments: String by scriptArgumentsProperty
}
