package me.vripper.gui.components.fragments

import javafx.scene.image.Image
import javafx.scene.image.WritableImage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import me.vripper.gui.components.fragments.ImageLoadPipeline.Companion.RESIZE_RELOAD_RATIO
import me.vripper.gui.event.GuiEventBus
import me.vripper.gui.utils.AppEndpointManager
import me.vripper.gui.utils.ImageCacheManager
import me.vripper.gui.utils.ImageDecodeUtils
import me.vripper.gui.utils.WidgetSettings
import me.vripper.utilities.LoggerDelegate
import kotlin.math.max
import kotlin.math.min

/**
 * Owns the encoded-bytes fetch, decoded-frame cache and speculative preload
 * machinery of the photo viewer. Navigation/zoom orchestration lives in the
 * fragment; this class has no UI dependency beyond the shared placeholder image
 * and the [currentIndex]/[requestToken] callbacks it uses to drop stale work.
 *
 * Threading: all methods are safe to call from the FX thread; the suspending
 * load/decode functions run on IO dispatchers and only touch their own,
 * internally synchronised state.
 */
internal class ImageLoadPipeline(
    private val sources: List<ImageSource>,
    viewerSettings: WidgetSettings.ImageViewerSettings,
    private val currentIndex: () -> Int,
    private val requestToken: () -> Int
) {

    private val logger by LoggerDelegate()

    // 1x1 fully transparent image used as a sentinel for "no image available".
    // A WritableImage starts out transparent, so no encoded PNG is needed.
    val missingPlaceholder: Image = WritableImage(1, 1)

    private val decodedCache: DecodedImageCache

    private val bytePreloadRadius = viewerSettings.bytePreloadRadius

    init {
        val configured = viewerSettings.decodedCacheMb.coerceIn(64, 4096).toLong() * 1024 * 1024
        val maxHeap = Runtime.getRuntime().maxMemory()
        val budget = if (maxHeap == Long.MAX_VALUE) configured else min(configured, maxHeap / 4)
        decodedCache = DecodedImageCache(budget)
    }

    // Limits how many background downloads/decodes run at once so a wide preload
    // cannot spike CPU or hammer the endpoint.
    private val bytePreloadSemaphore = Semaphore(viewerSettings.bytePreloadConcurrency.coerceIn(1, 8))
    private val decodePreloadSemaphore =
        Semaphore(viewerSettings.decodePreloadConcurrency.coerceIn(1, 4))

    // Caps how many interactive bitmap decodes (navigation + deep zoom) run at
    // the same time. Preload decodes use a separate limit so they never delay
    // the current image.
    private val decodeSemaphore = Semaphore(DECODE_CONCURRENCY)

    // One lock per image index, so a speculative preload and a navigation to the
    // same neighbour share a single decode instead of decoding it twice.
    private val decodeLocks = HashMap<Int, Mutex>()
    private val decodeLocksLock = Any()

    // Intrinsic (real) resolution of each source image, read from the encoded
    // header on a background thread while loading.
    private val sourceSizes = HashMap<Int, Pair<Int, Int>>()

    // Encoded format and byte size per image, read from the data while loading.
    data class EncodedInfo(val format: String, val byteSize: Long)

    private val encodedInfos = HashMap<Int, EncodedInfo>()

    // Scope for speculative warm-up work. Kept alive across navigation: stale
    // jobs bail out via [requestToken], while downloads already in flight are
    // allowed to finish (cancelling them would abort the server-streaming gRPC
    // call mid-transfer). Its job is stable so reload/resize can drop the queued
    // preloads with cancelChildren() instead of recreating the scope.
    private val preloadJob = SupervisorJob()
    private val preloadScope = CoroutineScope(preloadJob + Dispatchers.IO)

    // Set while the user is panning/dragging, so speculative decodes and
    // downloads are kept off the CPU/IO while the render thread needs them.
    @Volatile
    var preloadSuspended = false
        private set

    // Maximum size (device pixels) an image will be displayed at. Written on the
    // FX thread, read while decoding on a background thread.
    @Volatile
    var decodeWidth = 0.0
        private set

    @Volatile
    var decodeHeight = 0.0
        private set

    // Decode target the current cache contents were built for. When the window
    // grows beyond this, cached images are re-decoded at the larger size.
    private var builtForWidth = 0.0
    private var builtForHeight = 0.0

    // ---- decoded-frame cache accessors -------------------------------------

    fun cachedImage(i: Int): Image? = decodedCache.get(i)

    fun cachedScale(i: Int): Double = decodedCache.scaleOf(i)

    fun containsCached(i: Int): Boolean = decodedCache.contains(i)

    fun pin(i: Int) = decodedCache.pin(i)

    fun removeCached(i: Int) = decodedCache.remove(i)

    fun clearCache() = decodedCache.clear()

    fun decodedCacheStats(): DecodedImageCache.Stats = decodedCache.stats()

    fun sourceSize(i: Int): Pair<Int, Int>? = synchronized(sourceSizes) { sourceSizes[i] }

    fun encodedInfo(i: Int): EncodedInfo? = synchronized(encodedInfos) { encodedInfos[i] }

    // ---- decode target / resize --------------------------------------------

    /**
     * Ratchets the decode target up to at least [targetWidth] x [targetHeight]
     * and reports whether the cached frames were built for a noticeably smaller
     * target (and so should be rebuilt).
     */
    fun growDecodeTarget(targetWidth: Double, targetHeight: Double): Boolean {
        if (targetWidth > decodeWidth) decodeWidth = targetWidth
        if (targetHeight > decodeHeight) decodeHeight = targetHeight
        return targetOutgrewCache()
    }

    /** Marks the cache as built for the current (ratcheted) decode target. */
    fun markBuiltForCurrentTarget() {
        builtForWidth = decodeWidth
        builtForHeight = decodeHeight
    }

    /** Marks the cache as built for a fixed default size (pre-layout startup). */
    fun markBuiltForDefault(size: Double) {
        builtForWidth = size
        builtForHeight = size
    }

    /**
     * Whether the (ratcheted) decode target now exceeds the size the cached
     * frames were built for by more than [RESIZE_RELOAD_RATIO].
     */
    fun targetOutgrewCache(): Boolean =
        builtForWidth > 0.0 && (decodeWidth > builtForWidth * RESIZE_RELOAD_RATIO ||
                decodeHeight > builtForHeight * RESIZE_RELOAD_RATIO)

    // ---- preload control ----------------------------------------------------

    fun suspendPreloads() {
        if (preloadSuspended) return
        // Let in-flight downloads finish instead of resetting their gRPC
        // streams; speculative decodes check this flag and are skipped.
        preloadSuspended = true
    }

    fun resumePreloads(current: Int) {
        if (!preloadSuspended) return
        preloadSuspended = false
        preloadAround(current)
    }

    fun cancelPreloads() = preloadJob.cancelChildren()

    fun dispose() {
        preloadScope.cancel()
        decodedCache.clear()
        synchronized(sourceSizes) { sourceSizes.clear() }
        synchronized(encodedInfos) { encodedInfos.clear() }
    }

    // ---- loading ------------------------------------------------------------

    suspend fun loadImageFor(
        i: Int,
        speculative: Boolean = false,
        minScale: Double = 0.0
    ): Image {
        // Reuse a cached frame only when it already has at least the detail we
        // need. Otherwise (e.g. navigating while zoomed into a neighbour that was
        // only preloaded at viewport resolution) we decode at the required scale
        // straight away instead of showing a blurred frame that is replaced a
        // moment later.
        val cachedScale = decodedCache.scaleOf(i)
        val cached = decodedCache.get(i)
        // A cached placeholder means "not downloaded yet" at the time it was
        // checked, not a usable frame: retry instead of showing it forever.
        if (cached != null && cached !== missingPlaceholder && cachedScale >= minScale * 0.99) {
            return cached
        }

        // Serialise decodes of the same index. A speculative preload and a
        // navigation to the same neighbour would otherwise both decode the
        // (expensive) image; the second caller now re-checks the cache after
        // waiting and reuses the first result.
        return decodeLockFor(i).withLock {
            val freshScale = decodedCache.scaleOf(i)
            val fresh = decodedCache.get(i)
            if (fresh != null && fresh !== missingPlaceholder && freshScale >= minScale * 0.99) {
                fresh
            } else {
                decodeImageFor(i, speculative, minScale)
            }
        }
    }

    /**
     * Decodes a single image at the given scale (fraction of its real
     * resolution) for deep-zoom display. The result is not put in the shared
     * cache; the caller owns it.
     */
    suspend fun decodeAtScale(i: Int, scale: Double): Image {
        try {
            val bytes = obtainBytes(i)
            if (bytes == null || bytes.isEmpty()) return missingPlaceholder
            val sourceSize = ImageDecodeUtils.readSourceSize(bytes)
            synchronized(sourceSizes) {
                if (sourceSize != null) sourceSizes[i] = sourceSize
            }
            val (width, height) =
                if (sourceSize != null && sourceSize.first > 0 && sourceSize.second > 0) {
                    (sourceSize.first * scale).coerceAtLeast(1.0) to
                            (sourceSize.second * scale).coerceAtLeast(1.0)
                } else {
                    ImageDecodeUtils.decodeSize(
                        null,
                        if (decodeWidth > 0.0) decodeWidth else DEFAULT_DECODE_SIZE,
                        if (decodeHeight > 0.0) decodeHeight else DEFAULT_DECODE_SIZE
                    )
                }
            currentCoroutineContext().ensureActive()
            return decodeSemaphore.withPermit {
                // Skip work for jobs that were cancelled while waiting for a
                // permit (e.g. rapid zoom changes).
                currentCoroutineContext().ensureActive()
                ImageDecodeUtils.decodeScaled(bytes, width, height)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to decode image {} at scale {}", i, scale, e)
            return missingPlaceholder
        }
    }

    private fun decodeLockFor(i: Int): Mutex = synchronized(decodeLocksLock) {
        decodeLocks.getOrPut(i) { Mutex() }
    }

    /**
     * The actual (expensive) fetch + decode of one image. Only ever runs once at
     * a time per index; [loadImageFor] deduplicates callers.
     */
    private suspend fun decodeImageFor(
        i: Int,
        speculative: Boolean,
        minScale: Double
    ): Image {
        // Do not start a new speculative download once the user is panning.
        // Skip without caching, so it is retried when preloading resumes.
        if (speculative && preloadSuspended) return missingPlaceholder
        try {
            val data = obtainBytes(i)
            if (data == null || data.isEmpty()) {
                decodedCache.put(i, missingPlaceholder, 1.0)
                return missingPlaceholder
            }
            val sourceSize = ImageDecodeUtils.readSourceSize(data)
            if (sourceSize != null) {
                synchronized(sourceSizes) { sourceSizes[i] = sourceSize }
            }
            val info = EncodedInfo(ImageDecodeUtils.detectFormat(data), data.size.toLong())
            synchronized(encodedInfos) { encodedInfos[i] = info }
            val viewportWidth = if (decodeWidth > 0.0) decodeWidth else DEFAULT_DECODE_SIZE
            val viewportHeight = if (decodeHeight > 0.0) decodeHeight else DEFAULT_DECODE_SIZE
            // Raise the decode target to the current zoom so a zoomed view is sharp
            // from the first frame.
            val zoomWidth = (sourceSize?.first ?: 0) * minScale
            val zoomHeight = (sourceSize?.second ?: 0) * minScale
            val (width, height) = ImageDecodeUtils.decodeSize(
                sourceSize,
                max(viewportWidth, zoomWidth),
                max(viewportHeight, zoomHeight)
            )
            val scale = if (sourceSize != null && sourceSize.first > 0) width / sourceSize.first else 1.0
            // If this job was cancelled (fast scrolling) while it was fetching the
            // bytes, do not spend CPU on the expensive decode.
            currentCoroutineContext().ensureActive()
            // The user may have started panning while this speculative job was
            // fetching: let the download finish (cancelling it would reset the gRPC
            // stream) but skip the expensive decode.
            if (speculative && preloadSuspended) return missingPlaceholder
            // Speculative (preload) decodes are bounded separately and must not take
            // a permit from this semaphore: otherwise a slow decode of a large
            // neighbour would delay the image the user is actually looking at.
            val image = if (speculative) {
                ImageDecodeUtils.decodeScaled(data, width, height)
            } else {
                decodeSemaphore.withPermit {
                    // A job cancelled while queued for a permit must not start
                    // the expensive decode after it finally gets one.
                    currentCoroutineContext().ensureActive()
                    ImageDecodeUtils.decodeScaled(data, width, height)
                }
            }
            decodedCache.put(i, image, scale)
            return image
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed read or decode must not escape as an uncaught coroutine
            // exception (which would leave the viewer empty with a spinning
            // progress bar). Show the same placeholder as an unavailable file;
            // Refresh retries it.
            if (speculative) {
                logger.debug("Failed to preload image {}", i, e)
            } else {
                logger.warn("Failed to load image {}", i, e)
            }
            decodedCache.put(i, missingPlaceholder, 1.0)
            return missingPlaceholder
        }
    }

    /**
     * Returns the encoded bytes for an image.
     *
     * Only bytes streamed over gRPC are put in the persistent
     * [ImageCacheManager], so a deep-zoom re-decode (or reopening the viewer)
     * does not transfer the file over gRPC again. In local mode the full-size
     * file already lives on this machine: caching a second copy would double
     * the disk usage (and could go stale) without any I/O benefit.
     */
    private suspend fun obtainBytes(i: Int): ByteArray? {
        val source = sources[i]
        val read: suspend () -> ByteArray? = { source.readBytes() }
        if (AppEndpointManager.currentAppState() != GuiEventBus.RemoteSession) {
            return read()
        }
        return ImageCacheManager.load(source.cacheKey(), read)
    }

    // ---- preloading ---------------------------------------------------------

    /**
     * Warms up the neighbours of [current] in the background: the immediate
     * neighbours are fully decoded (so prev/next is instant), while a wider
     * window only pre-loads the encoded bytes into [ImageCacheManager] so a
     * later navigation is a local decode instead of a transfer.
     */
    fun preloadAround(current: Int) {
        if (preloadSuspended) return
        if (sources.isEmpty()) return
        val count = sources.size
        // Decode the immediate neighbours first: they are the ones the user is
        // most likely to reach, and they must not queue behind byte downloads.
        preloadDecoded((current + 1) % count)
        preloadDecoded((current - 1 + count) % count)
        val radius = bytePreloadRadius.coerceIn(0, 20)
        for (offset in 2..radius) {
            preloadBytes((current + offset) % count)
            preloadBytes((current - offset + count) % count)
        }
    }

    /**
     * Decodes a neighbour into the decoded-frame cache, bounded by the
     * configured decode-preload concurrency so preloading never serialises the
     * CPU.
     */
    private fun preloadDecoded(i: Int) {
        if (i == currentIndex()) return
        if (decodedCache.contains(i)) return
        val request = requestToken()
        preloadScope.launch {
            decodePreloadSemaphore.withPermit {
                if (request != requestToken()) return@launch
                if (i == currentIndex() || decodedCache.contains(i)) return@launch
                loadImageFor(i, speculative = true)
            }
        }
    }

    /**
     * Fetches and caches only the encoded bytes of a neighbour (no decode),
     * bounded by the configured byte-preload concurrency.
     */
    private fun preloadBytes(i: Int) {
        // In local mode the file is read straight from disk on demand: there is
        // no transfer to pre-warm, so this would only waste I/O.
        if (AppEndpointManager.currentAppState() != GuiEventBus.RemoteSession) return
        if (i == currentIndex()) return
        val request = requestToken()
        preloadScope.launch {
            bytePreloadSemaphore.withPermit {
                if (request != requestToken()) return@launch
                if (i == currentIndex()) return@launch
                try {
                    obtainBytes(i)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Preloading is best-effort: a failed transfer must not
                    // surface as an uncaught coroutine exception.
                    logger.debug("Failed to preload bytes for image {}", i, e)
                }
            }
        }
    }

    companion object {
        const val DEFAULT_DECODE_SIZE = 2048.0
        private const val RESIZE_RELOAD_RATIO = 1.05

        // Maximum number of bitmap decodes running concurrently.
        private const val DECODE_CONCURRENCY = 2
    }
}
