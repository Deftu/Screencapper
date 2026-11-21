package dev.deftu.screencapper.upload

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretCipherTest {
    private val sandbox = Files.createTempDirectory("screencapper-secrets").toFile()
    private val keyFile = File(sandbox, "screencapper/.key")

    @AfterTest
    fun cleanup() {
        sandbox.deleteRecursively()
    }

    @Test
    fun `sealed values round trip and never contain the plain text`() {
        val cipher = SecretCipher(keyFile)
        val sealed = cipher.seal("my-api-key-123")
        assertTrue(sealed.startsWith("enc:v1:"))
        assertFalse("my-api-key-123" in sealed)
        assertEquals("my-api-key-123", cipher.open(sealed))
        assertEquals("my-api-key-123", SecretCipher(keyFile).open(sealed))
    }

    @Test
    fun `each seal uses a fresh iv`() {
        val cipher = SecretCipher(keyFile)
        assertNotEquals(cipher.seal("same"), cipher.seal("same"))
    }

    @Test
    fun `the key is created on first use with 256 bits`() {
        assertFalse(keyFile.exists())
        SecretCipher(keyFile).seal("x")
        assertTrue(keyFile.isFile)
        assertEquals(32, keyFile.readBytes().size)
        if ("posix" in FileSystems.getDefault().supportedFileAttributeViews()) {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(keyFile.toPath())))
        }
    }

    @Test
    fun `tampered values do not open`() {
        val cipher = SecretCipher(keyFile)
        val sealed = cipher.seal("secret-value")
        val bytes = Base64.getUrlDecoder().decode(sealed.removePrefix("enc:v1:"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertNull(cipher.open("enc:v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)))
        assertNull(cipher.open("enc:v1:AAAA"))
        assertNull(cipher.open("enc:v1:"))
    }

    @Test
    fun `a missing key fails to open without creating one`() {
        val sealed = SecretCipher(keyFile).seal("secret-value")
        keyFile.delete()
        val cipher = SecretCipher(keyFile)
        assertNull(cipher.open(sealed))
        assertFalse(keyFile.exists())
        assertEquals("a $sealed b", cipher.openAll("a $sealed b"))
    }

    @Test
    fun `a corrupt key fails to open and is replaced on the next seal`() {
        val sealed = SecretCipher(keyFile).seal("secret-value")
        keyFile.writeBytes(byteArrayOf(1, 2, 3))
        val cipher = SecretCipher(keyFile)
        assertNull(cipher.open(sealed))
        val fresh = cipher.seal("other")
        assertEquals("other", SecretCipher(keyFile).open(fresh))
        assertEquals(32, keyFile.readBytes().size)
    }

    @Test
    fun `a different key does not open the value`() {
        val sealed = SecretCipher(keyFile).seal("secret-value")
        val other = SecretCipher(File(sandbox, "other/.key"))
        other.seal("x")
        assertNull(other.open(sealed))
    }

    @Test
    fun `open all replaces every sealed part of a text`() {
        val cipher = SecretCipher(keyFile)
        val text = "https://discord.com/api/webhooks/1/${cipher.seal("tok")}?key=${cipher.seal("k")}"
        assertEquals("https://discord.com/api/webhooks/1/tok?key=k", cipher.openAll(text))
        assertTrue(SecretCipher.isSealed(text))
        assertFalse(SecretCipher.isSealed("plain"))
    }
}
