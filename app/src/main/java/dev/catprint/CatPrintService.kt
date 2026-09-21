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
import dev.catprint.Prefs.darkness
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class CatPrintService : PrintService() {
    private val io = Executors.newSingleThreadExecutor()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    private fun infos(): List<PrinterInfo> = Prefs.printers(this).map { (addr, name) ->
        val id: PrinterId = generatePrinterId(addr)
        val caps = PrinterCapabilitiesInfo.Builder(id)
            .addMediaSize(MediaSize("cat48_roll", "48 mm roll (long)", 1890, 11000), true)
            .addMediaSize(MediaSize("cat48_100", "48 × 100 mm", 1890, 3937), false)
            .addMediaSize(MediaSize("cat48_50", "48 × 50 mm label", 1890, 1969), false)
            .addResolution(PrintAttributes.Resolution("r203", "203 dpi", 203, 203), true)
            .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
            .setMinMargins(PrintAttributes.Margins(0, 0, 0, 0))
            .build()
        PrinterInfo.Builder(id, name, PrinterInfo.STATUS_IDLE).setCapabilities(caps).build()
    }

    override fun onCreatePrinterDiscoverySession() = object : PrinterDiscoverySession() {
        override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) = addPrinters(infos())
        override fun onValidatePrinters(printerIds: MutableList<PrinterId>) = addPrinters(infos())
        override fun onStopPrinterDiscovery() {}
        override fun onStartPrinterStateTracking(printerId: PrinterId) {}
        override fun onStopPrinterStateTracking(printerId: PrinterId) {}
        override fun onDestroy() {}
    }

    override fun onRequestCancelPrintJob(job: PrintJob) {
        cancelled.add(job.id.toString()); job.cancel()
    }

    override fun onPrintJobQueued(job: PrintJob) {
        job.start()
        io.execute {
            try { print(job); if (!job.isCancelled) job.complete() }
            catch (e: Exception) { if (!job.isCancelled) job.fail(e.message ?: "Print failed") }
        }
    }

    private fun print(job: PrintJob) {
        val addr = job.info.printerId?.localId ?: throw IOException("No printer")
        val pfd = job.document.data ?: throw IOException("No document data")
        val key = job.id.toString()
        pfd.use {
            val renderer = PdfRenderer(pfd)
            CatPrinterLink(this).use { link ->
                link.connect(addr)
                link.send(CatProtocol.begin(darkness))
                for (i in 0 until renderer.pageCount) {
                    if (cancelled.remove(key)) break
                    renderer.openPage(i).use { page ->
                        val scale = CatProtocol.WIDTH.toFloat() / page.width
                        val h = (page.height * scale).toInt().coerceIn(1, 12000)
                        val bmp = Bitmap.createBitmap(CatProtocol.WIDTH, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, Matrix().apply { setScale(scale, scale) },
                            PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val rows = CatProtocol.toRows(bmp)
                        bmp.recycle()
                        rows.chunked(8).forEach { grp ->
                            link.send(grp.fold(ByteArray(0)) { acc, r -> acc + CatProtocol.line(r) })
                        }
                    }
                }
                link.send(CatProtocol.end())
                Thread.sleep(2500)   // let the printer drain its buffer before disconnecting
            }
            renderer.close()
        }
    }

    private inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R = try { block(this) } finally { close() }
    private inline fun <R> PdfRenderer.Page.use(block: (PdfRenderer.Page) -> R): R = try { block(this) } finally { close() }
}
