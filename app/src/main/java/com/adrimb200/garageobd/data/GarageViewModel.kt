package com.adrimb200.garageobd.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sin

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, DEMO }

data class DashboardState(
    val devices: List<PairedDevice> = emptyList(),
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val deviceName: String = "",
    val rpm: Int? = null,
    val coolant: Int? = null,
    val speed: Int? = null,
    val rpmHistory: List<Int> = emptyList(),
    val dtcs: List<String> = emptyList(),
    val dtcsRead: Boolean = false,
    val readingDtcs: Boolean = false,
    val error: String? = null,
)

class GarageViewModel(application: Application) : AndroidViewModel(application) {
    private val elm = BluetoothElm(application)
    private val mutableState = MutableStateFlow(DashboardState())
    val state = mutableState.asStateFlow()
    private var connectJob: Job? = null
    private var pollingJob: Job? = null
    private var demoJob: Job? = null
    private var dtcJob: Job? = null
    private val generation = AtomicInteger(0)

    fun bluetoothPresent(): Boolean = elm.isBluetoothAvailable()
    fun bluetoothEnabled(): Boolean = elm.isBluetoothEnabled()

    fun refreshDevices() {
        try {
            mutableState.update { it.copy(devices = elm.pairedDevices(), error = null) }
        } catch (_: SecurityException) {
            mutableState.update { it.copy(error = "Concede permiso para acceder a los dispositivos Bluetooth emparejados.") }
        } catch (e: Exception) {
            mutableState.update { it.copy(error = e.message ?: "No se pudo consultar Bluetooth") }
        }
    }

    fun startDemo() {
        disconnect()
        mutableState.update {
            it.copy(status = ConnectionStatus.DEMO, deviceName = "Simulación", rpm = 0,
                coolant = 82, speed = 0, rpmHistory = emptyList(), dtcs = emptyList(),
                dtcsRead = false, error = null)
        }
        demoJob = viewModelScope.launch {
            var step = 0
            while (isActive) {
                val value = (1700 + 900 * sin(step / 3.3) + 420 * sin(step / 1.7)).toInt().coerceAtLeast(700)
                mutableState.update {
                    it.copy(rpm = value, coolant = 82 + (step / 24).coerceAtMost(9),
                        speed = (value / 72).coerceAtMost(140),
                        rpmHistory = (it.rpmHistory + value).takeLast(48))
                }
                step++
                delay(650)
            }
        }
    }

    fun connectTo(device: PairedDevice) {
        disconnect()
        val token = generation.get()
        mutableState.update { it.copy(status = ConnectionStatus.CONNECTING, deviceName = device.name, error = null) }
        connectJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    elm.connect(device.address)
                    elm.initialize()
                }
                if (generation.get() != token) return@launch
                mutableState.update { it.copy(status = ConnectionStatus.CONNECTED, error = null) }
                startPolling(token)
            } catch (_: TimeoutCancellationException) {
                if (generation.get() == token) {
                    elm.close()
                    mutableState.update { it.copy(status = ConnectionStatus.DISCONNECTED,
                        error = "Tiempo de espera agotado. Comprueba el contacto y el adaptador ELM327.") }
                }
            } catch (_: CancellationException) {
                if (generation.get() == token) elm.close()
            } catch (e: Exception) {
                if (generation.get() == token) {
                    elm.close()
                    mutableState.update {
                        it.copy(status = ConnectionStatus.DISCONNECTED,
                            error = "Error de conexión: ${e.message ?: "sin respuesta"}")
                    }
                }
            }
        }
    }

    private fun startPolling(token: Int) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val rpm = ObdParser.rpm(elm.execute("010C"))
                    val temp = ObdParser.coolant(elm.execute("0105"))
                    val speed = ObdParser.speed(elm.execute("010D"))
                    mutableState.update { previous ->
                        previous.copy(rpm = rpm, coolant = temp, speed = speed,
                            rpmHistory = if (rpm == null) previous.rpmHistory
                            else (previous.rpmHistory + rpm).takeLast(48))
                    }
                    delay(650)
                }
            } catch (_: TimeoutCancellationException) {
                if (generation.get() == token) {
                    elm.close()
                    mutableState.update { it.copy(status = ConnectionStatus.DISCONNECTED,
                        error = "ELM327 dejó de responder durante la lectura de parámetros (timeout).") }
                }
            } catch (_: CancellationException) {
                // Desconexión manual o consulta puntual de DTC.
            } catch (e: Exception) {
                if (generation.get() == token) {
                    elm.close()
                    mutableState.update { it.copy(status = ConnectionStatus.DISCONNECTED,
                        error = "Se interrumpió la comunicación: ${e.message ?: "error desconocido"}") }
                }
            }
        }
    }

    fun readFaultCodes() {
        if (state.value.status == ConnectionStatus.DEMO) {
            mutableState.update { it.copy(dtcs = listOf("P0133", "P0420"), dtcsRead = true) }
            return
        }
        if (state.value.status != ConnectionStatus.CONNECTED || state.value.readingDtcs) return
        val token = generation.get()
        dtcJob = viewModelScope.launch {
            mutableState.update { it.copy(readingDtcs = true, error = null) }
            try {
                pollingJob?.cancelAndJoin()
                val reply = elm.execute("03", 12_000)
                if (reply.contains("NO DATA", ignoreCase = true)) {
                    mutableState.update { it.copy(dtcs = emptyList(), dtcsRead = true) }
                } else if (!reply.contains("43")) {
                    mutableState.update { it.copy(error = "Respuesta DTC no reconocida: ${reply.take(120)}") }
                } else {
                    mutableState.update { it.copy(dtcs = ObdParser.storedDtcs(reply), dtcsRead = true) }
                }
            } catch (_: TimeoutCancellationException) {
                mutableState.update { it.copy(error = "Se agotó el tiempo esperando códigos de avería.") }
            } catch (_: CancellationException) {
                // Se desconectó.
            } catch (e: Exception) {
                mutableState.update { it.copy(error = "Error leyendo DTC: ${e.message}") }
            } finally {
                mutableState.update { it.copy(readingDtcs = false) }
                if (generation.get() == token && state.value.status == ConnectionStatus.CONNECTED) startPolling(token)
            }
        }
    }

    fun disconnect() {
        generation.incrementAndGet()
        connectJob?.cancel()
        pollingJob?.cancel()
        demoJob?.cancel()
        dtcJob?.cancel()
        elm.close()
        mutableState.update {
            it.copy(status = ConnectionStatus.DISCONNECTED, deviceName = "", rpm = null,
                coolant = null, speed = null, rpmHistory = emptyList(), dtcs = emptyList(),
                readingDtcs = false, dtcsRead = false, error = null)
        }
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
