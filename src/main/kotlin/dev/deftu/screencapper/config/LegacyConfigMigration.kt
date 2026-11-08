package dev.deftu.screencapper.config

import com.google.gson.JsonParser
import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.upload.ResponseUrl
import dev.deftu.screencapper.upload.UploaderProfile
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Moves settings from the 1.x Vigilance config and its sharex.json into the OneConfig config */
object LegacyConfigMigration {
    private val LINE = Regex("""^\s*"?([^"=]+?)"?\s*=\s*(.+?)\s*$""")

    fun run() {
        val dir = File(FabricLoader.getInstance().gameDir.toFile(), "Deftu")
        val toml = File(dir, "screencapper.toml")
        val hostJson = File(dir, "sharex.json")
        val marker = File(FabricLoader.getInstance().configDir.toFile(), "screencapper/.migrated")
        if (marker.exists() || (!toml.isFile && !hostJson.isFile)) return

        runCatching {
            var profile = UploaderProfile(name = "Custom host")
            var useCustom = false

            if (toml.isFile) {
                val values = toml.readLines().mapNotNull { LINE.find(it) }.associate { match ->
                    match.groupValues[1].replace('.', '_').lowercase() to match.groupValues[2].trim('"')
                }
                fun value(suffix: String) = values.entries.firstOrNull { it.key.endsWith(suffix) }?.value
                fun bool(suffix: String, apply: (Boolean) -> Unit) = value(suffix)?.toBooleanStrictOrNull()?.let(apply)

                bool("config_general_toggle") { ScreencapperConfig.enabled = it }
                bool("config_general_auto_copy") { ScreencapperConfig.autoCopy = it }
                bool("config_chat_upload") { ScreencapperConfig.chatUpload = it }
                bool("config_chat_copy") { ScreencapperConfig.chatCopy = it }
                bool("config_chat_open") { ScreencapperConfig.chatOpen = it }
                bool("config_chat_open_folder") { ScreencapperConfig.chatOpenFolder = it }
                bool("config_chat_delete") { ScreencapperConfig.chatDelete = it }
                bool("config_upload_toggle") { ScreencapperConfig.autoUpload = it }
                useCustom = value("config_upload_mode")?.toIntOrNull() == 1
                value("config_upload_sharex_url")?.let { profile = profile.copy(url = it) }
                value("config_upload_sharex_request_method")?.toIntOrNull()?.let {
                    profile = profile.copy(method = RequestMethod.entries.getOrElse(it) { RequestMethod.POST })
                }
                value("config_upload_sharex_request_type")?.toIntOrNull()?.let {
                    profile = profile.copy(body = if (it == 0) RequestBody.RAW else RequestBody.MULTIPART)
                }
            }

            if (hostJson.isFile) {
                val json = JsonParser.parseString(hostJson.readText()).asJsonObject
                fun pairs(key: String) = json.getAsJsonObject(key)?.entrySet()?.filter { it.value.isJsonPrimitive }?.map { it.key to it.value.asString }.orEmpty()
                profile = profile.copy(
                    url = profile.url.ifBlank { json.get("url")?.asString.orEmpty() },
                    fileField = json.get("fileFormName")?.asString?.takeIf { it.isNotBlank() } ?: profile.fileField,
                    responseUrl = json.get("urlPath")?.asString?.let(ResponseUrl::normalize) ?: profile.responseUrl,
                    headers = pairs("headers"),
                    formFields = pairs("arguments"),
                )
            }

            if (profile.url.isNotBlank()) ScreencapperConfig.addProfile(profile, makeDefault = useCustom)
            ScreencapperConfig.save()
            marker.parentFile.mkdirs()
            marker.createNewFile()
            retire(toml)
            retire(hostJson)
        }.onFailure { Screencapper.LOGGER.error("Failed to migrate the old Screencapper config", it) }
    }

    private fun retire(file: File) {
        if (!file.isFile) return
        runCatching {
            Files.move(file.toPath(), File(file.parentFile, "${file.name}.migrated").toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { Screencapper.LOGGER.warn("Couldn't rename {} after migrating it", file, it) }
    }
}
