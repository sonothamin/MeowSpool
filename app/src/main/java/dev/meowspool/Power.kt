package dev.meowspool

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Battery-optimisation exemption: needed so Android doesn't freeze the print server or Bluetooth link in the background. */
object Power {
    fun exempt(c: Context) = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)

    /** Shows the system "let app run in background?" prompt; falls back to the general list if unavailable. */
    fun request(c: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}"))
        try { c.startActivity(direct) } catch (_: Throwable) {
            runCatching { c.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }
}
