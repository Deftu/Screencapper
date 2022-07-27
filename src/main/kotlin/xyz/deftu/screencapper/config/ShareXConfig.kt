package xyz.deftu.screencapper.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import xyz.deftu.screencapper.utils.Multithreading
import java.io.File
import java.util.concurrent.TimeUnit

object ShareXConfig {
    private var initialized = false
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .setLenient()
        .create()

    lateinit var file: File
        private set
    lateinit var data: ShareXConfigData
        private set

    fun initialize(directory: File) {
        if (initialized) return
        if (!directory.exists()) directory.mkdirs()
        file = File(directory, "sharex.json")
        Multithreading.schedule(this::load, 0, 5, TimeUnit.MINUTES)
        initialized = true
    }

    fun save() {
        if (!file.exists()) file.createNewFile()
        if (!::data.isInitialized) data = ShareXConfigData.createDefault()
        file.writeText(gson.toJson(data))
    }

    fun load() {
        if (!file.exists()) save()
        val content = JsonParser.parseString(file.readText())
        if (!content.isJsonObject) throw IllegalStateException("sharex.json is not a JSON object!")
        data = gson.fromJson(content, ShareXConfigData::class.java)
    }
}

data class ShareXConfigData(
    var headers: JsonObject?,
    var arguments: JsonObject?,
    var fileFormName: String,
    var url: String,
    var urlPath: String
) {
    companion object {
        fun createDefault() = ShareXConfigData(
            JsonObject(),
            JsonObject(),
            "file",
            "",
            "\$json:url$"
        )
    }
}
