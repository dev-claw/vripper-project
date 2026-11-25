package me.vripper.gui.model.settings

import javafx.beans.property.SimpleIntegerProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class ConnectionSettingsModel {
    val maxThreadsProperty = SimpleIntegerProperty()
    var maxThreads: Int by maxThreadsProperty

    val maxTotalThreadsProperty = SimpleIntegerProperty()
    var maxTotalThreads: Int by maxTotalThreadsProperty

    val timeoutProperty = SimpleIntegerProperty()
    var timeout: Int by timeoutProperty

    val maxAttemptsProperty = SimpleIntegerProperty()
    var maxAttempts: Int by maxAttemptsProperty
}
