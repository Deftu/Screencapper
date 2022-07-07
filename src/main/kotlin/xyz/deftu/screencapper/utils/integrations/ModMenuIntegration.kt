package xyz.deftu.screencapper.utils.integrations

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import xyz.deftu.screencapper.config.ScreencapperConfig

class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory() = ConfigScreenFactory {
        ScreencapperConfig.gui()
    }
}
