package me.vripper.gui.model

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleStringProperty
import me.vripper.utilities.formatSI
import tornadofx.getValue
import tornadofx.setValue

class ImageModel(
    id: Long,
    index: Int,
    url: String,
    progress: Double,
    status: String,
    size: Long,
    downloaded: Long,
    filename: String,
    thumbUrl: String,
    val postEntityId: Long
) {

    val idProperty = SimpleLongProperty(id)
    var id: Long by idProperty

    val indexProperty = SimpleIntegerProperty(index)
    var index: Int by indexProperty

    val urlProperty = SimpleStringProperty(url)
    var url: String by urlProperty

    val progressProperty = SimpleDoubleProperty(progress)
    var progress: Double by progressProperty

    val statusProperty = SimpleStringProperty(status)
    var status: String by statusProperty

    val filenameProperty = SimpleStringProperty(filename)
    var filename: String by filenameProperty

    val thumbUrlProperty = SimpleStringProperty(thumbUrl)
    var thumbUrl: String by thumbUrlProperty

    val sizeProperty = SimpleStringProperty(size.formatSI())
    var size = size
        set(value) {
            field = value
            sizeProperty.set(value.formatSI())
        }

    val downloadedProperty = SimpleStringProperty(downloaded.formatSI())
    var downloaded = downloaded
        set(value) {
            field = value
            downloadedProperty.set(value.formatSI())
        }
}
