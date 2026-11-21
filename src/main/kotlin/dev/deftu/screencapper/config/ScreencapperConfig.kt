package dev.deftu.screencapper.config

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.gui.ChatActionsRow
import dev.deftu.screencapper.gui.ScreenshotKey
import dev.deftu.screencapper.gui.UploaderListEditor
import dev.deftu.screencapper.gui.wheel.WheelOptionsEditor
import dev.deftu.screencapper.screenshot.Panoramas
import dev.deftu.screencapper.screenshot.capture.compression.CompressionChoice
import dev.deftu.screencapper.screenshot.capture.compression.CompressionFields
import dev.deftu.screencapper.screenshot.capture.compression.ImageFormat
import dev.deftu.screencapper.screenshot.capture.compression.ResizeMode
import dev.deftu.screencapper.screenshot.capture.highres.HighResRenderer
import dev.deftu.screencapper.screenshot.panorama.AutoPanoramas
import dev.deftu.screencapper.upload.Secrets
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.UploaderSecrets
import org.polyfrost.oneconfig.api.config.v1.KtConfig
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.Visualizer

object ScreencapperConfig : KtConfig("screencapper.json", Screencapper.NAME, Category.QOL, "/assets/screencapper/screencapper.svg") {
    private const val GENERAL = "General"
    private const val CAPTURE = "Capture"
    private const val SAVING = "Saving"
    private const val CHAT = "Chat"
    private const val UPLOADING = "Uploading"
    private const val PRIVACY = "Privacy"
    private const val ABOUT = "About"

    private const val WHEEL = "Capture wheel"
    private const val HIGH_RES = "High-res"
    private const val NORMAL_SHOTS = "Normal screenshots"
    private const val HIGH_RES_SHOTS = "High-res screenshots"
    private const val SAVED_WITH = "Saved with screenshots"
    private const val LOCATION = "Location"

    private const val FORMAT_INFO = "PNG keeps edges sharp; JPEG is far smaller but slightly soft."
    private const val QUALITY_INFO = "Higher looks better and makes bigger files."
    private const val COLOURS_INFO = "Fewer colours make smaller files; 256 looks the same at a glance."
    private const val DITHER_INFO = "Mixes nearby colours so gradients like the sky don't band."
    private const val RESIZE_INFO = "Smaller images make much smaller files."
    private const val SCALE_INFO = "The size to save at, as a share of the original."
    private const val MAX_WIDTH_INFO = "Wider images are scaled down to this width."
    private const val KEEP_ORIGINAL_INFO = "Also saves an untouched PNG in screenshots/original."

    var enabled by masterToggle()

    val openScreenshots by button(
        name = "Screenshots",
        description = "Browse, copy, upload and delete screenshots; the preview's settings are in the HUD editor.",
        category = GENERAL,
        subcategory = GENERAL,
        text = "Open",
    ) { ScreenshotActions.openManager() }

    var autoCopy by switch(name = "Copy new screenshots", def = true, description = "Copies each new screenshot to the clipboard.", category = GENERAL, subcategory = GENERAL)

    var shutterSound by switch(name = "Shutter sound", def = true, description = "Plays a camera shutter when you take a screenshot.", category = GENERAL, subcategory = GENERAL)
    var shutterVolume by slider(name = "Volume", def = 100f, description = "How loud the shutter is.", category = GENERAL, subcategory = GENERAL, unit = "%", min = 0f, max = 100f, step = 5f)

    var advancementShots by switch(name = "Screenshot advancements", def = false, description = "Takes a screenshot when you earn an advancement.", category = GENERAL, subcategory = GENERAL)

    val resetTitlePanorama by button(
        name = "Reset title background",
        description = "Switches the title screen from your panorama back to Minecraft's own.",
        category = GENERAL,
        subcategory = GENERAL,
        text = "Reset",
    ) { Panoramas.resetTitleBackground() }

    var leavePanorama by switch(name = "Panorama on leave", def = false, description = "Takes a panorama when you leave a world and makes it your title background.", category = GENERAL, subcategory = GENERAL)
    var leavePanoramaSize by dropdown(name = "Face size", defaultOption = 1024, options = AutoPanoramas.SIZES.toTypedArray(), description = "Bigger faces look sharper but make leaving slower.", category = GENERAL, subcategory = GENERAL, stringTransformer = { "$it px" })

