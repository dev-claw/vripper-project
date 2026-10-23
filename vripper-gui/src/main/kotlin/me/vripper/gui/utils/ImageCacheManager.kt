package me.vripper.gui.utils

import kotlinx.coroutines.*
import me.vripper.utilities.ApplicationProperties
import me.vripper.utilities.hash256
import org.koin.core.component.KoinComponent
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.name


/**
 * Persistent, process-wide cache for encoded image bytes. It stores both remote
 * previews and the full-size images shown by the image viewer as files under the
 * application data directory, so the cache survives application restarts. The
 * oldest entries are evicted once the total size limit is exceeded.
 *
 * Entries are named after a hash of their logical key, so a lookup only needs to
 * recompute the hash; the in-memory index (size + last-access time) is rebuilt by
 * scanning the directory on startup.
 */
object ImageCacheManager : KoinComponent {

    private data class Entry(
        val path: Path,
        val size: Long,
        val date: FileTime
    )

    private const val SUFFIX = ".bin"
    private const val TEMP_SUFFIX = ".tmp"
    private const val MIN_THRESHOLD = 16L * 1024 * 1024

    // Remote previews are small: fail instead of blocking a thumbnail worker
    // indefinitely when the host is unreachable.
    private const val REMOTE_TIMEOUT_MS = 10_000

    private val root: Path = ApplicationProperties.VRIPPER_DIR.resolve("cache")

    // Maximum total size on disk, from the client's viewer settings.
    @Volatile
    private var threshold: Long = diskCacheBytes()

    private fun diskCacheBytes(): Long =
        (WidgetSettings.loadSettings().imageViewerSettings.diskCacheMb.toLong() * 1024 * 1024)
            .coerceAtLeast(MIN_THRESHOLD)

    /**
     * Re-applies the disk cache size limit, evicting immediately if it shrank.
     */
    fun resize(maxBytes: Long) {
        threshold = maxBytes.coerceAtLeast(MIN_THRESHOLD)
        evict()
    }

    // Ordered by last access, so eviction always drops the least recently used
    // entry first.
    private val entries = TreeSet<Entry>(compareBy<Entry> { it.date }.thenBy { it.path })
    private val byPath = HashMap<Path, Entry>()
    private val lock = ReentrantLock()
    private val cacheSize = AtomicLong(0)

