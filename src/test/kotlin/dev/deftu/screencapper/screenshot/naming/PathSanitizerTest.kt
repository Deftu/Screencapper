package dev.deftu.screencapper.screenshot.naming

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathSanitizerTest {
    @Test
    fun removesCharactersIllegalOnAnyPlatform() {
        assertEquals("abcdefgh", PathSanitizer.segment("a<b>c:d\"e|f?g*h"))
        assertEquals("tab", PathSanitizer.segment("t\u0000a\tb"))
    }

    @Test
    fun trimsDotsAndSpacesAtBothEnds() {
        assertEquals("name", PathSanitizer.segment("  name. . "))
        assertEquals("hidden", PathSanitizer.segment("..hidden"))
        assertEquals("", PathSanitizer.segment(".."))
        assertEquals("", PathSanitizer.segment("."))
    }

    @Test
    fun renamesWindowsReservedNames() {
        assertEquals("CON_", PathSanitizer.segment("CON"))
        assertEquals("nul_.txt", PathSanitizer.segment("nul.txt"))
        assertEquals("Com1_", PathSanitizer.segment("Com1"))
        assertEquals("LPT9_", PathSanitizer.segment("LPT9"))
        assertEquals("CONSOLE", PathSanitizer.segment("CONSOLE"))
    }

    @Test
    fun capsSegmentLength() {
        val long = PathSanitizer.segment("a".repeat(500))
        assertEquals(PathSanitizer.MAX_SEGMENT, long.length)
        assertTrue(!PathSanitizer.segment("a".repeat(PathSanitizer.MAX_SEGMENT - 1) + ". x").endsWith("."))
    }

    @Test
    fun isIdempotent() {
        for (raw in listOf("CON", "a:b", " .x. ", "original/x", "a".repeat(300))) {
            val once = PathSanitizer.path(raw)
            assertEquals(once, once?.let(PathSanitizer::path))
        }
    }

    @Test
    fun pathDropsEscapingAndEmptySegments() {
        assertEquals("a/b/c", PathSanitizer.path("/a//./../b\\c"))
        assertEquals("C/Windows/x", PathSanitizer.path("C:\\Windows\\x"))
        assertNull(PathSanitizer.path("../.."))
        assertNull(PathSanitizer.path("a/"))
    }

    @Test
    fun keepOriginalFolderNameIsNotUsableAsAFolder() {
        assertEquals("original_/x", PathSanitizer.path("original/x"))
        assertEquals("Original_/x", PathSanitizer.path("Original/x"))
        assertEquals("original", PathSanitizer.path("original"))
    }

    @Test
    fun panoramasFolderIsNotUsableAtTheTopLevel() {
        assertEquals("panoramas_/x", PathSanitizer.path("panoramas/x"))
        assertEquals("Panoramas_/x", PathSanitizer.path("Panoramas/x"))
        assertEquals("panoramas_/x", PathSanitizer.path("/./panoramas/x"))
        assertEquals("World/panoramas/x", PathSanitizer.path("World/panoramas/x"))
        assertEquals("panoramas", PathSanitizer.path("panoramas"))
        assertEquals("panoramas_/x", PathSanitizer.path("panoramas_/x"))
    }

    @Test
    fun limitsFolderDepth() {
        val deep = PathSanitizer.path((1..30).joinToString("/"))!!
        assertEquals(PathSanitizer.MAX_DEPTH, deep.split('/').size)
        assertTrue(deep.endsWith("/30"))
    }
}
