package dev.deftu.screencapper.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import dev.deftu.lib.DeftuLib
import dev.deftu.screencapper.Screencapper
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.util.concurrent.TimeUnit

private val legacyConfigDir by lazy {
    File(FabricLoader.getInstance().configDir.toFile(), "Deftu")
}

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

        convertLegacy()
        if (!directory.exists()) directory.mkdirs()

        file = File(directory, "sharex.json")
        DeftuLib.multithreader.schedule(this::load, 0, 5, TimeUnit.MINUTES)
        initialized = true
    }

    fun save() {
        if (!file.exists()) file.createNewFile()
        if (!::data.isInitialized) data = ShareXConfigData.createDefault()
        file.writeText(gson.toJson(data))
    }

    fun load() {
        if (!file.exists()) {
            data = ShareXConfigData.createDefault()
            save()
            return
        }

        loadFrom(file)
    }

    fun convertLegacy() {
        val file = File(legacyConfigDir, "sharex.json")
        if (!file.exists()) return

        loadFrom(file)
        save()
        file.delete()
    }

    private fun loadFrom(file: File) {
        val content = JsonParser.parseString(file.readText())
        if (!content.isJsonObject) throw IllegalStateException("${file.name} is not a JSON object!")

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
