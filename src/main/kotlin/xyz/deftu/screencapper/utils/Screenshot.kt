package xyz.deftu.screencapper.utils

import net.minecraft.client.texture.NativeImage
import java.io.File
import java.net.URL

data class Screenshot(
    val image: NativeImage,
    val file: File,
    val url: URL? = null
)
