package dev.deftu.screencapper.screenshot.panorama

import dev.deftu.screencapper.screenshot.naming.NameTemplate
import dev.deftu.screencapper.screenshot.naming.PathSanitizer

data class PanoramaFace(val index: Int, val yawOffset: Float, val pitch: Float) {
    val fileName: String get() = PanoramaLayout.fileName(index)

    fun yaw(playerYaw: Float): Float = (playerYaw + yawOffset) % 360f
}

object PanoramaLayout {
    const val DEFAULT_SIZE = 1024
    const val SUFFIX = "panorama"

    private const val MAX_RENDER_SIDE = 4096
    private val SUPERSAMPLES = listOf(4, 2)
    private val FACE_NAME = Regex("panorama_([0-5])\\.png", RegexOption.IGNORE_CASE)

    val SIZES = listOf(1024, 2048, 4096)

    val FACES = listOf(
        PanoramaFace(0, 0f, 0f),
        PanoramaFace(1, 90f, 0f),
        PanoramaFace(2, 180f, 0f),
        PanoramaFace(3, -90f, 0f),
        PanoramaFace(4, 0f, -90f),
        PanoramaFace(5, 0f, 90f),
    )

    val CUBE_ORDER = listOf(1, 3, 5, 4, 0, 2)

    fun fileName(index: Int): String = "${SUFFIX}_$index.png"

    fun faceIndex(name: String): Int? = FACE_NAME.matchEntire(name)?.groupValues?.get(1)?.toInt()

    fun folderPath(base: String, attempt: Int): String {
        val last = base.substringAfterLast('/').substringAfterLast('\\')
        val name = if (NameTemplate.COUNT in last) {
            last.replace(NameTemplate.COUNT, attempt.toString()) + "_$SUFFIX"
        } else {
            last + "_$SUFFIX" + if (attempt == 1) "" else "_$attempt"
        }
        return "${PathSanitizer.PANORAMAS}/" + PathSanitizer.segment(name).ifEmpty { SUFFIX }
    }

    fun supersample(faceSize: Int, maxTextureSize: Int, canDownscale: Boolean): Int {
        if (!canDownscale) return 1
        val limit = minOf(maxOf(MAX_RENDER_SIDE, faceSize), maxTextureSize)
        return SUPERSAMPLES.firstOrNull { faceSize.toLong() * it <= limit } ?: 1
    }

    fun normalizedSize(size: Int): Int = if (size in SIZES) size else DEFAULT_SIZE

    fun stackCube(faces: List<IntArray>, size: Int): IntArray {
        val area = size * size
        val stacked = IntArray(area * CUBE_ORDER.size)
        CUBE_ORDER.forEachIndexed { slot, face ->
            for (y in 0 until size) faces[face].copyInto(stacked, slot * area + y * size, (size - 1 - y) * size, (size - y) * size)
        }
        return stacked
    }
}
