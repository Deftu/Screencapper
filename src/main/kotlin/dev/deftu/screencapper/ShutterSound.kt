package dev.deftu.screencapper

import dev.deftu.screencapper.config.ScreencapperConfig
import net.minecraft.client.Minecraft
//? if >=1.21.11 {
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
//?} else if >=1.21 {
/*import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
*///?} else {
/*import net.minecraft.client.sound.instance.AbstractSoundInstance
import net.minecraft.client.sound.instance.SoundInstance
import net.minecraft.resource.Identifier
*///?}

object ShutterSound {
    private const val NAMESPACE = "screencapper"
    private const val PATH = "shutter"

    @JvmStatic
    fun play() {
        if (!ScreencapperConfig.enabled || !ScreencapperConfig.shutterSound) return
        val volume = (ScreencapperConfig.shutterVolume / 100f).coerceIn(0f, 1f)
        if (volume <= 0f) return
        MainThread.run { start(volume) }
    }

    private fun start(volume: Float) {
        //? if >=1.21.11 {
        val event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(NAMESPACE, PATH))
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(event, 1f, volume))
        //?} else if >=1.21 {
        /*val event = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(NAMESPACE, PATH))
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(event, 1f, volume))
        *///?} else {
        /*val sound = object : AbstractSoundInstance(Identifier(NAMESPACE, PATH)) {
            init {
                this.volume = volume
                this.pitch = 1f
                this.attenuation = SoundInstance.Attenuation.NONE
            }
        }
        Minecraft.getInstance().soundManager.play(sound)
        *///?}
    }
}
