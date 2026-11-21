package dev.deftu.screencapper.upload.integration

import com.google.gson.JsonParser
import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.upload.DeletionRequest
import dev.deftu.screencapper.upload.SecretCipher
import dev.deftu.screencapper.upload.SecretRefs
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.UploaderSecrets
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IntegrationContractTest {
    private val sandbox = Files.createTempDirectory("screencapper-integration").toFile()
    private val cipher = SecretCipher(File(sandbox, ".key"))
    private val password = "app-password-0123456789"

    private val profile = UploaderProfile(
        id = "cloud",
        name = "Cloud",
        integration = IntegrationSettings("nextcloud", mapOf("server" to "https://cloud.example", "user" to "sam", "appPassword" to password)),
    )

    @AfterTest
    fun cleanup() {
        sandbox.deleteRecursively()
    }

    @Test
    fun `integration settings are saved sealed and load back the same`() {
        val saved = UploaderProfile.encodeList(listOf(profile), cipher)
        assertFalse(password in saved)
        assertTrue("https://cloud.example" in saved)
        assertEquals(listOf(profile), UploaderProfile.decodeList(saved, cipher))
    }

    @Test
    fun `sealed integration settings count as locked when they can't be opened`() {
        val sealed = UploaderSecrets.seal(profile, cipher)
        assertEquals(listOf("Setting appPassword"), UploaderSecrets.lockedFields(sealed))
        assertTrue(UploaderSecrets.hasPlainSecrets(profile))
    }

    @Test
    fun `deletion credentials become references and resolve back`() {
        val request = DeletionRequest(DeletionKind.REQUEST, DeletionMethod.DELETE, "Screenshots/a.png", listOf("password" to password), integration = "nextcloud")
        val stored = SecretRefs.toReferences(request, profile)
        assertFalse(password in stored.toJson().toString())
        assertEquals("nextcloud", DeletionRequest.fromJson(JsonParser.parseString(stored.toJson().toString()).asJsonObject).integration)
        val resolved = assertIs<SecretRefs.Resolution.Ready>(SecretRefs.resolve(stored, listOf(profile))).request
        assertEquals(request, resolved)
    }

    @Test
    fun `loopback query is decoded`() {
        assertEquals(mapOf("code" to "4/a b", "state" to "x=y", "flag" to ""), LoopbackReceiver.parseQuery("code=4%2Fa+b&state=x%3Dy&flag"))
    }
}
