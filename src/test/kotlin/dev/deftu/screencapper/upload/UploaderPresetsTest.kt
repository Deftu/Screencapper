package dev.deftu.screencapper.upload

import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.config.LinkMode
import dev.deftu.screencapper.config.RequestBody
import dev.deftu.screencapper.config.RequestMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UploaderPresetsTest {
    private val webhook = "https://discord.com/api/webhooks/123456789/abcDEF-123_xyz"

    @Test
    fun `discord preset waits for the message and reads the attachment url`() {
        val profile = UploaderPresets.discord(webhook)
        assertEquals("$webhook?wait=true", profile.url)
        assertEquals(RequestMethod.POST, profile.method)
        assertEquals(RequestBody.MULTIPART, profile.body)
        assertEquals("files[0]", profile.fileField)
        assertEquals("attachments[0].url||embeds[0].image.url", profile.responseUrl)
        assertEquals(LinkMode.LINK, profile.linkMode)
        assertEquals(DeletionRequest(DeletionKind.REQUEST, DeletionMethod.DELETE, "$webhook/messages/{json:id}"), profile.deletion)
        assertFalse(profile.isBuiltIn)
    }

    @Test
    fun `discord preset keeps a thread id for both upload and deletion`() {
        val profile = UploaderPresets.discord("$webhook/?thread_id=55&wait=false")
        assertEquals("$webhook?thread_id=55&wait=true", profile.url)
        assertEquals("$webhook/messages/{json:id}?thread_id=55", profile.deletion.url)
    }

    @Test
    fun `discord preset rejects urls that are not webhooks`() {
        assertNull(UploaderPresets.discordProblem(webhook))
        assertNull(UploaderPresets.discordProblem("https://canary.discord.com/api/webhooks/1/t"))
        assertNull(UploaderPresets.discordProblem("https://discordapp.com/api/v10/webhooks/1/t"))
        assertNotNull(UploaderPresets.discordProblem("https://example.com/api/webhooks/1/t"))
        assertNotNull(UploaderPresets.discordProblem("https://discord.com/channels/1/2"))
        assertNotNull(UploaderPresets.discordProblem("http://discord.com/api/webhooks/1/t"))
    }

    @Test
    fun `immich preset fills the asset upload fields`() {
        val profile = UploaderPresets.immich("https://photos.example/api/", "secret-key", ownerLink = false)
        assertEquals("https://photos.example/api/assets", profile.url)
        assertEquals(RequestMethod.POST, profile.method)
        assertEquals(RequestBody.MULTIPART, profile.body)
        assertEquals("assetData", profile.fileField)
        assertEquals(listOf("x-api-key" to "secret-key", "Accept" to "application/json"), profile.headers)
        assertEquals(
            listOf(
                "deviceAssetId" to "screencapper-{uuid}",
                "deviceId" to "screencapper",
                "fileCreatedAt" to "{createdAt}",
                "fileModifiedAt" to "{modifiedAt}",
                "filename" to "{fileName}",
            ),
            profile.formFields,
        )
        assertEquals(LinkMode.NONE, profile.linkMode)
        assertEquals("https://photos.example/photos/{json:id}", profile.responseUrl)
        assertEquals(
            DeletionRequest(
                DeletionKind.REQUEST,
                DeletionMethod.DELETE,
                "https://photos.example/api/assets",
                listOf("x-api-key" to "secret-key", "Content-Type" to "application/json"),
                """{"ids":["{json:id}"]}""",
            ),
            profile.deletion,
        )
    }

    @Test
    fun `immich owner link is a link result`() {
        assertEquals(LinkMode.LINK, UploaderPresets.immich("http://192.168.1.5:2283", "k", ownerLink = true).linkMode)
        assertEquals("http://192.168.1.5:2283/api/assets", UploaderPresets.immich("http://192.168.1.5:2283", "k", ownerLink = true).url)
    }

    @Test
    fun `immich server must be a web address`() {
        assertNull(UploaderPresets.immichProblem("https://photos.example"))
        assertNotNull(UploaderPresets.immichProblem("photos.example"))
        assertNotNull(UploaderPresets.immichProblem(""))
    }

    @Test
    fun `profiles with presets survive the config round trip`() {
        val profiles = listOf(UploaderPresets.discord(webhook), UploaderPresets.immich("https://photos.example", "k", ownerLink = false))
        assertEquals(profiles, UploaderProfile.decodeList(UploaderProfile.encodeList(profiles)))
    }

    @Test
    fun `profiles saved before results and deletions existed still load`() {
        val old = """[{"id":"x","name":"Old","url":"https://h.example","method":"POST","body":"MULTIPART","fileField":"file","responseUrl":"url","headers":[],"formFields":[],"sizeLimitMb":"","keepMetadata":false}]"""
        val profile = UploaderProfile.decodeList(old).single()
        assertEquals(LinkMode.LINK, profile.linkMode)
        assertEquals(DeletionRequest(), profile.deletion)
        assertNull(profile.discord)
    }

    @Test
    fun `discord preset sends a payload that never pings`() {
        val profile = UploaderPresets.discord(webhook)
        assertEquals(listOf("payload_json" to """{"allowed_mentions":{"parse":[]}}"""), profile.formFields)
        assertEquals(DiscordOptions(), profile.discord)
    }

    @Test
    fun `discord options choose the thread for upload and deletion`() {
        val profile = UploaderPresets.discord(webhook, DiscordOptions(threadId = "77", silent = true))
        assertEquals("$webhook?thread_id=77&wait=true", profile.url)
        assertEquals("$webhook/messages/{json:id}?thread_id=77", profile.deletion.url)
        assertEquals(listOf("payload_json" to DiscordMessage.template(DiscordOptions(threadId = "77", silent = true))), profile.formFields)

        val cleared = UploaderPresets.discord("$webhook?thread_id=55", DiscordOptions())
        assertEquals("$webhook?wait=true", cleared.url)
        assertEquals("55", UploaderPresets.discordThreadId("$webhook?thread_id=55&wait=true"))
        assertEquals("55", UploaderPresets.discord("$webhook?thread_id=55").discord?.threadId)
    }

    @Test
    fun `editing discord options keeps the rest of the profile`() {
        val original = UploaderPresets.discord(webhook).let {
            it.copy(name = "Mine", keepMetadata = true, sizeLimitMb = "8", formFields = it.formFields + ("extra" to "x"))
        }
        val options = DiscordOptions(content = "{player}", embed = true, threadId = "9")
        val edited = UploaderPresets.withDiscord(original, options)
        assertEquals(original.id, edited.id)
        assertEquals("Mine", edited.name)
        assertEquals(true, edited.keepMetadata)
        assertEquals("8", edited.sizeLimitMb)
        assertEquals("$webhook?thread_id=9&wait=true", edited.url)
        assertEquals(listOf("extra" to "x", "payload_json" to DiscordMessage.template(options)), edited.formFields)
        assertEquals("$webhook/messages/{json:id}?thread_id=9", edited.deletion.url)
        assertEquals(options, edited.discord)
    }

    @Test
    fun `discord options survive the config round trip`() {
        val profile = UploaderPresets.discord(webhook, DiscordOptions(content = "Hi {player}", embed = true, title = "{world}", color = 0xABCDEF, silent = true, threadName = "{name}"))
        assertEquals(listOf(profile), UploaderProfile.decodeList(UploaderProfile.encodeList(listOf(profile))))
    }

    @Test
    fun `editing the webhook url rebuilds the upload and deletion urls`() {
        val edited = UploaderPresets.discord("$webhook?thread_id=55").copy(url = "https://discord.com/api/webhooks/987/newToken")
        val refreshed = UploaderPresets.refreshed(edited)
        assertEquals("https://discord.com/api/webhooks/987/newToken?thread_id=55&wait=true", refreshed.url)
        assertEquals("https://discord.com/api/webhooks/987/newToken/messages/{json:id}?thread_id=55", refreshed.deletion.url)
        val plain = UploaderProfile(id = "p", name = "P", url = "https://host.example/up")
        assertEquals(plain, UploaderPresets.refreshed(plain))
    }

    @Test
    fun `the host shown for a templated url never includes the rest of the url`() {
        assertEquals("host.example", UploaderProfile(id = "t", name = "T", url = "https://host.example/{fileName}?api_key=abc").host)
        assertEquals("Custom host", UploaderProfile(id = "t", name = "T", url = "not a url ?api_key=abc").host)
    }
}
