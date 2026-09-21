package dev.meowspool

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

enum class HistorySource { TEST, DIRECT, SERVICE, API, REPRINT }

data class HistoryEntry(
    val id: String,
    val time: Long,
    val printerName: String,
    val printerAddr: String,
    val source: HistorySource,
    val rows: Int,
    val ok: Boolean,
    val error: String? = null,
    val darkness: Int = 60,
    val feedMm: Int = 12,
) {
    val thumbFile: File get() = File(History.dir, "$id.jpg")
    /** Full 1-bit page data (every row as sent), kept so the job can be viewed, exported, shared and reprinted. */
    val rowsFile: File get() = File(History.dir, "$id.rows")
}

/**
 * Rolling log of finished print jobs — test pages, direct file prints, and jobs routed through
 * Android's print service — with a small thumbnail per entry. All three flows go through
 * [PrintEngine.sendRows], so that's the single place a job gets recorded.
 */
object History {
    private const val MAX = 40
    private const val THUMB_W = 160
    /** Cap how many rows we render into a thumbnail, so a very long job doesn't blow up memory. */
    const val PREVIEW_ROWS = 1200

    lateinit var dir: File; private set
    private lateinit var indexFile: File
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    fun init(c: Context) {
        dir = File(c.filesDir, "history").apply { mkdirs() }
        indexFile = File(dir, "index.tsv")
        _entries.value = load()
    }

    private fun load(): List<HistoryEntry> = runCatching {
        if (!indexFile.exists()) emptyList() else indexFile.readLines().mapNotNull(::parse)
    }.getOrDefault(emptyList())

    private fun parse(line: String): HistoryEntry? {
        val p = line.split('\t')
        if (p.size < 7) return null
        return runCatching {
            HistoryEntry(
                id = p[0], time = p[1].toLong(), printerName = p[2], printerAddr = p[3],
                source = HistorySource.valueOf(p[4]), rows = p[5].toInt(), ok = p[6] == "1",
                error = p.getOrNull(7)?.takeIf { it.isNotEmpty() },
                darkness = p.getOrNull(8)?.toIntOrNull() ?: 60, feedMm = p.getOrNull(9)?.toIntOrNull() ?: 12,
            )
        }.getOrNull()
    }

    private fun flatten(s: String) = s.replace('\t', ' ').replace('\n', ' ')
    private fun serialize(e: HistoryEntry) = listOf(
        e.id, e.time, flatten(e.printerName), e.printerAddr, e.source.name, e.rows, if (e.ok) 1 else 0, flatten(e.error ?: ""), e.darkness, e.feedMm,
    ).joinToString("\t")

    private fun save() = runCatching { indexFile.writeText(_entries.value.joinToString("\n", transform = ::serialize)) }

    /** Record a finished job (success or failure) and save its thumbnail; trims to the last [MAX] entries. */
    fun record(printerName: String, printerAddr: String, source: HistorySource, preview: Bitmap?, rows: Int, ok: Boolean, error: String?,
                rowData: List<ByteArray>? = null, darkness: Int = 60, feedMm: Int = 12) {
        val entry = HistoryEntry(UUID.randomUUID().toString(), System.currentTimeMillis(), printerName, printerAddr, source, rows, ok, error, darkness, feedMm)
        rowData?.let { d -> runCatching { entry.rowsFile.outputStream().buffered().use { o -> d.forEach { o.write(it) } } } }
        preview?.let { runCatching { saveThumb(it, entry.thumbFile) } }
        val kept = (listOf(entry) + _entries.value)
        val trimmed = kept.take(MAX)
        (kept - trimmed.toSet()).forEach { it.thumbFile.delete(); it.rowsFile.delete() }
        _entries.value = trimmed
        save()
    }

    private fun saveThumb(src: Bitmap, out: File) {
        val cropped = if (src.height > PREVIEW_ROWS) Bitmap.createBitmap(src, 0, 0, src.width, PREVIEW_ROWS) else src
        val h = (cropped.height * THUMB_W / cropped.width.toFloat()).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(cropped, THUMB_W, h, true)
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        if (scaled !== src) scaled.recycle()
        if (cropped !== src) cropped.recycle()
    }

    fun remove(e: HistoryEntry) { e.thumbFile.delete(); e.rowsFile.delete(); _entries.value = _entries.value - e; save() }
    fun clear() { _entries.value.forEach { it.thumbFile.delete(); it.rowsFile.delete() }; indexFile.delete(); _entries.value = emptyList() }

    fun loadRows(e: HistoryEntry): List<ByteArray>? = runCatching {
        val b = e.rowsFile.readBytes(); val n = CatProtocol.BYTES
        if (b.size < n) null else List(b.size / n) { b.copyOfRange(it * n, (it + 1) * n) }
    }.getOrNull()

    /** Write the whole job as a PNG (falls back to the thumbnail for older entries without row data). */
    fun writePng(e: HistoryEntry, out: java.io.OutputStream) {
        val rows = loadRows(e)
        val bmp = if (rows != null) CatProtocol.rowsToBitmap(rows) else BitmapFactory.decodeFile(e.thumbFile.path) ?: throw java.io.IOException("Nothing to export")
        try { bmp.compress(Bitmap.CompressFormat.PNG, 100, out) } finally { bmp.recycle() }
    }
}
