package me.vripper.gui.model

import javafx.beans.property.SimpleBooleanProperty
import tornadofx.getValue
import tornadofx.setValue

class PostsColumnsModel(
    preview: Boolean,
    title: Boolean,
    progress: Boolean,
    status: Boolean,
    automation: Boolean,
    path: Boolean,
    total: Boolean,
    hosts: Boolean,
    addedOn: Boolean,
    order: Boolean,
) {
    val previewProperty = SimpleBooleanProperty(preview)
    var preview: Boolean by previewProperty

    val titleProperty = SimpleBooleanProperty(title)
    var title: Boolean by titleProperty

    val progressProperty = SimpleBooleanProperty(progress)
    var progress: Boolean by progressProperty

    val statusProperty = SimpleBooleanProperty(status)
    var status: Boolean by statusProperty

    val automationProperty = SimpleBooleanProperty(automation)
    var automation: Boolean by automationProperty

    val pathProperty = SimpleBooleanProperty(path)
    var path: Boolean by pathProperty

    val totalProperty = SimpleBooleanProperty(total)
    var total: Boolean by totalProperty

    val hostsProperty = SimpleBooleanProperty(hosts)
    var hosts: Boolean by hostsProperty

    val addedOnProperty = SimpleBooleanProperty(addedOn)
    var addedOn: Boolean by addedOnProperty

    val orderProperty = SimpleBooleanProperty(order)
    var order: Boolean by orderProperty
}