package dev.deftu.screencapper.gui

internal class PreviewOrder(private val remembered: Int = 64) {
    private val shown = LinkedHashSet<String>()
    private var newestName: String? = null
    private var newestFromPixels = false
    private var newestAt = Long.MIN_VALUE
    private var ticket = 0L

    @Synchronized
    fun claim(name: String, savedAt: Long, fromPixels: Boolean): Long? {
        if (!fromPixels && ((name == newestName && !newestFromPixels) || name in shown || savedAt < newestAt)) return null
        newestName = name
        newestFromPixels = fromPixels
        newestAt = maxOf(newestAt, savedAt)
        return ++ticket
    }

    @Synchronized
    fun forget(name: String) {
        shown.remove(name)
        if (newestName == name) newestName = null
    }

    @Synchronized
    fun isCurrent(ticket: Long): Boolean = ticket == this.ticket

    @Synchronized
    fun commit(ticket: Long, action: () -> Unit): Boolean {
        if (ticket != this.ticket) return false
        action()
        newestName?.let { name ->
            shown.remove(name)
            shown.add(name)
            if (shown.size > remembered) shown.remove(shown.first())
        }
        return true
    }
}
