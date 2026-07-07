package me.vripper.gui.model.settings

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleStringProperty
import tornadofx.getValue
import tornadofx.setValue

class WorkspaceSettingsModel {
    val browseLayoutProperty = SimpleStringProperty()
    var browseLayout: String by browseLayoutProperty

    val detailsPanelVisibleProperty = SimpleBooleanProperty()
    var detailsPanelVisible: Boolean by detailsPanelVisibleProperty

    val detailsPanelDividerPositionProperty = SimpleDoubleProperty()
    var detailsPanelDividerPosition: Double by detailsPanelDividerPositionProperty

    val windowWidthProperty = SimpleDoubleProperty()
    var windowWidth: Double by windowWidthProperty

    val windowHeightProperty = SimpleDoubleProperty()
    var windowHeight: Double by windowHeightProperty

    val windowXProperty = SimpleDoubleProperty()
    var windowX: Double by windowXProperty

    val windowYProperty = SimpleDoubleProperty()
    var windowY: Double by windowYProperty

    val windowMaximizedProperty = SimpleBooleanProperty()
    var windowMaximized: Boolean by windowMaximizedProperty
}
