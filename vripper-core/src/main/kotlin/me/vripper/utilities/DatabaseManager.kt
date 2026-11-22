package me.vripper.utilities

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.sql.Connection
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DatabaseManager {
    private const val MAX_POOL_SIZE = 8
    private const val BUSY_TIMEOUT_MS = 30_000

    private var database: Database? = null
    private var dataSource: HikariDataSource? = null

    private val writeLock = ReentrantLock(true)

    fun connect() {
        val sqliteConfig = SQLiteConfig().apply {
            setJournalMode(SQLiteConfig.JournalMode.WAL)
            setBusyTimeout(BUSY_TIMEOUT_MS)
        }
        val sqliteDataSource = SQLiteDataSource(sqliteConfig).apply {
            url = "jdbc:sqlite:${ApplicationProperties.VRIPPER_DIR}/vripper.db"
        }
        val hikariConfig = HikariConfig().apply {
            poolName = "vripper-sqlite"
            dataSource = sqliteDataSource
            maximumPoolSize = MAX_POOL_SIZE
            minimumIdle = 1
            connectionTimeout = BUSY_TIMEOUT_MS.toLong()
            isAutoCommit = true
        }
        val pool = HikariDataSource(hikariConfig)
        dataSource = pool
        val config = DatabaseConfig {
            defaultMaxAttempts = 5
            defaultMinRetryDelay = 100
            defaultMaxRetryDelay = 2_000
        }
        database =
            Database.connect(pool, databaseConfig = config)
                .also { update(it.connector.invoke().connection as Connection) }
    }

    fun close() {
        dataSource?.close()
        dataSource = null
        database = null
    }

    fun <T> writeTransaction(statement: Transaction.() -> T): T =
        writeLock.withLock {
            transaction(requireDatabase()) { statement() }
        }

    suspend fun <T> writeTransactionAsync(statement: Transaction.() -> T): T =
        withContext(Dispatchers.IO) {
            writeTransaction(statement)
        }

    private fun requireDatabase(): Database =
        checkNotNull(database) { "Database is not connected" }

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
