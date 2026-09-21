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
class CatPrinterLink(private val ctx: Context) : AutoCloseable {
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
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                sConn.release()
            } else {
                dead = true
                listOf(sConn, sMtu, sSvc, sDesc, sWrite).forEach { it.release() }
            }
        }
        override fun onMtuChanged(g: BluetoothGatt, m: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = m
            sMtu.release()
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { sSvc.release() }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { sDesc.release() }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { sWrite.release() }
    }

    private fun wait(s: Semaphore, ms: Long, what: String) {
        if (!s.tryAcquire(ms, TimeUnit.MILLISECONDS) || dead) throw IOException("Printer: $what failed")
    }

    fun connect(address: String) {
        val adapter = ctx.getSystemService(BluetoothManager::class.java).adapter
            ?: throw IOException("No Bluetooth")
        if (!adapter.isEnabled) throw IOException("Bluetooth is off")
        gatt = adapter.getRemoteDevice(address).connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE)
        wait(sConn, 15000, "connect")
        gatt!!.requestMtu(247); sMtu.tryAcquire(3, TimeUnit.SECONDS)
        gatt!!.discoverServices(); wait(sSvc, 10000, "service discovery")
        val chars = gatt!!.services.flatMap { it.characteristics }
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
    }

    fun send(data: ByteArray) {
        val chunk = (mtu - 3).coerceIn(20, 180)
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

    override fun close() { try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {} }
}
