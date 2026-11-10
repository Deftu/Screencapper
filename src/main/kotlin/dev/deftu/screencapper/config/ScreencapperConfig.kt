package dev.deftu.screencapper.config

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.gui.UploaderListEditor
import dev.deftu.screencapper.upload.UploaderProfile
import org.polyfrost.oneconfig.api.config.v1.KtConfig
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Visualizer

object ScreencapperConfig : KtConfig("screencapper.json", Screencapper.NAME, Category.QOL, "/assets/screencapper/screencapper.svg") {
    private const val GENERAL = "General"
    private const val CAPTURE = "Capture"
    private const val CHAT = "Chat"
    private const val COMPRESSION = "Compression"

    var enabled by masterToggle()

    var autoCopy by switch(
        name = "Copy new screenshots",
        def = true,
        description = "Copies each new screenshot to the system clipboard.",
        category = GENERAL,
        subcategory = "Capture",
    )

    var fastCapture by switch(
        name = "Fast saving",
        def = true,
        description = "Saves screenshots on Screencapper's own threads with a faster PNG encoder, so taking one never stalls the game. Turn this off if another mod needs vanilla's screenshot saving.",
        category = GENERAL,
        subcategory = "Capture",
    )

    val openScreenshots by button(
        name = "Screenshots",
        description = "Browse, copy, upload and delete your screenshots. The preview's settings are in the HUD editor.",
        category = GENERAL,
        subcategory = "Capture",
        text = "Open",
    ) { ScreenshotActions.openManager() }

    var holdForMenu by switch(
        name = "Hold for capture wheel",
        def = true,
        description = "Holding the screenshot key in a world opens a wheel with high-res, no HUD and timer shots. Tapping it still takes a normal screenshot. Needs fast saving.",
        category = CAPTURE,
        subcategory = "Capture wheel",
    )

    var holdThreshold by slider(
        name = "Hold time",
        def = 300f,
        description = "How long the screenshot key must be held to open the capture wheel.",
        category = CAPTURE,
        subcategory = "Capture wheel",
        unit = "ms",
        min = 150f,
        max = 1000f,
        step = 50f,
    )

    var highResWidth by number(name = "Width", def = 3840f, description = "Width of custom high-res screenshots, in pixels.", category = CAPTURE, subcategory = "High-res", unit = "px", min = 16f, max = 16384f)
    var highResHeight by number(name = "Height", def = 2160f, description = "Height of custom high-res screenshots, in pixels.", category = CAPTURE, subcategory = "High-res", unit = "px", min = 16f, max = 16384f)
    var highResDelay by slider(name = "Delay", def = 3f, description = "Frames to wait at the new size before capturing, so the frame can settle.", category = CAPTURE, subcategory = "High-res", min = 1f, max = 20f, step = 1f)
    var highResHideHud by switch(
        name = "Hide HUD",
        def = false,
        description = "Hides the HUD in high-res screenshots.",
        category = CAPTURE,
        subcategory = "High-res",
    )

    var highResFileName by text(
        name = "File name",
        def = "huge_%time%",
        description = "Name for high-res screenshots. %time% is the date and time, %world% the world or server name.",
        category = CAPTURE,
        subcategory = "High-res",
    )

    var replaceMessage by switch(
        name = "Replace screenshot message",
        def = true,
        description = "Replaces the vanilla screenshot message with a card that has quick actions and follow-up lines.",
        category = CHAT,
        subcategory = "Message",
    )

    var chatView by switch(name = "View", def = true, description = "Shows the action that opens the screenshot in the Screenshots page.", category = CHAT, subcategory = "Actions")
    var chatUpload by switch(name = "Upload", def = true, description = "Shows the upload action, plus an uploader picker when you have more than one.", category = CHAT, subcategory = "Actions")
    var chatCopy by switch(name = "Copy", def = true, description = "Shows the copy action.", category = CHAT, subcategory = "Actions")
    var chatOpen by switch(name = "Open", def = true, description = "Shows the open action.", category = CHAT, subcategory = "Actions")
    var chatOpenFolder by switch(name = "Open folder", def = true, description = "Shows the open folder action.", category = CHAT, subcategory = "Actions")
    var chatDelete by switch(name = "Delete", def = true, description = "Shows the delete action.", category = CHAT, subcategory = "Actions")

