package dev.catprint

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.lifecycleScope
import dev.catprint.ui.MeowApp
import dev.catprint.ui.UiState

class SetupActivity : ComponentActivity() {
    private lateinit var ui: UiState

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        Dbg.d("Setup", "permissions result $r")
        if (r.values.all { it }) ui.scan() else ui.permissionDenied()
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        enableEdgeToEdge()
        ui = UiState(Scanner(this), SnackbarHostState(), lifecycleScope).also {
            it.serviceCheck = ::serviceEnabled
            it.requestScan = {
                val missing = needed().filter { p -> checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED }
                if (missing.isEmpty()) ui.scan() else perms.launch(missing.toTypedArray())
            }
        }
        setContent { MeowApp(ui) }
    }

    override fun onStart() { super.onStart(); ui.onStart() }
    override fun onResume() { super.onResume(); ui.refreshService() }
    override fun onStop() { super.onStop(); ui.onStop() }

    /** Only report "off" when we can tell it's off: not bound by the system and the setting is readable but lacks us. */
    private fun serviceEnabled(): Boolean {
        if (CatPrintService.bound) return true
        val v = Settings.Secure.getString(contentResolver, "enabled_print_services")
        if (v.isNullOrBlank()) return true // unreadable on some Android versions; avoid a false alarm
        return v.split(':').any { it.startsWith("$packageName/") }
    }

    private fun needed() = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}
