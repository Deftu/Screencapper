package dev.deftu.screencapper.utils

import net.minecraft.client.texture.NativeImage
import java.io.File
import java.net.URL

data class Screenshot(
    val image: NativeImage,
    @Suppress("ArrayInDataClass") val bytes: ByteArray,
    val file: File,
    val url: URL? = null
)
