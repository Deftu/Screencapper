package dev.deftu.screencapper.upload

import java.net.URLDecoder

data class UrlSecret(val name: String, val range: IntRange)

object SecretFields {
    private val PARTS = listOf("key", "token", "secret", "auth", "password", "userhash")
    private val USERINFO = Regex("""^[A-Za-z][A-Za-z0-9+.-]*://([^/?#@\s]+)@""")
    private val WEBHOOK = Regex("""/webhooks/(\d+)/([^/?#\s]+)""")
    private val IMGUR_DELETE = Regex("""^https://api\.imgur\.com/3/image/([^/?#\s]+)""", RegexOption.IGNORE_CASE)
    private val JSON_FIELD = Regex(""""([^"\\]+)"\s*:\s*"((?:[^"\\]|\\.)+)"""")

    fun isSecretName(name: String): Boolean {
        val normalized = decode(name).trim().lowercase()
        return normalized.isNotEmpty() && PARTS.any { it in normalized }
    }

    fun urlSecrets(url: String): List<UrlSecret> {
        val out = mutableListOf<UrlSecret>()
        USERINFO.find(url)?.groups?.get(1)?.let { out += UrlSecret("userinfo", it.range) }
        WEBHOOK.find(url)?.let { match -> out += UrlSecret("webhook.${match.groupValues[1]}", match.groups[2]!!.range) }
        IMGUR_DELETE.find(url)?.let { match -> out += UrlSecret("imgur.deletehash", match.groups[1]!!.range) }
        val queryStart = url.indexOf('?')
        if (queryStart >= 0) {
            val end = url.indexOf('#', queryStart).let { if (it < 0) url.length else it }
            out += params(url, queryStart + 1, end, "query.")
        }
        return out
    }

    fun bodySecrets(body: String): List<UrlSecret> {
        val trimmed = body.trimStart()
        if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return params(body, 0, body.length, "body.")
        return JSON_FIELD.findAll(body)
            .filter { isSecretName(it.groupValues[1]) }
            .map { UrlSecret("body.${refName(it.groupValues[1])}", it.groups[2]!!.range) }
            .toList()
    }

    fun mapUrl(url: String, transform: (name: String, value: String) -> String): String = mapRanges(url, urlSecrets(url), transform)

    fun mapBody(body: String, transform: (name: String, value: String) -> String): String = mapRanges(body, bodySecrets(body), transform)

    private fun mapRanges(text: String, secrets: List<UrlSecret>, transform: (name: String, value: String) -> String): String {
        var out = text
        for (secret in secrets.sortedByDescending { it.range.first }) {
            out = out.replaceRange(secret.range, transform(secret.name, text.substring(secret.range)))
        }
        return out
    }

    private fun params(text: String, from: Int, end: Int, prefix: String): List<UrlSecret> {
        val out = mutableListOf<UrlSecret>()
        var start = from
        while (start <= end) {
            val stop = text.indexOf('&', start).let { if (it < 0 || it > end) end else it }
            val equals = text.indexOf('=', start)
            if (equals in start until stop && stop > equals + 1) {
                val name = text.substring(start, equals)
                if (isSecretName(name)) out += UrlSecret("$prefix${refName(name)}", equals + 1 until stop)
            }
            start = stop + 1
        }
        return out
    }

    fun refName(name: String): String = name.trim().map { if (it == '{' || it == '}' || it == ':' || it.isWhitespace()) '_' else it }.joinToString("")

    private fun decode(name: String): String = runCatching { URLDecoder.decode(name, Charsets.UTF_8) }.getOrDefault(name)
}
