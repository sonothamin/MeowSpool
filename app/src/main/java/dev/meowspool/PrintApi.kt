package dev.meowspool

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class ApiError(val status: Int, msg: String) : Exception(msg)

/** Routes for the print server: a small web page (/) and a JSON API (/api/...). See docs/API.md. */
class PrintApi(private val ctx: Context) {
    private val web = Prefs.serverWeb
    private val api = Prefs.serverApi
    private val auth = Prefs.serverAuth
    private val token = Prefs.serverToken

    fun handle(r: Req): Resp {
        // With auth on the token is the protection, so any origin may call us; with it off, refuse cross-site browser POSTs (drive-by printing).
        val cors = if (auth) mapOf("Access-Control-Allow-Origin" to "*", "Access-Control-Allow-Headers" to "Authorization, Content-Type, X-API-Key", "Access-Control-Allow-Methods" to "GET, POST, OPTIONS") else emptyMap()
        val resp = try { route(r) } catch (e: ApiError) { Resp.err(e.status, e.message ?: "error") }
        return Resp(resp.status, resp.type, resp.body, resp.extra + cors)
    }

    private fun route(r: Req): Resp {
        if (r.method == "OPTIONS") return Resp(204, "text/plain", ByteArray(0))
        if (r.path == "/" || r.path == "/index.html") return if (web) Resp.html(WEB_PAGE.replace("@AUTH@", auth.toString())) else throw ApiError(404, "Web interface is disabled")
        if (!r.path.startsWith("/api")) throw ApiError(404, "Not found")
        if (!api && !(web && r.path.startsWith("/api/") && r.header("referer") != null)) throw ApiError(404, "API is disabled")
        if (auth && !authorized(r)) throw ApiError(401, "Missing or wrong token: send 'Authorization: Bearer <token>' or 'X-API-Key: <token>'")
        if (r.method == "POST" && !auth) r.header("origin")?.let { o ->
            val host = r.header("host")
            if (host == null || o.substringAfter("://") != host) throw ApiError(403, "Cross-origin request blocked")
        }
        return when {
            r.path == "/api" && r.method == "GET" -> Resp.json(200, index())
            r.path == "/api/status" && r.method == "GET" -> Resp.json(200, status())
            r.path == "/api/history" && r.method == "GET" -> Resp.json(200, history(r.query["limit"]?.toIntOrNull() ?: 20))
            r.path == "/api/print" && r.method == "POST" -> Resp.json(200, print(r))
            r.path == "/api/test" && r.method == "POST" -> { val (a, n) = printer(r.query); PrintEngine.printTestPage(a, n); Resp.json(200, JSONObject().put("ok", true)) }
            r.path == "/api/feed" && r.method == "POST" -> { PrintEngine.feed(printer(r.query).first, r.query["mm"]?.toIntOrNull()?.coerceIn(1, 200) ?: Prefs.feedStepMm); Resp.json(200, JSONObject().put("ok", true)) }
            r.path == "/api/retract" && r.method == "POST" -> { PrintEngine.retract(printer(r.query).first, r.query["mm"]?.toIntOrNull()?.coerceIn(1, 200) ?: Prefs.retractStepMm); Resp.json(200, JSONObject().put("ok", true)) }
            else -> throw ApiError(404, "Unknown endpoint. GET /api lists them.")
        }
    }

    private fun authorized(r: Req): Boolean {
        val given = r.header("authorization")?.removePrefix("Bearer ")?.trim() ?: r.header("x-api-key") ?: r.query["token"] ?: return false
        return MessageDigest.isEqual(given.toByteArray(), token.toByteArray())
    }

    private fun printer(q: Map<String, String>): Pair<String, String> {
        val addr = q["printer"] ?: Prefs.selected ?: throw ApiError(409, "No printer selected in the app")
        val name = Prefs.printers().firstOrNull { it.first == addr }?.second ?: throw ApiError(404, "Unknown printer '$addr'")
        return addr to name
    }

    private fun index() = JSONObject().put("name", "MeowSpool").put("endpoints", JSONArray(listOf(
        "GET /api/status", "GET /api/history?limit=20", "POST /api/print (body = image or PDF, or multipart 'file'; params in query or form fields)",
        "POST /api/test", "POST /api/feed?mm=20", "POST /api/retract?mm=20")))
        .put("printParams", "printer, darkness 0-100, feed mm, copies 1-20, size 5-100 (%), side mm, vert mm, rotation 0|90|180|270, brightness/contrast -100..100, invert, dither smooth|sharp|pattern, lineBefore, lineAfter, pages e.g. 2-4")

