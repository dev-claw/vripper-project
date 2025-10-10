package me.vripper.gui.utils

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import kotlinx.coroutines.*
import me.vripper.utilities.hash256
import org.koin.core.component.KoinComponent
import java.net.URI
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString


object PreviewCacheManager : KoinComponent {

    data class Entry(
        val postEntityId: Long,
        val path: String,
        val size: Int,
        val date: LocalDateTime = LocalDateTime.now()
    ) {
        override fun hashCode(): Int {
            return Objects.hash(postEntityId, path, size)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Entry

            if (postEntityId != other.postEntityId) return false
            if (path != other.path) return false
            if (size != other.size) return false

            return true
        }
    }

    private const val MAX_ENTRIES = 10000
    private const val THRESHOLD = 1024 * 1024L * 200
    private val entries = TreeSet<Entry>(Comparator.comparing { it.date })
    private val fs = Jimfs.newFileSystem(Configuration.unix())
    private val rootPath = Path("/previews")
    private val lock = ReentrantLock()
    private var cacheSize = AtomicLong(0)
    private val coroutineScope = CoroutineScope(SupervisorJob())

    init {
        coroutineScope.launch {
            while (isActive) {
//                println("Cache state check")
//                println("Cache entries = ${entries.size}")
//                println("Cache entries limit = $MAX_ENTRIES")
//                println("Cache size = ${cacheSize.get().formatSI()}")
//                println("Cache size LIMIT = ${THRESHOLD.formatSI()}")

                val entriesDelta = entries.size - MAX_ENTRIES
                if (entriesDelta > 0) {
                    val deleted = mutableListOf<Entry>()
                    (1..entriesDelta).forEach { _ ->
                        val element = lock.withLock {
                            entries.removeFirst()
                        }
                        deleted.add(element)
                    }
                    deleted.forEach {
                        fs.getPath(it.path).deleteIfExists()
                    }
                }

                val sizeDelta = cacheSize.get() - THRESHOLD
                if (sizeDelta > 0) {
                    do {
                        val element = lock.withLock {
                            entries.removeFirst()
                        }
                        cacheSize.addAndGet(element.size * -1L)
                    } while (cacheSize.get() - THRESHOLD > 0)
                }

//                println("Cache state check completed")
                delay(30_000)
            }
        }
    }

    fun load(postEntityId: Long, url: String): ByteArray {
        val fileName = url.hash256()
        val path = fs.getPath(rootPath.resolve(postEntityId.toString()).resolve(fileName).pathString)
        if (Files.exists(path)) {
            return Files.readAllBytes(path)
        }
        path.parent.createDirectories()
        return URI.create(url).toURL().openStream().use { `is` ->
            val bytes = `is`.readAllBytes()
            Files.write(path, bytes)
            cacheSize.addAndGet(bytes.size.toLong())
            val entry = Entry(postEntityId, path.pathString, bytes.size)
            lock.withLock {
                entries.add(entry)
            }
            bytes
        }
    }
}