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

    var revision by mutableIntStateOf(0)
        private set

    private val file: File
        get() = File(FabricLoader.getInstance().configDir.toFile(), "screencapper/index.json")

    fun peek(id: String): Map<String, String>? {
        if (!loaded) {
            requestLoad()
            return null
        }
        return synchronized(lock) { entries[id] }
    }

    fun fields(id: String): Map<String, String>? {
        ensureLoaded()
        return synchronized(lock) { entries[id] }
    }

    fun record(id: String, file: File) {
        worker.execute {
            ensureLoaded()
            put(id, read(file))
            save()
            changed()
        }
    }

    fun forget(id: String) {
        worker.execute {
            ensureLoaded()
            val removed = synchronized(lock) { entries.remove(id) != null }
            if (removed) {
                save()
                changed()
            }
        }
    }

    fun move(from: String, to: String) {
        worker.execute {
            ensureLoaded()
            val moved = synchronized(lock) {
                val fields = entries.remove(from) ?: return@synchronized false
                entries[to] = fields
                true
            }
            if (moved) {
                save()
                changed()
            }
        }
    }

    fun backfill(directory: File, files: Map<String, File>) {
        worker.execute {
            ensureLoaded()
            val missing = synchronized(lock) { files.filterKeys { it !in entries }.toList() }
            val gone = synchronized(lock) { entries.keys.toList() }.filter { it !in files && !File(directory, it).exists() }
            if (missing.isEmpty() && gone.isEmpty()) return@execute

            synchronized(lock) { gone.forEach(entries::remove) }
            missing.forEachIndexed { index, (id, file) ->
                if (file.isFile) put(id, read(file))
                if ((index + 1) % BACKFILL_PUBLISH_EVERY == 0) changed()
            }
            save()
            changed()
        }
    }

    private fun read(file: File): Map<String, String> =
        if (file.extension.equals("png", ignoreCase = true)) PngText.read(file).filterKeys { it.startsWith(MetadataKeys.PREFIX) } else emptyMap()

    private fun put(id: String, fields: Map<String, String>) {
        synchronized(lock) { entries[id] = fields }
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