    // In-flight loads keyed by cache key, so concurrent requests for the same
    // image share one fetch instead of racing to download and store it twice.
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ByteArray?>>()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        root.createDirectories()
        loadFromDisk()
        evict()
        coroutineScope.launch {
            while (isActive) {
                evict()
                delay(30_000)
            }
        }
    }

    private fun previewKey(postEntityId: Long, url: String): String = "preview:$postEntityId:$url"

    private fun pathOf(key: String): Path = root.resolve(key.hash256() + SUFFIX)

    /**
     * Rebuilds the in-memory index from the files left on disk by previous runs.
     * Stale temp files (from an interrupted write) are removed.
     */
    private fun loadFromDisk() = lock.withLock {
        entries.clear()
        byPath.clear()
        cacheSize.set(0)
        Files.newDirectoryStream(root).use { stream ->
            for (path in stream) {
                if (!path.isRegularFile()) continue
                when {
                    path.name.endsWith(SUFFIX) -> {
                        val size = runCatching { Files.size(path) }.getOrDefault(0L)
                        val date = runCatching { Files.getLastModifiedTime(path) }
                            .getOrElse { FileTime.from(Instant.now()) }
                        val entry = Entry(path, size, date)
                        entries.add(entry)
                        byPath[path] = entry
                        cacheSize.addAndGet(size)
                    }

                    path.name.endsWith(TEMP_SUFFIX) -> runCatching { Files.deleteIfExists(path) }
                }
            }
        }
    }

    /**
     * Returns the encoded bytes cached for [key], or null when not cached. A hit
     * refreshes the entry's recency so it is evicted last. The (potentially
     * large) file is read *outside* the lock so a slow disk read cannot stall
     * eviction or stores happening on other threads.
     */
    fun get(key: String): ByteArray? {
        val path = pathOf(key)
        val known = lock.withLock {
            if (!path.isRegularFile()) {
                byPath.remove(path)?.let { dropLocked(it) }
                false
            } else {
                touchLocked(path)
                true
            }
        }
        if (!known) return null
        return runCatching { Files.readAllBytes(path) }.getOrNull()
    }

    /**
     * Stores [bytes] under [key], replacing any previous value. The write is
     * atomic so an interrupted store cannot leave a half-written entry.
     */
    fun put(key: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val target = pathOf(key)
        root.createDirectories()
        // Use a unique temp file per store: concurrent stores for the same key
        // (e.g. a hover preview and the viewer loading the same URL) would
        // otherwise race on a shared "<target>.tmp" and one move would fail.
        val temp = Files.createTempFile(root, target.fileName.toString(), TEMP_SUFFIX)
        try {
            Files.write(temp, bytes)
            try {
                Files.move(
                    temp,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            runCatching { Files.deleteIfExists(temp) }
        }
        val now = FileTime.from(Instant.now())
        runCatching { Files.setLastModifiedTime(target, now) }
        lock.withLock {
            byPath[target]?.let {
                entries.remove(it)
                cacheSize.addAndGet(-it.size)
            }
            val entry = Entry(target, bytes.size.toLong(), now)
            entries.add(entry)
            byPath[target] = entry
            cacheSize.addAndGet(bytes.size.toLong())
        }
        evict()
    }

    /**
     * Returns the cached bytes for [key], invoking [loader] only on a miss and
     * caching its result. A null load result is not cached and is returned
     * as-is.
     */
    suspend fun load(key: String, loader: suspend () -> ByteArray?): ByteArray? {
        get(key)?.let { return it }
        while (true) {
            // Someone else is already fetching (or has fetched) this key: share
            // their result instead of starting a duplicate download/store.
            inFlight[key]?.let { return it.await() }
            val mine = CompletableDeferred<ByteArray?>()
            if (inFlight.putIfAbsent(key, mine) != null) continue
            try {
                // The value may have been stored while we contended for the map.
                val cached = get(key)
                if (cached != null) {
                    mine.complete(cached)
                    return cached
                }
                val bytes = loader()
                if (bytes != null) put(key, bytes)
                mine.complete(bytes)
                return bytes
            } catch (t: Throwable) {
                mine.completeExceptionally(t)
                throw t
            } finally {
                inFlight.remove(key, mine)
                // Safety net: any waiter still holding this deferred must not
                // hang if we returned without completing it.
                mine.complete(null)
            }
        }
    }

    /**
     * Loads a remote preview image, caching it by post and URL.
     */
    suspend fun load(postEntityId: Long, url: String): ByteArray {
        val key = previewKey(postEntityId, url)
        return load(key) { openStream(url).use { it.readAllBytes() } } ?: ByteArray(0)
    }

    /**
     * Opens a thumbnail URL with bounded connect/read timeouts so an
     * unreachable host cannot pin a thumbnail worker for minutes.
     */
    private fun openStream(url: String): InputStream {
        val connection = URI.create(url).toURL().openConnection()
        if (connection is HttpURLConnection) {
            connection.connectTimeout = REMOTE_TIMEOUT_MS
            connection.readTimeout = REMOTE_TIMEOUT_MS
            connection.instanceFollowRedirects = true
        }
        return try {
            connection.getInputStream()
        } catch (e: Exception) {
            (connection as? HttpURLConnection)?.disconnect()
            throw e
        }
    }

    /**
     * Drops a single cached entry.
     */
    fun remove(key: String) {
        lock.withLock {
            byPath.remove(pathOf(key))?.let { dropLocked(it) }
        }
    }

    /**
     * Marks [path] as most recently used, both on disk (last-modified time) and
     * in the in-memory index.
     */
    private fun touchLocked(path: Path) {
        val now = FileTime.from(Instant.now())
        runCatching { Files.setLastModifiedTime(path, now) }
        byPath[path]?.let { entries.remove(it) }
        val size = byPath[path]?.size ?: runCatching { Files.size(path) }.getOrDefault(0L)
        val entry = Entry(path, size, now)
        entries.add(entry)
        byPath[path] = entry
    }

    private fun dropLocked(entry: Entry) {
        entries.remove(entry)
        byPath.remove(entry.path)
        cacheSize.addAndGet(-entry.size)
        runCatching { Files.deleteIfExists(entry.path) }
    }

    private fun evict() = lock.withLock {
        while (cacheSize.get() > threshold && entries.isNotEmpty()) {
            dropLocked(entries.first())
        }
    }
}
