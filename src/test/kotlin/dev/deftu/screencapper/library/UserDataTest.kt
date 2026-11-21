package dev.deftu.screencapper.library

import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserDataTest {
    private val sandbox = Files.createTempDirectory("screencapper-userdata").toFile()

    @AfterTest
    fun cleanUp() {
        sandbox.deleteRecursively()
    }

    @Test
    fun addsAndRemovesTagsIgnoringCaseAndSpacing() {
        val data = UserData()
        assertTrue(data.addTag("a.png", "  Big   Build "))
        assertFalse(data.addTag("a.png", "big build"))
        assertFalse(data.addTag("a.png", "   "))
        assertTrue(data.addTag("a.png", "night"))
        assertEquals(listOf("Big Build", "night"), data.tagsOf("a.png"))

        assertTrue(data.removeTag("a.png", "BIG BUILD"))
        assertFalse(data.removeTag("a.png", "missing"))
        assertEquals(listOf("night"), data.tagsOf("a.png"))
        assertEquals(emptyList(), data.tagsOf("b.png"))
    }

    @Test
    fun reusesTheSpellingOfAnExistingTag() {
        val data = UserData()
        data.addTag("a.png", "Redstone")
        data.addTag("b.png", "redstone")
        data.addTag("c.png", "nether")
        assertEquals(listOf("Redstone"), data.tagsOf("b.png"))
        assertEquals(listOf("nether", "Redstone"), data.allTags())
    }

    @Test
    fun emptyEntriesAreDropped() {
        val data = UserData()
        data.addTag("a.png", "x")
        data.removeTag("a.png", "x")
        data.setNote("b.png", "hello")
        data.setNote("b.png", "  ")
        assertEquals(emptySet(), data.ids())
    }

    @Test
    fun notesAreTrimmed() {
        val data = UserData()
        assertTrue(data.setNote("a.png", "  first line\nsecond  "))
        assertFalse(data.setNote("a.png", "first line\nsecond"))
        assertEquals("first line\nsecond", data.noteOf("a.png"))
        assertEquals("", data.noteOf("b.png"))
    }

    @Test
    fun albumsAreNamedCollections() {
        val data = UserData()
        assertTrue(data.createAlbum("Builds"))
        assertFalse(data.createAlbum("builds"))
        assertFalse(data.createAlbum(" "))
        assertTrue(data.addToAlbum("a.png", "BUILDS"))
        assertTrue(data.addToAlbum("a.png", "Trips"))
        assertFalse(data.addToAlbum("a.png", "trips"))
        assertTrue(data.addToAlbum("b.png", "Builds"))
        assertEquals(listOf("Builds", "Trips"), data.albums)
        assertEquals(listOf("Builds", "Trips"), data.albumsOf("a.png"))
        assertEquals(setOf("a.png", "b.png"), data.inAlbum("builds"))

        assertTrue(data.removeFromAlbum("a.png", "builds"))
        assertFalse(data.removeFromAlbum("a.png", "builds"))
        assertEquals(listOf("Trips"), data.albumsOf("a.png"))
        assertEquals(listOf("Builds", "Trips"), data.albums)
    }

    @Test
    fun renamingAnAlbumKeepsItsScreenshots() {
        val data = UserData()
        data.addToAlbum("a.png", "Builds")
        data.addToAlbum("a.png", "Trips")
        assertFalse(data.renameAlbum("Builds", "trips"))
        assertFalse(data.renameAlbum("Missing", "Other"))
        assertFalse(data.renameAlbum("Builds", ""))
        assertTrue(data.renameAlbum("builds", "Megabuilds"))
        assertTrue(data.renameAlbum("Megabuilds", "MegaBuilds"))
        assertEquals(listOf("MegaBuilds", "Trips"), data.albums)
        assertEquals(listOf("MegaBuilds", "Trips"), data.albumsOf("a.png"))
    }

    @Test
    fun deletingAnAlbumOnlyDropsMembership() {
        val data = UserData()
        data.addToAlbum("a.png", "Builds")
        data.addTag("a.png", "keep")
        data.addToAlbum("b.png", "Builds")
        assertTrue(data.deleteAlbum("builds"))
        assertFalse(data.deleteAlbum("builds"))
        assertEquals(emptyList(), data.albums)
        assertEquals(emptyList(), data.albumsOf("a.png"))
        assertEquals(listOf("keep"), data.tagsOf("a.png"))
        assertEquals(setOf("a.png"), data.ids())
    }

    @Test
    fun moveCarriesEverythingAndMerges() {
        val data = UserData()
        data.addTag("a.png", "one")
        data.addToAlbum("a.png", "Builds")
        data.setNote("a.png", "first")
        data.addTag("World/a.png", "two")
        assertTrue(data.move("a.png", "World/a.png"))
        assertFalse(data.move("a.png", "World/a.png"))
        assertEquals(listOf("two", "one"), data.tagsOf("World/a.png"))
        assertEquals(listOf("Builds"), data.albumsOf("World/a.png"))
        assertEquals("first", data.noteOf("World/a.png"))
        assertEquals(setOf("World/a.png"), data.ids())
    }

    @Test
    fun forgetAndRetainRemoveEntries() {
        val data = UserData()
        data.addTag("a.png", "x")
        data.addTag("b.png", "x")
        data.addTag("c.png", "x")
        data.createAlbum("Empty")
        assertTrue(data.forget("a.png"))
        assertFalse(data.forget("a.png"))
        assertTrue(data.retainAll { it == "b.png" })
        assertFalse(data.retainAll { it == "b.png" })
        assertEquals(setOf("b.png"), data.ids())
        assertEquals(listOf("Empty"), data.albums)
    }

    @Test
    fun jsonRoundTrip() {
        val data = UserData()
        data.createAlbum("Empty")
        data.addToAlbum("World/a.png", "Builds")
        data.addTag("World/a.png", "night")
        data.addTag("World/a.png", "Big Build")
        data.setNote("World/a.png", "line one\n\"quoted\" line two")
        data.setNote("b.png", "just a note")

        val text = data.toJson().toString()
        val read = UserData.fromJson(JsonParser.parseString(text).asJsonObject)
        assertEquals(listOf("Empty", "Builds"), read.albums)
        assertEquals(listOf("night", "Big Build"), read.tagsOf("World/a.png"))
        assertEquals(listOf("Builds"), read.albumsOf("World/a.png"))
        assertEquals("line one\n\"quoted\" line two", read.noteOf("World/a.png"))
        assertEquals("just a note", read.noteOf("b.png"))
        assertEquals(setOf("World/a.png", "b.png"), read.ids())
    }

    @Test
    fun readingSkipsBrokenEntriesAndAddsMissingAlbums() {
        val json = """
            {
              "albums": ["Builds", 5, "builds"],
              "screenshots": {
                "a.png": { "tags": ["ok", ["bad"]], "albums": ["Trips"], "note": "hi" },
                "b.png": "nonsense",
                "c.png": { "tags": [] }
              }
            }
        """.trimIndent()
        val read = UserData.fromJson(JsonParser.parseString(json).asJsonObject)
        assertEquals(listOf("Builds", "Trips"), read.albums)
        assertEquals(listOf("ok"), read.tagsOf("a.png"))
        assertEquals("hi", read.noteOf("a.png"))
        assertEquals(setOf("a.png"), read.ids())
    }

    @Test
    fun writeIsAtomicAndReadBack() {
        val file = File(sandbox, "screencapper/library.json")
        val data = UserData()
        data.addTag("a.png", "x")
        data.write(file)
        assertTrue(file.isFile)
        assertFalse(File(file.parentFile, "library.json.tmp").exists())
        assertEquals(listOf("x"), UserData.read(file).tagsOf("a.png"))
        assertEquals(emptySet(), UserData.read(File(sandbox, "missing.json")).ids())
    }
}
