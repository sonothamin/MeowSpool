package dev.meowspool

import java.io.*
import java.net.*
import java.util.concurrent.Executors

class Req(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: ByteArray) {
    fun header(k: String) = headers[k.lowercase()]
}

class Resp(val status: Int, val type: String, val body: ByteArray, val extra: Map<String, String> = emptyMap()) {
    companion object {
        fun json(status: Int, j: org.json.JSONObject) = Resp(status, "application/json; charset=utf-8", j.toString(2).toByteArray())
        fun html(s: String) = Resp(200, "text/html; charset=utf-8", s.toByteArray())
        fun err(status: Int, msg: String) = json(status, org.json.JSONObject().put("ok", false).put("error", msg))
    }
}

/** Tiny dependency-free HTTP/1.1 server: one request per connection, bodies held in memory (capped). */
class HttpServer(private val port: Int, private val lan: Boolean, private val handler: (Req) -> Resp) {
    companion object { const val MAX_BODY = 30 * 1024 * 1024 }
    private var ss: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    fun start() {
        val s = ServerSocket().apply { reuseAddress = true }
        s.bind(InetSocketAddress(if (lan) InetAddress.getByName("0.0.0.0") else InetAddress.getLoopbackAddress(), port))
        ss = s
        pool.execute { while (!s.isClosed) try { val c = s.accept(); pool.execute { serve(c) } } catch (_: IOException) { break } }
    }

    fun stop() { runCatching { ss?.close() }; pool.shutdownNow() }

    private fun line(i: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = i.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString()
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > 8192) throw IOException("header too long")
        }
    }

    private fun serve(c: Socket) = c.use {
        try {
            c.soTimeout = 20_000
            val inp = BufferedInputStream(c.getInputStream()); val out = c.getOutputStream()
            val first = line(inp)?.split(' ') ?: return
            if (first.size < 2) return
            val headers = HashMap<String, String>()
            while (true) { val l = line(inp) ?: break; if (l.isEmpty()) break; l.indexOf(':').takeIf { it > 0 }?.let { headers[l.substring(0, it).trim().lowercase()] = l.substring(it + 1).trim() } }
            val len = headers["content-length"]?.toLongOrNull() ?: 0L
            val (path, qs) = first[1].split('?', limit = 2).let { it[0] to it.getOrNull(1).orEmpty() }
            val resp = if (len > MAX_BODY) Resp.err(413, "Body too large (max ${MAX_BODY / 1024 / 1024} MB)") else {
                if (headers["expect"]?.startsWith("100") == true) { out.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray()); out.flush() }
                val body = ByteArray(len.toInt()).also { DataInputStream(inp).readFully(it) }
                val query = qs.split('&').filter { it.isNotEmpty() }.associate { kv ->
                    val p = kv.split('=', limit = 2); URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p.getOrElse(1) { "" }, "UTF-8")
                }
                try { handler(Req(first[0].uppercase(), path, query, headers, body)) }
                catch (e: Throwable) { Dbg.e("Http", "handler failed", e); Resp.err(500, e.message ?: e.javaClass.simpleName) }
            }
            val reason = mapOf(200 to "OK", 204 to "No Content", 400 to "Bad Request", 401 to "Unauthorized", 403 to "Forbidden", 404 to "Not Found", 409 to "Conflict", 413 to "Payload Too Large", 500 to "Internal Server Error")[resp.status] ?: "OK"
            val h = StringBuilder("HTTP/1.1 ${resp.status} $reason\r\nContent-Type: ${resp.type}\r\nContent-Length: ${resp.body.size}\r\nConnection: close\r\nCache-Control: no-store\r\n")
            resp.extra.forEach { (k, v) -> h.append("$k: $v\r\n") }
            out.write(h.append("\r\n").toString().toByteArray()); if (first[0].uppercase() != "HEAD") out.write(resp.body); out.flush()
        } catch (e: Throwable) { Dbg.d("Http", "connection error: ${e.message}") }
    }
}
