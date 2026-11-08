package dev.deftu.screencapper.gui

import androidx.compose.ui.graphics.ImageBitmap
import dev.deftu.screencapper.screenshot.Screenshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

class Thumbnail(val bitmap: ImageBitmap, val width: Int, val height: Int, internal val bytes: Long)

/**
 * Decoded, downscaled screenshots.
 *
 * Decodes run two at a time and are shared between callers asking for the same image, since a full-size screenshot
 * takes tens of megabytes while it's being read. Cached bitmaps are capped by their total size.
 */
object Thumbnails {
    private const val MAX_CACHE_BYTES = 160L * 1024 * 1024

    @OptIn(ExperimentalCoroutinesApi::class)
    private val decoder = Dispatchers.IO.limitedParallelism(2)

    private val cache = LinkedHashMap<String, Thumbnail>(64, 0.75f, true)
    private var cachedBytes = 0L
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Thumbnail?>>()

    private fun key(screenshot: Screenshot, width: Int) = "${screenshot.file.absolutePath}@${screenshot.modified}#$width"

    fun peek(screenshot: Screenshot, width: Int): Thumbnail? = synchronized(cache) { cache[key(screenshot, width)] }

    suspend fun load(screenshot: Screenshot, width: Int): Thumbnail? {
        val key = key(screenshot, width)
        while (true) {
            peek(screenshot, width)?.let { return it }
            if (key in failed) return null

            val mine = CompletableDeferred<Thumbnail?>()
            val existing = inFlight.putIfAbsent(key, mine)
            if (existing != null) {
                try {
                    return existing.await()
                } catch (e: CancellationException) {
                    // The caller that started this decode went away before it ran, so try again as the owner.
                    currentCoroutineContext().ensureActive()
                    continue
                }
            }

            try {
                val thumbnail = withContext(decoder) { decode(screenshot, width) }
                if (thumbnail != null) put(key, thumbnail) else failed += key
                mine.complete(thumbnail)
                return thumbnail
            } catch (e: Throwable) {
                mine.completeExceptionally(e)
                throw e
            } finally {
                inFlight.remove(key, mine)
            }
        }
    }

    private fun put(key: String, thumbnail: Thumbnail) = synchronized(cache) {
        cache.put(key, thumbnail)?.let { cachedBytes -= it.bytes }
        cachedBytes += thumbnail.bytes
        val iterator = cache.values.iterator()
        while (cachedBytes > MAX_CACHE_BYTES && cache.size > 1 && iterator.hasNext()) {
            val eldest = iterator.next()
            if (eldest === thumbnail) continue
            cachedBytes -= eldest.bytes
            iterator.remove()
        }
    }

    private fun decode(screenshot: Screenshot, width: Int): Thumbnail? = runCatching {
        ImageIO.createImageInputStream(screenshot.file).use { stream ->
            val reader = ImageIO.getImageReaders(stream ?: return null).asSequence().firstOrNull() ?: return null
            try {
                reader.input = stream
                val sourceWidth = reader.getWidth(0)
                val sourceHeight = reader.getHeight(0)
                val param = reader.defaultReadParam
                val step = (sourceWidth / width).coerceAtLeast(1)
                if (step > 1) param.setSourceSubsampling(step, step, 0, 0)
                val image = downscale(reader.read(0, param), width)
                Thumbnail(PolyBitmap.fromAwt(image).asComposeImageBitmap(), sourceWidth, sourceHeight, image.width.toLong() * image.height * 4)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

    fun downscale(image: BufferedImage, maxWidth: Int): BufferedImage {
        if (image.width <= maxWidth) return image
        val height = (image.height * (maxWidth.toDouble() / image.width)).toInt().coerceAtLeast(1)
        val out = BufferedImage(maxWidth, height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(image, 0, 0, maxWidth, height, null)
        g.dispose()
        return out
    }
}
