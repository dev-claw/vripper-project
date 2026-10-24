package me.vripper.gui.model.settings

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleStringProperty
import tornadofx.getValue
import tornadofx.setValue

class PostsSettingsModel {
    val postsViewModeProperty = SimpleStringProperty()
    var postsViewMode: String by postsViewModeProperty

    val postsGridTileSizeProperty = SimpleDoubleProperty()
    var postsGridTileSize: Double by postsGridTileSizeProperty

    val postsPreviewPopupProperty = SimpleBooleanProperty()
    var postsPreviewPopup: Boolean by postsPreviewPopupProperty
}