    private fun status(): JSONObject {
        val printers = JSONArray(); Prefs.printers().forEach { printers.put(JSONObject().put("address", it.first).put("name", it.second)) }
        val o = JSONObject().put("ok", true).put("printers", printers).put("paper", Paper.selected().name)
        val addr = Prefs.selected ?: return o.put("selected", JSONObject.NULL)
        val st = PrinterManager.state(addr)
        o.put("selected", addr).put("connection", st.conn.name.lowercase()).put("printing", st.printing).put("error", st.error ?: JSONObject.NULL)
        st.status?.let { s -> o.put("status", JSONObject().put("outOfPaper", s.outOfPaper).put("coverOpen", s.coverOpen).put("overheat", s.overheat).put("lowBattery", s.lowPower).put("busy", s.busy).put("problems", JSONArray(s.problems()))) }
        return o
    }

    private fun history(n: Int): JSONObject {
        val a = JSONArray()
        History.entries.value.take(n.coerceIn(1, 100)).forEach { a.put(JSONObject().put("time", it.time).put("printer", it.printerName).put("source", it.source.name.lowercase()).put("rows", it.rows).put("ok", it.ok).put("error", it.error ?: JSONObject.NULL)) }
        return JSONObject().put("ok", true).put("history", a)
    }

    // ---- printing ----

    private fun print(r: Req): JSONObject {
        var params = r.query; var data = r.body; var name: String? = null
        val ct = r.header("content-type").orEmpty()
        if (ct.startsWith("multipart/form-data")) {
            val m = multipart(data, ct.substringAfter("boundary=").trim('"', ' '))
            data = m.file ?: throw ApiError(400, "Multipart body needs a 'file' part"); name = m.name; params = m.fields + r.query
        }
        if (data.isEmpty()) throw ApiError(400, "Empty body: send an image or PDF")
        val (addr, _) = printer(params)
        val pdf = data.size > 4 && String(data, 0, 4, Charsets.ISO_8859_1) == "%PDF"
        val tmp = File.createTempFile("api", if (pdf) ".pdf" else ".img", ctx.cacheDir)
        try {
            tmp.writeBytes(data)
            val doc = try { DocSource.open(ctx, Uri.fromFile(tmp), name, pdf) } catch (e: Throwable) { throw ApiError(400, "Not a supported image or PDF") }
            doc.use {
                var s = it.defaults()
                fun int(k: String, lo: Int, hi: Int) = params[k]?.toIntOrNull()?.coerceIn(lo, hi)
                fun bool(k: String) = params[k]?.lowercase()?.let { v -> v in setOf("1", "true", "on", "yes") }
                int("darkness", 0, 100)?.let { v -> s = s.copy(darkness = v) }
                int("feed", 0, 100)?.let { v -> s = s.copy(feedMm = v) }
                int("copies", 1, 20)?.let { v -> s = s.copy(copies = v) }
                int("size", 5, 100)?.let { v -> s = s.copy(sizePct = v) }
                int("side", 0, 10)?.let { v -> s = s.copy(sideMm = v) }
                int("vert", 0, 10)?.let { v -> s = s.copy(vertMm = v) }
                int("rotation", 0, 359)?.let { v -> s = s.copy(rotation = v / 90 * 90) }
                int("brightness", -100, 100)?.let { v -> s = s.copy(brightness = v) }
                int("contrast", -100, 100)?.let { v -> s = s.copy(contrast = v) }
                bool("invert")?.let { v -> s = s.copy(invert = v) }
                bool("lineBefore")?.let { v -> s = s.copy(lineBefore = v) }
                bool("lineAfter")?.let { v -> s = s.copy(lineAfter = v) }
                params["dither"]?.let { v -> Dither.values().firstOrNull { d -> d.name.equals(v, true) || d.label.equals(v, true) }?.let { d -> s = s.copy(dither = d) } }
                params["pages"]?.let { v ->
                    val a = v.substringBefore('-').trim().toIntOrNull(); val b = v.substringAfter('-', v).trim().toIntOrNull() ?: a
                    if (a != null && b != null) s = s.copy(firstPage = (a - 1).coerceIn(0, it.pageCount - 1), lastPage = (b - 1).coerceIn(a - 1, it.pageCount - 1).coerceAtLeast(0))
                }
                if (s.firstPage > s.lastPage) s = s.copy(firstPage = 0)
                val rows = ArrayList<ByteArray>()
                repeat(s.copies) { for (i in s.firstPage..s.lastPage) {
                    val bmp = Composer.compose(it.page(i), s)
                    try { rows += CatProtocol.toRows(bmp, s.dither) } finally { bmp.recycle() }
                    if (rows.size > 40_000) throw ApiError(413, "Job too long (max ~5 m of paper)")
                } }
                PrintEngine.sendRows(addr, rows, s.options(), HistorySource.API)
                return JSONObject().put("ok", true).put("pages", s.lastPage - s.firstPage + 1).put("copies", s.copies).put("rows", rows.size)
            }
        } finally { tmp.delete() }
    }