    var confirmChatDeletes by switch(
        name = "Confirm deletes",
        def = true,
        description = "Asks before deleting a screenshot from chat.",
        category = CHAT,
        subcategory = "Follow-ups",
    )

    var chatUploadLinks by switch(
        name = "Post upload links",
        def = true,
        description = "Adds a line with the link when an upload finishes.",
        category = CHAT,
        subcategory = "Follow-ups",
    )

    var autoUpload by switch(
        name = "Upload new screenshots",
        def = false,
        description = "Uploads each new screenshot to your default uploader and copies its link.",
        category = GENERAL,
        subcategory = "Uploading",
    )

    var uploaders by Provider(
        "[]", "Uploaders", "Imgur and Catbox are built in. Add your own hosts, or import a ShareX .sxcu file. The starred uploader is used by default.", GENERAL, "Uploaders",
        String::class.java, Visualizer { prop -> UploaderListEditor(prop) },
    ).fullWidth()

    var defaultUploader by Provider(UploaderProfile.IMGUR_ID, null, null, null, null, String::class.java, Visualizer.TextVisualizer()) {
        addMetadata("hidden", true)
    }

    val compressionPresets by info(
        name = "Presets",
        description = "Lossless: PNG with every pixel kept. The largest files.\n" +
            "Smaller PNG: fewer colours. Looks the same at a glance, often about half the size.\n" +
            "Balanced: high quality JPEG. Hard to tell apart, about 15% of the size.\n" +
            "Small: JPEG at most 1920 pixels wide. About 7% of the size.\n" +
            "Tiny: JPEG at most 1280 pixels wide. About 2% of the size, for quick sharing.\n" +
            "Custom shows every option. Sizes are rough and depend on the scene.",
        category = COMPRESSION,
        subcategory = "Presets",
    )

    var normalChoice by dropdown(name = "Save as", defaultOption = dev.deftu.screencapper.screenshot.capture.compression.CompressionChoice.LOSSLESS, options = dev.deftu.screencapper.screenshot.capture.compression.CompressionChoice.FOR_SAVING, description = "How normal screenshots are saved.", category = COMPRESSION, subcategory = "Normal screenshots", stringTransformer = { it.label })
    var normalFormat by dropdown(name = "Format", defaultOption = dev.deftu.screencapper.screenshot.capture.compression.ImageFormat.PNG, options = dev.deftu.screencapper.screenshot.capture.compression.ImageFormat.entries.toTypedArray(), description = "PNG keeps text and edges sharp. JPEG is far smaller but slightly soft.", category = COMPRESSION, subcategory = "Normal screenshots", stringTransformer = { it.displayName })
    var normalQuality by slider(name = "Quality", def = 90f, description = "Higher looks better and makes bigger files.", category = COMPRESSION, subcategory = "Normal screenshots", min = 10f, max = 100f, step = 5f)
    var normalColours by dropdown(name = "Colours", defaultOption = 0, options = arrayOf(0, 256, 128, 64), description = "Fewer colours make smaller files. 256 looks the same at a glance.", category = COMPRESSION, subcategory = "Normal screenshots", stringTransformer = { if (it == 0) "All colours" else "$it colours" })
    var normalDither by switch(name = "Dithering", def = true, description = "Mixes nearby colours so smooth gradients, like the sky, don't show bands.", category = COMPRESSION, subcategory = "Normal screenshots")
    var normalResize by dropdown(name = "Resize", defaultOption = dev.deftu.screencapper.screenshot.capture.compression.ResizeMode.NONE, options = dev.deftu.screencapper.screenshot.capture.compression.ResizeMode.entries.toTypedArray(), description = "Smaller images make much smaller files.", category = COMPRESSION, subcategory = "Normal screenshots", stringTransformer = { it.displayName })
    var normalScale by slider(name = "Scale", def = 50f, description = "The size to save at, as a share of the original.", category = COMPRESSION, subcategory = "Normal screenshots", unit = "%", min = 10f, max = 100f, step = 5f)
    var normalMaxWidth by number(name = "Max width", def = 1920f, description = "Wider images are scaled down to this width. Narrower ones are left alone.", category = COMPRESSION, subcategory = "Normal screenshots", unit = "px", min = 160f, max = 16384f)
    var normalKeepOriginal by switch(name = "Keep the original", def = false, description = "Also saves an untouched PNG in screenshots/original.", category = COMPRESSION, subcategory = "Normal screenshots")

