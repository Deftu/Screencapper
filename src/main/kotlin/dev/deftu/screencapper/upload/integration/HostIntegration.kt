package dev.deftu.screencapper.upload.integration

import com.google.gson.JsonObject
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.upload.DeletionRequest
import dev.deftu.screencapper.upload.TemplateValues
import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.UploadResult
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.integration.flickr.FlickrIntegration
import dev.deftu.screencapper.upload.integration.googlephotos.GooglePhotosIntegration
import dev.deftu.screencapper.upload.integration.nextcloud.NextcloudIntegration

data class IntegrationSettings(val kind: String, val values: Map<String, String> = emptyMap()) {
    operator fun get(key: String): String = values[key].orEmpty()

    fun with(vararg pairs: Pair<String, String>): IntegrationSettings = copy(values = values + pairs)

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("kind", kind)
        add("values", JsonObject().apply { values.forEach { (key, value) -> addProperty(key, value) } })
    }

    companion object {
        fun fromJson(json: JsonObject): IntegrationSettings? {
            val kind = json.get("kind")?.asString?.takeIf { it.isNotBlank() } ?: return null
            val values = (json.get("values") as? JsonObject)?.entrySet()
                ?.filter { it.value.isJsonPrimitive }
                ?.associate { it.key to it.value.asString }
                .orEmpty()
            return IntegrationSettings(kind, values)
        }
    }
}

class IntegrationUpload(
    val profile: UploaderProfile,
    val settings: IntegrationSettings,
    val bytes: ByteArray,
    val fileName: String,
    val contentType: String,
    val values: TemplateValues,
)

interface HostIntegration {
    val kind: String
    val displayName: String

    fun host(settings: IntegrationSettings): String

    fun upload(upload: IntegrationUpload): UploadResult

    fun delete(request: DeletionRequest) {
        throw UploadException("$displayName can't delete uploads.")
    }
}

object Integrations {
    val all: List<HostIntegration> = listOf(NextcloudIntegration, GooglePhotosIntegration, FlickrIntegration)

    fun find(kind: String): HostIntegration? = all.firstOrNull { it.kind == kind }

    fun of(profile: UploaderProfile): HostIntegration? = profile.integration?.let { find(it.kind) }

    fun current(profileId: String): IntegrationSettings? = ScreencapperConfig.profiles.firstOrNull { it.id == profileId }?.integration

    @Synchronized
    fun update(profileId: String, transform: (IntegrationSettings) -> IntegrationSettings) {
        val custom = ScreencapperConfig.profiles.filterNot { it.isBuiltIn }
        val target = custom.firstOrNull { it.id == profileId } ?: return
        val settings = target.integration ?: return
        val updated = transform(settings)
        if (updated == settings) return
        ScreencapperConfig.uploaders = ScreencapperConfig.encodeProfiles(custom.map { if (it.id == profileId) it.copy(integration = updated) else it })
        ScreencapperConfig.save()
    }
}
