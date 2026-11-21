package dev.deftu.screencapper.screenshot.panorama

internal class TitleBackgroundState {
    data class Change(val id: String, val overridden: Boolean)

    private var applied = ""

    @Volatile
    private var held: String? = null

    @Volatile
    private var missedWhileHeld = false

    fun show(id: String) {
        applied = id
        held = id
        missedWhileHeld = false
    }

    fun missed() {
        if (held != null) missedWhileHeld = true
    }

    fun settle(id: String, kept: Boolean) {
        if (held == id) {
            held = null
            if (missedWhileHeld && applied == id) applied = STALE
            missedWhileHeld = false
        }
        if (!kept && applied == id) applied = STALE
    }

    fun change(wanted: String): Change? {
        if (held != null || wanted == applied) return null
        val overridden = applied.isNotEmpty()
        applied = wanted
        return Change(wanted, overridden)
    }

    private companion object {
        const val STALE = "\u0000"
    }
}
