package me.vripper.gui.model

import javafx.beans.property.SimpleBooleanProperty
import tornadofx.getValue
import tornadofx.setValue

class ThreadsColumnsModel(
    title: Boolean,
    link: Boolean,
    count: Boolean,
    forum: Boolean = true,
    starter: Boolean = true,
    lastPost: Boolean = true,
    lastPoster: Boolean = true,
    replyCount: Boolean = true,
    views: Boolean = true,
) {
    val titleProperty = SimpleBooleanProperty(title)
    var title: Boolean by titleProperty

    val linkProperty = SimpleBooleanProperty(link)
    var link: Boolean by linkProperty

    val countProperty = SimpleBooleanProperty(count)
    var count: Boolean by countProperty

    val forumProperty = SimpleBooleanProperty(forum)
    var forum: Boolean by forumProperty

    val starterProperty = SimpleBooleanProperty(starter)
    var starter: Boolean by starterProperty

    val lastPostProperty = SimpleBooleanProperty(lastPost)
    var lastPost: Boolean by lastPostProperty

    val lastPosterProperty = SimpleBooleanProperty(lastPoster)
    var lastPoster: Boolean by lastPosterProperty

    val replyCountProperty = SimpleBooleanProperty(replyCount)
    var replyCount: Boolean by replyCountProperty

    val viewsProperty = SimpleBooleanProperty(views)
    var views: Boolean by viewsProperty
}
