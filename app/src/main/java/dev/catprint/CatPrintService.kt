package dev.catprint

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
import kotlinx.coroutines.flow.collect
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class CatPrintService : PrintService() {
    private val T = "Service"
    private val io = Executors.newSingleThreadExecutor()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private var latched: String? = null

    override fun onConnected() {
        Dbg.d(T, "service connected (system bound us)")
        latched = Prefs.selected?.also { PrinterManager.latch(it) }
    }

    override fun onDisconnected() {
        Dbg.d(T, "service disconnected")
        latched?.let { PrinterManager.release(it) }; latched = null
    }

    private fun infos(only: Set<String>? = null): List<PrinterInfo> =
        Prefs.printers().filter { only == null || it.first in only }.map { (addr, name) ->
            val id: PrinterId = generatePrinterId(addr)
            val caps = PrinterCapabilitiesInfo.Builder(id)
                .addMediaSize(MediaSize("cat48_roll", "48 mm roll (long)", 1890, 11000), true)
                .addMediaSize(MediaSize("cat48_100", "48 × 100 mm", 1890, 3937), false)
                .addMediaSize(MediaSize("cat48_50", "48 × 50 mm label", 1890, 1969), false)
                .addResolution(PrintAttributes.Resolution("r203", "203 dpi", 203, 203), true)
                .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
                .setMinMargins(PrintAttributes.Margins(0, 0, 0, 0))
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

    override fun onPrintJobQueued(job: PrintJob) {
        Dbg.d(T, "job queued ${job.id} printer=${job.info.printerId?.localId}")
        job.start()
        io.execute {
            try {
                print(job)
                if (!job.isCancelled) { job.complete(); Dbg.d(T, "job complete ${job.id}") }
            } catch (e: Throwable) {
                Dbg.e(T, "job failed ${job.id}", e)
                if (!job.isCancelled) job.fail(e.message ?: "Print failed")
            }
        }
    }

    private fun print(job: PrintJob) {
        val addr = job.info.printerId?.localId ?: throw IOException("No printer")
        val key = job.id.toString()
        val pfd = job.document.data ?: throw IOException("No document data")
        PrinterManager.latch(addr)
        try {
            // 1) render every page to 1-bit rows before touching the radio
            val rows = ArrayList<ByteArray>()
            pfd.use {
                val renderer = PdfRenderer(pfd)
                try {
                    Dbg.d(T, "pdf pages=${renderer.pageCount}")
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        try {
                            val scale = CatProtocol.WIDTH.toFloat() / page.width
                            val h = (page.height * scale).toInt().coerceIn(1, 12000)
                            val bmp = Bitmap.createBitmap(CatProtocol.WIDTH, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            rows += CatProtocol.toRows(bmp)
                            bmp.recycle()
                            Dbg.d(T, "page $i -> ${h} rows")
                        } finally { page.close() }
                    }
                } finally { renderer.close() }
            }
            // 2) send over the latched link
            PrinterManager.withLink(addr) { link ->
                link.requestStatus(); Thread.sleep(400)
                link.status?.takeIf { it.blocking }?.let { throw IOException(it.problems().joinToString()) }
                link.send(CatProtocol.begin(Prefs.darkness))
                for (grp in rows.chunked(8)) {
                    if (cancelled.remove(key)) { Dbg.d(T, "job cancelled mid-send"); break }
                    link.send(grp.fold(ByteArray(0)) { acc, r -> acc + CatProtocol.line(r) })
                }
                link.send(CatProtocol.end())
                // wait (bounded) for the printer to report idle
                val t0 = System.currentTimeMillis()
                Thread.sleep(1500)
                while (System.currentTimeMillis() - t0 < 8000) {
                    link.requestStatus(); Thread.sleep(600)
                    if (link.status?.busy != true) break
                }
                Dbg.d(T, "send finished in ${System.currentTimeMillis() - t0}ms")
            }
        } finally { PrinterManager.release(addr) }
    }
}
