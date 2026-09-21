package dev.meowspool

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.lifecycleScope
import dev.meowspool.ui.MeowSpoolRoot
import dev.meowspool.ui.UiState

class MainActivity : ComponentActivity() {
    private lateinit var ui: UiState

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        Dbg.d("Setup", "permissions result $r")
        if (r.values.all { it }) ui.scan() else ui.permissionDenied()
    }

    private val notif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        enableEdgeToEdge()
        ui = UiState(Scanner(this), SnackbarHostState(), lifecycleScope).also {
            it.serviceCheck = ::serviceEnabled
            it.batteryCheck = { Power.exempt(this) }
            it.applyServer = { ServerService.apply(this) }
            it.requestNotif = { if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
            it.requestScan = {
                val missing = needed().filter { p -> checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED }
                if (missing.isEmpty()) ui.scan() else perms.launch(missing.toTypedArray())
            }
        }
        ui.refreshService()
        if (Prefs.serverEnabled && !ServerService.state.value.running) ServerService.apply(this)
        handleShare(intent)
        setContent { MeowSpoolRoot(ui) }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent) }
    override fun onStart() { super.onStart(); ui.onStart() }
    override fun onResume() { super.onResume(); ui.refreshService() }
    override fun onStop() { super.onStop(); ui.onStop() }

    /** Someone else's "Share" -> MeowSpool: pull out the image/PDF and send it to the Direct print screen. */
    private fun handleShare(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val uri = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
        Dbg.d("Share", "handleShare uri=$uri")
        uri?.let { ui.share(it) }
    }

    /** Only report "off" when we can tell it's off: not bound by the system and the setting is readable but lacks us. */
    private fun serviceEnabled(): Boolean {
        if (MeowSpoolService.bound) return true
        val v = Settings.Secure.getString(contentResolver, "enabled_print_services")
        if (v.isNullOrBlank()) return true // unreadable on some Android versions; avoid a false alarm
        return v.split(':').any { it.startsWith("$packageName/") }
    }

    private fun needed() = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}
