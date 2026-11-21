package dev.deftu.screencapper.upload

import dev.deftu.screencapper.Screencapper
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SecretCipher(private val keyFile: File) {
    private val lock = Any()
    private var key: SecretKey? = null

    fun seal(plain: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyForSealing(), GCMParameterSpec(TAG_BITS, iv))
        return PREFIX + ENCODER.encodeToString(iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    fun open(sealed: String): String? = runCatching {
        val bytes = Base64.getUrlDecoder().decode(sealed.removePrefix(PREFIX))
        if (!sealed.startsWith(PREFIX) || bytes.size <= IV_BYTES) return null
        val key = loadKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    fun openAll(text: String): String {
        if (!isSealed(text)) return text
        return TOKEN.replace(text) { match -> open(match.value) ?: match.value }
    }

    private fun loadKey(): SecretKey? = synchronized(lock) {
        key ?: runCatching { keyFile.takeIf { it.isFile }?.readBytes() }.getOrNull()
            ?.takeIf { it.size == KEY_BYTES }
            ?.let { SecretKeySpec(it, "AES") }
            ?.also { key = it }
    }

    private fun keyForSealing(): SecretKey = synchronized(lock) {
        loadKey() ?: createKey()
    }

    private fun createKey(): SecretKey {
        val bytes = ByteArray(KEY_BYTES).also(random::nextBytes)
        val dir = keyFile.absoluteFile.parentFile
        dir.mkdirs()
        if (keyFile.exists()) Files.move(keyFile.toPath(), File(dir, "${keyFile.name}.bak").toPath(), StandardCopyOption.REPLACE_EXISTING)
        val temp = File(dir, "${keyFile.name}.tmp").toPath()
        Files.deleteIfExists(temp)
        if (POSIX) {
            Files.createFile(temp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } else {
            Files.createFile(temp)
        }
        Files.write(temp, bytes)
        Files.move(temp, keyFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return SecretKeySpec(bytes, "AES").also { key = it }
    }

    companion object {
        const val PREFIX = "enc:v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private val TOKEN = Regex("""enc:v1:[A-Za-z0-9_-]+""")
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val random = SecureRandom()
        private val POSIX = "posix" in FileSystems.getDefault().supportedFileAttributeViews()

        fun isSealed(text: String): Boolean = TOKEN.containsMatchIn(text)
    }
}

object Secrets {
    val cipher: SecretCipher by lazy { SecretCipher(File(FabricLoader.getInstance().configDir.toFile(), "screencapper/.key")) }

    fun <T> sealing(what: String, sealed: () -> T, plain: () -> T): T = runCatching(sealed).getOrElse {
        Screencapper.LOGGER.error("Couldn't encrypt the secrets in {}, saving them unencrypted: {}", what, it.javaClass.simpleName)
        plain()
    }
}
