package dev.deftu.screencapper.screenshot.naming

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class NameTemplateTest {
    private val at = LocalDateTime.of(2026, 11, 20, 9, 5, 7)
    private val values = NameValues(at, world = "New World", dimension = "minecraft:the_nether", player = "Deftu", version = "26.3")

    @Test
    fun defaultTemplateKeepsTheVanillaName() {
        assertEquals("2026-11-20_09.05.07", NameTemplate.expand(NameTemplate.DEFAULT, values))
    }

    @Test
    fun highResTokensKeepTheirMeaning() {
        assertEquals("huge_2026-11-20_09.05.07", NameTemplate.expand("huge_%time%", values))
        assertEquals("huge_New World", NameTemplate.expand("huge_%world%", values))
        assertEquals("huge_no_world", NameTemplate.expand("huge_%world%", values.copy(world = null)))
    }

    @Test
    fun expandsEveryToken() {
        assertEquals(
            "2026-11-20 09.05.07 2026 11 20 the_nether Deftu 26.3",
            NameTemplate.expand("%date% %clock% %year% %month% %day% %dimension% %player% %version%", values),
        )
    }

    @Test
    fun moddedDimensionKeepsItsNamespace() {
        assertEquals("aether_the_aether", NameTemplate.expand("%dimension%", values.copy(dimension = "aether:the_aether")))
    }

    @Test
    fun unknownTokensStayLiteral() {
        assertEquals("%nope%_2026-11-20", NameTemplate.expand("%nope%_%date%", values))
    }

    @Test
    fun countTokenIsLeftForReservation() {
        assertEquals("shot_%count%", NameTemplate.expand("shot_%count%", values))
    }

    @Test
    fun slashMakesSubfolders() {
        assertEquals("New World/2026-11-20_09.05.07", NameTemplate.expand("%world%/%time%", values))
        assertEquals("a/b/2026-11-20_09.05.07", NameTemplate.expand("a\\b/%time%", values))
    }

    @Test
    fun templateCannotWriteIntoThePanoramasFolder() {
        assertEquals("panoramas_/2026-11-20", NameTemplate.expand("panoramas/%date%", values))
        assertEquals("New World/panoramas/2026-11-20", NameTemplate.expand("%world%/panoramas/%date%", values))
    }

    @Test
    fun slashesInsideValuesDoNotMakeFolders() {
        assertEquals("ab/2026-11-20", NameTemplate.expand("%world%/%date%", values.copy(world = "a/b")))
        assertEquals("a..b", NameTemplate.expand("%world%", values.copy(world = "a\\..\\b")))
        assertEquals("2026-11-20_09.05.07", NameTemplate.expand("%world%", values.copy(world = "..")))
    }

    @Test
    fun emptyFileNameFallsBackToTheTime() {
        assertEquals("shots/2026-11-20_09.05.07", NameTemplate.expand("shots/", values))
        assertEquals("2026-11-20_09.05.07", NameTemplate.expand("", values))
        assertEquals("huge_2026-11-20_09.05.07", NameTemplate.expand("???", values, fallback = "huge_%time%"))
    }

    @Test
    fun templatesCannotEscapeTheFolder() {
        assertEquals("etc/2026-11-20", NameTemplate.expand("/../../etc/%date%", values))
        assertEquals("C/x", NameTemplate.expand("C:/x", values))
    }
}
