package me.vripper.gui.model

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class ThreadSelectionModel(
    index: Int,
    title: String,
    url: String,
    hosts: String,
    val postId: Long,
    val threadId: Long,
    previewList: List<String>
) {
    val indexProperty = SimpleIntegerProperty(index)
    var index: Int by indexProperty

    val titleProperty = SimpleStringProperty(title)
    var title: String by titleProperty

    val urlProperty = SimpleStringProperty(url)
    var url: String by urlProperty

    val hostsProperty = SimpleStringProperty(hosts)
    var hosts: String by hostsProperty

    val previewListProperty = SimpleListProperty(FXCollections.observableArrayList(previewList))
    var previewList: ObservableList<String> by previewListProperty
}
