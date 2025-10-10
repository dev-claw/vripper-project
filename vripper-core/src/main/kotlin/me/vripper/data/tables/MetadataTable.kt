package me.vripper.data.tables

import org.jetbrains.exposed.sql.Table

internal object MetadataTable : Table(name = "METADATA") {
    val postIdRef = long("POST_ID_REF").references(PostTable.id, fkName = "METADATA_POST_ID_REF_POST_ID_FK")
    val data = varchar("DATA", 1_000_000)
}