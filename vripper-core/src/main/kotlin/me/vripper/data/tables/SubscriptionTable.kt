package me.vripper.data.tables

import org.jetbrains.exposed.dao.id.LongIdTable

internal object SubscriptionTable : LongIdTable("SUBSCRIPTION", columnName = "ID") {
    val total = integer("TOTAL").default(0)
    val url = varchar("URL", 200)
    val threadId = long("THREAD_ID").uniqueIndex()
    val title = varchar("TITLE", 255)
    val forumId = long("FORUM_ID").nullable()
    val forum = varchar("FORUM", 255).nullable()
    val prefixId = varchar("PREFIX_ID", 255).nullable()
    val starter = varchar("STARTER", 255).nullable()
    val starterId = long("STARTER_ID").nullable()
    val created = varchar("CREATED", 32).nullable()
    val lastPost = varchar("LAST_POST", 32).nullable()
    val lastPostId = long("LAST_POST_ID").nullable()
    val lastPoster = varchar("LAST_POSTER", 255).nullable()
    val replyCount = integer("REPLY_COUNT").nullable()
    val views = integer("VIEWS").nullable()
    val open = bool("OPEN").nullable()
    val sticky = bool("STICKY").nullable()
    val newestPostId = long("NEWEST_POST_ID").nullable()
}
