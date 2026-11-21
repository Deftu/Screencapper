package dev.deftu.screencapper.upload

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscordMessageTest {
    private fun parse(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private fun values(details: ShotDetails = ShotDetails.NONE) = TemplateValues(
        fileName = "2026-11-20_12.00.00.png",
        createdAt = Instant.parse("2026-11-20T12:00:00Z"),
        modifiedAt = Instant.parse("2026-11-20T12:00:00Z"),
        uploadedAt = Instant.EPOCH,
        uuid = "u",
        details = details,
    )

    @Test
    fun `default template never pings anyone`() {
        assertEquals("""{"allowed_mentions":{"parse":[]}}""", DiscordMessage.template(DiscordOptions()))
    }

    @Test
    fun `allowing mentions parses users roles and everyone`() {
        val json = parse(DiscordMessage.template(DiscordOptions(allowMentions = true)))
        assertEquals(listOf("users", "roles", "everyone"), json.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").map { it.asString })
    }

    @Test
    fun `identity text and thread name go to the top level`() {
        val json = parse(DiscordMessage.template(DiscordOptions(content = "Shot by {player}", username = "Camera", avatarUrl = "https://a.example/x.png", threadName = "{name}")))
        assertEquals("Shot by {player}", json.get("content").asString)
        assertEquals("Camera", json.get("username").asString)
        assertEquals("https://a.example/x.png", json.get("avatar_url").asString)
        assertEquals("{name}", json.get("thread_name").asString)
        assertFalse(json.has("embeds"))
        assertFalse(json.has("attachments"))
    }

    @Test
    fun `embed shows the uploaded file through an attachment reference`() {
        val options = DiscordOptions(embed = true, title = "{world}", description = "At {coords}", color = 0x112233, footer = "{version}", timestamp = true, imageInEmbed = true)
        val json = parse(DiscordMessage.template(options))
        val embed = json.getAsJsonArray("embeds").single().asJsonObject
        assertEquals("{world}", embed.get("title").asString)
        assertEquals("At {coords}", embed.get("description").asString)
        assertEquals(0x112233, embed.get("color").asInt)
        assertEquals("{version}", embed.getAsJsonObject("footer").get("text").asString)
        assertEquals("{createdAt}", embed.get("timestamp").asString)
        assertEquals("attachment://{fileName}", embed.getAsJsonObject("image").get("url").asString)
        val attachment = json.getAsJsonArray("attachments").single().asJsonObject
        assertEquals(0, attachment.get("id").asInt)
        assertEquals("{fileName}", attachment.get("filename").asString)
    }

    @Test
    fun `embed without the image leaves the file as a plain attachment`() {
        val json = parse(DiscordMessage.template(DiscordOptions(embed = true, title = "t", timestamp = false, imageInEmbed = false)))
        val embed = json.getAsJsonArray("embeds").single().asJsonObject
        assertFalse(embed.has("image"))
        assertFalse(embed.has("timestamp"))
        assertFalse(json.has("attachments"))
    }

    @Test
    fun `silent and hidden previews set the documented flags`() {
        assertEquals(4096, parse(DiscordMessage.template(DiscordOptions(silent = true))).get("flags").asInt)
        assertEquals(4100, parse(DiscordMessage.template(DiscordOptions(silent = true, hideLinkPreviews = true))).get("flags").asInt)
        assertEquals(4096, parse(DiscordMessage.template(DiscordOptions(embed = true, silent = true, hideLinkPreviews = true))).get("flags").asInt)
        assertFalse(parse(DiscordMessage.template(DiscordOptions())).has("flags"))
    }

    @Test
    fun `expanded payload escapes token values`() {
        val details = ShotDetails(player = "Ste\"ve\\", world = "line\nbreak")
        val template = DiscordMessage.template(DiscordOptions(content = "{player} in {world}", embed = true, title = "{player}"))
        val json = parse(UploadTemplate.expandField(DiscordMessage.FIELD, template, values(details)))
        assertEquals("Ste\"ve\\ in line\nbreak", json.get("content").asString)
        assertEquals("Ste\"ve\\", json.getAsJsonArray("embeds").single().asJsonObject.get("title").asString)
        assertEquals("attachment://2026-11-20_12.00.00.png", json.getAsJsonArray("embeds").single().asJsonObject.getAsJsonObject("image").get("url").asString)
        assertEquals("2026-11-20_12.00.00.png", json.getAsJsonArray("attachments").single().asJsonObject.get("filename").asString)
    }

    @Test
    fun `stripped metadata empties the tokens and drops what expanded to nothing`() {
        val template = DiscordMessage.template(DiscordOptions(content = "{player}", username = "{player}", embed = true, title = "{world}", imageInEmbed = false, timestamp = false))
        assertEquals("""{"allowed_mentions":{"parse":[]}}""", UploadTemplate.expandField(DiscordMessage.FIELD, template, values()))
    }

    @Test
    fun `tidy clamps every field to discord limits`() {
        val payload = JsonObject().apply {
            addProperty("content", "a".repeat(2500))
            addProperty("username", "b".repeat(100))
            addProperty("thread_name", "t".repeat(150))
            add("embeds", JsonParser.parseString("""[{"title":"${"c".repeat(300)}","description":"${"d".repeat(5000)}","footer":{"text":"${"e".repeat(2100)}"}}]"""))
        }.toString()
        val json = parse(DiscordMessage.tidy(payload))
        val content = json.get("content").asString
        assertEquals(2000, content.length)
        assertTrue(content.endsWith("…"))
        assertEquals(80, json.get("username").asString.length)
        assertEquals(100, json.get("thread_name").asString.length)
        val embed = json.getAsJsonArray("embeds").single().asJsonObject
        val title = embed.get("title").asString.length
        val description = embed.get("description").asString.length
        val footer = embed.getAsJsonObject("footer").get("text").asString.length
        assertEquals(256, title)
        assertTrue(description <= 4096)
        assertTrue(footer <= 2048)
        assertTrue(title + description + footer <= 6000)
    }

    @Test
    fun `tidy keeps emoji whole when cutting`() {
        val payload = JsonObject().apply { addProperty("content", "a".repeat(1998) + "📸" + "z".repeat(10)) }.toString()
        val content = parse(DiscordMessage.tidy(payload)).get("content").asString
        assertTrue(content.length <= 2000)
        assertFalse(content.dropLast(1).last().isHighSurrogate())
    }

    @Test
    fun `tidy drops a bot name discord rejects`() {
        assertNull(parse(DiscordMessage.tidy("""{"username":"Discord Fan"}""")).get("username"))
        assertNull(parse(DiscordMessage.tidy("""{"username":"clydebot"}""")).get("username"))
    }

    @Test
    fun `tidy leaves text that is not a json object alone`() {
        assertEquals("not json {", DiscordMessage.tidy("not json {"))
    }

    @Test
    fun `options discord would reject are reported`() {
        assertNull(DiscordMessage.problem(DiscordOptions()))
        assertNull(DiscordMessage.problem(DiscordOptions(avatarUrl = "https://a.example/x.png", threadId = "123")))
        assertNotNull(DiscordMessage.problem(DiscordOptions(threadName = "Shots", threadId = "123")))
        assertNotNull(DiscordMessage.problem(DiscordOptions(threadId = "12a")))
        assertNotNull(DiscordMessage.problem(DiscordOptions(avatarUrl = "avatar.png")))
        assertNotNull(DiscordMessage.problem(DiscordOptions(username = "My Discord Bot")))
    }

    @Test
    fun `options survive the json round trip`() {
        val options = DiscordOptions("c", "u", "https://a.example", true, "t", "d", 0x123456, "f", false, false, true, true, true, "thread", "42")
        assertEquals(options, DiscordOptions.fromJson(options.toJson()))
        assertEquals(DiscordOptions(), DiscordOptions.fromJson(JsonObject()))
    }
}
