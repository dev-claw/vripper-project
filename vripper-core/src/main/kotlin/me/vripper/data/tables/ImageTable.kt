package me.vripper.data.tables

import org.jetbrains.exposed.dao.id.LongIdTable

internal object ImageTable : LongIdTable(name = "IMAGE", columnName = "ID") {
    val downloaded = long("DOWNLOADED")
    val host = byte("HOST")
    val index = integer("INDEX")
    val status = varchar("STATUS", 15)
    val filename = varchar("FILENAME", 260)
    val size = long("SIZE")
    val url = varchar("URL", 200)
    val thumbUrl = varchar("THUMB_URL", 200)
    val postIdRef = long("POST_ID_REF").references(id, fkName = "IMAGE_POST_ID_REF_POST_ID_FK")
}