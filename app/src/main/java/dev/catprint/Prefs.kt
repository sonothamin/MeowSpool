package dev.catprint

import android.content.Context

/** Saved printers are stored as "address|name". */
object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("cat", Context.MODE_PRIVATE)

    fun printers(c: Context): List<Pair<String, String>> =
        p(c).getStringSet("printers", emptySet())!!.map {
            val i = it.indexOf('|'); it.substring(0, i) to it.substring(i + 1)
        }

    fun add(c: Context, addr: String, name: String) {
        val s = p(c).getStringSet("printers", emptySet())!!.toMutableSet()
        s.removeAll { it.startsWith("$addr|") }
        s.add("$addr|$name")
        p(c).edit().putStringSet("printers", s).apply()
    }

    fun clear(c: Context) = p(c).edit().remove("printers").apply()

    var Context.darkness: Int
        get() = p(this).getInt("darkness", 60)
        set(v) = p(this).edit().putInt("darkness", v).apply()
}
