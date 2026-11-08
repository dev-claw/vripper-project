package me.vripper.data.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

internal object PostActionTable : LongIdTable(name = "POST_ACTION", columnName = "ID") {
    val postIdRef = long("POST_ID_REF").references(PostTable.id, fkName = "POST_ACTION_POST_ID_REF_POST_ID_FK").uniqueIndex()
    val action = varchar("ACTION", 20)
    val state = varchar("STATE", 20)
    val attempts = integer("ATTEMPTS").default(0)
    val lastError = varchar("LAST_ERROR", 10_000).default("")
    val missingFields = varchar("MISSING_FIELDS", 10_000).default("")
    val targetPath = varchar("TARGET_PATH", 1_000).default("")
    val createdAt = datetime("CREATED_AT").default(LocalDateTime.now())
    val updatedAt = datetime("UPDATED_AT").default(LocalDateTime.now())
}