    @Suppress("unused")
    private val screenshotKey by Provider(
        null, "Screenshot key", "The vanilla screenshot key, so changing it here also changes it in Controls.", CAPTURE, WHEEL,
        Void::class.java, ScreenshotKey.visualizer,
    )

    var holdForMenu by switch(
        name = "Hold for capture wheel",
        def = true,
        description = "Holding the screenshot key opens a wheel of high-res, no-HUD and timer shots while a tap still takes a normal one (needs fast saving).",
        category = CAPTURE,
        subcategory = WHEEL,
    )

    var holdThreshold by slider(name = "Hold time", def = 300f, description = "How long to hold the key before the wheel opens.", category = CAPTURE, subcategory = WHEEL, unit = "ms", min = 150f, max = 1000f, step = 50f)

    var wheelOptions by Provider(
        "", "Wheel options", "Which shots the wheel offers, in what order, and how each one is taken.", CAPTURE, WHEEL,
        String::class.java, Visualizer { prop -> WheelOptionsEditor(prop) },
    ).fullWidth()

    var highResWidth by number(name = "Width", def = 3840f, description = "Width of custom high-res screenshots.", category = CAPTURE, subcategory = HIGH_RES, unit = "px", min = 16f, max = 16384f)
    var highResHeight by number(name = "Height", def = 2160f, description = "Height of custom high-res screenshots.", category = CAPTURE, subcategory = HIGH_RES, unit = "px", min = 16f, max = 16384f)
    var highResDelay by slider(name = "Delay", def = 3f, description = "Frames to wait at the new size so the image can settle.", category = CAPTURE, subcategory = HIGH_RES, min = 1f, max = 20f, step = 1f)
    var highResHideHud by Provider(false, null, null, null, null, Boolean::class.javaObjectType, Visualizer.SwitchVisualizer()) {
        addMetadata("hidden", true)
    }

    var fastCapture by switch(
        name = "Fast saving",
        def = true,
        description = "Saves on background threads with a faster encoder; turn it off if another mod needs vanilla saving.",
        category = SAVING,
        subcategory = GENERAL,
    )

    var fileName by text(
        name = "File name",
        def = "%time%",
        description = "Accepts %time%, %date%, %clock%, %year%, %month%, %day%, %world%, %dimension%, %player%, %version% and %count%; use / for folders (needs fast saving).",
        category = SAVING,
        subcategory = NORMAL_SHOTS,
    )

    var normalChoice by dropdown(name = "Save as", defaultOption = CompressionChoice.LOSSLESS, options = CompressionChoice.FOR_SAVING, description = "Lossless keeps every pixel, the other presets make smaller files, and Custom shows every option.", category = SAVING, subcategory = NORMAL_SHOTS, stringTransformer = { it.label })
    var normalFormat by dropdown(name = "Format", defaultOption = ImageFormat.PNG, options = ImageFormat.entries.toTypedArray(), description = FORMAT_INFO, category = SAVING, subcategory = NORMAL_SHOTS, stringTransformer = { it.displayName })
    var normalQuality by slider(name = "Quality", def = 90f, description = QUALITY_INFO, category = SAVING, subcategory = NORMAL_SHOTS, min = 10f, max = 100f, step = 5f)
    var normalColours by dropdown(name = "Colours", defaultOption = 0, options = arrayOf(0, 256, 128, 64), description = COLOURS_INFO, category = SAVING, subcategory = NORMAL_SHOTS, stringTransformer = { if (it == 0) "All colours" else "$it colours" })
    var normalDither by switch(name = "Dithering", def = true, description = DITHER_INFO, category = SAVING, subcategory = NORMAL_SHOTS)
    var normalResize by dropdown(name = "Resize", defaultOption = ResizeMode.NONE, options = ResizeMode.entries.toTypedArray(), description = RESIZE_INFO, category = SAVING, subcategory = NORMAL_SHOTS, stringTransformer = { it.displayName })
    var normalScale by slider(name = "Scale", def = 50f, description = SCALE_INFO, category = SAVING, subcategory = NORMAL_SHOTS, unit = "%", min = 10f, max = 100f, step = 5f)
    var normalMaxWidth by number(name = "Max width", def = 1920f, description = MAX_WIDTH_INFO, category = SAVING, subcategory = NORMAL_SHOTS, unit = "px", min = 160f, max = 16384f)
    var normalKeepOriginal by switch(name = "Keep the original", def = false, description = KEEP_ORIGINAL_INFO, category = SAVING, subcategory = NORMAL_SHOTS)

