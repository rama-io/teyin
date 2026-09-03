package com.rama.teyin.managers

import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
/**
 * Lightweight background executor for disk I/O and main-thread result dispatching.
 * Zero external dependencies: uses standard JVM Executors and Android's Handler/Looper.
 */
object TaskRunner {
    private val workerIndex = AtomicInteger(1)
    private val ioExecutor: ExecutorService = Executors.newFixedThreadPool(2) { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "teyin-io-worker-${workerIndex.getAndIncrement()}").apply {
            isDaemon = false
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    interface Cancellable {
        fun cancel()
        val isCancelled: Boolean
    }

    private class TaskToken : Cancellable {
        @Volatile
        override var isCancelled: Boolean = false
            private set

        @Volatile
        var future: Future<*>? = null

        override fun cancel() {
            isCancelled = true
            future?.cancel(true)
            mainHandler.removeCallbacksAndMessages(this)
        }
    }

    /**
     * Executes [task] on the background I/O pool and posts [onResult] to the main UI thread.
     * If an unhandled exception occurs in [task], [onError] is posted to the main thread if provided.
     * Returns a [Cancellable] handle that, when cancelled, suppresses main-thread callbacks.
     */
    fun <T> execute(
        task: () -> T,
        onError: ((Throwable) -> Unit)? = null,
        onResult: (T) -> Unit
    ): Cancellable {
        val token = TaskToken()
        token.future = ioExecutor.submit {
            if (token.isCancelled) return@submit
            try {
                val result = task()
                if (!token.isCancelled) {
                    mainHandler.postAtTime(Runnable {
                        if (!token.isCancelled) {
                            onResult(result)
                        }
                    }, token, SystemClock.uptimeMillis())
                }
            } catch (t: Throwable) {
                Log.w("TaskRunner", "Unhandled task exception", t)
                if (!token.isCancelled) {
                    if (onError != null) {
                        mainHandler.postAtTime(Runnable {
                            if (!token.isCancelled) {
                                onError(t)
                            }
                        }, token, SystemClock.uptimeMillis())
                    } else {
                        Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), t)
                    }
                }
            }
        }
        return token
    }

    fun <T> execute(task: () -> T, onResult: (T) -> Unit): Cancellable = execute(task, null, onResult)

    /**
     * Executes a fire-and-forget [task] on the background I/O pool.
     */
    fun execute(task: () -> Unit) {
        ioExecutor.execute {
            try {
                task()
            } catch (t: Throwable) {
                Log.w("TaskRunner", "Unhandled fire-and-forget task exception", t)
            }
        }
    }

    /**
     * Posts [action] to the main UI thread.
     */
    fun postMain(action: () -> Unit) {
        mainHandler.post(action)
    }
}
