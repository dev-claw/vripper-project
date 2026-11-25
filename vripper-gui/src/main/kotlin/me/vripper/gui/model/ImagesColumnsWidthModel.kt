package me.vripper.gui.model

import javafx.beans.property.SimpleDoubleProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class ImagesColumnsWidthModel(
    preview: Double,
    index: Double,
    link: Double,
    progress: Double,
    filename: Double,
    status: Double,
    size: Double,
    downloaded: Double,
) {
    val previewProperty = SimpleDoubleProperty(preview)
    var preview: Double by previewProperty

    val indexProperty = SimpleDoubleProperty(index)
    var index: Double by indexProperty

    val linkProperty = SimpleDoubleProperty(link)
    var link: Double by linkProperty

    val progressProperty = SimpleDoubleProperty(progress)
    var progress: Double by progressProperty

    val filenameProperty = SimpleDoubleProperty(filename)
    var filename: Double by filenameProperty

    val statusProperty = SimpleDoubleProperty(status)
    var status: Double by statusProperty

    val sizeProperty = SimpleDoubleProperty(size)
    var size: Double by sizeProperty

    val downloadedProperty = SimpleDoubleProperty(downloaded)
    var downloaded: Double by downloadedProperty
}