package me.vripper.gui.model

import javafx.beans.property.SimpleBooleanProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class ThreadSelectionColumnsModel(
    preview: Boolean,
    index: Boolean,
    title: Boolean,
    link: Boolean,
    hosts: Boolean,
) {
    val previewProperty = SimpleBooleanProperty(preview)
    var preview: Boolean by previewProperty

    val indexProperty = SimpleBooleanProperty(index)
    var index: Boolean by indexProperty

    val titleProperty = SimpleBooleanProperty(title)
    var title: Boolean by titleProperty

    val linkProperty = SimpleBooleanProperty(link)
    var link: Boolean by linkProperty

    val hostsProperty = SimpleBooleanProperty(hosts)
    var hosts: Boolean by hostsProperty
}