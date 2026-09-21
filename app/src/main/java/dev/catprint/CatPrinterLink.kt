package dev.catprint

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** Blocking BLE link to the printer's AE01 (write) / AE02 (notify) characteristics. Call from a worker thread. */
@SuppressLint("MissingPermission")
class CatPrinterLink(private val ctx: Context, private val addr: String) : AutoCloseable {
    private val T = "Link"
    @Volatile var status: PrinterStatus? = null; private set
    @Volatile var connected = false; private set
    @Volatile var onStatus: ((PrinterStatus) -> Unit)? = null
    @Volatile var onDropped: (() -> Unit)? = null

    private val TX = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
    private val RX = UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb")
    private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var gatt: BluetoothGatt? = null
    private var tx: BluetoothGattCharacteristic? = null
    @Volatile private var mtu = 23
    @Volatile private var dead = false
    private val sConn = Semaphore(0); private val sMtu = Semaphore(0); private val sSvc = Semaphore(0)
    private val sDesc = Semaphore(0); private val sWrite = Semaphore(0)

    private val cb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Dbg.d(T, "$addr conn state: gattStatus=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                sConn.release()
            } else {
                val was = connected
                connected = false; dead = true
                listOf(sConn, sMtu, sSvc, sDesc, sWrite).forEach { it.release() }
                if (was) { Dbg.d(T, "$addr dropped"); onDropped?.invoke() }
            }
        }
        override fun onMtuChanged(g: BluetoothGatt, m: Int, status: Int) {
            Dbg.d(T, "mtu=$m status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = m
            sMtu.release()
        }
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) c.value?.let { onNotify(it) }
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onNotify(value)
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { sSvc.release() }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { sDesc.release() }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { sWrite.release() }
    }

    private fun onNotify(v: ByteArray) {
        Dbg.d(T, "notify ${Dbg.hex(v)}")
        if (v.size >= 7 && v[0] == 0x51.toByte() && v[1] == 0x78.toByte() && (v[2].toInt() and 0xFF) == 0xA3) {
            val st = PrinterStatus(v[6].toInt() and 0xFF)
            Dbg.d(T, "status raw=0x%02x problems=%s busy=%s".format(st.raw, st.problems(), st.busy))
            status = st; onStatus?.invoke(st)
        }
    }

    /** Ask the printer for its state; the reply arrives via [onStatus]. */
    fun requestStatus() {
        try { send(CatProtocol.packet(0xA3, byteArrayOf(1))) }
        catch (e: IOException) {
            Dbg.e(T, "status poll failed, marking link dead", e)
            val was = connected; connected = false; dead = true
            if (was) onDropped?.invoke()
        }
    }

    private fun wait(s: Semaphore, ms: Long, what: String) {
        if (!s.tryAcquire(ms, TimeUnit.MILLISECONDS) || dead) throw IOException("Printer: $what failed")
    }

    fun connect(address: String) {
        try { doConnect(address) } catch (e: Exception) { Dbg.e(T, "connect failed", e); close(); throw e }
    }

    private fun doConnect(address: String) {
        Dbg.d(T, "connecting $address")
        val adapter = ctx.getSystemService(BluetoothManager::class.java).adapter
            ?: throw IOException("No Bluetooth")
        if (!adapter.isEnabled) throw IOException("Bluetooth is off")
        gatt = adapter.getRemoteDevice(address).connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE)
        wait(sConn, 15000, "connect")
        gatt!!.requestMtu(247); sMtu.tryAcquire(3, TimeUnit.SECONDS)
        gatt!!.discoverServices(); wait(sSvc, 10000, "service discovery")
        val chars = gatt!!.services.flatMap { it.characteristics }
        Dbg.d(T, "services: " + gatt!!.services.joinToString { it.uuid.toString().substring(4, 8) })
        tx = chars.firstOrNull { it.uuid == TX } ?: throw IOException("Not a supported cat printer (no AE01)")
        tx!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        chars.firstOrNull { it.uuid == RX }?.let { rx ->
            gatt!!.setCharacteristicNotification(rx, true)
            rx.getDescriptor(CCCD)?.let { d ->
                if (Build.VERSION.SDK_INT >= 33) gatt!!.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                else { @Suppress("DEPRECATION") run { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; gatt!!.writeDescriptor(d) } }
                sDesc.tryAcquire(2, TimeUnit.SECONDS)
            }
        }
        connected = true
        Dbg.d(T, "connected, mtu=$mtu")
    }

    fun send(data: ByteArray) {
        if (!connected) throw IOException("Printer: not connected")
        val chunk = (mtu - 3).coerceIn(20, 180)
        Dbg.d(T, "send ${data.size}B chunk=$chunk head=${Dbg.hex(data, 12)}")
        var i = 0
        while (i < data.size) {
            val part = data.copyOfRange(i, minOf(i + chunk, data.size))
            val g = gatt!!; val c = tx!!
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, part, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION") run { c.value = part; g.writeCharacteristic(c) }
            }
            wait(sWrite, 3000, "write")
            Thread.sleep(6)
            i += chunk
        }
    }

    override fun close() { connected = false; Dbg.d(T, "close $addr"); try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {} }
}
