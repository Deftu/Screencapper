package dev.deftu.screencapper.metadata

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.deftu.screencapper.MainThread
import dev.deftu.screencapper.Screencapper
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

/**
 * Each screenshot's metadata fields by file name, kept in `config/screencapper/index.json` so searching never has to
 * open the images.
 *
 * Loading, updates and saves all run in order on one background thread. Files the index doesn't know yet are read from
 * their PNG text chunks.
 */
object MetadataIndex {
    private const val BACKFILL_PUBLISH_EVERY = 64

    private val gson = GsonBuilder().create()
    private val lock = Any()
    private val entries = HashMap<String, Map<String, String>>()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "Screencapper metadata index").apply { isDaemon = true } }

    @Volatile
    private var loaded = false
    @Volatile
    private var loadQueued = false

    /** Bumps whenever the index changes, so open views re-run their search */
    var revision by mutableIntStateOf(0)
        private set

    private val file: File
        get() = File(FabricLoader.getInstance().configDir.toFile(), "screencapper/index.json")

    /** Fields for [name] without blocking: null until the index has loaded, which this starts in the background */
    fun peek(name: String): Map<String, String>? {
        if (!loaded) {
            requestLoad()
            return null
        }
        return synchronized(lock) { entries[name] }
    }

    /** Fields for [name], loading the index first if needed. Blocks, so keep it off the render thread */
    fun fields(name: String): Map<String, String>? {
        ensureLoaded()
        return synchronized(lock) { entries[name] }
    }

    /** Indexes a freshly saved screenshot from its text chunks */
    fun record(file: File) {
        worker.execute {
            ensureLoaded()
            put(file.name, read(file))
            save()
            changed()
        }
    }

    fun forget(name: String) {
        worker.execute {
            ensureLoaded()
            val removed = synchronized(lock) { entries.remove(name) != null }
            if (removed) {
                save()
                changed()
            }
        }
    }

    /** Reads the text chunks of every file the index hasn't seen, and drops entries whose file is gone */
    fun backfill(files: List<File>) {
        worker.execute {
            ensureLoaded()
            val missing = synchronized(lock) { files.filter { it.name !in entries } }
            val directory = files.firstOrNull()?.parentFile
            val gone = if (directory == null) emptyList() else synchronized(lock) { entries.keys.toList() }.filter { !File(directory, it).exists() }
            if (missing.isEmpty() && gone.isEmpty()) return@execute

            synchronized(lock) { gone.forEach(entries::remove) }
            missing.forEachIndexed { index, file ->
                put(file.name, read(file))
                if ((index + 1) % BACKFILL_PUBLISH_EVERY == 0) changed()
            }
            save()
            changed()
        }
    }

    private fun read(file: File): Map<String, String> =
        if (file.extension.equals("png", ignoreCase = true)) PngText.read(file).filterKeys { it.startsWith(MetadataKeys.PREFIX) } else emptyMap()

    private fun put(name: String, fields: Map<String, String>) {
        synchronized(lock) { entries[name] = fields }
    }

    private fun requestLoad() {
        if (loadQueued) return
        loadQueued = true
        worker.execute {
            ensureLoaded()
            changed()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val file = file
            if (file.isFile) {
                runCatching {
                    val root = JsonParser.parseString(file.readText()).asJsonObject
                    val read = HashMap<String, Map<String, String>>()
                    for ((name, value) in root.entrySet()) {
                        val fields = (value as? JsonObject)?.entrySet()?.mapNotNull { (key, field) ->
                            runCatching { key to field.asString }.getOrNull()
                        }?.toMap() ?: continue
                        read[name] = fields
                    }
                    synchronized(lock) { entries.putAll(read) }
                }.onFailure { Screencapper.LOGGER.warn("Couldn't read the screenshot index, it will be rebuilt", it) }
            }
            loaded = true
        }
    }

    /** Only ever called on [worker], so writes land in order */
    private fun save() {
        val json = synchronized(lock) {
            JsonObject().apply {
                for ((name, fields) in entries) {
                    add(name, JsonObject().apply { fields.forEach(::addProperty) })
                }
            }
        }
        runCatching {
            val target = file
            target.parentFile.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(gson.toJson(json))
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { Screencapper.LOGGER.error("Failed to save the screenshot index", it) }
    }

    private fun changed() {
        MainThread.run { revision++ }
    }
}
