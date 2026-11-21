package dev.deftu.screencapper.screenshot

class AutoCaptureSchedule(
    private val delayTicks: Long,
    private val windowTicks: Long,
    private val cooldownTicks: Long,
) {
    private val seen = HashSet<String>()
    private var scope: Any? = null
    private var dueAt = NONE
    private var expiresAt = NONE
    private var lastShotAt = NONE

    val pending: Boolean get() = dueAt != NONE

    fun earned(id: String, scope: Any?, now: Long) {
        if (scope !== this.scope) {
            this.scope = scope
            seen.clear()
            cancel()
        }
        if (!seen.add(id)) return

        val expires = now + windowTicks
        if (pending) {
            expiresAt = maxOf(expiresAt, expires)
            return
        }

        val due = if (lastShotAt == NONE) now + delayTicks else maxOf(now + delayTicks, lastShotAt + cooldownTicks)
        if (due > expires) return
        dueAt = due
        expiresAt = expires
    }

    fun shouldShoot(now: Long, blocked: Boolean): Boolean {
        if (!pending || now < dueAt) return false
        if (now > expiresAt) {
            cancel()
            return false
        }
        if (blocked) return false
        cancel()
        lastShotAt = now
        return true
    }

    private fun cancel() {
        dueAt = NONE
        expiresAt = NONE
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}

class DoneAdvancement(val id: String, val toasts: Boolean)

fun newlyEarnedAdvancements(doneBefore: Set<String>, doneAfter: List<DoneAdvancement>, reset: Boolean, shown: Boolean): List<String> =
    if (reset || !shown) emptyList() else doneAfter.filter { it.toasts && it.id !in doneBefore }.map { it.id }
