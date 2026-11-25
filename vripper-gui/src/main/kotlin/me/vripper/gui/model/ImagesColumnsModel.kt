package me.vripper.gui.model

import javafx.beans.property.SimpleBooleanProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class ImagesColumnsModel(
    preview: Boolean,
    index: Boolean,
    link: Boolean,
    progress: Boolean,
    filename: Boolean,
    status: Boolean,
    size: Boolean,
    downloaded: Boolean,
) {
    val previewProperty = SimpleBooleanProperty(preview)
    var preview: Boolean by previewProperty

    val indexProperty = SimpleBooleanProperty(index)
    var index: Boolean by indexProperty

    val linkProperty = SimpleBooleanProperty(link)
    var link: Boolean by linkProperty

    val progressProperty = SimpleBooleanProperty(progress)
    var progress: Boolean by progressProperty

    val filenameProperty = SimpleBooleanProperty(filename)
    var filename: Boolean by filenameProperty

    val statusProperty = SimpleBooleanProperty(status)
    var status: Boolean by statusProperty

    val sizeProperty = SimpleBooleanProperty(size)
    var size: Boolean by sizeProperty

    val downloadedProperty = SimpleBooleanProperty(downloaded)
    var downloaded: Boolean by downloadedProperty
}