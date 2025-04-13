package me.vripper.utilities

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DatabaseManager {
    private var database: Database? = null
    private var connected: Boolean = false
    private var lock = ReentrantLock()

    fun connect() {
        lock.withLock {
            database =
                Database.connect("jdbc:sqlite:${ApplicationProperties.VRIPPER_DIR}/vripper.db")
                    .also { update(it.connector.invoke().connection as Connection) }
            connected = true
        }
    }

    fun disconnect() {
        lock.withLock {
            database?.let { TransactionManager.closeAndUnregister(it) }
            connected = false
        }
    }

    fun isConnected(): Boolean {
        return lock.withLock { connected }
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