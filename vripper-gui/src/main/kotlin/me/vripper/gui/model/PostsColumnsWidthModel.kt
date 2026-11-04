package me.vripper.gui.model

import javafx.beans.property.SimpleDoubleProperty
import tornadofx.getValue
import tornadofx.setValue

class PostsColumnsWidthModel(
    preview: Double,
    title: Double,
    progress: Double,
    status: Double,
    automation: Double,
    path: Double,
    total: Double,
    hosts: Double,
    addedOn: Double,
    order: Double,
) {
    val previewProperty = SimpleDoubleProperty(preview)
    var preview: Double by previewProperty

    val titleProperty = SimpleDoubleProperty(title)
    var title: Double by titleProperty

    val progressProperty = SimpleDoubleProperty(progress)
    var progress: Double by progressProperty

    val statusProperty = SimpleDoubleProperty(status)
    var status: Double by statusProperty

    val automationProperty = SimpleDoubleProperty(automation)
    var automation: Double by automationProperty

    val pathProperty = SimpleDoubleProperty(path)
    var path: Double by pathProperty

    val totalProperty = SimpleDoubleProperty(total)
    var total: Double by totalProperty

    val hostsProperty = SimpleDoubleProperty(hosts)
    var hosts: Double by hostsProperty

    val addedOnProperty = SimpleDoubleProperty(addedOn)
    var addedOn: Double by addedOnProperty

    val orderProperty = SimpleDoubleProperty(order)
    var order: Double by orderProperty
}