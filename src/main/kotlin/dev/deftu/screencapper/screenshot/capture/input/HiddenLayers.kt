package dev.deftu.screencapper.screenshot.capture.input

object HiddenLayers {
    @Volatile
    private var chat = false

    @Volatile
    private var hand = false

    @JvmStatic
    fun hidesChat(): Boolean = chat

    @JvmStatic
    fun hidesHand(): Boolean = hand

    fun hide(chat: Boolean, hand: Boolean) {
        this.chat = chat
        this.hand = hand
    }

    fun clear() {
        chat = false
        hand = false
    }
}
