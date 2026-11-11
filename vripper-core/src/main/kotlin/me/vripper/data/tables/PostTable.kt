package me.vripper.data.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

internal object PostTable : LongIdTable(name = "POST", columnName = "ID") {
    val done = integer("DONE")
    val hosts = varchar("HOSTS", 255)
    val outputPath = varchar("OUTPUT_PATH", 260)
    val folderName = varchar("FOLDER_NAME", 260)
    val postId = long("POST_ID")
    val status = varchar("STATUS", 15)
    val threadId = long("THREAD_ID")
    val postTitle = varchar("POST_TITLE", 255)
    val threadTitle = varchar("THREAD_TITLE", 255)
    val forum = varchar("FORUM", 255)
    val total = integer("TOTAL")
    val url = varchar("URL", 200)
    val token = varchar("TOKEN", 51)
    val addedAt = datetime("ADDED_AT").default(LocalDateTime.now())
    val rank = integer("RANK").default(0)
    val downloaded = long("DOWNLOADED")
    val size = long("SIZE")
    val author = varchar("AUTHOR", 255).nullable()
    val userId = long("USER_ID").nullable()
    val postDate = varchar("POST_DATE", 32).nullable()
    val edited = varchar("EDITED", 32).nullable()
}