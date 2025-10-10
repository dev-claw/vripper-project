package me.vripper.gui.model

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import tornadofx.getValue
import tornadofx.setValue

class PostModel(
    var id: Long,
    var vgPostId: Long,
    title: String,
    progress: Double,
    status: String,
    url: String,
    done: Int,
    total: Int,
    hosts: String,
    addedOn: String,
    order: String,
    path: String,
    folderName: String,
    progressCount: String,
    previewList: List<String>,
    altTitles: List<String>,
    postedBy: String,
    var vgThreadId: Long
) {

    val titleProperty = SimpleStringProperty(title)
    var title: String by titleProperty

    val progressProperty = SimpleDoubleProperty(progress)
    var progress: Double by progressProperty

    val statusProperty = SimpleStringProperty(status)
    var status: String by statusProperty

    val urlProperty = SimpleStringProperty(url)
    var url: String by urlProperty

    val doneProperty = SimpleIntegerProperty(done)
    var done: Int by doneProperty

    val totalProperty = SimpleIntegerProperty(total)
    var total: Int by totalProperty

    val hostsProperty = SimpleStringProperty(hosts)
    var hosts: String by hostsProperty

    val addedOnProperty = SimpleStringProperty(addedOn)
    var addedOn: String by addedOnProperty

    val orderProperty = SimpleStringProperty(order)
    var order: String by orderProperty

    val pathProperty = SimpleStringProperty(path)
    var path: String by pathProperty

    val folderNameProperty = SimpleStringProperty(folderName)
    var folderName: String by folderNameProperty

    val progressCountProperty = SimpleStringProperty(progressCount)
    var progressCount: String by progressCountProperty

    val previewListProperty = SimpleListProperty(FXCollections.observableArrayList(previewList))
    var previewList: ObservableList<String> by previewListProperty

    val altTitlesProperty =
        SimpleListProperty(FXCollections.observableArrayList(altTitles))
    var altTitles: ObservableList<String> by altTitlesProperty

    val postedByProperty = SimpleStringProperty(postedBy)
    var postedBy: String by postedByProperty
}
