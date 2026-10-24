package me.vripper.data.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

internal object TaskTable : LongIdTable(name = "TASK", columnName = "ID") {
    val name = varchar("NAME", 255)
    val category = varchar("CATEGORY", 255)
    val description = varchar("DESCRIPTION", 1000)
    val status = varchar("STATUS", 15)
    val result = varchar("RESULT", 10_000)
    val createdAt = datetime("CREATED_AT").default(LocalDateTime.now())
    val updatedAt = datetime("UPDATED_AT").default(LocalDateTime.now())
}
