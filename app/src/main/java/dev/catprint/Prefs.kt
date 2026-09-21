package dev.catprint

import android.content.Context
import android.content.SharedPreferences

/** Saved printers are stored as "address|name". */
object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.applicationContext.getSharedPreferences("cat", Context.MODE_PRIVATE) }

    fun printers(): List<Pair<String, String>> =
        sp.getStringSet("printers", emptySet())!!.map {
            val i = it.indexOf('|'); it.substring(0, i) to it.substring(i + 1)
        }.sortedBy { it.second }

    fun add(addr: String, name: String) {
        val s = sp.getStringSet("printers", emptySet())!!.toMutableSet()
        s.removeAll { it.startsWith("$addr|") }
        s.add("$addr|$name")
        sp.edit().putStringSet("printers", s).apply()
        if (selected == null) selected = addr
    }

    fun remove(addr: String) {
        val s = sp.getStringSet("printers", emptySet())!!.filterNot { it.startsWith("$addr|") }.toSet()
        sp.edit().putStringSet("printers", s).apply()
        if (selected == addr) selected = printers().firstOrNull()?.first
    }

    fun clear() { sp.edit().remove("printers").remove("selected").apply() }

    /** The printer we keep a latched (persistent) connection to. */
    var selected: String?
        get() = sp.getString("selected", null)
        set(v) = sp.edit().putString("selected", v).apply()

    var debug: Boolean
        get() = sp.getBoolean("debug", false)
        set(v) = sp.edit().putBoolean("debug", v).apply()

    var darkness: Int
        get() = sp.getInt("darkness", 60)
        set(v) = sp.edit().putInt("darkness", v).apply()

    /** Stack trace of the last uncaught crash (kept until dismissed) so it can be shown in the UI. */
    var lastCrash: String?
        get() = sp.getString("lastCrash", null)
        set(v) { sp.edit().apply { if (v == null) remove("lastCrash") else putString("lastCrash", v) }.commit() }
}
