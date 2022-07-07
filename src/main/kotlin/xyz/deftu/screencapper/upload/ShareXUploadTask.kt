package xyz.deftu.screencapper.upload

import com.google.gson.GsonBuilder
import net.minecraft.client.MinecraftClient
import net.minecraft.text.TranslatableText
import net.minecraft.util.Formatting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.logging.log4j.LogManager
import xyz.deftu.screencapper.Screencapper
import xyz.deftu.screencapper.config.RequestType
import xyz.deftu.screencapper.config.ScreencapperConfig
import xyz.deftu.screencapper.config.ShareXConfig
import xyz.deftu.screencapper.utils.Screenshot
import java.io.FileInputStream
import java.net.URL

object ShareXUploadTask {
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .setLenient()
        .create()

    fun upload(screenshot: Screenshot): Screenshot {
        var screenshot = screenshot
        if (ScreencapperConfig.shareXUploadUrl.isEmpty()) {
            MinecraftClient.getInstance().inGameHud.chatHud.addMessage(TranslatableText("${Screencapper.ID}.error.upload_missing_url")
                .formatted(Formatting.RED))
            return screenshot
        }
        val response = Screencapper.httpClient.newCall(Request.Builder()
            .url(ScreencapperConfig.shareXUploadUrl)
            .post(handleRequestBody(screenshot))
            .build()).execute()
        if (response.isSuccessful) {
            val upload = gson.fromJson(response.body?.string(), UploadResponse::class.java)
            if (upload.url != null) {
                screenshot = Screenshot(screenshot.image, screenshot.file, URL(upload.url))
            }
        } else {
            MinecraftClient.getInstance().inGameHud.chatHud.addMessage(TranslatableText("${Screencapper.ID}.error.upload_error", response.code)
                .formatted(Formatting.RED))
            LogManager.getLogger("Screencapper (ShareX Upload)").error("""
                Upload failed with code ${response.code}:
                ${response.message}
            """.trimIndent())
        }
        response.close()
        return screenshot
    }

    private fun handleRequestBody(screenshot: Screenshot): RequestBody {
        return when (ScreencapperConfig.shareXRequestType) {
            RequestType.NONE -> ByteArray(0).toRequestBody()
            RequestType.JSON -> {
                val json = ShareXConfig.json
                json.addProperty("file", FileInputStream(screenshot.file).readBytes().toString())
                gson.toJson(json).toRequestBody("application/json".toMediaType())
            }
            RequestType.FORM -> {
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", screenshot.file.name, screenshot.file.asRequestBody("application/octet-stream".toMediaType()))
                ShareXConfig.json.entrySet().forEach {
                    if (!it.value.isJsonPrimitive) return@forEach
                    multipart.addFormDataPart(it.key, it.value.asJsonPrimitive.asString)
                }
                multipart.build()
            }
        }
    }
}

private data class UploadResponse(
    val url: String?
)
