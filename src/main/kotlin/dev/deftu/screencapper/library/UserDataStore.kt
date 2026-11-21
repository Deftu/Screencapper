package dev.deftu.screencapper.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.deftu.screencapper.MainThread
import dev.deftu.screencapper.Screencapper
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

object UserDataStore {
    private val lock = Any()
    private var data: UserData? = null
    private var version = 0L
    private var written = 0L
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "Screencapper library data").apply { isDaemon = true } }

    var revision by mutableIntStateOf(0)
        private set

    private val file: File
        get() = File(FabricLoader.getInstance().configDir.toFile(), "screencapper/library.json")

    fun albums(): List<String> = read { it.albums }

    fun allTags(): List<String> = read { it.allTags() }

    fun tagsOf(id: String): List<String> = read { it.tagsOf(id) }

    fun albumsOf(id: String): List<String> = read { it.albumsOf(id) }

    fun noteOf(id: String): String = read { it.noteOf(id) }

    fun inAlbum(album: String): Set<String> = read { it.inAlbum(album) }

    fun albumProblem(name: String, current: String? = null): String? {
        val clean = UserData.clean(name)
        return when {
            clean.isEmpty() -> "Enter a name."
            albums().any { it.equals(clean, ignoreCase = true) && !it.equals(current, ignoreCase = true) } -> "An album with that name already exists."
            else -> null
        }
    }

    fun addTag(ids: Collection<String>, tag: String): Boolean = edit { data -> ids.count { data.addTag(it, tag) } > 0 }

    fun removeTag(id: String, tag: String): Boolean = edit { it.removeTag(id, tag) }

    fun setNote(id: String, note: String): Boolean = edit { it.setNote(id, note) }

    fun createAlbum(name: String): Boolean = edit { it.createAlbum(name) }

    fun renameAlbum(from: String, to: String): Boolean = edit { it.renameAlbum(from, to) }

    fun deleteAlbum(name: String): Boolean = edit { it.deleteAlbum(name) }

    fun addToAlbum(ids: Collection<String>, album: String): Boolean =
        edit { data -> (data.createAlbum(album) or (ids.count { data.addToAlbum(it, album) } > 0)) }

    fun removeFromAlbum(id: String, album: String): Boolean = edit { it.removeFromAlbum(id, album) }

    fun move(from: String, to: String) {
        edit { it.move(from, to) }
    }

    fun forget(id: String) {
        edit { it.forget(id) }
    }

    fun clean(directory: File, present: Set<String>) {
        if (present.isEmpty() || !directory.isDirectory) return
        val gone = read { data -> data.ids().filter { it !in present } }.filter { !File(directory, it).exists() }.toSet()
        if (gone.isNotEmpty()) edit { data -> data.retainAll { it !in gone } }
    }

    private fun <T> read(block: (UserData) -> T): T = synchronized(lock) { block(loaded()) }

    private fun edit(block: (UserData) -> Boolean): Boolean {
        val changed = synchronized(lock) { block(loaded()) }
        if (changed) {
            saveAsync()
            MainThread.run { revision++ }
        }
        return changed
    }

    private fun loaded(): UserData {
        data?.let { return it }
        val file = file
        val read = runCatching { UserData.read(file) }.getOrElse {
            Screencapper.LOGGER.error("Failed to read screenshot tags, albums and notes, keeping a copy at {}", backup(file), it)
            UserData()
        }
        data = read
        return read
    }

    private fun backup(file: File): File {
        val copy = File(file.parentFile, "${file.name}.bak")
        runCatching { Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        return copy
    }

    private fun saveAsync() {
        val (next, json) = synchronized(lock) { ++version to loaded().toJson() }
        writer.execute {
            if (next <= written) return@execute
            written = next
            runCatching { UserData.write(file, json) }
                .onFailure { Screencapper.LOGGER.error("Failed to save screenshot tags, albums and notes", it) }
        }
    }
}
