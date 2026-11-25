package me.vripper.gui.model

import javafx.beans.property.SimpleDoubleProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class ThreadSelectionColumnsWidthModel(
    preview: Double,
    index: Double,
    title: Double,
    link: Double,
    hosts: Double,
) {
    val previewProperty = SimpleDoubleProperty(preview)
    var preview: Double by previewProperty

    val indexProperty = SimpleDoubleProperty(index)
    var index: Double by indexProperty

    val titleProperty = SimpleDoubleProperty(title)
    var title: Double by titleProperty

    val linkProperty = SimpleDoubleProperty(link)
    var link: Double by linkProperty

    val hostsProperty = SimpleDoubleProperty(hosts)
    var hosts: Double by hostsProperty
}