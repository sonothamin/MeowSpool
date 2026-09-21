package dev.catprint

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.catprint.ui.MeowTheme
import dev.catprint.ui.Printer
import dev.catprint.ui.SetupScreen

class SetupActivity : ComponentActivity() {
    private lateinit var scanner: Scanner
    private var selected by mutableStateOf<String?>(null)
    private var saved by mutableStateOf<List<Printer>>(emptyList())
    private var latched: String? = null

    private fun loadSaved() { saved = Prefs.printers().map { Printer(it.first, it.second) } }

    /** Selecting a printer latches the connection right away. */
    private fun select(addr: String?) {
        Prefs.selected = addr
        selected = addr
        if (latched != addr) {
            latched?.let { PrinterManager.release(it) }
            latched = addr?.also { PrinterManager.latch(it) }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        enableEdgeToEdge()
        scanner = Scanner(this)
        loadSaved()
        selected = Prefs.selected
        setContent {
            MeowTheme {
                val snack = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val states by PrinterManager.states.collectAsState()
                val found by scanner.found.collectAsState()
                val scanning by scanner.scanning.collectAsState()
                var testing by remember { mutableStateOf(false) }
                var crash by remember { mutableStateOf(Prefs.lastCrash) }
                var serviceOn by remember { mutableStateOf(serviceEnabled()) }

                // Re-check the print-service toggle when returning from system settings.
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) serviceOn = serviceEnabled() }
                    owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
                }

                fun scan() {
                    if (!scanner.start()) scope.launch { snack.showSnackbar("Turn Bluetooth on to scan") }
                }
                val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
                    Dbg.d("Setup", "permissions result $r")
                    if (r.values.all { it }) scan()
                    else scope.launch { snack.showSnackbar("Bluetooth permission is needed to find printers") }
                }

                SetupScreen(
                    saved = saved,
                    nearby = found.map { Printer(it.key, it.value) },
                    selected = selected,
                    states = states,
                    scanning = scanning,
                    serviceOn = serviceOn,
                    snackbar = snack,
                    onScan = {
                        val missing = needed().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                        if (missing.isEmpty()) scan() else perms.launch(missing.toTypedArray())
                    },
                    onSelect = { p ->
                        Prefs.add(p.addr, p.name); loadSaved(); select(p.addr)
                        scope.launch { snack.showSnackbar("Using ${p.name}", duration = SnackbarDuration.Short) }
                    },
                    onRemove = { p ->
                        Prefs.remove(p.addr); loadSaved(); select(Prefs.selected)
                        scope.launch {
                            val r = snack.showSnackbar("Forgot ${p.name}", "Undo", duration = SnackbarDuration.Long)
                            if (r == SnackbarResult.ActionPerformed) { Prefs.add(p.addr, p.name); loadSaved(); if (selected == null) select(p.addr) }
                        }
                    },
                    onReconnect = { PrinterManager.reconnect(it) },
                    onOpenPrintSettings = { startActivity(Intent(Settings.ACTION_PRINT_SETTINGS)) },
                    testing = testing,
                    onTestPrint = { p ->
                        testing = true
                        scope.launch(Dispatchers.IO) {
                            val err = try { PrintEngine.printTestPage(p.addr, p.name); null } catch (e: Throwable) { Dbg.e("Setup", "test print failed", e); e.message ?: "Print failed" }
                            testing = false
                            snack.showSnackbar(if (err == null) "Test page sent" else "Test print failed: $err")
                        }
                    },
                    crash = crash,
                    onDismissCrash = { Prefs.lastCrash = null; crash = null },
                )
            }
        }
    }

    override fun onStart() { super.onStart(); Prefs.selected?.let { select(it) } }

    override fun onStop() {
        super.onStop()
        scanner.stop()
        latched?.let { PrinterManager.release(it) }; latched = null
    }

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
