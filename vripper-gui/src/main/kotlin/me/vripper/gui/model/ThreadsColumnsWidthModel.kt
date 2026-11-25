package me.vripper.gui.model

import javafx.beans.property.SimpleDoubleProperty
import me.vripper.gui.fx.getValue
import me.vripper.gui.fx.setValue

class ThreadsColumnsWidthModel(
    title: Double,
    link: Double,
    count: Double,
    forum: Double = 150.0,
    starter: Double = 150.0,
    lastPost: Double = 150.0,
    lastPoster: Double = 150.0,
    replyCount: Double = 100.0,
    views: Double = 100.0,
) {
    val titleProperty = SimpleDoubleProperty(title)
    var title: Double by titleProperty

    val linkProperty = SimpleDoubleProperty(link)
    var link: Double by linkProperty

    val countProperty = SimpleDoubleProperty(count)
    var count: Double by countProperty

    val forumProperty = SimpleDoubleProperty(forum)
    var forum: Double by forumProperty

    val starterProperty = SimpleDoubleProperty(starter)
    var starter: Double by starterProperty

    val lastPostProperty = SimpleDoubleProperty(lastPost)
    var lastPost: Double by lastPostProperty

    val lastPosterProperty = SimpleDoubleProperty(lastPoster)
    var lastPoster: Double by lastPosterProperty

    val replyCountProperty = SimpleDoubleProperty(replyCount)
    var replyCount: Double by replyCountProperty

    val viewsProperty = SimpleDoubleProperty(views)
    var views: Double by viewsProperty
}
