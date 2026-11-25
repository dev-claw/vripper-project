package me.vripper.gui.model.settings

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleStringProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class ViperSettingsModel {
    val loginProperty = SimpleBooleanProperty()
    var login: Boolean by loginProperty

    val usernameProperty = SimpleStringProperty()
    var username: String by usernameProperty

    val passwordProperty = SimpleStringProperty()
    var password: String by passwordProperty

    val thanksProperty = SimpleBooleanProperty()
    var thanks: Boolean by thanksProperty

    val hostProperty = SimpleStringProperty()
    var host: String by hostProperty

    val requestLimitProperty = SimpleLongProperty()
    var requestLimit: Long by requestLimitProperty

    val fetchMetadataProperty = SimpleBooleanProperty()
    var fetchMetadata: Boolean by fetchMetadataProperty

    val watchSubscriptionsProperty = SimpleBooleanProperty()
    var watchSubscriptions: Boolean by watchSubscriptionsProperty

    val watchIntervalMinutesProperty = SimpleIntegerProperty()
    var watchIntervalMinutes: Int by watchIntervalMinutesProperty

    val watchThreadsProperty = SimpleBooleanProperty()
    var watchThreads: Boolean by watchThreadsProperty
}
