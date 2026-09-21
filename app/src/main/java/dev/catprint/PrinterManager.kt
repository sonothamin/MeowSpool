package dev.catprint

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

enum class Conn { IDLE, CONNECTING, CONNECTED, ERROR }
data class PState(
    val conn: Conn = Conn.IDLE,
    val status: PrinterStatus? = null,
    val error: String? = null,
    val printing: Boolean = false,
)

/**
 * Keeps ("latches") a BLE connection open to each printer that someone holds, reconnects on drop,
 * and polls status. Holders: the app UI (selected printer), the print service (tracking / jobs).
 * The connection is released [GRACE_MS] after the last holder lets go.
 */
object PrinterManager {
    private const val T = "Manager"
    private const val GRACE_MS = 30_000L
    private const val POLL_MS = 2_500L

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = ReentrantLock()                      // serialises radio I/O (poll vs print)
    private val links = ConcurrentHashMap<String, CatPrinterLink>()
    private val holds = HashMap<String, Int>()
    private val loops = HashMap<String, Job>()
    private val releases = HashMap<String, Job>()
    private val _states = MutableStateFlow<Map<String, PState>>(emptyMap())
    val states = _states.asStateFlow()

    fun init(c: Context) { app = c.applicationContext }
    fun state(addr: String) = _states.value[addr] ?: PState()
    private fun update(addr: String, f: (PState) -> PState) = _states.update { it + (addr to f(it[addr] ?: PState())) }

    @Synchronized fun latch(addr: String) {
        releases.remove(addr)?.cancel()
        holds[addr] = (holds[addr] ?: 0) + 1
        Dbg.d(T, "latch $addr holds=${holds[addr]}")
        if (loops[addr]?.isActive != true) loops[addr] = scope.launch { maintain(addr) }
    }

    @Synchronized fun release(addr: String) {
        val n = (holds[addr] ?: 0) - 1
        Dbg.d(T, "release $addr holds=$n")
        if (n > 0) { holds[addr] = n; return }
        holds.remove(addr)
        releases[addr]?.cancel()
        releases[addr] = scope.launch { delay(GRACE_MS); teardown(addr) }
    }

    private fun teardown(addr: String) {
        synchronized(this) { if (holds.containsKey(addr)) return; loops.remove(addr)?.cancel(); releases.remove(addr) }
        Dbg.d(T, "teardown $addr (no holders)")
        lock.lock()
        try { links.remove(addr)?.close() } finally { lock.unlock() }
        update(addr) { PState() }
    }

    /** Drop the current link; the maintain loop reconnects. */
    fun reconnect(addr: String) {
        Dbg.d(T, "manual reconnect $addr")
        scope.launch {
            lock.lock()
            try { links.remove(addr)?.close() } finally { lock.unlock() }
            update(addr) { it.copy(conn = Conn.IDLE, error = null) }
        }
    }

    private suspend fun maintain(addr: String) {
        var backoff = 1000L
        Dbg.d(T, "maintain loop start $addr")
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    val link = links[addr]
                    if (link == null || !link.connected) {
                        if (!ensureConnected(addr)) { delay(backoff); backoff = (backoff * 2).coerceAtMost(15_000); continue }
                        backoff = 1000
                    } else if (lock.tryLock()) {
                        try { link.requestStatus() } finally { lock.unlock() }
                    }
                    delay(POLL_MS)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { Dbg.e(T, "loop error", e); delay(backoff) }
            }
        } finally { Dbg.d(T, "maintain loop end $addr") }
    }

    private fun ensureConnected(addr: String): Boolean {
        lock.lock()
        try {
            links[addr]?.let { if (it.connected) return true; it.close(); links.remove(addr) }
            update(addr) { it.copy(conn = Conn.CONNECTING, error = null) }
            val link = CatPrinterLink(app, addr)
            link.onStatus = { s -> update(addr) { it.copy(status = s) } }
            link.onDropped = { update(addr) { it.copy(conn = Conn.ERROR, error = "Connection lost") } }
            link.connect(addr)
            links[addr] = link
            update(addr) { it.copy(conn = Conn.CONNECTED, error = null) }
            link.requestStatus()
            return true
        } catch (e: Exception) {
            Dbg.e(T, "ensureConnected $addr failed", e)
            update(addr) { it.copy(conn = Conn.ERROR, error = e.message ?: e.javaClass.simpleName) }
            return false
        } finally { lock.unlock() }
    }

    /** Run [block] with exclusive use of the (already latched, else on-demand) link. */
    fun <R> withLink(addr: String, block: (CatPrinterLink) -> R): R {
        lock.lock()
        try {
            if (links[addr]?.connected != true) {
                Dbg.d(T, "withLink: not latched yet, connecting on demand")
                if (!ensureConnected(addr)) throw IOException(state(addr).error ?: "Cannot connect to printer")
            }
            update(addr) { it.copy(printing = true) }
            try { return block(links[addr]!!) } finally { update(addr) { it.copy(printing = false) } }
        } finally { lock.unlock() }
    }

}
