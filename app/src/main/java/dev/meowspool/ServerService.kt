package dev.meowspool

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

data class ServerState(val running: Boolean = false, val port: Int = 0, val lan: Boolean = true, val error: String? = null)

/** Foreground service hosting the print server so it keeps answering with the screen off. */
class ServerService : Service() {
    companion object {
        val state = MutableStateFlow(ServerState())
        private const val CH = "server"; private const val ID = 4711; private const val STOP = "dev.meowspool.STOP_SERVER"

        /** Start, restart (settings changed) or stop the service to match [Prefs.serverEnabled]. */
        fun apply(c: Context) {
            val i = Intent(c, ServerService::class.java)
            if (Prefs.serverEnabled) runCatching { c.startForegroundService(i) }.onFailure { Dbg.e("Server", "cannot start", it) } else c.stopService(i)
        }

        fun lanIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()

        /** Address to show/copy; includes the token in the #fragment (never sent to the server) when auth is on. */
        fun url(withToken: Boolean = false): String? {
            val s = state.value; if (!s.running) return null
            val host = if (s.lan) lanIp() ?: return null else "127.0.0.1"
            return "http://$host:${s.port}/" + if (withToken && Prefs.serverAuth) "#t=${Prefs.serverToken}" else ""
        }
    }

    private var server: HttpServer? = null
    private var latched: String? = null
    private var wake: PowerManager.WakeLock? = null
    @Suppress("DEPRECATION") private var wifi: WifiManager.WifiLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == STOP) { Prefs.serverEnabled = false; stopSelf(); return START_NOT_STICKY }
        if (!Prefs.serverEnabled) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Print server", NotificationManager.IMPORTANCE_LOW))
        try {
            ServiceCompat.startForeground(this, ID, notification("Starting…"), if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (e: Throwable) { Dbg.e("Server", "startForeground failed", e); state.value = ServerState(error = e.message); stopSelf(); return START_NOT_STICKY }
        stopServer()
        val port = Prefs.serverPort; val lan = Prefs.serverLan
        try {
            server = HttpServer(port, lan, PrintApi(this)::handle).also { it.start() }
            state.value = ServerState(true, port, lan)
            hold()
            Prefs.selected?.let { PrinterManager.latch(it); latched = it }
            Dbg.d("Server", "listening on ${if (lan) "0.0.0.0" else "127.0.0.1"}:$port")
        } catch (e: Throwable) {
            Dbg.e("Server", "start failed", e)
            state.value = ServerState(false, port, lan, if (e is java.net.BindException) "Port $port is already in use" else e.message)
        }
        nm.notify(ID, notification(state.value.error ?: url() ?: "Running"))
        return START_STICKY
    }

    private fun hold() {
        if (wake == null) wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "meowspool:server").apply { acquire() }
        @Suppress("DEPRECATION")
        if (wifi == null) wifi = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "meowspool:server").apply { acquire() }
    }

    private fun stopServer() {
        server?.stop(); server = null
        latched?.let { PrinterManager.release(it) }; latched = null
    }

    override fun onDestroy() {
        stopServer()
        runCatching { wake?.release() }; runCatching { wifi?.release() }; wake = null; wifi = null
        state.value = ServerState()
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ServerService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CH).setSmallIcon(R.drawable.ic_meowspool).setContentTitle("MeowSpool print server")
            .setContentText(text).setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
    }
}
