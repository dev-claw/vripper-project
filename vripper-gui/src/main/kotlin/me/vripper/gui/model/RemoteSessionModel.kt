package me.vripper.gui.model

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import tornadofx.getValue
import tornadofx.setValue

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