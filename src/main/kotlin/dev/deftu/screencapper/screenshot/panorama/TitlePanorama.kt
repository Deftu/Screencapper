package dev.deftu.screencapper.screenshot.panorama

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.ScreenshotLibrary
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

//? if >=1.21.8 {
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.CubeMapTexture
import net.minecraft.client.renderer.texture.TextureContents
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection
import net.minecraft.server.packs.resources.ResourceManager
//?} else if >=1.21.4 {
/*import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.client.renderer.texture.TextureContents
import net.minecraft.server.packs.resources.ResourceManager
*///?} else if >=1.21 {
/*import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.FastColor
*///?} else {
/*import net.minecraft.client.render.texture.SimpleTexture
import net.minecraft.client.render.texture.TextureUtil
import net.minecraft.client.resource.manager.ResourceManager
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
*///?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.texture.MipmapStrategy
import net.minecraft.resources.Identifier
//?} else if >=1.21 {
/*import net.minecraft.resources.ResourceLocation as Identifier
*///?} else {
/*import net.minecraft.resource.Identifier
*///?}

object TitlePanorama {
    private const val BASE = "textures/gui/title/background/panorama"

    private val OPAQUE = 0xFF000000.toInt()

    private val installed = AtomicBoolean(false)
    private val state = TitleBackgroundState()

    fun install() {
        if (!installed.compareAndSet(false, true)) return
        EventManager.register(TickEvent.End::class.java) { _: TickEvent.End -> tick() }
    }

    fun showLeave(id: String, dir: File, size: Int, faces: List<IntArray>): Boolean {
        if (!ready()) return false
        require(size > 0 && faces.size == PanoramaLayout.FACES.size && faces.all { it.size >= size * size }) { "The panorama faces differ in size" }
        override(dir, faces, size)
        state.show(id)
        return true
    }

    fun settleLeave(id: String, kept: Boolean) {
        state.settle(id, kept)
    }

    private fun tick() {
        if (!ready()) return
        val change = state.change(ScreencapperConfig.titlePanorama) ?: return
        runCatching { apply(change.id, change.overridden) }
            .onFailure { Screencapper.LOGGER.error("Couldn't change the title screen panorama", it) }
    }

    private fun apply(id: String, overridden: Boolean) {
        val dir = id.takeIf { it.isNotEmpty() }?.let { PanoramaFolder.resolve(ScreenshotLibrary.directory, it, allowLegacy = true) }
        if (dir != null && PanoramaFolder.faces(dir) != null) {
            override(dir, null, 0)
            return
        }
        if (id.isNotEmpty()) Screencapper.LOGGER.warn("The title panorama {} no longer has six matching faces, using vanilla's", id)
        if (overridden) restore()
    }

    private fun ready(): Boolean {
        //? if >=1.21 {
        return Minecraft.getInstance().isGameLoadFinished
        //?} else {
        /*return true
        *///?}
    }

    //? if <1.21.8 {
    /*private fun faceFile(dir: File, index: Int): File = File(dir, PanoramaLayout.fileName(index))
    *///?}

    //? if >=1.21.4 {
    private fun paint(image: NativeImage, argb: IntArray, size: Int, rows: Int = size) {
        for (y in 0 until rows) for (x in 0 until size) image.setPixel(x, y, argb[y * size + x] or OPAQUE)
    }
    //?} else if >=1.21 {
    /*private fun paint(image: NativeImage, argb: IntArray, size: Int) {
        for (y in 0 until size) for (x in 0 until size) image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(argb[y * size + x] or OPAQUE))
    }
    *///?}

    //? if >=1.21 {
    private fun painted(width: Int, height: Int, draw: (NativeImage) -> Unit): NativeImage {
        val image = NativeImage(width, height, false)
        try {
            draw(image)
            return image
        } catch (e: Throwable) {
            image.close()
            throw e
        }
    }
    //?}

    //? if >=1.21 && <1.21.8 {
    /*private fun faceImages(faces: List<IntArray>, size: Int): List<NativeImage> {
        val images = ArrayList<NativeImage>(faces.size)
        try {
            for (argb in faces) images += painted(size, size) { paint(it, argb, size) }
            return images
        } catch (e: Throwable) {
            images.forEach(NativeImage::close)
            throw e
        }
    }
    *///?}

    //? if >=1.21.8 {
    private val CUBE: Identifier = Identifier.withDefaultNamespace(BASE)

    private fun override(dir: File, captured: List<IntArray>?, size: Int) {
        val image = captured?.let { faces -> painted(size, size * PanoramaLayout.CUBE_ORDER.size) { stacked -> paint(stacked, PanoramaLayout.stackCube(faces, size), size, size * PanoramaLayout.CUBE_ORDER.size) } }
        try {
            Minecraft.getInstance().textureManager.registerAndLoad(CUBE, FolderCubeMap(CUBE, dir, image))
        } catch (e: Throwable) {
            image?.close()
            throw e
        }
    }

    private fun restore() {
        Minecraft.getInstance().textureManager.registerAndLoad(CUBE, CubeMapTexture(CUBE))
    }

    private class FolderCubeMap(id: Identifier, private val dir: File, private var captured: NativeImage?) : CubeMapTexture(id) {
        override fun loadContents(resourceManager: ResourceManager): TextureContents {
            val image = captured
            captured = null
            if (image != null) return TextureContents(image, metadata())
            return runCatching { TextureContents(stack(), metadata()) }.getOrElse {
                Screencapper.LOGGER.warn("Couldn't load the title panorama, using vanilla's", it)
                state.missed()
                super.loadContents(resourceManager)
            }
        }

