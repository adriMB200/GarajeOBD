package com.adrimb200.garageobd.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

/** Un registro simple de un adaptador previamente emparejado con Android. */
data class PairedDevice(val name: String, val address: String)

class BluetoothElm(context: Context) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager?.adapter
    private val appContext = context.applicationContext
    private val commandMutex = Mutex()

    @Volatile private var socket: BluetoothSocket? = null
    private val spp: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun isBluetoothAvailable(): Boolean = adapter != null

    fun isBluetoothEnabled(): Boolean = try {
        requirePermission()
        adapter?.isEnabled == true
    } catch (_: SecurityException) { false }

    private fun requirePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) throw SecurityException("Se necesita el permiso de Dispositivos cercanos")
    }

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedDevice> {
        requirePermission()
        val bt = adapter ?: return emptyList()
        if (!bt.isEnabled) return emptyList()
        return bt.bondedDevices.orEmpty().map { dev ->
            PairedDevice(dev.name?.ifBlank { "Dispositivo Bluetooth" } ?: "Dispositivo Bluetooth", dev.address)
        }.sortedBy { it.name.lowercase() }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String) = withContext(Dispatchers.IO) {
        requirePermission()
        val bt = adapter ?: throw IOException("Tu teléfono no tiene Bluetooth")
        if (!bt.isEnabled) throw IOException("Activa Bluetooth antes de conectar")
        val device = bt.getRemoteDevice(address)
        // No se realiza descubrimiento: no necesitamos BLUETOOTH_SCAN ni ubicación.
        var firstFailure: IOException? = null
        for (secure in listOf(true, false)) {
            currentCoroutineContext().ensureActive()
            val candidate = if (secure) device.createRfcommSocketToServiceRecord(spp)
                else device.createInsecureRfcommSocketToServiceRecord(spp)
            socket = candidate // Permite interrumpir connect() cerrando el socket desde otro hilo.
            try {
                candidate.connect()
                return@withContext
            } catch (e: IOException) {
                candidate.closeSafely()
                if (socket === candidate) socket = null
                currentCoroutineContext().ensureActive()
                if (firstFailure == null) firstFailure = e
            }
        }
        throw IOException("No se pudo abrir Bluetooth SPP. Comprueba el emparejamiento y que el ELM327 no esté ocupado.", firstFailure)
    }

    suspend fun initialize() {
        execute("ATZ", 7_000)
        execute("ATE0")
        execute("ATL0")
        execute("ATS0")
        execute("ATH0")
        execute("ATSP0") // Selección automática del protocolo OBD.
        val confirmation = execute("0100", 15_000)
        if (!ObdParser.hasEngineResponse(confirmation)) {
            throw IOException("El ELM327 responde, pero la ECU no devuelve datos OBD-II. Comprueba el contacto, el motor y la compatibilidad. Respuesta: ${confirmation.take(80)}")
        }
    }

    /** Envía un comando y espera el prompt `>`; jamás intercala comandos. */
    suspend fun execute(command: String, timeoutMs: Long = 5_000): String =
        commandMutex.withLock {
            withContext(Dispatchers.IO) {
                val active = socket ?: throw IOException("Bluetooth desconectado")
                withTimeout(timeoutMs) {
                    active.outputStream.write("$command\r".toByteArray(Charsets.US_ASCII))
                    active.outputStream.flush()
                    readUntilPrompt(active)
                }
            }
        }

    private suspend fun readUntilPrompt(active: BluetoothSocket): String {
        val incoming = StringBuilder()
        val stream = active.inputStream
        while (true) {
            val available = stream.available()
            if (available > 0) {
                repeat(available.coerceAtMost(512)) {
                    val ch = stream.read()
                    if (ch == -1) throw IOException("ELM327 ha cerrado la conexión")
                    if (ch == '>'.code) return incoming.toString().trim()
                    if (incoming.length < 8192) incoming.append(ch.toChar())
                }
            } else delay(25)
        }
    }

    fun close() {
        val old = socket
        socket = null
        old?.closeSafely()
    }
}

private fun BluetoothSocket.closeSafely() = try { close() } catch (_: IOException) { }
