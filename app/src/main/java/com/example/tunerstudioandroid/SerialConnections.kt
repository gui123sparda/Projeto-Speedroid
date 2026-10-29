package com.example.tunerstudioandroid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import org.clonetuner.comm.SerialIo
import java.io.IOException
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

/** Classic Bluetooth SPP (e.g. HC-05/HC-06), paired in Android settings first. */
@SuppressLint("MissingPermission")
class BluetoothSerial(private val device: BluetoothDevice) : SerialIo {
    @Volatile private var socket: BluetoothSocket? = null
    override fun description() = "Bluetooth · ${device.name ?: device.address}"
    override fun open() {
        val candidate = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"))
        socket = candidate
        val timeout = Timer("bluetooth-connect-timeout", true)
        timeout.schedule(object : TimerTask() { override fun run() { runCatching { candidate.close() } } }, 12000)
        try { candidate.connect() } catch (e: Exception) { close(); throw e } finally { timeout.cancel() }
    }
    override fun isOpen() = socket?.isConnected == true
    override fun read(buffer: ByteArray, count: Int): Int {
        try {
            val input = socket?.inputStream ?: throw IOException("Bluetooth desconectado")
            val available = input.available()
            if (available == 0) { Thread.sleep(5); return 0 }
            return input.read(buffer, 0, minOf(count, available)).also { if (it < 0) throw IOException("Conexão encerrada") }
        } catch (e: Exception) { throw IllegalStateException("Leitura Bluetooth: ${e.message}", e) }
    }
    override fun write(buffer: ByteArray): Int {
        try {
            val output = socket?.outputStream ?: throw IOException("Bluetooth desconectado")
            output.write(buffer); output.flush(); return buffer.size
        } catch (e: Exception) { throw IllegalStateException("Envio Bluetooth: ${e.message}", e) }
    }
    override fun close() { runCatching { socket?.close() }; socket = null }
}

class UsbSerial(private val manager: UsbManager, private val port: UsbSerialPort, private val baud: Int) : SerialIo {
    @Volatile private var opened = false
    private var connection: android.hardware.usb.UsbDeviceConnection? = null
    override fun description() = "USB · ${port.driver.javaClass.simpleName} · porta ${port.portNumber + 1} · $baud baud"
    override fun open() {
        val link = manager.openDevice(port.driver.device) ?: throw IOException("Sem permissão para USB")
        connection = link
        try {
            port.open(link)
            port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            // Avoid toggling DTR: many Arduino boards reset when it changes.
            opened = true
        } catch (e: Exception) { close(); throw e }
    }
    override fun isOpen() = opened && port.isOpen
    override fun read(buffer: ByteArray, count: Int): Int {
        try {
            val temporary = ByteArray(count)
            val read = port.read(temporary, 150)
            temporary.copyInto(buffer, 0, 0, read)
            return read
        } catch (e: Exception) { opened = false; throw IllegalStateException("Leitura USB: ${e.message}", e) }
    }
    override fun write(buffer: ByteArray): Int {
        try { port.write(buffer, 1500); return buffer.size }
        catch (e: Exception) { opened = false; throw IllegalStateException("Envio USB: ${e.message}", e) }
    }
    override fun close() { opened = false; runCatching { port.close() }; connection?.close(); connection = null }
}
