package dev.deftu.screencapper.upload

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import dev.deftu.lib.DeftuLib
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Formatting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.logging.log4j.LogManager
import dev.deftu.lib.utils.TextHelper
import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.config.RequestMethod
import dev.deftu.screencapper.config.RequestType
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.config.ShareXConfig
import dev.deftu.screencapper.utils.Screenshot
import java.net.URL

object ShareXUploadTask {
    private val urlNameRegex = "\\\$json:(?<path>.+)\\\$".toRegex()

    fun upload(screenshot: Screenshot): Screenshot {
        Screencapper.sendMessage(
            TextHelper.createTranslatableText("${Screencapper.ID}.text.chat.upload.sharex.start")
            .formatted(Formatting.GRAY))
        var screenshot = screenshot
        if (ScreencapperConfig.shareXUploadUrl.isEmpty()) {
            MinecraftClient.getInstance().inGameHud.chatHud.addMessage(TextHelper.createTranslatableText("${Screencapper.ID}.error.upload_missing_url")
                .formatted(Formatting.RED))
            return screenshot
        }
        val response = DeftuLib.browserHttpClient.newCall(Request.Builder()
            .url(ScreencapperConfig.shareXUploadUrl)
            .handleRequestBody(screenshot)
            .build()).execute()
        if (response.isSuccessful) {
            val uploadJson = JsonParser.parseString(response.body?.string())
            if (!uploadJson.isJsonObject) throw IllegalStateException("Invalid response from ShareX host. Expected a JSON object but received a ${uploadJson.javaClass.name}")
            val upload = uploadJson.asJsonObject
            val urlName = ShareXConfig.data.urlPath
            val path = urlNameRegex.find(urlName)?.groups?.get("path")?.value ?: throw IllegalStateException("Invalid URL name format")
            var url: String

            fun getUrl(part: String): JsonElement {
                return if (part.contains("[")) {
                    val index = part.substring(part.indexOf("[") + 1, part.indexOf("]")).toInt()
                    upload.getAsJsonArray(part.substring(0, part.indexOf("["))).get(index)
                } else {
                    upload.get(part)
                }
            }

            url = if (path.contains(".")) {
                path.split(".").filter {
                    it.isNotBlank()
                }.fold(uploadJson) { initial, path ->
                    getUrl(path)
                }.asString
            } else getUrl(path).asString
            screenshot = Screenshot(screenshot.image, screenshot.bytes, screenshot.file, URL(url))
        } else {
            MinecraftClient.getInstance().inGameHud.chatHud.addMessage(TextHelper.createTranslatableText("${Screencapper.ID}.error.upload_error", response.code)
                .formatted(Formatting.RED))
            LogManager.getLogger("Screencapper (ShareX Upload)").error("""
                Upload failed with code ${response.code}:
                ${response.message}
            """.trimIndent())
        }
        response.close()
        Screencapper.sendMessage(TextHelper.createTranslatableText("${Screencapper.ID}.text.chat.upload.sharex.end")
            .formatted(Formatting.GREEN))
        return screenshot
    }

    private fun Request.Builder.handleRequestBody(screenshot: Screenshot) = apply {
        when (ScreencapperConfig.shareXRequestType) {
            RequestType.NONE -> {
                val data = ByteArray(0).toRequestBody()
                when (ScreencapperConfig.shareXRequestMethod) {
                    RequestMethod.POST -> post(data)
                    RequestMethod.PUT -> put(data)
                }
            }
            RequestType.FORM -> {
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(ShareXConfig.data.fileFormName, screenshot.file.name, screenshot.file.asRequestBody("application/octet-stream".toMediaType()))
                ShareXConfig.data.headers?.entrySet()?.forEach {
                    if (!it.value.isJsonPrimitive) return@forEach
                    header(it.key, it.value.asJsonPrimitive.asString)
                }
                ShareXConfig.data.arguments?.entrySet()?.forEach {
                    if (!it.value.isJsonPrimitive) return@forEach
                    multipart.addFormDataPart(it.key, it.value.asJsonPrimitive.asString)
                }
                when (ScreencapperConfig.shareXRequestMethod) {
                    RequestMethod.POST -> post(multipart.build())
                    RequestMethod.PUT -> put(multipart.build())
                }
            }
        }
    }
}
