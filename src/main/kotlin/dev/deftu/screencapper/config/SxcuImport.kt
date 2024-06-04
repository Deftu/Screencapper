package dev.deftu.screencapper.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import org.apache.logging.log4j.LogManager
import java.io.File

object SxcuImport {
    private val logger = LogManager.getLogger("SXCU Importer")
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .setLenient()
        .create()

    fun import(file: File) {
        val sxcu = gson.fromJson(file.readText(), Sxcu::class.java)
        if (sxcu.version != null) {
            if (sxcu.version.substringBefore(".").toInt() < 13) throw IllegalStateException("SXCU version is too old. It must be version 13 or higher.")
        } else logger.warn("Could not check SXCU config's version... This could cause problems!")
        if (!sxcu.destinationType.lowercase().contains("imageuploader")) throw IllegalStateException("SXCU destination type does not include images. This is required.")

        ScreencapperConfig.shareXRequestMethod = sxcu.requestMethod ?: RequestMethod.POST
        ScreencapperConfig.shareXUploadUrl = sxcu.requestUrl
        ScreencapperConfig.markDirty()
        ScreencapperConfig.writeData()

        ShareXConfig.data.url = sxcu.requestUrl
        ShareXConfig.data.urlPath = sxcu.url
        ShareXConfig.data.fileFormName = sxcu.fileFormName
        sxcu.headers?.let { ShareXConfig.data.headers = it }
        sxcu.arguments?.let { ShareXConfig.data.arguments = it }
        ShareXConfig.save()
    }
}

data class Sxcu(
    @SerializedName("Version") val version: String?,
    @SerializedName("DestinationType") val destinationType: String,
    @SerializedName("RequestMethod") val requestMethod: RequestMethod?,
    @SerializedName("RequestURL") val requestUrl: String,
    @SerializedName("Headers") val headers: JsonObject?,
    @SerializedName("Arguments") val arguments: JsonObject?,
    @SerializedName("FileFormName") val fileFormName: String,
    @SerializedName("URL") val url: String
)
