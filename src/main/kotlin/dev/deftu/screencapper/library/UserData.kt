package dev.deftu.screencapper.library

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

class UserData {
    private class Entry {
        val tags = mutableListOf<String>()
        val albums = mutableListOf<String>()
        var note = ""

        val isEmpty: Boolean get() = tags.isEmpty() && albums.isEmpty() && note.isEmpty()
    }

    private val albumNames = mutableListOf<String>()
    private val entries = LinkedHashMap<String, Entry>()

    val albums: List<String> get() = albumNames.toList()

    fun ids(): Set<String> = entries.keys.toSet()

    fun tagsOf(id: String): List<String> = entries[id]?.tags?.toList().orEmpty()

    fun albumsOf(id: String): List<String> = entries[id]?.albums?.toList().orEmpty()

    fun noteOf(id: String): String = entries[id]?.note.orEmpty()

    fun allTags(): List<String> =
        entries.values.flatMap { it.tags }.distinctBy(::key).sortedBy(::key)

    fun inAlbum(album: String): Set<String> {
        val name = findAlbum(album) ?: return emptySet()
        return entries.filterValues { name in it.albums }.keys.toSet()
    }

    fun addTag(id: String, tag: String): Boolean {
        val clean = clean(tag)
        if (clean.isEmpty()) return false
        val existing = entries[id]
        if (existing != null && existing.tags.any { key(it) == key(clean) }) return false
        val spelling = entries.values.asSequence().flatMap { it.tags }.firstOrNull { key(it) == key(clean) } ?: clean
        entry(id).tags += spelling
        return true
    }

    fun removeTag(id: String, tag: String): Boolean {
        val entry = entries[id] ?: return false
        val removed = entry.tags.removeAll { key(it) == key(clean(tag)) }
        dropIfEmpty(id)
        return removed
    }

    fun setNote(id: String, note: String): Boolean {
        val clean = note.trim()
        if (noteOf(id) == clean) return false
        entry(id).note = clean
        dropIfEmpty(id)
        return true
    }

    fun createAlbum(name: String): Boolean {
        val clean = clean(name)
        if (clean.isEmpty() || findAlbum(clean) != null) return false
        albumNames += clean
        return true
    }

    fun renameAlbum(from: String, to: String): Boolean {
        val old = findAlbum(from) ?: return false
        val clean = clean(to)
        if (clean.isEmpty() || clean == old) return false
        if (findAlbum(clean).let { it != null && it != old }) return false
        albumNames[albumNames.indexOf(old)] = clean
        for (entry in entries.values) {
            val at = entry.albums.indexOf(old)
            if (at >= 0) entry.albums[at] = clean
        }
        return true
    }

    fun deleteAlbum(name: String): Boolean {
        val album = findAlbum(name) ?: return false
        albumNames.remove(album)
        for (id in entries.keys.toList()) {
            entries[id]?.albums?.remove(album)
            dropIfEmpty(id)
        }
        return true
    }

    fun addToAlbum(id: String, album: String): Boolean {
        createAlbum(album)
        val name = findAlbum(album) ?: return false
        val entry = entry(id)
        if (name in entry.albums) return false
        entry.albums += name
        return true
    }

    fun removeFromAlbum(id: String, album: String): Boolean {
        val name = findAlbum(album) ?: return false
        val removed = entries[id]?.albums?.remove(name) == true
        dropIfEmpty(id)
        return removed
    }

    fun move(from: String, to: String): Boolean {
        if (from == to) return false
        val moved = entries.remove(from) ?: return false
        val target = entry(to)
        for (tag in moved.tags) if (target.tags.none { key(it) == key(tag) }) target.tags += tag
        for (album in moved.albums) if (album !in target.albums) target.albums += album
        if (moved.note.isNotEmpty()) target.note = if (target.note.isEmpty()) moved.note else "${target.note}\n${moved.note}"
        return true
    }

    fun forget(id: String): Boolean = entries.remove(id) != null

    fun retainAll(keep: (String) -> Boolean): Boolean = entries.keys.retainAll(keep)

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("version", VERSION)
        add("albums", strings(albumNames))
        add("screenshots", JsonObject().apply {
            for ((id, entry) in entries) {
                add(id, JsonObject().apply {
                    if (entry.tags.isNotEmpty()) add("tags", strings(entry.tags))
                    if (entry.albums.isNotEmpty()) add("albums", strings(entry.albums))
                    if (entry.note.isNotEmpty()) addProperty("note", entry.note)
                })
            }
        })
    }

    fun write(file: File) = write(file, toJson())

    private fun entry(id: String): Entry = entries.getOrPut(id, ::Entry)

    private fun dropIfEmpty(id: String) {
        if (entries[id]?.isEmpty == true) entries.remove(id)
    }

    private fun findAlbum(name: String): String? {
        val wanted = key(clean(name))
        return albumNames.firstOrNull { key(it) == wanted }
    }

    companion object {
        private const val VERSION = 1
        private val GSON = GsonBuilder().setPrettyPrinting().create()
        private val WHITESPACE = Regex("\\s+")

        fun clean(name: String): String = name.trim().replace(WHITESPACE, " ")

        private fun key(name: String): String = name.lowercase(Locale.ROOT)

        private fun strings(values: List<String>): JsonArray = JsonArray().apply { values.forEach(::add) }

        private fun stringsOf(element: JsonElement?): List<String> =
            (element as? JsonArray)?.mapNotNull { item -> item.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString }.orEmpty()

        fun fromJson(root: JsonObject): UserData {
            val data = UserData()
            stringsOf(root.get("albums")).forEach(data::createAlbum)
            val shots = root.get("screenshots") as? JsonObject ?: return data
            for ((id, value) in shots.entrySet()) {
                val shot = value as? JsonObject ?: continue
                stringsOf(shot.get("tags")).forEach { data.addTag(id, it) }
                stringsOf(shot.get("albums")).forEach { data.addToAlbum(id, it) }
                shot.get("note")?.takeIf { it.isJsonPrimitive }?.let { data.setNote(id, it.asString) }
            }
            return data
        }

        fun write(file: File, json: JsonObject) {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(GSON.toJson(json))
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }

        fun read(file: File): UserData =
            if (file.isFile) fromJson(JsonParser.parseString(file.readText()).asJsonObject) else UserData()
    }
}
