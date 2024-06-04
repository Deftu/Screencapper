package dev.deftu.screencapper.utils.integrations

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import dev.deftu.screencapper.config.ScreencapperConfig

class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory() = ConfigScreenFactory {
        ScreencapperConfig.gui()
    }
}
