package dev.catprint

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Locale

/** Debug logger: always on; kept in a ring buffer and shown in the app's Debug log screen. */
object Dbg {
    private const val MAX = 1000
    private val buf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    val version = MutableStateFlow(0)

    fun d(tag: String, msg: String) {
        Log.d("Meow", "[$tag] $msg")
        add("D", tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e("Meow", "[$tag] $msg", t)
        add("E", tag, msg + (t?.let { " — ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    private fun add(lvl: String, tag: String, msg: String) {
        synchronized(buf) {
            buf.addLast("${fmt.format(System.currentTimeMillis())} $lvl/$tag $msg")
            while (buf.size > MAX) buf.removeFirst()
        }
        version.value++
    }

    fun snapshot(): List<String> = synchronized(buf) { buf.toList() }
    fun clear() { synchronized(buf) { buf.clear() }; version.value++ }
    fun hex(b: ByteArray, max: Int = 32): String =
        b.take(max).joinToString(" ") { "%02x".format(it) } + if (b.size > max) " …(${b.size}B)" else ""
}
