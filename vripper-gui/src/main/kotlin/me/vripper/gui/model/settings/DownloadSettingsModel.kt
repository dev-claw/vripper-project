package me.vripper.gui.model.settings

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class DownloadSettingsModel {
    val downloadPathProperty = SimpleStringProperty()
    var downloadPath: String by downloadPathProperty

    val autoStartProperty = SimpleBooleanProperty()
    var autoStart: Boolean by autoStartProperty

    val autoQueueThresholdProperty = SimpleIntegerProperty()
    var autoQueueThreshold: Int by autoQueueThresholdProperty

    val forceOrderProperty = SimpleBooleanProperty()
    var forceOrder: Boolean by forceOrderProperty

    val forumSubfolderProperty = SimpleBooleanProperty()
    var forumSubfolder: Boolean by forumSubfolderProperty

    val siteSubDirectoryProperty = SimpleBooleanProperty()
    var siteSubDirectory: Boolean by siteSubDirectoryProperty

    val threadSubLocationProperty = SimpleBooleanProperty()
    var threadSubLocation: Boolean by threadSubLocationProperty

    val clearCompletedProperty = SimpleBooleanProperty()
    var clearCompleted: Boolean by clearCompletedProperty

    val appendPostIdProperty = SimpleBooleanProperty()
    var appendPostId: Boolean by appendPostIdProperty
}
