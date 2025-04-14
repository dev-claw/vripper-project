package me.vripper.utilities

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jetbrains.exposed.sql.Database
import java.sql.Connection

object DatabaseManager {
    private var database: Database? = null

    fun connect() {
        database =
            Database.connect("jdbc:sqlite:${ApplicationProperties.VRIPPER_DIR}/vripper.db")
                .also { update(it.connector.invoke().connection as Connection) }
    }

    private fun update(connection: Connection) {
        connection.use { cn ->
            val database: liquibase.database.Database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(cn))
            Liquibase(
                "db.changelog-master.xml", ClassLoaderResourceAccessor(), database
            ).use { liquibase ->
                liquibase.update(Contexts(), LabelExpression())
            }
        }
    }
}