    val normalCompressionFields: CompressionFields
        get() = CompressionFields(::normalChoice, ::normalFormat, ::normalQuality, ::normalColours, ::normalDither, ::normalResize, ::normalScale, ::normalMaxWidth, ::normalKeepOriginal)

    var highResFileName by text(
        name = "File name",
        def = "huge_%time%",
        description = "Accepts the same %tokens% as normal screenshots; use / for folders.",
        category = SAVING,
        subcategory = HIGH_RES_SHOTS,
    )

    var highResChoice by dropdown(name = "Save as", defaultOption = CompressionChoice.LOSSLESS, options = CompressionChoice.FOR_SAVING, description = "How high-res screenshots are saved; lossless ones can be very large.", category = SAVING, subcategory = HIGH_RES_SHOTS, stringTransformer = { it.label })
    var highResFormat by dropdown(name = "Format", defaultOption = ImageFormat.PNG, options = ImageFormat.entries.toTypedArray(), description = FORMAT_INFO, category = SAVING, subcategory = HIGH_RES_SHOTS, stringTransformer = { it.displayName })
    var highResQuality by slider(name = "Quality", def = 90f, description = QUALITY_INFO, category = SAVING, subcategory = HIGH_RES_SHOTS, min = 10f, max = 100f, step = 5f)
    var highResColours by dropdown(name = "Colours", defaultOption = 0, options = arrayOf(0, 256, 128, 64), description = COLOURS_INFO, category = SAVING, subcategory = HIGH_RES_SHOTS, stringTransformer = { if (it == 0) "All colours" else "$it colours" })
    var highResDither by switch(name = "Dithering", def = true, description = DITHER_INFO, category = SAVING, subcategory = HIGH_RES_SHOTS)
    var highResResize by dropdown(name = "Resize", defaultOption = ResizeMode.NONE, options = ResizeMode.entries.toTypedArray(), description = RESIZE_INFO, category = SAVING, subcategory = HIGH_RES_SHOTS, stringTransformer = { it.displayName })
    var highResScale by slider(name = "Scale", def = 50f, description = SCALE_INFO, category = SAVING, subcategory = HIGH_RES_SHOTS, unit = "%", min = 10f, max = 100f, step = 5f)
    var highResMaxWidth by number(name = "Max width", def = 1920f, description = MAX_WIDTH_INFO, category = SAVING, subcategory = HIGH_RES_SHOTS, unit = "px", min = 160f, max = 16384f)
    var highResKeepOriginal by switch(name = "Keep the original", def = false, description = KEEP_ORIGINAL_INFO, category = SAVING, subcategory = HIGH_RES_SHOTS)

    val highResCompressionFields: CompressionFields
        get() = CompressionFields(::highResChoice, ::highResFormat, ::highResQuality, ::highResColours, ::highResDither, ::highResResize, ::highResScale, ::highResMaxWidth, ::highResKeepOriginal)

    var replaceMessage by switch(
        name = "Replace screenshot message",
        def = true,
        description = "Swaps the vanilla screenshot message for a card with quick actions.",
        category = CHAT,
        subcategory = GENERAL,
    )

    private val chatActions by Provider(
        null, "Actions", "Buttons shown on the screenshot card.", CHAT, GENERAL,
        Void::class.java, Visualizer { _ -> ChatActionsRow() },
    )

    var chatView by switch(name = "View", def = true, description = "Shows the action that opens the screenshot in the Screenshots page.", category = CHAT, subcategory = GENERAL)
    var chatUpload by switch(name = "Upload", def = true, description = "Shows the upload action, plus an uploader picker when you have more than one.", category = CHAT, subcategory = GENERAL)
    var chatCopy by switch(name = "Copy", def = true, description = "Shows the copy action.", category = CHAT, subcategory = GENERAL)
    var chatOpen by switch(name = "Open", def = true, description = "Shows the open action.", category = CHAT, subcategory = GENERAL)
    var chatOpenFolder by switch(name = "Open folder", def = true, description = "Shows the open folder action.", category = CHAT, subcategory = GENERAL)
    var chatDelete by switch(name = "Delete", def = true, description = "Shows the delete action.", category = CHAT, subcategory = GENERAL)