    private class Multi(val file: ByteArray?, val name: String?, val fields: Map<String, String>)

    private fun multipart(b: ByteArray, boundary: String): Multi {
        val delim = "--$boundary".toByteArray(Charsets.ISO_8859_1)
        fun find(pat: ByteArray, from: Int): Int { outer@ for (i in from..b.size - pat.size) { for (j in pat.indices) if (b[i + j] != pat[j]) continue@outer; return i }; return -1 }
        var file: ByteArray? = null; var fname: String? = null; val fields = HashMap<String, String>()
        var pos = find(delim, 0)
        while (pos >= 0) {
            val start = pos + delim.size
            if (start + 2 <= b.size && b[start] == '-'.code.toByte() && b[start + 1] == '-'.code.toByte()) break
            val next = find(delim, start); if (next < 0) break
            val hdrEnd = find("\r\n\r\n".toByteArray(), start); if (hdrEnd < 0 || hdrEnd > next) break
            val hdr = String(b, start, hdrEnd - start, Charsets.ISO_8859_1)
            val content = b.copyOfRange(hdrEnd + 4, (next - 2).coerceAtLeast(hdrEnd + 4))
            val fn = Regex("filename=\"([^\"]*)\"").find(hdr)?.groupValues?.get(1)
            val nm = Regex("name=\"([^\"]*)\"").find(hdr)?.groupValues?.get(1)
            if (fn != null) { if (file == null || nm == "file") { file = content; fname = fn } } else if (nm != null) fields[nm] = String(content)
            pos = next
        }
        return Multi(file, fname, fields)
    }
}

private const val WEB_PAGE = """<!doctype html><html><head><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><title>MeowSpool</title>
<style>:root{color-scheme:light dark}body{font:16px system-ui;max-width:460px;margin:0 auto;padding:20px;display:grid;gap:14px}h1{margin:0;font-size:24px}
label{display:grid;gap:4px;font-size:14px}.row{display:grid;grid-template-columns:1fr 1fr;gap:10px}input,select,button{font:inherit;padding:10px;border-radius:12px;border:1px solid #8886}
button{background:#7c5cff;color:#fff;border:0;font-weight:600}button:disabled{opacity:.5}#st{opacity:.75;font-size:14px}#msg{min-height:1.4em}.ck{display:flex;gap:8px;align-items:center}</style></head><body>
<h1>🐱 MeowSpool</h1><div id=st>Checking printer…</div>
<input id=f type=file accept="image/*,application/pdf">
<div class=row><label>Darkness <input id=darkness type=number min=0 max=100 placeholder=default></label><label>Copies <input id=copies type=number min=1 max=20 value=1></label>
<label>Size % <input id=size type=number min=5 max=100 value=100></label><label>Feed mm <input id=feed type=number min=0 max=100 placeholder=default></label></div>
<label>Dithering <select id=dither><option value="">Default</option><option>smooth</option><option>sharp</option><option>pattern</option></select></label>
<label class=ck><input id=invert type=checkbox> Invert</label>
<input id=tok type=password placeholder="Access token" style="display:none">
<button id=go>Print</button><div id=msg></div>
<script>
var AUTH=@AUTH@,T=(location.hash.match(/t=([^&]+)/)||[])[1]||"",g=function(i){return document.getElementById(i)};
if(AUTH){g("tok").style.display="";g("tok").value=T}
function H(){var t=AUTH?g("tok").value:"";return t?{Authorization:"Bearer "+t}:{}}
function st(){fetch("/api/status",{headers:H()}).then(function(r){return r.json()}).then(function(j){
if(!j.ok){g("st").textContent=j.error;return}
var s=j.status,p=s&&s.problems.length?" · "+s.problems.join(", "):"";
g("st").textContent=j.selected?(j.printers.filter(function(x){return x.address==j.selected})[0].name+" · "+j.connection+p):"No printer selected in the app"}).catch(function(){g("st").textContent="Can't reach the phone"})}
st();setInterval(st,5000);
g("go").onclick=function(){var f=g("f").files[0];if(!f){g("msg").textContent="Choose a file first";return}
var q=[];["darkness","copies","size","feed","dither"].forEach(function(k){if(g(k).value)q.push(k+"="+encodeURIComponent(g(k).value))});
if(g("invert").checked)q.push("invert=1");
g("go").disabled=true;g("msg").textContent="Printing…";
fetch("/api/print?"+q.join("&"),{method:"POST",headers:H(),body:f}).then(function(r){return r.json()}).then(function(j){
g("msg").textContent=j.ok?"Done ✓ ("+j.pages+" page"+(j.pages==1?"":"s")+")":"Failed: "+j.error}).catch(function(e){g("msg").textContent="Failed: "+e}).then(function(){g("go").disabled=false})};
</script></body></html>"""