        private fun stack(): NativeImage {
            val faces = checkNotNull(PanoramaFolder.faces(dir)) { "The panorama no longer has six matching faces" }
            val images = ArrayList<NativeImage>()
            try {
                for (file in faces) images += file.inputStream().use(NativeImage::read)
                val width = images[0].width
                val height = images[0].height
                check(images.all { it.width == width && it.height == height }) { "The panorama faces differ in size" }
                return painted(width, height * PanoramaLayout.CUBE_ORDER.size) { stacked ->
                    PanoramaLayout.CUBE_ORDER.forEachIndexed { slot, face -> images[face].copyRect(stacked, 0, 0, 0, slot * height, width, height, false, true) }
                }
            } finally {
                images.forEach(NativeImage::close)
            }
        }

        private fun metadata(): TextureMetadataSection {
            //? if >=1.21.11 {
            return TextureMetadataSection(true, false, MipmapStrategy.MEAN, 0f)
            //?} else {
            /*return TextureMetadataSection(true, false)
            *///?}
        }
    }
    //?} else if >=1.21.4 {
    /*private val FACES: List<Identifier> = PanoramaLayout.FACES.map { Identifier.withDefaultNamespace("${BASE}_${it.index}.png") }

    private fun override(dir: File, captured: List<IntArray>?, size: Int) {
        val images = captured?.let { faceImages(it, size) }
        val manager = Minecraft.getInstance().textureManager
        try {
            FACES.forEachIndexed { index, id -> manager.registerAndLoad(id, FolderFace(id, faceFile(dir, index), images?.get(index))) }
        } catch (e: Throwable) {
            images?.forEach(NativeImage::close)
            throw e
        }
    }

    private fun restore() {
        val manager = Minecraft.getInstance().textureManager
        FACES.forEach { id -> manager.registerAndLoad(id, SimpleTexture(id)) }
    }

    private class FolderFace(id: Identifier, private val file: File, private var captured: NativeImage?) : SimpleTexture(id) {
        override fun loadContents(resourceManager: ResourceManager): TextureContents {
            val image = captured
            captured = null
            if (image != null) return TextureContents(image, null)
            return runCatching { TextureContents(file.inputStream().use(NativeImage::read), null) }.getOrElse {
                Screencapper.LOGGER.warn("Couldn't load the title panorama face {}, using vanilla's", file.name, it)
                state.missed()
                super.loadContents(resourceManager)
            }
        }
    }
    *///?} else if >=1.21 {
    /*private val FACES: List<Identifier> = PanoramaLayout.FACES.map { Identifier.withDefaultNamespace("${BASE}_${it.index}.png") }

    private fun override(dir: File, captured: List<IntArray>?, size: Int) {
        val images = captured?.let { faceImages(it, size) }
        val manager = Minecraft.getInstance().textureManager
        try {
            FACES.forEachIndexed { index, id -> manager.register(id, FolderFace(id, faceFile(dir, index), images?.get(index))) }
        } catch (e: Throwable) {
            images?.forEach(NativeImage::close)
            throw e
        }
    }

    private fun restore() {
        val manager = Minecraft.getInstance().textureManager
        FACES.forEach(manager::release)
    }

    private class FolderFace(id: Identifier, private val file: File, private var captured: NativeImage?) : SimpleTexture(id) {
        override fun getTextureImage(resourceManager: ResourceManager): SimpleTexture.TextureImage {
            val image = captured
            captured = null
            if (image != null) return SimpleTexture.TextureImage(null, image)
            return runCatching { SimpleTexture.TextureImage(null, file.inputStream().use(NativeImage::read)) }.getOrElse {
                Screencapper.LOGGER.warn("Couldn't load the title panorama face {}, using vanilla's", file.name, it)
                state.missed()
                super.getTextureImage(resourceManager)
            }
        }
    }
    *///?} else {
    /*private val FACES: List<Identifier> = PanoramaLayout.FACES.map { Identifier("${BASE}_${it.index}.png") }

    private fun override(dir: File, captured: List<IntArray>?, size: Int) {
        val manager = Minecraft.getInstance().textureManager
        FACES.forEachIndexed { index, id -> manager.register(id, FolderFace(id, faceFile(dir, index), captured?.let { faceImage(it[index], size) })) }
    }

    private fun faceImage(argb: IntArray, size: Int): BufferedImage =
        BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, size, size, IntArray(size * size) { argb[it] or OPAQUE }, 0, size) }

    private fun restore() {
        val manager = Minecraft.getInstance().textureManager
        FACES.forEach(manager::close)
    }

    private class FolderFace(id: Identifier, private val file: File, private var captured: BufferedImage?) : SimpleTexture(id) {
        override fun load(resourceManager: ResourceManager) {
            val image = captured ?: runCatching { ImageIO.read(file) }.getOrNull()
            captured = null
            if (image == null) {
                Screencapper.LOGGER.warn("Couldn't load the title panorama face {}, using vanilla's", file.name)
                state.missed()
                super.load(resourceManager)
                return
            }
            clearGlId()
            TextureUtil.upload(glId, image, false, false)
        }
    }
    *///?}
}
