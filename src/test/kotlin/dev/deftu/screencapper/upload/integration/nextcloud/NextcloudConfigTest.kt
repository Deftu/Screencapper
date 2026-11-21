package dev.deftu.screencapper.upload.integration.nextcloud

import dev.deftu.screencapper.upload.SecretCipher
import dev.deftu.screencapper.upload.SecretFields
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.integration.IntegrationSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NextcloudConfigTest {
    private val sandbox = Files.createTempDirectory("screencapper-nextcloud").toFile()
    private val cipher = SecretCipher(File(sandbox, ".key"))

    @AfterTest
    fun cleanup() {
        sandbox.deleteRecursively()
    }

    private val config = NextcloudConfig(
        server = "https://cloud.example.com",
        user = "sam@example.com",
        appPassword = "Ab3dE-fGh1J-kLm2N-oPq3R-sTu4V",
        userId = "sam",
        folder = "Games/Minecraft",
        link = NextcloudLink.NONE,
    )

    @Test
    fun `settings round trip keeps every field`() {
        assertEquals(config, NextcloudConfig.from(config.toSettings()))
        assertEquals("nextcloud", config.toSettings().kind)
    }

    @Test
    fun `missing settings fall back to the Screenshots folder and a public link`() {
        val loaded = NextcloudConfig.from(IntegrationSettings("nextcloud", mapOf("server" to "https://cloud.example.com")))
        assertEquals("Screenshots", loaded.folder)
        assertEquals(NextcloudLink.PUBLIC, loaded.link)
        assertEquals("", loaded.userId)
    }

    @Test
    fun `a blank folder stays blank so uploads go to the top folder`() {
        assertEquals("", NextcloudConfig.from(config.copy(folder = "").toSettings()).folder)
    }

    @Test
    fun `only the app password is treated as a secret`() {
        val secret = config.toSettings().values.keys.filter { SecretFields.isSecretName(it) }
        assertEquals(listOf("appPassword"), secret)
    }

    @Test
    fun `app password is sealed in the saved uploader list`() {
        val profile = UploaderProfile(id = "nc", name = "Nextcloud", integration = config.toSettings())
        val saved = UploaderProfile.encodeList(listOf(profile), cipher)
        assertFalse(config.appPassword in saved)
        assertTrue("sam@example.com" in saved)
        assertEquals(config, NextcloudConfig.from(UploaderProfile.decodeList(saved, cipher).single().integration!!))
    }
}
