package dev.meowspool

import android.content.Context
import android.content.SharedPreferences

/** Saved printers are stored as "address|name". */
object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.applicationContext.getSharedPreferences("meowspool", Context.MODE_PRIVATE) }

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

    var darkness: Int
        get() = sp.getInt("darkness", 60)
        set(v) = sp.edit().putInt("darkness", v).apply()

    /** Stack trace of the last uncaught crash (kept until dismissed) so it can be shown in the UI. */
    var lastCrash: String?
        get() = sp.getString("lastCrash", null)
        set(v) { sp.edit().apply { if (v == null) remove("lastCrash") else putString("lastCrash", v) }.commit() }

    // Paper presets (built-ins live in [Paper]); custom ones stored as "id|name|lengthMm".
    var paperId: String
        get() = sp.getString("paperId", "roll")!!
        set(v) = sp.edit().putString("paperId", v).apply()
    fun customPapers(): List<Triple<String, String, Int>> = sp.getStringSet("papers", emptySet())!!.mapNotNull {
        val s = it.split('|'); if (s.size == 3) Triple(s[0], s[1], s[2].toIntOrNull() ?: return@mapNotNull null) else null
    }.sortedBy { it.first }
    fun addPaper(id: String, name: String, mm: Int) {
        val s = sp.getStringSet("papers", emptySet())!!.toMutableSet(); s.add("$id|${name.replace('|', ' ')}|$mm")
        sp.edit().putStringSet("papers", s).apply()
    }
    fun removePaper(id: String) {
        sp.edit().putStringSet("papers", sp.getStringSet("papers", emptySet())!!.filterNot { it.startsWith("$id|") }.toSet()).apply()
        if (paperId == id) paperId = "roll"
    }

    /** Extra margin (mm) added inside the 48 mm printable width, each side / top+bottom. */
    var marginSideMm: Int
        get() = sp.getInt("marginSideMm", 0)
        set(v) = sp.edit().putInt("marginSideMm", v).apply()
    var marginVertMm: Int
        get() = sp.getInt("marginVertMm", 0)
        set(v) = sp.edit().putInt("marginVertMm", v).apply()

    var dither: String
        get() = sp.getString("dither", "FLOYD")!!
        set(v) = sp.edit().putString("dither", v).apply()
    var feedMm: Int
        get() = sp.getInt("feedMm", 12)
        set(v) = sp.edit().putInt("feedMm", v).apply()
    var lineBefore: Boolean
        get() = sp.getBoolean("lineBefore", false)
        set(v) = sp.edit().putBoolean("lineBefore", v).apply()
    var lineAfter: Boolean
        get() = sp.getBoolean("lineAfter", false)
        set(v) = sp.edit().putBoolean("lineAfter", v).apply()
    var lineDashed: Boolean
        get() = sp.getBoolean("lineDashed", true)
        set(v) = sp.edit().putBoolean("lineDashed", v).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    /** 0 = follow system, 1 = light, 2 = dark. */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()
    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamic", true)
        set(v) = sp.edit().putBoolean("dynamic", v).apply()
}
