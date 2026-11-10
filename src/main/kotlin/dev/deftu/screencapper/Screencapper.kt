package dev.deftu.screencapper

import com.mojang.brigadier.arguments.StringArgumentType
import dev.deftu.screencapper.config.LegacyConfigMigration
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.gui.ScreenshotPreviewHud
import dev.deftu.screencapper.gui.ScreenshotsPage
import dev.deftu.screencapper.screenshot.Screenshot
import dev.deftu.screencapper.screenshot.ScreenshotLibrary
import net.fabricmc.api.ClientModInitializer
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.polyfrost.oneconfig.api.commands.v1.CommandManager
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPages

object Screencapper : ClientModInitializer {
    const val NAME = "@MOD_NAME@"
    const val ID = "@MOD_ID@"
    const val VERSION = "@MOD_VERSION@"

    @JvmField
    val LOGGER: Logger = LogManager.getLogger(NAME)

    @OptIn(ExperimentalOneConfigUiApi::class)
    override fun onInitializeClient() {
        ScreencapperConfig.preload()
        LegacyConfigMigration.run()
        HudManager.register(ScreenshotPreviewHud(), ScreencapperConfig.id, "/assets/screencapper/screencapper.svg")
        if (OneConfigPages.byId(ScreenshotsPage.id) == null) OneConfigPages.register(ScreenshotsPage)
        registerCommands()
    }

    private fun registerCommands() {
        fun action(name: String, run: (Screenshot) -> Unit) = CommandManager.literal(name)
            .then(CommandManager.argument("screenshot", StringArgumentType.greedyString()).executes { ctx ->
                val screenshot = ScreenshotLibrary.find(StringArgumentType.getString(ctx, "screenshot"))
                if (screenshot == null) {
                    ScreenshotActions.notifyError("Screenshot not found", "It was deleted or moved out of your screenshots folder.")
                    0
                } else {
                    run(screenshot)
                    1
                }
            })

        @OptIn(ExperimentalOneConfigUiApi::class)
        CommandManager.register(
            CommandManager.literal(ID)
                .executes { OneConfigUI.open(OneConfigPages.modConfig(ScreencapperConfig.id)); 1 }
                .then(CommandManager.literal("screenshots").executes { ScreenshotActions.openManager(); 1 })
                .then(CommandManager.literal("folder").executes { ScreenshotActions.openFolder(); 1 }
                    .then(CommandManager.argument("screenshot", StringArgumentType.greedyString()).executes { ScreenshotActions.openFolder(); 1 }))
                .then(action("upload") { ScreenshotActions.upload(it) })
                .then(action("copy") { ScreenshotActions.copy(it) })
                .then(action("copy-link") { ScreenshotActions.copyLink(it) })
                .then(action("open") { ScreenshotActions.open(it) })
                .then(action("delete") { ScreenshotActions.delete(it) })
                .then(action("view") { ScreenshotActions.openManager(it) })
                .then(action("pick") { ChatCards.showUploaderPicker(it) })
                .then(action("confirm-delete") { ChatCards.showDeleteConfirm(it) })
                .then(action("card") { ChatCards.showActions(it) })
                .then(CommandManager.literal("upload-to").then(CommandManager.argument("uploader", StringArgumentType.word())
                    .then(CommandManager.argument("screenshot", StringArgumentType.greedyString()).executes { ctx ->
                        val profile = ScreencapperConfig.profiles.firstOrNull { it.id == StringArgumentType.getString(ctx, "uploader") }
                        val screenshot = ScreenshotLibrary.find(StringArgumentType.getString(ctx, "screenshot"))
                        if (profile == null || screenshot == null) {
                            ScreenshotActions.notifyError("Couldn't upload", "That screenshot or uploader no longer exists.")
                            0
                        } else {
                            ScreenshotActions.upload(screenshot, profile)
                            1
                        }
                    })))
        )
        CommandManager.register(CommandManager.literal("screenshots").executes { ScreenshotActions.openManager(); 1 })
    }
}
