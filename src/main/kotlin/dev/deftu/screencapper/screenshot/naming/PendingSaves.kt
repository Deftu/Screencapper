package dev.deftu.screencapper.screenshot.naming

import java.io.File

class PendingSaves {
    private val pending = ArrayDeque<File>()

    @Synchronized
    fun add(file: File) {
        pending.addLast(file)
    }

    @Synchronized
    fun settle(message: String, oldestIfUnnamed: Boolean = true): File? {
        val file = pending.filter { message.contains(it.name) }.maxByOrNull { it.name.length }
            ?: pending.firstOrNull()?.takeIf { oldestIfUnnamed }
            ?: return null
        pending.remove(file)
        return file
    }
}
