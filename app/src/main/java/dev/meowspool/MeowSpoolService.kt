package dev.meowspool

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.print.PrintAttributes
import android.print.PrintAttributes.MediaSize
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class MeowSpoolService : PrintService() {
    private val T = "Service"
    private val io = Executors.newSingleThreadExecutor()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private var latched: String? = null

    companion object { @Volatile var bound = false }

    override fun onConnected() {
        bound = true
        Dbg.d(T, "service connected (system bound us)")
        latched = Prefs.selected?.also { PrinterManager.latch(it) }
    }

    override fun onDisconnected() {
        bound = false
        Dbg.d(T, "service disconnected")
        latched?.let { PrinterManager.release(it) }; latched = null
    }

    private fun infos(only: Set<String>? = null): List<PrinterInfo> =
        Prefs.printers().filter { only == null || it.first in only }.map { (addr, name) ->
            val id: PrinterId = generatePrinterId(addr)
            val caps = PrinterCapabilitiesInfo.Builder(id)
                .apply { val sel = Paper.selected().id; Paper.all().forEach { addMediaSize(MediaSize(it.id, it.name, Paper.WIDTH_MILS, it.heightMils), it.id == sel) } }
                .addResolution(PrintAttributes.Resolution("r203", "203 dpi", 203, 203), true)
                .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
                .setMinMargins(PrintAttributes.Margins(Paper.sideMils(), Paper.vertMils(), Paper.sideMils(), Paper.vertMils()))
                .build()
            val st = PrinterManager.state(addr)
            val probs = st.status?.problems().orEmpty()
            val (code, desc) = when {
                st.status?.blocking == true -> PrinterInfo.STATUS_UNAVAILABLE to probs.joinToString()
                st.printing || st.status?.busy == true -> PrinterInfo.STATUS_BUSY to "Busy"
                st.conn == Conn.ERROR -> PrinterInfo.STATUS_IDLE to "Reconnecting…"
                probs.isNotEmpty() -> PrinterInfo.STATUS_IDLE to probs.joinToString()
                st.conn == Conn.CONNECTED -> PrinterInfo.STATUS_IDLE to "Ready"
                else -> PrinterInfo.STATUS_IDLE to "Connecting…"
            }
            Dbg.d(T, "info $name status=$code desc=$desc")
            PrinterInfo.Builder(id, name, code).setDescription(desc).setCapabilities(caps).build()
        }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        Dbg.d(T, "discovery session created")
        return object : PrinterDiscoverySession() {
            private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            private val tracked = HashSet<String>()
            private var watcher: Job? = null

            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
                Dbg.d(T, "start discovery"); addPrinters(infos())
            }
            override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
                Dbg.d(T, "validate ${printerIds.size}"); addPrinters(infos())
            }
            override fun onStopPrinterDiscovery() { Dbg.d(T, "stop discovery") }

            override fun onStartPrinterStateTracking(printerId: PrinterId) {
                val a = printerId.localId
                Dbg.d(T, "track $a -> latch")
                if (tracked.add(a)) PrinterManager.latch(a)
                if (watcher == null) watcher = scope.launch {
                    PrinterManager.states.collect { if (tracked.isNotEmpty()) addPrinters(infos(tracked.toSet())) }
                }
            }
            override fun onStopPrinterStateTracking(printerId: PrinterId) {
                val a = printerId.localId
                Dbg.d(T, "untrack $a")
                if (tracked.remove(a)) PrinterManager.release(a)
            }
            override fun onDestroy() {
                Dbg.d(T, "discovery session destroyed")
                tracked.forEach { PrinterManager.release(it) }; tracked.clear(); scope.cancel()
            }
        }
    }

    override fun onRequestCancelPrintJob(job: PrintJob) {
        Dbg.d(T, "cancel requested ${job.id}")
        cancelled.add(job.id.toString()); job.cancel()
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** PrintJob may only be touched on the main thread: read what we need here, work off-thread, report back on main. */
    override fun onPrintJobQueued(job: PrintJob) {
        val key = job.id.toString()
        val addr = job.info.printerId?.localId
        val pfd = job.document.data
        Dbg.d(T, "job queued $key printer=$addr")
        job.start()
        io.execute {
            val err: Throwable? = try {
                if (addr == null) throw IOException("No printer")
                if (pfd == null) throw IOException("No document data")
                print(addr, key, pfd); null
            } catch (e: Throwable) { Dbg.e(T, "job failed $key", e); e }
            main.post {
                try {
                    when {
                        job.isCancelled -> {}
                        err == null -> { job.complete(); Dbg.d(T, "job complete $key") }
                        else -> { job.fail(err.message ?: "Print failed"); Dbg.d(T, "job marked failed") }
                    }
                } catch (e: Throwable) { Dbg.e(T, "could not finish job $key", e) }
                cancelled.remove(key)
            }
        }
    }

    private fun print(addr: String, key: String, pfd: android.os.ParcelFileDescriptor) {
        PrinterManager.latch(addr)
        try {
            // 1) render every page to 1-bit rows before touching the radio
            val rows = ArrayList<ByteArray>()
            // The framework hands us a pipe; PdfRenderer needs a seekable file, so spool to cache first.
            val tmp = java.io.File.createTempFile("job", ".pdf", cacheDir)
            val seekable = try {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                Dbg.d(T, "spooled ${tmp.length()}B")
                android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e: Throwable) { tmp.delete(); throw e }
            try { seekable.use {
                val renderer = PdfRenderer(seekable)
                try {
                    Dbg.d(T, "pdf pages=${renderer.pageCount}")
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        try {
                            val mm = page.width * 25.4f / 72f
                            val is58 = mm in (Paper.PAPER_MM - 3)..(Paper.PAPER_MM + 3)
                            val visible = if (is58) page.width * Paper.PRINTABLE_MM / Paper.PAPER_MM else page.width.toFloat()
                            val scale = CatProtocol.WIDTH / visible
                            val dx = if (is58) -(page.width - visible) / 2f * scale else 0f
                            val h = (page.height * scale).toInt().coerceIn(1, 12000)
                            val bmp = Bitmap.createBitmap(CatProtocol.WIDTH, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, Matrix().apply { setScale(scale, scale); postTranslate(dx, 0f) }, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            rows += CatProtocol.toRows(bmp)
                            bmp.recycle()
                            Dbg.d(T, "page $i -> ${h} rows")
                        } finally { page.close() }
                    }
                } finally { renderer.close() }
            } } finally { tmp.delete() }
            // 2) send over the latched link
            PrintEngine.sendRows(addr, rows, source = HistorySource.SERVICE) { cancelled.remove(key) }
        } finally { PrinterManager.release(addr) }
    }
}
