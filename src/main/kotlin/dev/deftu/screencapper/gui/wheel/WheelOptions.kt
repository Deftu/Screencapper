package dev.deftu.screencapper.gui.wheel

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.deftu.screencapper.screenshot.capture.CaptureKind
import dev.deftu.screencapper.screenshot.capture.compression.CompressionChoice
import dev.deftu.screencapper.screenshot.panorama.PanoramaLayout
import java.util.UUID

enum class WheelShotKind(val id: String, val label: String, val icon: String) {
    NORMAL("normal", "Screenshot", "eye") {
        override fun create(): WheelShot = WheelShot.Normal
        override fun read(json: JsonObject): WheelShot = WheelShot.Normal
    },
    HIGH_RES("high_res", "High-res", "maximise") {
        override fun create(): WheelShot = WheelShot.HighRes(0, 0)
        override fun read(json: JsonObject): WheelShot = WheelShot.HighRes(json.int("width") ?: 0, json.int("height") ?: 0)
    },
    TIMER("timer", "Timer", "bell") {
        override fun create(): WheelShot = WheelShot.Timer(WheelShot.Timer.DEFAULT_SECONDS)
        override fun read(json: JsonObject): WheelShot = WheelShot.Timer(json.int("seconds") ?: WheelShot.Timer.DEFAULT_SECONDS)
    },
    PANORAMA("panorama", "Panorama", "sun") {
        override fun create(): WheelShot = WheelShot.Panorama(PanoramaLayout.DEFAULT_SIZE)
        override fun read(json: JsonObject): WheelShot = WheelShot.Panorama(json.int("faceSize") ?: PanoramaLayout.DEFAULT_SIZE)
    };

    abstract fun create(): WheelShot

    abstract fun read(json: JsonObject): WheelShot

    companion object {
        fun byId(id: String?): WheelShotKind? = entries.firstOrNull { it.id == id }
    }
}

sealed interface WheelShot {
    val kind: WheelShotKind

    val captureKind: CaptureKind get() = CaptureKind.NORMAL

    fun validated(): WheelShot = this

    fun write(json: JsonObject) {}

    data object Normal : WheelShot {
        override val kind: WheelShotKind get() = WheelShotKind.NORMAL
    }

    data class HighRes(val width: Int, val height: Int) : WheelShot {
        override val kind: WheelShotKind get() = WheelShotKind.HIGH_RES

        override val captureKind: CaptureKind get() = CaptureKind.HIGH_RES

        val usesConfiguredSize: Boolean get() = width <= 0 || height <= 0

        fun size(configuredWidth: Int, configuredHeight: Int): Pair<Int, Int> =
            if (usesConfiguredSize) configuredWidth to configuredHeight else width to height

        override fun validated(): WheelShot =
            if (usesConfiguredSize) HighRes(0, 0) else HighRes(width.coerceIn(MIN_SIDE, MAX_SIDE), height.coerceIn(MIN_SIDE, MAX_SIDE))

        override fun write(json: JsonObject) {
            json.addProperty("width", width)
            json.addProperty("height", height)
        }

        companion object {
            const val MIN_SIDE = 16
            const val MAX_SIDE = 16384
        }
    }

    data class Timer(val seconds: Int) : WheelShot {
        override val kind: WheelShotKind get() = WheelShotKind.TIMER

        override fun validated(): WheelShot = Timer(seconds.coerceIn(MIN_SECONDS, MAX_SECONDS))

        override fun write(json: JsonObject) {
            json.addProperty("seconds", seconds)
        }

        companion object {
            const val DEFAULT_SECONDS = 3
            const val MIN_SECONDS = 1
            const val MAX_SECONDS = 60
        }
    }

    data class Panorama(val faceSize: Int) : WheelShot {
        override val kind: WheelShotKind get() = WheelShotKind.PANORAMA

        override val captureKind: CaptureKind get() = CaptureKind.HIGH_RES

        override fun validated(): WheelShot = Panorama(PanoramaLayout.normalizedSize(faceSize))

        override fun write(json: JsonObject) {
            json.addProperty("faceSize", faceSize)
        }
    }
}

data class WheelSlot(val label: String, val icon: String, val detail: String)

