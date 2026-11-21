package dev.deftu.screencapper.upload.integration.nextcloud

import java.net.URI

object NextcloudPaths {
    const val MAX_ATTEMPTS = 5

    private const val NUMBERED_ATTEMPTS = 2
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val HEX = "0123456789ABCDEF"
    private val ROUTES = listOf("/index.php", "/remote.php", "/ocs/", "/apps/")

    fun serverProblem(server: String): String? {
        val uri = runCatching { URI(server.trim()) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrEmpty()) {
            return "Enter your server's address, like https://cloud.example.com"
        }
        return null
    }

    fun normalizeServer(server: String): String {
        val uri = URI(server.trim())
        val path = uri.rawPath.orEmpty()
        val end = ROUTES.map { path.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: path.length
        return "${uri.scheme.lowercase()}://${uri.rawAuthority}${path.substring(0, end)}".trimEnd('/')
    }

    fun folderSegments(folder: String): List<String> = folder.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() }

    fun folderProblem(folder: String): String? =
        if (folderSegments(folder).any { it == "." || it == ".." }) "The folder can't contain . or .. parts." else null

    fun encodeSegment(segment: String): String = buildString {
        for (byte in segment.toByteArray(Charsets.UTF_8)) {
            val value = byte.toInt() and 0xFF
            if (value < 0x80 && value.toChar() in UNRESERVED) {
                append(value.toChar())
            } else {
                append('%').append(HEX[value shr 4]).append(HEX[value and 0xF])
            }
        }
    }

    fun davUrl(server: String, userId: String, segments: List<String>): String =
        "$server/remote.php/dav/files/${encodeSegment(userId)}" + segments.joinToString("") { "/" + encodeSegment(it) }

    fun sharePath(segments: List<String>): String = "/" + segments.joinToString("/")

    fun candidateName(fileName: String, attempt: Int, random: () -> String): String {
        if (attempt == 0) return fileName
        val dot = fileName.lastIndexOf('.').takeIf { it > 0 } ?: fileName.length
        val base = fileName.substring(0, dot)
        val extension = fileName.substring(dot)
        return if (attempt <= NUMBERED_ATTEMPTS) "$base (${attempt + 1})$extension" else "$base-${random()}$extension"
    }
}
