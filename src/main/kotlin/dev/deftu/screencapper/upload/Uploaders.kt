package dev.deftu.screencapper.upload

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import dev.deftu.screencapper.ChatCards
import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.config.LinkMode
import dev.deftu.screencapper.config.RequestBody
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.Screenshot
import dev.deftu.screencapper.screenshot.ScreenshotLibrary
import dev.deftu.screencapper.screenshot.SharedUploads
import dev.deftu.screencapper.screenshot.UploadHistory
import dev.deftu.screencapper.screenshot.UploadRecord
import dev.deftu.screencapper.screenshot.capture.compression.CompressionProfile
import dev.deftu.screencapper.screenshot.capture.compression.CompressionProfiles
import dev.deftu.screencapper.screenshot.capture.compression.FitResult
import dev.deftu.screencapper.screenshot.capture.compression.ImageCompressor
import dev.deftu.screencapper.screenshot.capture.compression.ImageFormat
import dev.deftu.screencapper.screenshot.capture.compression.PngChunks
import dev.deftu.screencapper.screenshot.capture.compression.SizeFitter
import dev.deftu.screencapper.upload.integration.HostIntegration
import dev.deftu.screencapper.upload.integration.IntegrationUpload
import dev.deftu.screencapper.upload.integration.Integrations
import org.polyfrost.oneconfig.api.notifications.v1.NotificationAction
import org.polyfrost.oneconfig.api.notifications.v1.NotificationType
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import org.polyfrost.oneconfig.utils.v1.Multithreading
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class UploadException(message: String) : IOException(message)

class UploadResult(val url: String, val deletion: DeletionRequest?)

private class Payload(val bytes: ByteArray, val fileName: String, val contentType: String)

object Uploaders {
    private val TIMEOUT = UploadHttp.TIMEOUT
    private val USER_AGENT = UploadHttp.USER_AGENT
    private val MANAGED_HEADERS = setOf("content-type", "content-length", "host", "connection", "expect", "upgrade")

    private val deleting = ConcurrentHashMap.newKeySet<DeletionTarget>()

    fun upload(screenshot: Screenshot, profile: UploaderProfile): UploadResult {
        val locked = UploaderSecrets.lockedFields(profile)
        if (locked.isNotEmpty()) {
            throw UploadException("${profile.name}'s saved secrets can't be read. Re-enter them in the uploader settings: ${locked.joinToString(", ")}.")
        }
        val payload = payload(screenshot, profile)
        return try {
            val integration = Integrations.of(profile)
            when {
                integration != null -> uploadToIntegration(integration, screenshot, payload, profile)
                profile.id == UploaderProfile.IMGUR_ID -> uploadToImgur(payload)
                else -> uploadToCustomHost(screenshot, payload, profile)
            }
        } catch (e: Exception) {
            throw UploadException(Redaction.redact(describe(e), secretsOf(profile)))
        }
    }

    fun isDeleting(record: UploadRecord): Boolean = record.deletion?.target?.let { it in deleting } == true

    fun deletionProblem(record: UploadRecord): String? {
        val request = record.deletion?.takeIf { it.kind != DeletionKind.NONE } ?: return null
        return problemOf(SecretRefs.resolve(request, ScreencapperConfig.profiles), record)
    }

    fun removeLink(screenshot: Screenshot, record: UploadRecord) {
        UploadHistory.remove(screenshot, record)
        ScreenshotLibrary.changed()
        ChatCards.uploadsChanged(screenshot)
        ScreenshotActions.notifySuccess("Link removed", "Removed the link from your history.")
    }

    private fun problemOf(resolution: SecretRefs.Resolution, record: UploadRecord): String? = when (resolution) {
        is SecretRefs.Resolution.Ready -> null
        SecretRefs.Resolution.Missing -> "The uploader for this upload was removed, so it can't be deleted from ${record.uploaderName}."
        is SecretRefs.Resolution.Locked ->
            if (resolution.inProfile) "The uploader's saved secret can't be read, so this upload can't be deleted from ${record.uploaderName}. Re-enter it in the uploader settings, or remove the link."
            else "This upload's saved secret can't be read, so it can't be deleted from ${record.uploaderName}."
    }