    val normalCompressionFields: dev.deftu.screencapper.screenshot.capture.compression.CompressionFields
        get() = dev.deftu.screencapper.screenshot.capture.compression.CompressionFields(::normalChoice, ::normalFormat, ::normalQuality, ::normalColours, ::normalDither, ::normalResize, ::normalScale, ::normalMaxWidth, ::normalKeepOriginal)

    var highResChoice by dropdown(name = "Save as", defaultOption = dev.deftu.screencapper.screenshot.capture.compression.CompressionChoice.LOSSLESS, options = dev.deftu.screencapper.screenshot.capture.compression.CompressionChoice.FOR_SAVING, description = "How high-res screenshots are saved. Lossless high-res shots can be very large.", category = COMPRESSION, subcategory = "High-res screenshots", stringTransformer = { it.label })
    var highResFormat by dropdown(name = "Format", defaultOption = dev.deftu.screencapper.screenshot.capture.compression.ImageFormat.PNG, options = dev.deftu.screencapper.screenshot.capture.compression.ImageFormat.entries.toTypedArray(), description = "PNG keeps text and edges sharp. JPEG is far smaller but slightly soft.", category = COMPRESSION, subcategory = "High-res screenshots", stringTransformer = { it.displayName })
    var highResQuality by slider(name = "Quality", def = 90f, description = "Higher looks better and makes bigger files.", category = COMPRESSION, subcategory = "High-res screenshots", min = 10f, max = 100f, step = 5f)
    var highResColours by dropdown(name = "Colours", defaultOption = 0, options = arrayOf(0, 256, 128, 64), description = "Fewer colours make smaller files. 256 looks the same at a glance.", category = COMPRESSION, subcategory = "High-res screenshots", stringTransformer = { if (it == 0) "All colours" else "$it colours" })
    var highResDither by switch(name = "Dithering", def = true, description = "Mixes nearby colours so smooth gradients, like the sky, don't show bands.", category = COMPRESSION, subcategory = "High-res screenshots")
    var highResResize by dropdown(name = "Resize", defaultOption = dev.deftu.screencapper.screenshot.capture.compression.ResizeMode.NONE, options = dev.deftu.screencapper.screenshot.capture.compression.ResizeMode.entries.toTypedArray(), description = "Smaller images make much smaller files.", category = COMPRESSION, subcategory = "High-res screenshots", stringTransformer = { it.displayName })
    var highResScale by slider(name = "Scale", def = 50f, description = "The size to save at, as a share of the original.", category = COMPRESSION, subcategory = "High-res screenshots", unit = "%", min = 10f, max = 100f, step = 5f)
    var highResMaxWidth by number(name = "Max width", def = 1920f, description = "Wider images are scaled down to this width. Narrower ones are left alone.", category = COMPRESSION, subcategory = "High-res screenshots", unit = "px", min = 160f, max = 16384f)
    var highResKeepOriginal by switch(name = "Keep the original", def = false, description = "Also saves an untouched PNG in screenshots/original.", category = COMPRESSION, subcategory = "High-res screenshots")

    val highResCompressionFields: dev.deftu.screencapper.screenshot.capture.compression.CompressionFields
        get() = dev.deftu.screencapper.screenshot.capture.compression.CompressionFields(::highResChoice, ::highResFormat, ::highResQuality, ::highResColours, ::highResDither, ::highResResize, ::highResScale, ::highResMaxWidth, ::highResKeepOriginal)

