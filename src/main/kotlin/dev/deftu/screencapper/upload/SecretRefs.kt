package dev.deftu.screencapper.upload

object SecretRefs {
    private val REF = Regex("""\{secret:([^:{}]+):([^{}]+)}""")
    private const val MIN_EMBEDDED_LENGTH = 6

    sealed interface Resolution {
        data class Ready(val request: DeletionRequest) : Resolution
        data object Missing : Resolution
        data class Locked(val inProfile: Boolean) : Resolution
    }

    class Loaded(val request: DeletionRequest, val changed: Boolean)

    fun reference(profileId: String, name: String): String = "{secret:$profileId:$name}"

    fun hasReference(text: String): Boolean = REF.containsMatchIn(text)

    fun toReferences(request: DeletionRequest, profile: UploaderProfile): DeletionRequest {
        val byValue = LinkedHashMap<String, String>()
        for ((name, value) in UploaderSecrets.named(profile)) {
            if (!SecretCipher.isSealed(value)) byValue.putIfAbsent(value, reference(profile.id, name))
        }
        if (byValue.isEmpty()) return request
        val embedded = byValue.keys.filter { it.length >= MIN_EMBEDDED_LENGTH }.sortedByDescending { it.length }
        val pattern = if (embedded.isEmpty()) null else Regex(embedded.joinToString("|") { Regex.escape(it) })
        fun embed(text: String): String = if (pattern == null) text else outsideRefs(text) { part -> pattern.replace(part) { byValue.getValue(it.value) } }
        fun exact(value: String): String = byValue[value] ?: embed(value)

        val slotted = request.copy(
            url = SecretFields.mapUrl(request.url) { _, value -> byValue[value] ?: value },
            headers = request.headers.map { (key, value) -> key to if (SecretFields.isSecretName(key)) exact(value) else value },
        )
        return slotted.copy(
            url = embed(slotted.url),
            headers = slotted.headers.map { (key, value) -> key to embed(value) },
            body = embed(slotted.body),
        )
    }

    fun resolve(request: DeletionRequest, profiles: List<UploaderProfile>): Resolution {
        var missing = false
        var lockedInProfile = false
        fun fill(text: String): String = REF.replace(text) { match ->
            val (profileId, name) = match.destructured
            val value = profiles.firstOrNull { it.id == profileId }?.let { profile -> UploaderSecrets.named(profile).firstOrNull { it.first == name }?.second }
            when {
                value == null -> missing = true
                SecretCipher.isSealed(value) -> lockedInProfile = true
            }
            value ?: match.value
        }
        val resolved = request.copy(
            url = fill(request.url),
            headers = request.headers.map { (key, value) -> key to fill(value) },
            body = fill(request.body),
        )
        val lockedInRecord = listOf(request.url, request.body).plus(request.headers.map { it.second }).any { SecretCipher.isSealed(it) }
        return when {
            missing -> Resolution.Missing
            lockedInProfile -> Resolution.Locked(inProfile = true)
            lockedInRecord -> Resolution.Locked(inProfile = false)
            else -> Resolution.Ready(resolved)
        }
    }

    fun load(stored: DeletionRequest, profile: UploaderProfile?, cipher: SecretCipher): Loaded {
        val opened = open(stored, cipher)
        val migrated = profile?.let { toReferences(opened, it) } ?: opened
        return Loaded(migrated, migrated != opened || hasPlainSecrets(stored))
    }

    fun seal(request: DeletionRequest, cipher: SecretCipher): DeletionRequest =
        UploaderSecrets.mapRequest(request, "") { _, value -> UploaderSecrets.sealValue(value, cipher) }

    private fun open(request: DeletionRequest, cipher: SecretCipher): DeletionRequest = request.copy(
        url = cipher.openAll(request.url),
        headers = request.headers.map { (key, value) -> key to cipher.openAll(value) },
        body = cipher.openAll(request.body),
    )

    private fun hasPlainSecrets(request: DeletionRequest): Boolean {
        var found = false
        UploaderSecrets.mapRequest(request, "") { _, value ->
            if (value.isNotBlank() && !SecretCipher.isSealed(value) && !hasReference(value)) found = true
            value
        }
        return found
    }

    private fun outsideRefs(text: String, transform: (String) -> String): String {
        val out = StringBuilder()
        var last = 0
        for (match in REF.findAll(text)) {
            out.append(transform(text.substring(last, match.range.first))).append(match.value)
            last = match.range.last + 1
        }
        return out.append(transform(text.substring(last))).toString()
    }
}