    fun deleteRemote(screenshot: Screenshot, record: UploadRecord) {
        val request = record.deletion?.takeIf { it.kind != DeletionKind.NONE }
        if (request == null) {
            ScreenshotActions.notifyError("Can't delete upload", "${record.uploaderName} gave no way to delete it.")
            return
        }
        val target = request.target
        if (target in deleting) return
        val sharedWith = SharedUploads.describe(UploadHistory.sharing(screenshot, record), screenshot.id)
        if (sharedWith != null) {
            UploadHistory.remove(screenshot, record)
            ScreenshotLibrary.changed()
            ChatCards.uploadsChanged(screenshot)
            ScreenshotActions.notifySuccess("Link removed", "Removed the link. The upload is still used by $sharedWith, so it was kept on ${record.uploaderName}.")
            return
        }
        val resolution = SecretRefs.resolve(request, ScreencapperConfig.profiles)
        val resolved = (resolution as? SecretRefs.Resolution.Ready)?.request
        if (resolved == null) {
            Notifications.send(
                "Can't delete upload", problemOf(resolution, record).orEmpty(), NotificationType.ERROR,
                actions = listOf(NotificationAction("Remove link", primary = true) { removeLink(screenshot, record) }),
            )
            return
        }
        if (request.kind == DeletionKind.BROWSER) {
            val uri = runCatching { URI(resolved.url) }.getOrNull()
            if (uri == null || !isWebLink(resolved.url) || !DesktopHelper.browse(uri)) {
                ScreenshotActions.notifyError("Couldn't open the deletion page", "Open it from ${record.uploaderName} instead.")
                return
            }
            UploadHistory.remove(screenshot, record)
            ScreenshotLibrary.changed()
            ChatCards.uploadsChanged(screenshot)
            ScreenshotActions.notifySuccess("Deletion page opened", "Finish deleting it in your browser. The link was removed from your history.")
            return
        }
        if (!deleting.add(target)) return
        ScreenshotLibrary.changed()
        ChatCards.uploadsChanged(screenshot)
        Multithreading.submit {
            val result = runCatching { sendDeletion(resolved) }
            result.onSuccess {
                val affected = UploadHistory.removeTarget(target) - screenshot.id
                deleting.remove(target)
                ScreenshotLibrary.changed()
                ChatCards.uploadsChanged(screenshot)
                affected.forEach { id -> ScreenshotLibrary.find(id)?.let { ChatCards.uploadsChanged(it) } }
                ScreenshotActions.notifySuccess("Upload deleted", "${screenshot.name} was deleted from ${record.uploaderName}.")
            }.onFailure {
                deleting.remove(target)
                val message = Redaction.redact(describe(it), Redaction.secretsOf(resolved))
                Screencapper.LOGGER.error("Failed to delete the {} upload of {}: {}", record.uploaderName, screenshot.name, message)
                ScreenshotLibrary.changed()
                ChatCards.uploadsChanged(screenshot)
                ScreenshotActions.notifyError("Couldn't delete upload", message)
            }
        }
    }

    fun keepsMetadata(profile: UploaderProfile): Boolean =
        if (profile.isBuiltIn) profile.id in builtInsKeepingMetadata() else profile.keepMetadata

    fun setKeepsMetadata(profile: UploaderProfile, keep: Boolean) {
        val ids = builtInsKeepingMetadata().toMutableSet()
        if (keep) ids += profile.id else ids -= profile.id
        ScreencapperConfig.builtInUploaderMetadata = ids.sorted().joinToString(",")
    }

    private fun builtInsKeepingMetadata(): Set<String> =
        ScreencapperConfig.builtInUploaderMetadata.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun payload(screenshot: Screenshot, profile: UploaderProfile): Payload {
        val keepMetadata = keepsMetadata(profile)
        val limit = profile.sizeLimitBytes
        val original = screenshot.readBytes()
        val isPng = PngChunks.isPng(original)
        val compression = CompressionProfiles.forUpload()
        if (compression == null) {
            val bytes = if (isPng && !keepMetadata) PngChunks.stripText(original) else original
            if (limit == null || bytes.size <= limit) return Payload(bytes, screenshot.name, contentType(screenshot))
        }

        val start = compression ?: CompressionProfile.UPLOAD
        val image = ImageCompressor.fromImage(screenshot.readImage())
        val text = if (keepMetadata && isPng && start.format == ImageFormat.PNG) PngChunks.readText(original) else emptyMap()
        return when (val fitted = SizeFitter.fit(limit, start) { attempt, scale -> ImageCompressor.encodeBytes(image, attempt, text, scale) }) {
            is FitResult.Fits -> Payload(fitted.bytes, "${screenshot.file.nameWithoutExtension}.${fitted.profile.format.extension}", fitted.profile.format.contentType)
            is FitResult.TooLarge -> throw UploadException("The screenshot is still over ${profile.name}'s ${formatMb(limit ?: 0)} limit at its smallest size.")
        }
    }