data class WheelOption(
    val id: String,
    val shot: WheelShot,
    val label: String,
    val icon: String,
    val hideHud: Boolean = false,
    val hideChat: Boolean = false,
    val hideHand: Boolean = false,
    val compression: CompressionChoice? = null,
) {
    fun validated(): WheelOption = copy(
        shot = shot.validated(),
        label = label.trim().take(WheelOptions.MAX_LABEL).trim().ifEmpty { shot.kind.label },
        icon = icon.takeIf { it in WheelOptions.ICONS } ?: shot.kind.icon,
        compression = compression?.takeIf { it in CompressionChoice.PRESETS },
    )

    fun withKind(kind: WheelShotKind): WheelOption {
        val old = shot.kind
        if (kind == old) return this
        return copy(
            shot = kind.create(),
            label = if (label == old.label) kind.label else label,
            icon = if (icon == old.icon) kind.icon else icon,
        )
    }

    fun slot(configuredWidth: Int, configuredHeight: Int): WheelSlot = WheelSlot(label, icon, detail(configuredWidth, configuredHeight))

    private fun detail(configuredWidth: Int, configuredHeight: Int): String = when (val current = shot) {
        is WheelShot.HighRes -> current.size(configuredWidth, configuredHeight).let { (width, height) -> "$width × $height" }
        is WheelShot.Timer -> if (current.seconds == 1) "Takes it in 1 second" else "Takes it in ${current.seconds} seconds"
        is WheelShot.Panorama -> "Six ${current.faceSize} × ${current.faceSize} faces"
        WheelShot.Normal -> when {
            hideHud -> "Hides the HUD for this shot"
            hideChat && hideHand -> "Hides chat and your hand for this shot"
            hideChat -> "Hides chat for this shot"
            hideHand -> "Hides your hand for this shot"
            else -> "Takes a normal screenshot"
        }
    }
}

object WheelOptions {
    const val MIN_OPTIONS = 2
    const val MAX_OPTIONS = 8
    const val MAX_LABEL = 24

    val ICONS = listOf("maximise", "layers", "spanner", "eye", "eye-off", "bell", "hud", "star", "sun", "moon", "paintbrush", "scissors")

    fun defaults(): List<WheelOption> = listOf(
        WheelOption("clean", WheelShot.Normal, "Clean", "eye-off", hideHud = true),
        WheelOption("no_chat", WheelShot.Normal, "No chat", "hud", hideChat = true),
        WheelOption("high_res_4k", WheelShot.HighRes(3840, 2160), "4K", "maximise", hideHud = true),
        WheelOption("panorama", WheelShot.Panorama(PanoramaLayout.DEFAULT_SIZE), "Panorama", "sun"),
        WheelOption("timer", WheelShot.Timer(WheelShot.Timer.DEFAULT_SECONDS), "Timer", "bell", hideHud = true),
    )

    fun create(): WheelOption = WheelOption(UUID.randomUUID().toString(), WheelShot.Normal, WheelShotKind.NORMAL.label, WheelShotKind.NORMAL.icon)

    fun decode(json: String): List<WheelOption> {
        if (json.isBlank()) return defaults()
        val decoded = runCatching {
            JsonParser.parseString(json).asJsonArray.mapNotNull { runCatching { read(it.asJsonObject) }.getOrNull() }
        }.getOrNull()?.map { it.validated() }?.distinctBy { it.id }?.take(MAX_OPTIONS)
        return decoded?.takeIf { it.size >= MIN_OPTIONS } ?: defaults()
    }

    fun encode(options: List<WheelOption>): String = JsonArray().apply { options.forEach { add(write(it)) } }.toString()

    fun canAdd(options: List<WheelOption>): Boolean = options.size < MAX_OPTIONS

    fun canRemove(options: List<WheelOption>): Boolean = options.size > MIN_OPTIONS

    private fun read(json: JsonObject): WheelOption? {
        val kind = WheelShotKind.byId(json.string("kind")) ?: return null
        return WheelOption(
            id = json.string("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            shot = kind.read(json),
            label = json.string("label").orEmpty(),
            icon = json.string("icon").orEmpty(),
            hideHud = json.bool("hideHud"),
            hideChat = json.bool("hideChat"),
            hideHand = json.bool("hideHand"),
            compression = CompressionChoice.entries.firstOrNull { it.name == json.string("compression") },
        )
    }

    private fun write(option: WheelOption): JsonObject = JsonObject().apply {
        addProperty("id", option.id)
        addProperty("kind", option.shot.kind.id)
        addProperty("label", option.label)
        addProperty("icon", option.icon)
        addProperty("hideHud", option.hideHud)
        addProperty("hideChat", option.hideChat)
        addProperty("hideHand", option.hideHand)
        option.compression?.let { addProperty("compression", it.name) }
        option.shot.write(this)
    }
}

fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in indices || target !in indices) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}

private fun JsonObject.string(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonObject.int(key: String): Int? = runCatching { get(key)?.asInt }.getOrNull()

private fun JsonObject.bool(key: String): Boolean = runCatching { get(key)?.asBoolean }.getOrNull() ?: false
