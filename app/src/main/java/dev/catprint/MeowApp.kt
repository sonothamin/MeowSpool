package dev.catprint

import android.app.Application

class MeowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        PrinterManager.init(this)
        Dbg.d("App", "process started")
    }
}