    private fun formatMb(bytes: Long): String = "%.0f MB".format(java.util.Locale.ROOT, bytes / 1_000_000.0)

    private fun uploadToImgur(payload: Payload): UploadResult {
        val boundary = "----Screencapper${UUID.randomUUID().toString().replace("-", "")}"
        val request = UploadHttp.request("https://api.imgur.com/3/image")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .header("Authorization", "Client-ID ${UploaderProfile.IMGUR_CLIENT_ID}")
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, payload, listOf("type" to "file"), "image")))
            .build()
        val body = send(request)
        val json = JsonParser.parseString(body).asJsonObject
        if (json.get("success")?.asBoolean != true) throw UploadException(UploadHttp.failure(json.get("status")?.asInt ?: 200, body))
        val data = json.getAsJsonObject("data")
        val deletion = data.get("deletehash")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }?.let {
            SecretRefs.toReferences(
                DeletionRequest(DeletionKind.REQUEST, DeletionMethod.DELETE, "https://api.imgur.com/3/image/$it", listOf("Authorization" to "Client-ID ${UploaderProfile.IMGUR_CLIENT_ID}")),
                UploaderProfile.IMGUR,
            )
        }
        return UploadResult(checkLink(data.get("link").asString), deletion)
    }

    private fun uploadToIntegration(integration: HostIntegration, screenshot: Screenshot, payload: Payload, profile: UploaderProfile): UploadResult {
        val settings = profile.integration ?: throw UploadException("${profile.name} isn't set up.")
        val values = TemplateValues.of(screenshot.file, payload.fileName, keepsMetadata(profile))
        val result = integration.upload(IntegrationUpload(profile, settings, payload.bytes, payload.fileName, payload.contentType, values))
        val link = if (result.url.isEmpty()) "" else checkLink(result.url)
        return UploadResult(link, result.deletion?.let { SecretRefs.toReferences(it, profile) })
    }

    private fun uploadToCustomHost(screenshot: Screenshot, payload: Payload, profile: UploaderProfile): UploadResult {
        if (profile.url.isBlank()) throw UploadException("${profile.name} has no upload URL set.")
        val values = TemplateValues.of(screenshot.file, payload.fileName, keepsMetadata(profile))
        val url = UploadTemplate.expandUrl(profile.url, values).trim()

        val builder = HttpRequest.newBuilder(uri(url)).timeout(TIMEOUT)
        if (profile.headers.none { it.first.trim().equals("User-Agent", true) }) builder.header("User-Agent", USER_AGENT)
        for ((key, value) in profile.headers) {
            if (key.trim().lowercase() !in MANAGED_HEADERS) builder.header(key.trim(), UploadTemplate.expand(value, values))
        }

        val publisher = when (profile.body) {
            RequestBody.RAW -> {
                builder.header("Content-Type", payload.contentType)
                HttpRequest.BodyPublishers.ofByteArray(payload.bytes)
            }

            RequestBody.MULTIPART -> {
                val boundary = "----Screencapper${UUID.randomUUID().toString().replace("-", "")}"
                builder.header("Content-Type", "multipart/form-data; boundary=$boundary")
                val fields = profile.formFields.map { (key, value) -> key to UploadTemplate.expandField(key, value, values) }
                HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, payload, fields, profile.fileField))
            }
        }
        builder.method(profile.method.name, publisher)

        val body = send(builder.build())
        val link = when (profile.linkMode) {
            LinkMode.LINK -> checkLink(ResponseUrl.extract(body, UploadTemplate.expand(UploaderPresets.responsePath(profile), values)))
            LinkMode.NONE -> ""
        }
        val deletion = runCatching { profile.deletion.render(values, body)?.let { SecretRefs.toReferences(it, profile) } }
            .onFailure { Screencapper.LOGGER.warn("Couldn't build the deletion request for {}: {}", profile.name, Redaction.redact(describe(it), secretsOf(profile))) }
            .getOrNull()
        return UploadResult(link, deletion)
    }

    private fun sendDeletion(request: DeletionRequest) {
        if (request.integration.isNotEmpty()) {
            val integration = Integrations.find(request.integration) ?: throw UploadException("This version of Screencapper can't delete ${request.integration} uploads.")
            integration.delete(request)
            return
        }
        val builder = HttpRequest.newBuilder(uri(request.url)).timeout(TIMEOUT)
        if (request.headers.none { it.first.trim().equals("User-Agent", true) }) builder.header("User-Agent", USER_AGENT)
        for ((key, value) in request.headers) {
            val name = key.trim().lowercase()
            if (name == "content-type" || name !in MANAGED_HEADERS) builder.header(key.trim(), value)
        }
        val hasBody = request.body.isNotBlank() && request.method != DeletionMethod.GET
        if (hasBody && request.headers.none { it.first.trim().equals("Content-Type", true) }) builder.header("Content-Type", "application/json")
        builder.method(request.method.name, if (hasBody) HttpRequest.BodyPublishers.ofString(request.body) else HttpRequest.BodyPublishers.noBody())
        send(builder.build())
    }

    private fun checkLink(link: String): String {
        if (!isWebLink(link)) throw UploadException("The host didn't return a web link")
        return link.trim()
    }

    private fun uri(url: String): URI = UploadHttp.uri(url)

    private fun describe(error: Throwable): String = when (error) {
        is UploadException -> error.message.orEmpty()
        else -> error.message?.let { "${error.javaClass.simpleName}: $it" } ?: error.javaClass.simpleName
    }

    private fun secretsOf(profile: UploaderProfile): List<String> = Redaction.secretsOf(profile)

    private fun multipart(boundary: String, payload: Payload, fields: List<Pair<String, String>>, fileField: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun write(text: String) = out.write(text.toByteArray(Charsets.UTF_8))
        for ((key, value) in fields) {
            write("--$boundary\r\nContent-Disposition: form-data; name=\"${escape(key)}\"\r\n\r\n$value\r\n")
        }
        val field = fileField.ifBlank { "file" }
        write("--$boundary\r\nContent-Disposition: form-data; name=\"${escape(field)}\"; filename=\"${escape(payload.fileName)}\"\r\n")
        write("Content-Type: ${payload.contentType}\r\n\r\n")
        out.write(payload.bytes)
        write("\r\n--$boundary--\r\n")
        return out.toByteArray()
    }

    private fun send(request: HttpRequest): String {
        return UploadHttp.send(request)
    }

    private fun contentType(screenshot: Screenshot): String =
        if (screenshot.file.extension.lowercase() in setOf("jpg", "jpeg")) "image/jpeg" else "image/png"

    private fun escape(value: String): String = value.replace("\"", "%22").replace("\r", "").replace("\n", "")
}

