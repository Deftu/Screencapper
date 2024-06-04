package dev.deftu.screencapper.config

import gg.essential.universal.ChatColor
import gg.essential.universal.UDesktop
import gg.essential.vigilance.Vigilant
import gg.essential.vigilance.data.Property
import gg.essential.vigilance.data.PropertyType
import net.minecraft.client.resource.language.I18n
import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.gui.preview.PreviewPosition
import net.fabricmc.loader.api.FabricLoader
import java.io.File

val deftuDir by lazy {
    val deftuDir = File(FabricLoader.getInstance().gameDir.toFile(), "Deftu")
    if (!deftuDir.exists() && !deftuDir.mkdirs())
        throw IllegalStateException("Could not create Deftu directory!")

    deftuDir
}

private val configFile by lazy {
    File(deftuDir, "@MOD_ID@.toml")
}

object ScreencapperConfig : Vigilant(
    file = configFile,
    guiTitle = "${ChatColor.AQUA}${Screencapper.NAME}"
) {
    @Property(
        type = PropertyType.SWITCH,
        name = "${Screencapper.ID}.config.general.toggle",
        description = "${Screencapper.ID}.config_description.general.toggle",
        category = "${Screencapper.ID}.config_category.general"
    ) var toggle = true
    @Property(
        type = PropertyType.SWITCH,
        name = "${Screencapper.ID}.config.general.auto_copy",
        description = "${Screencapper.ID}.config_description.general.auto_copy",
        category = "${Screencapper.ID}.config_category.general"
    ) var autoCopy = true
    @Property(
        type = PropertyType.SWITCH,
        name = "${Screencapper.ID}.config.general.preview",
        description = "${Screencapper.ID}.config_description.general.preview",
        category = "${Screencapper.ID}.config_category.general"
    ) var preview = true
    @Property(
        type = PropertyType.NUMBER,
        name = "${Screencapper.ID}.config.general.preview_time",
        description = "${Screencapper.ID}.config_description.general.preview_time",
        category = "${Screencapper.ID}.config_category.general",
        max = 60,
        min = 1
    ) var previewTime = 5
    private var _previewPosition = PreviewPosition.BOTTOM_RIGHT.ordinal
    var previewPosition: PreviewPosition
        get() = PreviewPosition.values()[_previewPosition]
        set(value) {
            _previewPosition = value.ordinal
        }

    @Property(
        type = PropertyType.SWITCH,
        name = "${Screencapper.ID}.config.chat.upload",
        description = "${Screencapper.ID}.config_description.chat.upload",
        category = "${Screencapper.ID}.config_category.chat"
    ) var chatUpload = true
    @Property(
        type = PropertyType.SWITCH,
        name = "${Screencapper.ID}.config.chat.copy",
        description = "${Screencapper.ID}.config_description.chat.copy",
        category = "${Screencapper.ID}.config_category.chat"
    ) var chatCopy = true
    @Property(
        type = PropertyType.SWITCH,
        name = "${Screencapper.ID}.config.chat.open",
        description = "${Screencapper.ID}.config_description.chat.open",
        category = "${Screencapper.ID}.config_category.chat"
    ) var chatOpen = true
    @Property(
        type = PropertyType.SWITCH,
        name = "${Screencapper.ID}.config.chat.open_folder",
        description = "${Screencapper.ID}.config_description.chat.open_folder",
        category = "${Screencapper.ID}.config_category.chat"
    ) var chatOpenFolder = true
    @Property(
        type = PropertyType.SWITCH,
        name = "${Screencapper.ID}.config.chat.delete",
        description = "${Screencapper.ID}.config_description.chat.delete",
        category = "${Screencapper.ID}.config_category.chat"
    ) var chatDelete = true

    @Property(
        type = PropertyType.SWITCH,
        name = "${Screencapper.ID}.config.upload.toggle",
        description = "${Screencapper.ID}.config_description.upload.toggle",
        category = "${Screencapper.ID}.config_category.upload"
    ) var uploadToggle = true
    private var _uploadMode = UploadMode.IMGUR.ordinal
    var uploadMode: UploadMode
        get() = UploadMode.values()[_uploadMode]
        set(value) {
            _uploadMode = value.ordinal
        }
    @Property(
        type = PropertyType.TEXT,
        name = "${Screencapper.ID}.config.upload.sharex.url",
        description = "${Screencapper.ID}.config_description.upload.sharex.url",
        category = "${Screencapper.ID}.config_category.upload"
    ) var shareXUploadUrl = ""
    private var _shareXRequestMethod = RequestMethod.POST.ordinal
    var shareXRequestMethod: RequestMethod
        get() = RequestMethod.values()[_shareXRequestMethod]
        set(value) {
            _shareXRequestMethod = value.ordinal
        }
    private var _shareXRequestType = RequestType.FORM.ordinal
    var shareXRequestType: RequestType
        get() = RequestType.values()[_shareXRequestType]
        set(value) {
            _shareXRequestType = value.ordinal
        }
    @Property(
        type = PropertyType.BUTTON,
        name = "${Screencapper.ID}.config.upload.sharex.open_config",
        description = "${Screencapper.ID}.config_description.upload.sharex.open_config",
        category = "${Screencapper.ID}.config_category.upload"
    ) fun openShareXConfigFile() {
        UDesktop.open(ShareXConfig.file)
    }
    @Property(
        type = PropertyType.BUTTON,
        name = "${Screencapper.ID}.config.upload.sharex.reload_config",
        description = "${Screencapper.ID}.config_description.upload.sharex.reload_config",
        category = "${Screencapper.ID}.config_category.upload"
    ) fun reloadShareXConfigFile() {
        ShareXConfig.load()
    }
    @Property(
        type = PropertyType.TEXT,
        name = "${Screencapper.ID}.config.upload.sharex.sxcu_path",
        description = "${Screencapper.ID}.config_description.upload.sharex.sxcu_path",
        category = "${Screencapper.ID}.config_category.upload"
    ) var shareXSxcuPath = ""
    @Property(
        type = PropertyType.BUTTON,
        name = "${Screencapper.ID}.config.upload.sharex.import_sxcu",
        description = "${Screencapper.ID}.config_description.upload.sharex.import_sxcu",
        category = "${Screencapper.ID}.config_category.upload"
    ) fun importSxcu() {
        if (shareXSxcuPath.isBlank()) return
        val file = File(shareXSxcuPath)
        if (!file.exists()) return
        SxcuImport.import(file)
    }

    // Show/hide ShareX properties
    @Property(
        type = PropertyType.SWITCH,
        name = "Show ShareX Properties",
        category = "Optional",
        hidden = true
    ) var showShareX = false

    init {
        updateShareX(_uploadMode)
        category("${Screencapper.ID}.config_category.general") {
            selector(
                field = ::_previewPosition,
                name = "${Screencapper.ID}.config.general.preview_position",
                description = "${Screencapper.ID}.config_description.general.preview_position",
                options = PreviewPosition.values().map {
                    I18n.translate(it.displayName)
                }
            )
        }

        category("${Screencapper.ID}.config_category.upload") {
            selector(
                field = ::_uploadMode,
                name = "${Screencapper.ID}.config.upload.mode",
                description = "${Screencapper.ID}.config_description.upload.mode",
                options = UploadMode.values().map {
                    it.type
                },
                triggerActionOnInitialization = true,
                action = this@ScreencapperConfig::updateShareX
            )

            selector(
                field = ::_shareXRequestMethod,
                name = "${Screencapper.ID}.config.upload.sharex.request_method",
                description = "${Screencapper.ID}.config_description.upload.sharex.request_method",
                options = RequestMethod.values().map {
                    it.name
                }
            )

            selector(
                field = ::_shareXRequestType,
                name = "${Screencapper.ID}.config.upload.sharex.request_type",
                description = "${Screencapper.ID}.config_description.upload.sharex.request_type",
                options = RequestType.values().map {
                    it.name
                }
            )
        }

        addDependency("shareXUploadUrl", "showShareX")
        addDependency("_shareXRequestMethod", "showShareX")
        addDependency("_shareXRequestType", "showShareX")
        addDependency("openShareXConfigFile", "showShareX")
        addDependency("reloadShareXConfigFile", "showShareX")
        addDependency("shareXSxcuPath", "showShareX")
        addDependency("importSxcu", "showShareX")
    }

    private fun updateShareX(uploadMode: Int) {
        showShareX = uploadMode == UploadMode.SHAREX.ordinal
    }
}