    var confirmChatDeletes by switch(name = "Confirm deletes", def = true, description = "Asks before deleting a screenshot from chat.", category = CHAT, subcategory = GENERAL)

    var chatUploadLinks by switch(name = "Post upload links", def = true, description = "Adds a line with the link when an upload finishes.", category = CHAT, subcategory = GENERAL)

    var uploaders by Provider(
        "[]", "Uploaders", "Imgur and Catbox are built in, and the starred uploader is the default.", UPLOADING, GENERAL,
        String::class.java, Visualizer { prop -> UploaderListEditor(prop) },
    ).fullWidth()

    var autoUpload by switch(name = "Upload new screenshots", def = false, description = "Uploads each new screenshot to the default uploader and copies its link.", category = UPLOADING, subcategory = GENERAL)

    var uploadChoice by dropdown(name = "Upload as", defaultOption = CompressionChoice.BALANCED, options = CompressionChoice.FOR_UPLOADS, description = "How screenshots are re-encoded before uploading; files shrink further if a host needs it.", category = UPLOADING, subcategory = GENERAL, stringTransformer = { it.label })
    var uploadFormat by dropdown(name = "Format", defaultOption = ImageFormat.PNG, options = ImageFormat.entries.toTypedArray(), description = FORMAT_INFO, category = UPLOADING, subcategory = GENERAL, stringTransformer = { it.displayName })
    var uploadQuality by slider(name = "Quality", def = 90f, description = QUALITY_INFO, category = UPLOADING, subcategory = GENERAL, min = 10f, max = 100f, step = 5f)
    var uploadColours by dropdown(name = "Colours", defaultOption = 0, options = arrayOf(0, 256, 128, 64), description = COLOURS_INFO, category = UPLOADING, subcategory = GENERAL, stringTransformer = { if (it == 0) "All colours" else "$it colours" })
    var uploadDither by switch(name = "Dithering", def = true, description = DITHER_INFO, category = UPLOADING, subcategory = GENERAL)
    var uploadResize by dropdown(name = "Resize", defaultOption = ResizeMode.NONE, options = ResizeMode.entries.toTypedArray(), description = RESIZE_INFO, category = UPLOADING, subcategory = GENERAL, stringTransformer = { it.displayName })
    var uploadScale by slider(name = "Scale", def = 50f, description = SCALE_INFO, category = UPLOADING, subcategory = GENERAL, unit = "%", min = 10f, max = 100f, step = 5f)
    var uploadMaxWidth by number(name = "Max width", def = 1920f, description = MAX_WIDTH_INFO, category = UPLOADING, subcategory = GENERAL, unit = "px", min = 160f, max = 16384f)

    val uploadCompressionFields: CompressionFields
        get() = CompressionFields(::uploadChoice, ::uploadFormat, ::uploadQuality, ::uploadColours, ::uploadDither, ::uploadResize, ::uploadScale, ::uploadMaxWidth, null)

    var defaultUploader by Provider(UploaderProfile.IMGUR_ID, null, null, null, null, String::class.java, Visualizer.TextVisualizer()) {
        addMetadata("hidden", true)
    }

    var titlePanorama by Provider("", null, null, null, null, String::class.java, Visualizer.TextVisualizer()) {
        addMetadata("hidden", true)
    }

    var builtInUploaderMetadata by Provider("", null, null, null, null, String::class.java, Visualizer.TextVisualizer()) {
        addMetadata("hidden", true)
    }

