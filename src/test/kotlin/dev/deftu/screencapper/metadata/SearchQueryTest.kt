package dev.deftu.screencapper.metadata

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchQueryTest {
    private val hypixel = SearchEntry(
        "2026-10-14_18.22.01.png",
        mapOf(
            MetadataKeys.SERVER to "Hypixel Network",
            MetadataKeys.SERVER_ADDRESS to "mc.hypixel.net",
            MetadataKeys.DIMENSION to "minecraft:the_nether",
            MetadataKeys.BIOME to "minecraft:cherry_grove",
            MetadataKeys.PLAYER to "Deftu",
            MetadataKeys.VERSION to "1.21.8",
            MetadataKeys.COORDINATES to "12, 64, -300",
            MetadataKeys.CHAT to "<Steve> Hello World\n[Server] see foo:bar now",
        ),
        LocalDate.of(2026, 10, 14),
    )

    private val singleplayer = SearchEntry(
        "2026-09-30_09.00.00.png",
        mapOf(
            MetadataKeys.WORLD to "My Survival World",
            MetadataKeys.DIMENSION to "minecraft:overworld",
            MetadataKeys.BIOME to "minecraft:plains",
            MetadataKeys.VERSION to "26.3",
        ),
        LocalDate.of(2026, 9, 30),
    )

    private val bare = SearchEntry("old.png", emptyMap(), LocalDate.of(2025, 1, 1))

    private fun matches(query: String, entry: SearchEntry) = SearchQuery.parse(query).matches(entry)

    @Test
    fun emptyQueryMatchesEverything() {
        assertTrue(SearchQuery.parse("   ").isEmpty)
        assertTrue(matches("", bare))
    }

    @Test
    fun serverMatchesNameOrAddress() {
        assertTrue(matches("server:hypixel", hypixel))
        assertTrue(matches("server:mc.hypixel", hypixel))
        assertFalse(matches("server:hypixel", singleplayer))
        assertFalse(matches("server:hypixel", bare))
    }

    @Test
    fun fieldFiltersAreCaseInsensitive() {
        assertTrue(matches("BIOME:Cherry", hypixel))
        assertTrue(matches("Dimension:NETHER", hypixel))
        assertTrue(matches("world:survival", singleplayer))
        assertTrue(matches("player:deftu version:1.21", hypixel))
        assertFalse(matches("player:deftu", singleplayer))
        assertFalse(matches("world:survival", hypixel))
    }

    @Test
    fun beforeAndAfterUseWholePeriods() {
        assertTrue(matches("before:2026-10", singleplayer))
        assertFalse(matches("before:2026-10", hypixel))
        assertTrue(matches("after:2026-09", hypixel))
        assertFalse(matches("after:2026-09", singleplayer))
        assertTrue(matches("after:2026-10-13", hypixel))
        assertFalse(matches("after:2026-10-14", hypixel))
        assertTrue(matches("before:2026-10-15", hypixel))
        assertFalse(matches("before:2026-10-14", hypixel))
        assertTrue(matches("before:2026", bare))
        assertFalse(matches("after:2025", bare))
        assertTrue(matches("after:2024 before:2026", bare))
    }

    @Test
    fun invalidDatesBecomeFreeWords() {
        assertFalse(matches("before:2026-13", singleplayer))
        assertFalse(matches("after:soon", hypixel))
    }

    @Test
    fun hasFilters() {
        assertTrue(matches("has:coords", hypixel))
        assertTrue(matches("has:chat", hypixel))
        assertFalse(matches("has:coords", singleplayer))
        assertFalse(matches("has:chat", bare))
    }

    @Test
    fun quotedPhrasesMatchAsOnePiece() {
        assertTrue(matches("\"hello world\"", hypixel))
        assertFalse(matches("\"world hello\"", hypixel))
        assertTrue(matches("world hello", hypixel))
        assertTrue(matches("world:\"my survival\"", singleplayer))
        assertFalse(matches("world:\"survival my\"", singleplayer))
        assertTrue(matches("\"survival world", singleplayer))
    }

    @Test
    fun freeWordsSearchNameFieldsAndChat() {
        assertTrue(matches("2026-10-14", hypixel))
        assertTrue(matches("steve", hypixel))
        assertTrue(matches("nether hypixel", hypixel))
        assertFalse(matches("nether plains", hypixel))
        assertTrue(matches("PLAINS", singleplayer))
    }

    @Test
    fun unknownFiltersAreFreeWords() {
        assertTrue(matches("foo:bar", hypixel))
        assertFalse(matches("foo:bar", singleplayer))
        assertFalse(matches("server:", hypixel))
        assertFalse(matches("has:wings", hypixel))
        assertFalse(matches("server:", singleplayer))
    }
}
