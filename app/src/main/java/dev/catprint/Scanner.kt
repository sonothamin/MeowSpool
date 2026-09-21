package dev.catprint

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** BLE scan wrapper exposing results as state. Call [start] only with permissions granted. */
@SuppressLint("MissingPermission")
class Scanner(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val _found = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _scanning = MutableStateFlow(false)
    val found = _found.asStateFlow()
    val scanning = _scanning.asStateFlow()

    private val stopper = Runnable { stop() }
    private val cb = object : ScanCallback() {
        override fun onScanResult(type: Int, r: ScanResult) {
            val name = r.device.name ?: r.scanRecord?.deviceName ?: return
            if (_found.value[r.device.address] == null) {
                Dbg.d("Scan", "found $name ${r.device.address}")
                _found.value = _found.value + (r.device.address to name)
            }
        }
        override fun onScanFailed(code: Int) { Dbg.e("Scan", "failed $code"); _scanning.value = false }
    }

    /** @return false if Bluetooth is unavailable/off. */
    fun start(): Boolean {
        val bt = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (bt == null || !bt.isEnabled) return false
        stop()
        _found.value = emptyMap()
        _scanning.value = true
        Dbg.d("Scan", "start")
        bt.bluetoothLeScanner.startScan(cb)
        main.postDelayed(stopper, 10_000)
        return true
    }

    fun stop() {
        main.removeCallbacks(stopper)
        if (!_scanning.value) return
        _scanning.value = false
        try { ctx.getSystemService(BluetoothManager::class.java).adapter.bluetoothLeScanner.stopScan(cb) } catch (_: Exception) {}
        Dbg.d("Scan", "stop")
    }
}
