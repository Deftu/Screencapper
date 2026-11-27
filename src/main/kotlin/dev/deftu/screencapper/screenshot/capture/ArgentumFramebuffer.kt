package dev.deftu.screencapper.screenshot.capture

//? if <1.21 {
/*import dev.rdh.argentum.api.render.framebuffer.MainFramebuffer
*///?}

object ArgentumFramebuffer {
    //? if <1.21 {
    /*private val available = runCatching {
        Class.forName("dev.rdh.argentum.api.render.framebuffer.MainFramebuffer", false, ArgentumFramebuffer::class.java.classLoader)
    }.isSuccess
    *///?}

    @JvmStatic
    fun request() {
        //? if <1.21 {
        /*if (available) MainFramebuffer.request()
        *///?}
    }

    @JvmStatic
    fun deferIfDirect(task: Runnable): Boolean {
        //? if <1.21 {
        /*if (!available || !MainFramebuffer.isRenderingDirect()) return false
        MainFramebuffer.runAfterFramebufferFrame(task)
        return true
        *///?} else {
        return false
        //?}
    }
}
