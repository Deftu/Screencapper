package dev.deftu.screencapper.screenshot

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.deftu.screencapper.Screencapper
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

data class UploadRecord(val uploaderId: String, val uploaderName: String, val url: String, val uploadedAt: Long)

/** Every link a screenshot was uploaded to, kept across restarts in `config/screencapper/uploads.json` */
object UploadHistory {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val lock = Any()
    private val records = HashMap<String, MutableList<UploadRecord>>()
    private var loaded = false
    private var revision = 0L
    private var written = 0L
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "Screencapper upload history").apply { isDaemon = true } }

    private val file: File
        get() = File(FabricLoader.getInstance().configDir.toFile(), "screencapper/uploads.json")

    fun of(screenshot: Screenshot): List<UploadRecord> = synchronized(lock) {
        ensureLoaded()
        records[screenshot.name]?.sortedByDescending { it.uploadedAt }.orEmpty()
    }

    fun latest(screenshot: Screenshot): UploadRecord? = of(screenshot).firstOrNull()

    fun count(): Int = synchronized(lock) {
        ensureLoaded()
        records.values.count { it.isNotEmpty() }
    }

    fun add(screenshot: Screenshot, record: UploadRecord) {
        synchronized(lock) {
            ensureLoaded()
            records.getOrPut(screenshot.name) { mutableListOf() }.add(record)
        }
        saveAsync()
    }

    fun remove(screenshot: Screenshot, record: UploadRecord) {
        synchronized(lock) {
            ensureLoaded()
            records[screenshot.name]?.remove(record)
            if (records[screenshot.name].isNullOrEmpty()) records.remove(screenshot.name)
        }
        saveAsync()
    }

    fun forget(screenshot: Screenshot) {
        val removed = synchronized(lock) {
            ensureLoaded()
            records.remove(screenshot.name) != null
        }
        if (removed) saveAsync()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val file = file
        if (!file.isFile) return
        val root = runCatching { JsonParser.parseString(file.readText()).asJsonObject }.getOrElse {
            Screencapper.LOGGER.error("Failed to read the upload history, keeping a copy at {}", backup(file), it)
            return
        }
        var skipped = 0
        for ((name, entries) in root.entrySet()) {
            val list = (entries as? JsonArray ?: continue).mapNotNull { entry ->
                runCatching {
                    val it = entry.asJsonObject
                    UploadRecord(it.get("uploader").asString, it.get("uploaderName").asString, it.get("url").asString, it.get("at").asLong)
                }.onFailure { skipped++ }.getOrNull()
            }
            if (list.isNotEmpty()) records[name] = list.toMutableList()
        }
        if (skipped > 0) Screencapper.LOGGER.warn("Skipped {} unreadable upload records, keeping a copy at {}", skipped, backup(file))
    }

    private fun backup(file: File): File {
        val copy = File(file.parentFile, "${file.name}.bak")
        runCatching { Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        return copy
    }

    private fun saveAsync() {
        val (version, json) = synchronized(lock) {
            ++revision to JsonObject().apply {
                for ((name, list) in records) {
                    add(name, JsonArray().apply {
                        list.forEach { record ->
                            add(JsonObject().apply {
                                addProperty("uploader", record.uploaderId)
                                addProperty("uploaderName", record.uploaderName)
                                addProperty("url", record.url)
                                addProperty("at", record.uploadedAt)
                            })
                        }
                    })
                }
            }
        }
        writer.execute {
            if (version <= written) return@execute
            written = version
            runCatching {
                val target = file
                target.parentFile.mkdirs()
                val temp = File(target.parentFile, "${target.name}.tmp")
                temp.writeText(gson.toJson(json))
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.onFailure { Screencapper.LOGGER.error("Failed to save the upload history", it) }
        }
    }
}
