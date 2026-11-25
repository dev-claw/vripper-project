package me.vripper.gui.model

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleStringProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class ThreadModel(
    title: String,
    link: String,
    total: Int,
    val threadId: Long,
    forum: String = "",
    starter: String = "",
    lastPost: String = "",
    lastPoster: String = "",
    replyCount: Int = 0,
    views: Int = 0,
    newestPostId: Long = 0,
    sticky: Boolean = false,
    open: Boolean = true,
) {
    val titleProperty = SimpleStringProperty(title)
    var title: String by titleProperty

    val linkProperty = SimpleStringProperty(link)
    var link: String by linkProperty

    val totalProperty = SimpleIntegerProperty(total)
    var total: Int by totalProperty

    val forumProperty = SimpleStringProperty(forum)
    var forum: String by forumProperty

    val starterProperty = SimpleStringProperty(starter)
    var starter: String by starterProperty

    val lastPostProperty = SimpleStringProperty(lastPost)
    var lastPost: String by lastPostProperty

    val lastPosterProperty = SimpleStringProperty(lastPoster)
    var lastPoster: String by lastPosterProperty

    val replyCountProperty = SimpleIntegerProperty(replyCount)
    var replyCount: Int by replyCountProperty

    val viewsProperty = SimpleIntegerProperty(views)
    var views: Int by viewsProperty

    val newestPostIdProperty = SimpleLongProperty(newestPostId)
    var newestPostId: Long by newestPostIdProperty

    val stickyProperty = SimpleBooleanProperty(sticky)
    var sticky: Boolean by stickyProperty

    val openProperty = SimpleBooleanProperty(open)
    var open: Boolean by openProperty
}
