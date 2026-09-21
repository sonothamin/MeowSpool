package dev.meowspool

import android.app.Application
import android.os.Build

class MeowSpoolApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        PrinterManager.init(this)
        History.init(this)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                Prefs.lastCrash = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MODEL}\nThread: ${t.name}\n" +
                    android.util.Log.getStackTraceString(e)
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
        Dbg.d("App", "process started")
    }
}
