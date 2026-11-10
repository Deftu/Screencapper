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

    init {
        listOf(::chatView, ::chatUpload, ::chatCopy, ::chatOpen, ::chatOpenFolder, ::chatDelete, ::confirmChatDeletes, ::chatUploadLinks).forEach { hideIf(it) { !replaceMessage } }
        addCallback(::replaceMessage) { refreshHidden(); false }
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
