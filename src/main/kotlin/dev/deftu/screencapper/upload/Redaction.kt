package dev.deftu.screencapper.upload

object Redaction {
    private val WEBHOOK = Regex("""(/webhooks/\d+/)[^/?#\s"{]+""")
    private val ORIGIN = Regex("""^(https?)://([^/?#\s]+)""", RegexOption.IGNORE_CASE)
    private val USERINFO = Regex("""(\b[A-Za-z][A-Za-z0-9+.-]*://)[^/?#\s@{]+@""")
    private val QUERY = Regex("""([?&])([^=&#\s]+)=([^&#\s"]+)""")
    private val NAMED = Regex("""(?<![{\w-])([A-Za-z0-9_-]+)(\s*[:=]\s*)((?:(?:Bearer|Basic|Client-ID|Token|Bot)\s+)?)([^\s",;&}]+)""")
    private val JSON_FIELD = Regex(""""([^"]+)"(\s*:\s*)"([^"]*)"""")
    private const val MASK = "***"

    fun redact(text: String, secrets: Collection<String>): String {
        var out = text
        for (secret in secrets.map { it.trim() }.filter { it.length >= 6 }.distinct().sortedByDescending { it.length }) {
            out = out.replace(secret, masked(secret))
        }
        out = WEBHOOK.replace(out) { it.groupValues[1] + MASK }
        out = USERINFO.replace(out) { it.groupValues[1] + "$MASK@" }
        out = QUERY.replace(out) { if (SecretFields.isSecretName(it.groupValues[2])) "${it.groupValues[1]}${it.groupValues[2]}=$MASK" else it.value }
        out = JSON_FIELD.replace(out) { if (SecretFields.isSecretName(it.groupValues[1])) "\"${it.groupValues[1]}\"${it.groupValues[2]}\"$MASK\"" else it.value }
        out = NAMED.replace(out) { match ->
            val (name, separator, scheme) = match.destructured
            if (SecretFields.isSecretName(name)) "$name$separator$scheme$MASK" else match.value
        }
        return out
    }

    fun secretsOf(profile: UploaderProfile): List<String> =
        listOf(profile.url) + profile.headers.map { it.second } + profile.formFields.map { it.second } + secretsOf(profile.deletion) +
            UploaderSecrets.named(profile).flatMap { (_, value) -> listOf(value, value.trim().substringAfterLast(' ')) }

    fun secretsOf(request: DeletionRequest): List<String> =
        listOf(request.url) + request.headers.map { it.second } + request.headers.filter { SecretFields.isSecretName(it.first) }.map { it.second.trim().substringAfterLast(' ') } +
            SecretFields.urlSecrets(request.url).map { request.url.substring(it.range) }

    private fun masked(secret: String): String =
        ORIGIN.find(secret)?.let { "${it.groupValues[1]}://${it.groupValues[2]}/$MASK" } ?: MASK
}
