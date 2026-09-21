package dev.catprint

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.*

/** MVP setup screen (plain views; Material UI comes later). */
@SuppressLint("MissingPermission")
class SetupActivity : Activity() {
    private val found = LinkedHashMap<String, String>()
    private val labels = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var status: TextView
    private var scanning = false
    private val ui = Handler(Looper.getMainLooper())
    private var latched: String? = null

    private val cb = object : ScanCallback() {
        override fun onScanResult(type: Int, r: ScanResult) {
            val name = r.device.name ?: r.scanRecord?.deviceName ?: return
            if (found.put(r.device.address, name) == null) { Dbg.d("Setup", "found $name ${r.device.address}"); refresh() }
        }
        override fun onScanFailed(code: Int) { Dbg.e("Setup", "scan failed $code"); scanning = false }
    }

    private val ticker = object : Runnable {
        override fun run() { renderStatus(); ui.postDelayed(this, 1000) }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        status = TextView(this)
        val scan = Button(this).apply { text = "Scan for printers"; setOnClickListener { startScan() } }
        val dark = SeekBar(this).apply {
            max = 100; progress = Prefs.darkness
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, p: Int, u: Boolean) { Prefs.darkness = p }
                override fun onStartTrackingTouch(b: SeekBar?) {}
                override fun onStopTrackingTouch(b: SeekBar?) {}
            })
        }
        val debug = Switch(this).apply {
            text = "Debug logging"; isChecked = Prefs.debug
            setOnCheckedChangeListener { _, on -> Prefs.debug = on; Dbg.d("Setup", "debug logging enabled") }
        }
        val viewLog = Button(this).apply { text = "View debug log"; setOnClickListener { showLog() } }
        val clear = Button(this).apply { text = "Forget saved printers"; setOnClickListener { Prefs.clear(); select(null); refresh() } }
        val enable = Button(this).apply {
            text = "Open system print settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_PRINT_SETTINGS)) }
        }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        val list = ListView(this).apply {
            this.adapter = this@SetupActivity.adapter
            setOnItemClickListener { _, _, pos, _ ->
                val e = found.entries.toList()[pos]
                Prefs.add(e.key, e.value); select(e.key)
                Toast.makeText(context, "Selected ${e.value}", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
        root.addView(status); root.addView(scan)
        root.addView(TextView(this).apply { text = "Darkness" }); root.addView(dark)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(debug); root.addView(viewLog); root.addView(clear); root.addView(enable)
        setContentView(root)
        Prefs.printers().forEach { found[it.first] = it.second }
        refresh()
    }

    /** Selecting a printer latches the connection right away. */
    private fun select(addr: String?) {
        Prefs.selected = addr
        if (latched != addr) {
            latched?.let { PrinterManager.release(it) }
            latched = addr?.also { PrinterManager.latch(it) }
        }
    }

    override fun onStart() {
        super.onStart()
        Prefs.selected?.let { select(it) }
        ui.post(ticker)
    }

    override fun onStop() {
        super.onStop()
        ui.removeCallbacks(ticker)
        latched?.let { PrinterManager.release(it) }; latched = null
    }

    private fun serviceEnabled(): Boolean {
        val cn = ComponentName(this, CatPrintService::class.java)
        val v = Settings.Secure.getString(contentResolver, "enabled_print_services") ?: return false
        return v.split(':').any { it == cn.flattenToString() || it == cn.flattenToShortString() }
    }

    private fun renderStatus() {
        val sel = Prefs.selected
        val name = Prefs.printers().firstOrNull { it.first == sel }?.second
        val sb = StringBuilder()
        sb.append(if (serviceEnabled()) "Print service: ON\n" else "Print service: OFF — enable “Cat Printer Service” in system print settings\n")
        if (sel == null) sb.append("No printer selected. Scan and tap one.")
        else {
            val st = PrinterManager.state(sel)
            sb.append("$name: ${st.conn}")
            st.error?.let { sb.append(" ($it)") }
            st.status?.let { s ->
                sb.append("\n").append(if (s.problems().isEmpty()) "OK" else s.problems().joinToString())
                if (s.busy) sb.append(", busy")
            }
        }
        status.text = sb
    }

    private fun refresh() {
        val saved = Prefs.printers().map { it.first }.toSet()
        labels.clear()
        found.forEach { (a, n) ->
            labels.add((if (a == Prefs.selected) "● " else if (a in saved) "✓ " else "") + "$n  ($a)")
        }
        adapter.notifyDataSetChanged()
    }

    private fun showLog() {
        val tv = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE; textSize = 10f; setTextIsSelectable(true)
            text = Dbg.snapshot().joinToString("\n").ifEmpty { "(empty — enable debug logging and try again)" }
        }
        AlertDialog.Builder(this).setTitle("Debug log")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy") { _, _ ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("log", tv.text))
            }
            .setNegativeButton("Clear") { _, _ -> Dbg.clear() }
            .show()
    }

    private fun needed() = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun startScan() {
        val missing = needed().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { Dbg.d("Setup", "requesting $missing"); requestPermissions(missing.toTypedArray(), 1); return }
        val bt = getSystemService(BluetoothManager::class.java).adapter
        if (bt == null || !bt.isEnabled) { Toast.makeText(this, "Turn Bluetooth on", Toast.LENGTH_SHORT).show(); return }
        val sc = bt.bluetoothLeScanner
        if (scanning) sc.stopScan(cb)
        scanning = true
        Dbg.d("Setup", "scan start")
        sc.startScan(cb)
        status.postDelayed({ if (scanning) { sc.stopScan(cb); scanning = false; Dbg.d("Setup", "scan stop") } }, 10000)
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        Dbg.d("Setup", "permissions result ${r.toList()}")
        if (r.isNotEmpty() && r.all { it == PackageManager.PERMISSION_GRANTED }) startScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (scanning) try { getSystemService(BluetoothManager::class.java).adapter.bluetoothLeScanner.stopScan(cb) } catch (_: Exception) {}
    }
}
