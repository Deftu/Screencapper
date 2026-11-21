package dev.deftu.screencapper.upload

import com.google.gson.JsonParser
import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DeletionRequestTest {
    private val values = TemplateValues("shot.png", Instant.EPOCH, Instant.EPOCH, Instant.ofEpochMilli(42), "uuid-1")

    private val discordResponse = """
        {"id":"1300000000000000001","channel_id":"99","attachments":[{"id":"7","filename":"shot.png","url":"https://cdn.discordapp.com/attachments/99/7/shot.png?ex=1&is=2&hm=3"}]}
    """.trimIndent()

    @Test
    fun `discord style request renders the message id into the url`() {
        val template = DeletionRequest(DeletionKind.REQUEST, DeletionMethod.DELETE, "https://discord.com/api/webhooks/1/tok/messages/{json:id}")
        assertEquals(
            DeletionRequest(DeletionKind.REQUEST, DeletionMethod.DELETE, "https://discord.com/api/webhooks/1/tok/messages/1300000000000000001"),
            template.render(values, discordResponse),
        )
    }

    @Test
    fun `headers and body are rendered with file and response values`() {
        val template = DeletionRequest(
            DeletionKind.REQUEST,
            DeletionMethod.DELETE,
            "https://photos.example/api/assets",
            headers = listOf("x-api-key" to "key", "X-Trace" to "{uuid}"),
            body = """{"ids":["{json:id}"]}""",
        )
        val rendered = template.render(values, """{"id":"5f0c","status":"created"}""")!!
        assertEquals(listOf("x-api-key" to "key", "X-Trace" to "uuid-1"), rendered.headers)
        assertEquals("""{"ids":["5f0c"]}""", rendered.body)
    }

    @Test
    fun `no deletion renders to nothing`() {
        assertNull(DeletionRequest().render(values, discordResponse))
        assertNull(DeletionRequest(DeletionKind.REQUEST, url = "  ").render(values, discordResponse))
    }

    @Test
    fun `browser deletion must render to a web link`() {
        val template = DeletionRequest(DeletionKind.BROWSER, DeletionMethod.GET, "{json:channel_id}")
        assertFailsWith<UploadException> { template.render(values, discordResponse) }
        val ok = DeletionRequest(DeletionKind.BROWSER, DeletionMethod.GET, "{json:deletion_url}")
        assertEquals("https://host.example/delete/abc", ok.render(values, """{"deletion_url":"https://host.example/delete/abc"}""")!!.url)
    }

    @Test
    fun `missing response value fails the render`() {
        val template = DeletionRequest(DeletionKind.REQUEST, DeletionMethod.DELETE, "https://x.example/{json:nope}")
        assertFailsWith<UploadException> { template.render(values, discordResponse) }
    }

    @Test
    fun `json round trip keeps every field`() {
        val request = DeletionRequest(DeletionKind.REQUEST, DeletionMethod.POST, "https://x.example/del", listOf("a" to "b"), "{}")
        assertEquals(request, DeletionRequest.fromJson(JsonParser.parseString(request.toJson().toString()).asJsonObject))
    }

    @Test
    fun `json with unknown kind or method falls back to defaults`() {
        val parsed = DeletionRequest.fromJson(JsonParser.parseString("""{"kind":"TELEPORT","method":"YEET","url":"https://x.example"}""").asJsonObject)
        assertEquals(DeletionRequest(DeletionKind.NONE, DeletionMethod.DELETE, "https://x.example"), parsed)
    }
}
