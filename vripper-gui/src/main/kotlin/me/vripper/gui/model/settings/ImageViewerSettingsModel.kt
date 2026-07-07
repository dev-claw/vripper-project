package me.vripper.gui.model.settings

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import tornadofx.getValue
import tornadofx.setValue

class ImageViewerSettingsModel {
    val decodedCacheMbProperty = SimpleIntegerProperty()
    var decodedCacheMb: Int by decodedCacheMbProperty

    val bytePreloadRadiusProperty = SimpleIntegerProperty()
    var bytePreloadRadius: Int by bytePreloadRadiusProperty

    val bytePreloadConcurrencyProperty = SimpleIntegerProperty()
    var bytePreloadConcurrency: Int by bytePreloadConcurrencyProperty

    val decodePreloadConcurrencyProperty = SimpleIntegerProperty()
    var decodePreloadConcurrency: Int by decodePreloadConcurrencyProperty

    val diskCacheMbProperty = SimpleIntegerProperty()
    var diskCacheMb: Int by diskCacheMbProperty

    val fadeAnimationProperty = SimpleBooleanProperty()
    var fadeAnimation: Boolean by fadeAnimationProperty

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
