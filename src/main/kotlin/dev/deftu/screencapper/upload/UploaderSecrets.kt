package dev.deftu.screencapper.upload

object UploaderSecrets {
    fun seal(profile: UploaderProfile, cipher: SecretCipher): UploaderProfile = mapSecrets(profile) { _, value -> sealValue(value, cipher) }

    fun open(profile: UploaderProfile, cipher: SecretCipher): UploaderProfile = profile.copy(
        url = cipher.openAll(profile.url),
        headers = profile.headers.map { (key, value) -> key to cipher.openAll(value) },
        formFields = profile.formFields.map { (key, value) -> key to cipher.openAll(value) },
        deletion = profile.deletion.copy(
            url = cipher.openAll(profile.deletion.url),
            headers = profile.deletion.headers.map { (key, value) -> key to cipher.openAll(value) },
            body = cipher.openAll(profile.deletion.body),
        ),
        integration = profile.integration?.let { settings -> settings.copy(values = settings.values.mapValues { cipher.openAll(it.value) }) },
    )

    fun hasPlainSecrets(profile: UploaderProfile): Boolean {
        var found = false
        mapSecrets(profile) { _, value ->
            if (value.isNotBlank() && !SecretCipher.isSealed(value)) found = true
            value
        }
        return found
    }

    fun lockedFields(profile: UploaderProfile): List<String> = listOfNotNull(
        "Upload URL".takeIf { SecretCipher.isSealed(profile.url) },
    ) + profile.headers.filter { SecretCipher.isSealed(it.second) }.map { "Header ${it.first.trim()}" } +
        profile.formFields.filter { SecretCipher.isSealed(it.second) }.map { "Field ${it.first.trim()}" } +
        listOfNotNull("Deletion URL".takeIf { SecretCipher.isSealed(profile.deletion.url) }) +
        profile.deletion.headers.filter { SecretCipher.isSealed(it.second) }.map { "Deletion header ${it.first.trim()}" } +
        listOfNotNull("Deletion body".takeIf { SecretCipher.PREFIX in profile.deletion.body }) +
        profile.integration?.values.orEmpty().filter { SecretCipher.isSealed(it.value) }.map { "Setting ${it.key}" }

    fun named(profile: UploaderProfile): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        mapSecrets(profile) { name, value ->
            if (value.isNotBlank()) out += name to value
            value
        }
        if (profile.id == UploaderProfile.IMGUR_ID) out += "header.authorization" to "Client-ID ${UploaderProfile.IMGUR_CLIENT_ID}"
        return out.sortedBy { if (it.first.startsWith("deletion.")) 0 else 1 }
    }

    internal fun sealValue(value: String, cipher: SecretCipher): String =
        if (value.isBlank() || SecretCipher.isSealed(value) || SecretRefs.hasReference(value)) value else cipher.seal(value)

    private fun mapSecrets(profile: UploaderProfile, transform: (name: String, value: String) -> String): UploaderProfile = profile.copy(
        url = SecretFields.mapUrl(profile.url) { name, value -> transform("url.$name", value) },
        headers = mapPairs(profile.headers, "header.", lowercase = true, transform),
        formFields = mapPairs(profile.formFields, "field.", lowercase = false, transform),
        deletion = mapRequest(profile.deletion, "deletion.", transform),
        integration = profile.integration?.let { settings ->
            settings.copy(values = settings.values.mapValues { (key, value) -> if (SecretFields.isSecretName(key)) transform("integration.${SecretFields.refName(key)}", value) else value })
        },
    )

    internal fun mapRequest(request: DeletionRequest, prefix: String, transform: (name: String, value: String) -> String): DeletionRequest = request.copy(
        url = SecretFields.mapUrl(request.url) { name, value -> transform("${prefix}url.$name", value) },
        headers = mapPairs(request.headers, "${prefix}header.", lowercase = true, transform),
        body = SecretFields.mapBody(request.body) { name, value -> transform(prefix + name, value) },
    )

    private fun mapPairs(
        pairs: List<Pair<String, String>>,
        prefix: String,
        lowercase: Boolean,
        transform: (name: String, value: String) -> String,
    ): List<Pair<String, String>> = pairs.map { (key, value) ->
        if (!SecretFields.isSecretName(key)) return@map key to value
        val name = SecretFields.refName(key).let { if (lowercase) it.lowercase() else it }
        key to transform(prefix + name, value)
    }
}
