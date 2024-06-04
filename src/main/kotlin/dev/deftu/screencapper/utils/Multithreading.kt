package dev.deftu.screencapper.utils

import java.util.concurrent.*


object Multithreading {
    private val executor = ThreadPoolExecutor(25, 25, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
    private val scheduledExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(25)

    fun runAsync(runnable: Runnable) =
        executor.execute(runnable)
    fun schedule(runnable: Runnable, initialDelay: Long, delay: Long, timeUnit: TimeUnit) =
        scheduledExecutor.scheduleAtFixedRate(runnable, initialDelay, delay, timeUnit)
    fun schedule(runnable: Runnable, delay: Long, timeUnit: TimeUnit) =
        scheduledExecutor.schedule(runnable, delay, timeUnit)
}
