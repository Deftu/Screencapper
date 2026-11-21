package dev.deftu.screencapper.screenshot

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.upload.DeletionRequest
import dev.deftu.screencapper.upload.DeletionTarget
import dev.deftu.screencapper.upload.SecretRefs
import dev.deftu.screencapper.upload.Secrets
import dev.deftu.screencapper.upload.UploaderProfile
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

data class UploadRecord(
    val uploaderId: String,
    val uploaderName: String,
    val url: String,
    val uploadedAt: Long,
    val deletion: DeletionRequest? = null,
) {
    val hasLink: Boolean get() = url.isNotBlank()

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("uploader", uploaderId)
        addProperty("uploaderName", uploaderName)
        addProperty("url", url)
        addProperty("at", uploadedAt)
        deletion?.let { add("deletion", it.toJson()) }
    }

    companion object {
        fun fromJson(json: JsonObject): UploadRecord = UploadRecord(
            json.get("uploader").asString,
            json.get("uploaderName").asString,
            json.get("url").asString,
            json.get("at").asLong,
            (json.get("deletion") as? JsonObject)
                ?.let { runCatching { DeletionRequest.fromJson(it) }.getOrNull() }
                ?.takeIf { it.kind != DeletionKind.NONE },
        )
    }
}

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
        records[screenshot.id]?.sortedByDescending { it.uploadedAt }.orEmpty()
    }

    fun latest(screenshot: Screenshot): UploadRecord? = of(screenshot).firstOrNull()

    fun latestLink(screenshot: Screenshot): UploadRecord? = of(screenshot).firstOrNull { it.hasLink }

    fun find(screenshot: Screenshot, uploadedAt: Long): UploadRecord? = of(screenshot).firstOrNull { it.uploadedAt == uploadedAt }

    fun sharing(screenshot: Screenshot, record: UploadRecord): List<UploadRef> = synchronized(lock) {
        ensureLoaded()
        SharedUploads.others(records, screenshot.id, record)
    }

    fun count(): Int = synchronized(lock) {
        ensureLoaded()
        records.values.count { it.isNotEmpty() }
    }

    fun add(screenshot: Screenshot, record: UploadRecord) {
        synchronized(lock) {
            ensureLoaded()
            records.getOrPut(screenshot.id) { mutableListOf() }.add(record)
        }
        saveAsync()
    }

    fun remove(screenshot: Screenshot, record: UploadRecord) {
        synchronized(lock) {
            ensureLoaded()
            records[screenshot.id]?.remove(record)
            if (records[screenshot.id].isNullOrEmpty()) records.remove(screenshot.id)
        }
        saveAsync()
    }

    fun removeTarget(target: DeletionTarget): Set<String> {
        val affected = synchronized(lock) {
            ensureLoaded()
            val ids = records.filterValues { list -> list.any { it.deletion?.target == target } }.keys.toSet()
            ids.forEach { id ->
                records[id]?.removeAll { it.deletion?.target == target }
                if (records[id].isNullOrEmpty()) records.remove(id)
            }
            ids
        }
        if (affected.isNotEmpty()) saveAsync()
        return affected
    }

    fun forget(screenshot: Screenshot) {
        val removed = synchronized(lock) {
            ensureLoaded()
            records.remove(screenshot.id) != null
        }
        if (removed) saveAsync()
    }

    fun move(from: String, to: String) {
        val moved = synchronized(lock) {
            ensureLoaded()
            val list = records.remove(from) ?: return@synchronized false
            records.getOrPut(to) { mutableListOf() }.addAll(list)
            true
        }
        if (moved) saveAsync()
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
        val profiles = runCatching { ScreencapperConfig.profiles }.getOrDefault(UploaderProfile.BUILT_IN)
        var skipped = 0
        var changed = false
        for ((name, entries) in root.entrySet()) {
            val list = (entries as? JsonArray ?: continue).mapNotNull { entry ->
                runCatching {
                    val record = UploadRecord.fromJson(entry.asJsonObject)
                    val deletion = record.deletion ?: return@runCatching record
                    val loaded = SecretRefs.load(deletion, profiles.firstOrNull { it.id == record.uploaderId }, Secrets.cipher)
                    if (loaded.changed) changed = true
                    record.copy(deletion = loaded.request)
                }.onFailure { skipped++ }.getOrNull()
            }
            if (list.isNotEmpty()) records[name] = list.toMutableList()
        }
        if (skipped > 0) Screencapper.LOGGER.warn("Skipped {} unreadable upload records, keeping a copy at {}", skipped, backup(file))
        if (changed) saveAsync()
    }

    private fun toJson(snapshot: Map<String, List<UploadRecord>>, seal: (DeletionRequest) -> DeletionRequest): JsonObject = JsonObject().apply {
        for ((name, list) in snapshot) {
            add(name, JsonArray().apply {
                list.forEach { record -> add(record.copy(deletion = record.deletion?.let(seal)).toJson()) }
            })
        }
    }

    private fun backup(file: File): File {
        val copy = File(file.parentFile, "${file.name}.bak")
        runCatching { Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        return copy
    }

    private fun saveAsync() {
        val (version, snapshot) = synchronized(lock) {
            ++revision to records.mapValues { it.value.toList() }
        }
        writer.execute {
            if (version <= written) return@execute
            written = version
            runCatching {
                val json = Secrets.sealing("the upload history", { toJson(snapshot) { SecretRefs.seal(it, Secrets.cipher) } }, { toJson(snapshot) { it } })
                val target = file
                target.parentFile.mkdirs()
                val temp = File(target.parentFile, "${target.name}.tmp")
                temp.writeText(gson.toJson(json))
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.onFailure { Screencapper.LOGGER.error("Failed to save the upload history", it) }
        }
    }
}

data class UploadRef(val screenshotId: String, val record: UploadRecord)

object SharedUploads {
    fun others(history: Map<String, List<UploadRecord>>, screenshotId: String, record: UploadRecord): List<UploadRef> {
        val target = record.deletion?.target ?: return emptyList()
        var skipped = false
        val out = mutableListOf<UploadRef>()
        for ((id, list) in history) {
            for (other in list) {
                if (!skipped && id == screenshotId && other == record) {
                    skipped = true
                    continue
                }
                if (other.deletion?.target == target) out += UploadRef(id, other)
            }
        }
        return out
    }

    fun describe(others: List<UploadRef>, screenshotId: String): String? {
        if (others.isEmpty()) return null
        val screenshots = others.map { it.screenshotId }.filter { it != screenshotId }.distinct().size
        return when {
            screenshots == 1 -> "1 other screenshot"
            screenshots > 1 -> "$screenshots other screenshots"
            others.size == 1 -> "another upload of this screenshot"
            else -> "${others.size} other uploads of this screenshot"
        }
    }
}