object ResponseUrl {
    private val TOKEN = Regex("""\{json:([^}]+)}|\${'$'}json:([^${'$'}]+)\${'$'}|\{response}""")
    private val SEGMENT = Regex("""([^.\[\]]+)|\[(\d+)]""")

    fun normalize(path: String): String {
        val trimmed = path.trim()
        val match = TOKEN.matchEntire(trimmed) ?: return trimmed
        if (match.value == "{response}") return ""
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
    }

    fun extract(body: String, path: String): String {
        val normalized = normalize(path)
        if (normalized.isEmpty()) return body.trim()
        if (TOKEN.containsMatchIn(normalized)) return fill(normalized, body)
        val alternatives = normalized.split("||").map(String::trim).filter(String::isNotEmpty)
        val json = parse(body)
        for (alternative in alternatives.dropLast(1)) runCatching { return valueAt(json, alternative) }
        return valueAt(json, alternatives.last())
    }

    fun fill(text: String, body: String): String {
        if (!TOKEN.containsMatchIn(text)) return text
        val json by lazy { parse(body) }
        return TOKEN.replace(text) { match ->
            val jsonPath = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (jsonPath.isEmpty()) body.trim() else valueAt(json, jsonPath)
        }
    }

    private fun parse(body: String): JsonElement =
        runCatching { JsonParser.parseString(body) }.getOrNull() ?: throw UploadException("The response is not JSON")

    private fun valueAt(root: JsonElement, path: String): String {
        var element = root
        for (match in SEGMENT.findAll(path)) {
            val (key, index) = match.destructured
            element = when {
                key.isNotEmpty() && element.isJsonObject -> element.asJsonObject.get(key)
                index.isNotEmpty() && element.isJsonArray -> element.asJsonArray.takeIf { index.toInt() < it.size() }?.get(index.toInt())
                else -> null
            } ?: throw UploadException("No value at \"$path\" in the response")
        }
        if (!element.isJsonPrimitive) throw UploadException("The value at \"$path\" isn't text")
        return element.asString
    }
}