    var metadataTime by switch(name = "Date and time", def = true, description = "Saves when the screenshot was taken, so date search still works after the file is copied.", category = PRIVACY, subcategory = SAVED_WITH)
    var metadataVersion by switch(name = "Game version", def = true, description = "Saves the Minecraft version.", category = PRIVACY, subcategory = SAVED_WITH)
    var metadataPlayer by switch(name = "Player name", def = true, description = "Saves your username; uploads strip it by default.", category = PRIVACY, subcategory = SAVED_WITH)
    var metadataPlace by switch(name = "World or server", def = true, description = "Saves the world name, or the server's name and address; uploads strip it by default.", category = PRIVACY, subcategory = LOCATION)
    var metadataDimension by switch(name = "Dimension", def = true, description = "Saves the dimension you were in.", category = PRIVACY, subcategory = LOCATION)
    var metadataBiome by switch(name = "Biome", def = true, description = "Saves the biome you were standing in.", category = PRIVACY, subcategory = LOCATION)
    var metadataCoordinates by switch(name = "Coordinates", def = false, description = "Saves your block coordinates, which anyone with the original file can read; uploads strip them by default.", category = PRIVACY, subcategory = LOCATION)
    var metadataChat by switch(name = "Recent chat", def = false, description = "Saves the last chat lines for search, which anyone with the original file can read; uploads strip them by default.", category = PRIVACY, subcategory = CHAT)
    var metadataChatLines by slider(name = "Chat lines", def = 10f, description = "How many recent chat lines to save.", category = PRIVACY, subcategory = CHAT, min = 1f, max = 50f, step = 1f)

    val credits by info(
        name = "Credits",
        description = "isXander made Shotify, which Screencapper started from.\n" +
            "Zetvue made the shutter sound.\n" +
            "ramidzkh's Fabrishot is the base for high-res capture.\n" +
            "Polyfrost's PolyPlus radial wheel is the base for the capture wheel.",
        category = ABOUT,
        subcategory = "Credits",
    )

    init {
        hideIf(::metadataChatLines) { !metadataChat }
        hideIf(::holdThreshold) { !holdForMenu }
        hideIf(::wheelOptions) { !holdForMenu }
        hideIf(::shutterVolume) { !shutterSound }
        hideIf(::resetTitlePanorama) { titlePanorama.isEmpty() }
        hideIf(::leavePanoramaSize) { !leavePanorama || HighResRenderer.usesTiling }
        addCallback(::metadataChat) { refreshHidden(); false }
        addCallback(::holdForMenu) { refreshHidden(); false }
        addCallback(::shutterSound) { refreshHidden(); false }
        addCallback(::titlePanorama) { refreshHidden(); false }
        addCallback(::leavePanorama) { refreshHidden(); false }
    }

    init {
        listOf(::chatView, ::chatUpload, ::chatCopy, ::chatOpen, ::chatOpenFolder, ::chatDelete).forEach { hideIf(it) { true } }
        listOf(::chatActions, ::confirmChatDeletes, ::chatUploadLinks).forEach { hideIf(it) { !replaceMessage } }
        addCallback(::replaceMessage) { refreshHidden(); false }
    }

    init {
        for (fields in listOf(normalCompressionFields, highResCompressionFields, uploadCompressionFields)) {
            fields.registerVisibility(hideIf = { prop, condition -> hideIf(prop, condition) }, refreshOn = { prop -> addCallback(prop.name, Runnable { refreshHidden() }) })
        }
    }

    val profiles: List<UploaderProfile>
        get() = UploaderProfile.BUILT_IN + UploaderProfile.decodeList(uploaders, Secrets.cipher)

    val defaultProfile: UploaderProfile
        get() = profiles.firstOrNull { it.id == defaultUploader } ?: UploaderProfile.IMGUR

    fun encodeProfiles(profiles: List<UploaderProfile>): String =
        Secrets.sealing("the uploaders", { UploaderProfile.encodeList(profiles, Secrets.cipher) }, { UploaderProfile.encodeList(profiles) })

    fun addProfile(profile: UploaderProfile, makeDefault: Boolean) {
        uploaders = encodeProfiles(profiles + profile)
        if (makeDefault) defaultUploader = profile.id
        save()
    }

    override fun onInitialized(tree: Tree) {
        super.onInitialized(tree)
        if (UploaderProfile.decodeList(uploaders).none { UploaderSecrets.hasPlainSecrets(it) }) return
        uploaders = encodeProfiles(profiles)
        save()
    }

    fun migrateHighResHideHud() {
        if (!highResHideHud) return
        highResHideHud = false
        save()
    }

    private fun refreshHidden() {
        tree?.map?.values?.forEach { (it as? Property<*>)?.revaluateDisplay() }
    }
}