    var uploadChoice by dropdown(name = "Upload as", defaultOption = dev.deftu.screencapper.screenshot.capture.compression.CompressionChoice.BALANCED, options = dev.deftu.screencapper.screenshot.capture.compression.CompressionChoice.FOR_UPLOADS, description = "How screenshots are re-encoded before uploading. Files are shrunk further when a host's size limit needs it.", category = COMPRESSION, subcategory = "Uploads", stringTransformer = { it.label })
    var uploadFormat by dropdown(name = "Format", defaultOption = dev.deftu.screencapper.screenshot.capture.compression.ImageFormat.PNG, options = dev.deftu.screencapper.screenshot.capture.compression.ImageFormat.entries.toTypedArray(), description = "PNG keeps text and edges sharp. JPEG is far smaller but slightly soft.", category = COMPRESSION, subcategory = "Uploads", stringTransformer = { it.displayName })
    var uploadQuality by slider(name = "Quality", def = 90f, description = "Higher looks better and makes bigger files.", category = COMPRESSION, subcategory = "Uploads", min = 10f, max = 100f, step = 5f)
    var uploadColours by dropdown(name = "Colours", defaultOption = 0, options = arrayOf(0, 256, 128, 64), description = "Fewer colours make smaller files. 256 looks the same at a glance.", category = COMPRESSION, subcategory = "Uploads", stringTransformer = { if (it == 0) "All colours" else "$it colours" })
    var uploadDither by switch(name = "Dithering", def = true, description = "Mixes nearby colours so smooth gradients, like the sky, don't show bands.", category = COMPRESSION, subcategory = "Uploads")
    var uploadResize by dropdown(name = "Resize", defaultOption = dev.deftu.screencapper.screenshot.capture.compression.ResizeMode.NONE, options = dev.deftu.screencapper.screenshot.capture.compression.ResizeMode.entries.toTypedArray(), description = "Smaller images make much smaller files.", category = COMPRESSION, subcategory = "Uploads", stringTransformer = { it.displayName })
    var uploadScale by slider(name = "Scale", def = 50f, description = "The size to save at, as a share of the original.", category = COMPRESSION, subcategory = "Uploads", unit = "%", min = 10f, max = 100f, step = 5f)
    var uploadMaxWidth by number(name = "Max width", def = 1920f, description = "Wider images are scaled down to this width. Narrower ones are left alone.", category = COMPRESSION, subcategory = "Uploads", unit = "px", min = 160f, max = 16384f)

    val uploadCompressionFields: dev.deftu.screencapper.screenshot.capture.compression.CompressionFields
        get() = dev.deftu.screencapper.screenshot.capture.compression.CompressionFields(::uploadChoice, ::uploadFormat, ::uploadQuality, ::uploadColours, ::uploadDither, ::uploadResize, ::uploadScale, ::uploadMaxWidth, null)

    /** Comma-separated ids of the built-in uploaders that keep metadata */
    var builtInUploaderMetadata by Provider("", null, null, null, null, String::class.java, Visualizer.TextVisualizer()) {
        addMetadata("hidden", true)
    }

    init {
        hideIf(::holdThreshold) { !holdForMenu }
        addCallback(::holdForMenu) { refreshHidden(); false }
    }

    init {
        listOf(::chatView, ::chatUpload, ::chatCopy, ::chatOpen, ::chatOpenFolder, ::chatDelete, ::confirmChatDeletes, ::chatUploadLinks).forEach { hideIf(it) { !replaceMessage } }
        addCallback(::replaceMessage) { refreshHidden(); false }
    }

    init {
        for (fields in listOf(normalCompressionFields, highResCompressionFields, uploadCompressionFields)) {
            fields.registerVisibility(hideIf = { prop, condition -> hideIf(prop, condition) }, refreshOn = { prop -> addCallback(prop.name, Runnable { refreshHidden() }) })
        }
    }

    val profiles: List<UploaderProfile>
        get() = UploaderProfile.BUILT_IN + UploaderProfile.decodeList(uploaders)

    val defaultProfile: UploaderProfile
        get() = profiles.firstOrNull { it.id == defaultUploader } ?: UploaderProfile.IMGUR

    fun addProfile(profile: UploaderProfile, makeDefault: Boolean) {
        uploaders = UploaderProfile.encodeList(profiles + profile)
        if (makeDefault) defaultUploader = profile.id
        save()
    }

    private fun refreshHidden() {
        tree?.map?.values?.forEach { (it as? Property<*>)?.revaluateDisplay() }
    }
}
