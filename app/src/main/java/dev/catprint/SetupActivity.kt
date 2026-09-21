package dev.catprint

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import dev.catprint.Prefs.darkness

@SuppressLint("MissingPermission")
class SetupActivity : Activity() {
    private val found = LinkedHashMap<String, String>()
    private val labels = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var status: TextView
    private var scanning = false

    private val cb = object : ScanCallback() {
        override fun onScanResult(type: Int, r: ScanResult) {
            val name = r.device.name ?: r.scanRecord?.deviceName ?: return
            if (found.put(r.device.address, name) == null) refresh()
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        status = TextView(this)
        val scan = Button(this).apply { text = "Scan for printers"; setOnClickListener { startScan() } }
        val dark = SeekBar(this).apply {
            max = 100; progress = darkness
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, p: Int, u: Boolean) { darkness = p }
                override fun onStartTrackingTouch(b: SeekBar?) {}
                override fun onStopTrackingTouch(b: SeekBar?) {}
            })
        }
        val clear = Button(this).apply { text = "Forget saved printers"; setOnClickListener { Prefs.clear(context); refresh() } }
        val enable = Button(this).apply {
            text = "Open system print settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_PRINT_SETTINGS)) }
        }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        val list = ListView(this).apply {
            this.adapter = this@SetupActivity.adapter
            setOnItemClickListener { _, _, pos, _ ->
                val e = found.entries.toList()[pos]
                Prefs.add(this@SetupActivity, e.key, e.value)
                Toast.makeText(context, "Saved ${e.value}", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
        root.addView(status); root.addView(scan)
        root.addView(TextView(this).apply { text = "Darkness" }); root.addView(dark)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(clear); root.addView(enable)
        setContentView(root)
        Prefs.printers(this).forEach { found[it.first] = it.second }
        refresh()
    }

    private fun refresh() {
        val saved = Prefs.printers(this).map { it.first }.toSet()
        labels.clear()
        found.forEach { (a, n) -> labels.add((if (a in saved) "✓ " else "") + "$n  ($a)") }
        adapter.notifyDataSetChanged()
        status.text = "Tap a printer to save it. Then enable “Cat Printer Service” in system print settings."
    }

    private fun needed() = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun startScan() {
        val missing = needed().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 1); return }
        val bt = getSystemService(BluetoothManager::class.java).adapter
        if (bt == null || !bt.isEnabled) { Toast.makeText(this, "Turn Bluetooth on", Toast.LENGTH_SHORT).show(); return }
        val sc = bt.bluetoothLeScanner
        if (scanning) sc.stopScan(cb)
        scanning = true
        sc.startScan(cb)
        status.postDelayed({ if (scanning) { sc.stopScan(cb); scanning = false } }, 10000)
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        if (r.isNotEmpty() && r.all { it == PackageManager.PERMISSION_GRANTED }) startScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (scanning) try { getSystemService(BluetoothManager::class.java).adapter.bluetoothLeScanner.stopScan(cb) } catch (_: Exception) {}
    }
}
