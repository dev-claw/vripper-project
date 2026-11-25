package me.vripper.gui.model

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class RemoteSessionModel(
    host: String,
    port: Int,
    passcode: String,
) {
    val hostProperty = SimpleStringProperty(host)
    var host: String by hostProperty

    val portProperty = SimpleIntegerProperty(port)
    var port: Int by portProperty

    val passcodeProperty = SimpleStringProperty(passcode)
    var passcode: String by passcodeProperty
}