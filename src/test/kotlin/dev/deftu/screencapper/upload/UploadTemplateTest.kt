package dev.deftu.screencapper.upload

import com.google.gson.JsonParser
import dev.deftu.screencapper.metadata.MetadataKeys
import dev.deftu.screencapper.screenshot.capture.PngEncoder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class UploadTemplateTest {
    private val values = TemplateValues(
        fileName = "2026-11-20_12.00.00.png",
        createdAt = Instant.parse("2026-11-20T12:00:00Z"),
        modifiedAt = Instant.parse("2026-11-20T12:00:01.250Z"),
        uploadedAt = Instant.ofEpochMilli(1_790_000_000_123L),
        uuid = "8f2c1d7e-0000-4000-8000-000000000001",
    )

    @Test
    fun `expand fills every known token`() {
        assertEquals(
            "8f2c1d7e-0000-4000-8000-000000000001|2026-11-20_12.00.00.png|2026-11-20T12:00:00Z|2026-11-20T12:00:01.250Z|1790000000123",
            UploadTemplate.expand("{uuid}|{fileName}|{createdAt}|{modifiedAt}|{timestamp}", values),
        )
    }

    @Test
    fun `expand accepts the ShareX filename spelling`() {
        assertEquals("name=2026-11-20_12.00.00.png", UploadTemplate.expand("name={filename}", values))
    }

    @Test
    fun `expand leaves unknown and response tokens literal`() {
        assertEquals("{random:a|b} {json:id} {input} {}", UploadTemplate.expand("{random:a|b} {json:id} {input} {}", values))
    }

    @Test
    fun `expand keeps the same uuid for every use in one upload`() {
        assertEquals("a-8f2c1d7e-0000-4000-8000-000000000001-8f2c1d7e-0000-4000-8000-000000000001", UploadTemplate.expand("a-{uuid}-{uuid}", values))
    }

    @Test
    fun `render fills response values after the file values`() {
        val response = """{"id":"abc","attachments":[{"url":"https://cdn.example/x.png"}]}"""
        assertEquals(
            "https://host.example/{fileName}/abc/https://cdn.example/x.png".replace("{fileName}", values.fileName),
            UploadTemplate.render("https://host.example/{fileName}/{json:id}/{json:attachments[0].url}", values, response),
        )
    }

    @Test
    fun `render fails when the response has no value at a path`() {
        assertFailsWith<UploadException> { UploadTemplate.render("{json:missing.id}", values, """{"id":"abc"}""") }
    }

    @Test
    fun `fresh values use distinct uuids`() {
        val first = TemplateValues.now("a.png", Instant.EPOCH, Instant.EPOCH)
        val second = TemplateValues.now("a.png", Instant.EPOCH, Instant.EPOCH)
        assertNotEquals(first.uuid, second.uuid)
    }

    private val metadata = mapOf(
        MetadataKeys.TIME to "2026-11-19T23:15:42+01:00",
        MetadataKeys.WORLD to "My World",
        MetadataKeys.DIMENSION to "minecraft:the_nether",
        MetadataKeys.BIOME to "minecraft:cherry_grove",
        MetadataKeys.VERSION to "26.3",
        MetadataKeys.PLAYER to "Steve",
        MetadataKeys.COORDINATES to "1, 64, -3",
    )

    private fun withDetails(details: ShotDetails) = TemplateValues(values.fileName, values.createdAt, values.modifiedAt, values.uploadedAt, values.uuid, details)

    private fun pngWith(text: Map<String, String>): File {
        val file = createTempFile("screencapper-template", ".png").toFile()
        file.deleteOnExit()
        file.outputStream().use { PngEncoder.encode(IntArray(4), 2, 2, 1, null, 1, it, text) }
        return file
    }

    @Test
    fun `screenshot tokens describe when and where the shot was taken`() {
        assertEquals(
            "Steve|My World|the nether|cherry grove|1, 64, -3|26.3|2026-11-19|23:15:42|2026-11-20_12.00.00",
            UploadTemplate.expand("{player}|{world}|{dimension}|{biome}|{coords}|{version}|{date}|{time}|{name}", withDetails(ShotDetails.of(metadata))),
        )
    }

    @Test
    fun `world token falls back to the server name`() {
        val details = ShotDetails.of(mapOf(MetadataKeys.SERVER to "Hypixel", MetadataKeys.SERVER_ADDRESS to "mc.hypixel.net"))
        assertEquals("Hypixel", UploadTemplate.expand("{world}", withDetails(details)))
    }

    @Test
    fun `date and time fall back to the file creation time`() {
        val local = values.createdAt.atZone(ZoneId.systemDefault())
        assertEquals("${local.toLocalDate()} ${DateTimeFormatter.ofPattern("HH:mm:ss").format(local)}", UploadTemplate.expand("{date} {time}", values))
    }

    @Test
    fun `screenshot tokens read the png metadata only when the uploader keeps it`() {
        val file = pngWith(metadata)
        assertEquals("Steve|My World|1, 64, -3|26.3", UploadTemplate.expand("{player}|{world}|{coords}|{version}", TemplateValues.of(file, "shot.png", keepMetadata = true)))
        assertEquals("|||", UploadTemplate.expand("{player}|{world}|{coords}|{version}", TemplateValues.of(file, "shot.png", keepMetadata = false)))
        assertEquals("shot", UploadTemplate.expand("{name}", TemplateValues.of(file, "shot.png", keepMetadata = false)))
    }

    @Test
    fun `json templates escape token values`() {
        val details = ShotDetails(player = "a\"b\\c", world = "x\ny")
        val expanded = UploadTemplate.expand("""{"content": "{player} @ {world}", "n": 1, "list": ["{player}"]}""", withDetails(details))
        val json = JsonParser.parseString(expanded).asJsonObject
        assertEquals("a\"b\\c @ x\ny", json.get("content").asString)
        assertEquals(1, json.get("n").asInt)
        assertEquals("a\"b\\c", json.getAsJsonArray("list").single().asString)
    }

    @Test
    fun `plain and broken json templates insert values as they are`() {
        val details = withDetails(ShotDetails(player = "a\"b"))
        assertEquals("name=a\"b", UploadTemplate.expand("name={player}", details))
        assertEquals("""{"a": "a"b"""", UploadTemplate.expand("""{"a": "{player}"""", details))
        assertEquals("""{a: a"b}""", UploadTemplate.expand("""{a: {player}}""", details))
    }

    @Test
    fun `json templates without tokens keep their formatting`() {
        assertEquals("""{ "a": 1 }""", UploadTemplate.expand("""{ "a": 1 }""", values))
    }

    @Test
    fun `file values are percent-encoded in urls but response values are not`() {
        val odd = TemplateValues("100% done & #1.png", values.createdAt, values.modifiedAt, values.uploadedAt, values.uuid)
        assertEquals("https://host.example/up/100%25%20done%20%26%20%231.png?n=100%25%20done%20%26%20%231", UploadTemplate.expandUrl("https://host.example/up/{fileName}?n={name}", odd))
        assertEquals("https://host.example/d/a b", UploadTemplate.renderUrl("{json:url}", odd, """{"url":"https://host.example/d/a b"}"""))
    }
}
