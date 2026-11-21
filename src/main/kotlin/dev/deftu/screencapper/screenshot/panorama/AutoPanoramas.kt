package dev.deftu.screencapper.screenshot.panorama

import dev.deftu.screencapper.screenshot.naming.PathSanitizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object AutoPanoramas {
    const val FOLDER = "auto"
    const val KEEP = 5
    const val MAX_SUPERSAMPLE = 2

    val SIZES = listOf(1024, 2048)

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")
    private val NAME = Regex("\\d{4}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}(_.+)?")

    fun folderPath(time: LocalDateTime, world: String?, attempt: Int): String {
        val place = world?.let(PathSanitizer::segment).orEmpty()
        val name = STAMP.format(time) + (if (place.isEmpty()) "" else "_$place") + (if (attempt > 1) "_$attempt" else "")
        return "${PathSanitizer.PANORAMAS}/$FOLDER/$name"
    }

    fun isAuto(name: String): Boolean = NAME.matches(name)

    fun nameOf(id: String): String? {
        val parts = id.split('/')
        if (parts.size != 3 || !parts[0].equals(PathSanitizer.PANORAMAS, ignoreCase = true) || !parts[1].equals(FOLDER, ignoreCase = true)) return null
        return parts[2].takeIf(::isAuto)
    }

    fun toPrune(names: Collection<String>, keep: Int, protected: String?): List<String> =
        names.filter(::isAuto).sortedDescending().drop(keep).filter { it != protected }

    fun faceSize(chosen: Int, tiled: Boolean): Int = if (tiled || chosen !in SIZES) PanoramaLayout.DEFAULT_SIZE else chosen
}
