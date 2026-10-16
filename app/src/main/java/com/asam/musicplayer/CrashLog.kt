package com.asam.musicplayer

import android.content.Context
import android.util.Log
import java.io.File

/** Saves the last crash to a file so it can be copied from Settings after the app reopens. */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                file(app).writeText("Thread: ${thread.name}\n" + Log.getStackTraceString(error))
            } catch (_: Throwable) {
                // Nothing more we can do while crashing.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }
}
