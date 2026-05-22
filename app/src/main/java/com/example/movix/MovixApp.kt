package com.example.movix

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class MovixApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(this, thread, throwable)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to write crash log", t)
            }
            default?.uncaughtException(thread, throwable) ?: run {
                Log.e(TAG, "Uncaught", throwable)
                exitProcess(2)
            }
        }
    }

    companion object {
        private const val TAG = "MovixApp"
        private const val CRASH_FILE = "last_crash.txt"

        fun crashFile(ctx: Context): File = File(ctx.filesDir, CRASH_FILE)

        fun writeCrashLog(ctx: Context, thread: Thread, throwable: Throwable) {
            val sw = StringWriter()
            PrintWriter(sw).use { throwable.printStackTrace(it) }
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val payload = buildString {
                appendLine("Movix TV ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
                appendLine("$now — thread ${thread.name}")
                appendLine("Android ${android.os.Build.VERSION.SDK_INT} • ${android.os.Build.MODEL}")
                appendLine("--")
                append(sw.toString())
            }
            crashFile(ctx).writeText(payload)
        }

        /** Lit et efface le crash log précédent. */
        fun consumeLastCrash(ctx: Context): String? {
            val f = crashFile(ctx)
            if (!f.exists()) return null
            val txt = runCatching { f.readText() }.getOrNull()
            f.delete()
            return txt
        }
    }
}
