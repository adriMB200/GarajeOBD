package com.adrimb200.garageobd.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val selectedGauges: List<ObdSensor> = ObdSensor.defaults,
    val elmVersion: String = "",
    val obdProtocol: String = "",
    val supportedPids: Set<Int>? = null,
    val sensorValues: Map<ObdSensor, Double> = emptyMap(),
    val dtcs: List<String> = emptyList(),
    val dtcsRead: Boolean = false,
    val readingDtcs: Boolean = false,
    val error: String? = null,
)

class GarageViewModel(application: Application) : AndroidViewModel(application) {
    private val elm = BluetoothElm(application)
    private val prefs = application.getSharedPreferences("garage_obd_gauges", 0)
    private fun savedGauges(): List<ObdSensor> {
        val saved = prefs.getString("gauges", null) ?: return ObdSensor.defaults
        val valid = saved.split(",").mapNotNull { name ->
            ObdSensor.values().firstOrNull { it.name == name }
        }.distinct().take(ObdSensor.MAX_GAUGES)
        return valid.ifEmpty { ObdSensor.defaults }
    }

    private val mutableState = MutableStateFlow(DashboardState(selectedGauges = savedGauges()))
    val state = mutableState.asStateFlow()
    private var connectJob: Job? = null
    private var pollingJob: Job? = null
    private var demoJob: Job? = null
    private var dtcJob: Job? = null
    private val generation = AtomicInteger(0)

    fun bluetoothPresent(): Boolean = elm.isBluetoothAvailable()
    fun bluetoothEnabled(): Boolean = elm.isBluetoothEnabled()

    fun toggleGauge(sensor: ObdSensor) {
        val current = state.value.selectedGauges
        val next = if (sensor in current) {
            if (current.size == 1) return // Siempre debe quedar al menos un reloj
            current - sensor
        } else {
            if (current.size >= ObdSensor.MAX_GAUGES) return
            current + sensor
        }
        mutableState.update { it.copy(selectedGauges = next) }
        prefs.edit().putString("gauges", next.joinToString(",") { it.name }).apply()
    }

    fun refreshDevices() {
        try {
            mutableState.update { it.copy(devices = elm.pairedDevices(), error = null) }
        } catch (_: SecurityException) {
            mutableState.update { it.copy(error = "Concede permiso para acceder a Bluetooth.") }
        } catch (e: Exception) {
            mutableState.update { it.copy(error = e.message ?: "No se pudo consultar Bluetooth") }
        }
    }

    fun startDemo() {
        disconnect()
        mutableState.update {
            it.copy(status = ConnectionStatus.DEMO, deviceName = "Simulación", rpm = 0,
                coolant = 82, speed = 0, rpmHistory = emptyList(), sensorValues = emptyMap(),
                supportedPids = ObdSensor.values().map { s -> s.pid }.toSet(),
                dtcs = emptyList(), dtcsRead = false, error = null)
        }
        demoJob = viewModelScope.launch {
            var step = 0
            while (isActive) {
                val value = (1700 + 900 * sin(step / 3.3) + 420 * sin(step / 1.7)).toInt().coerceAtLeast(700)
                val coolant = 82 + (step / 24).coerceAtMost(9)
                val speed = (value / 72).coerceAtMost(140)
                val demo = mapOf(
                    ObdSensor.RPM to value.toDouble(),
                    ObdSensor.COOLANT to coolant.toDouble(),
                    ObdSensor.SPEED to speed.toDouble(),
                    ObdSensor.LOAD to (23.0 + 12.0 * sin(step / 4.0)),
                    ObdSensor.MAP to (45.0 + 15.0 * sin(step / 3.0)),
                    ObdSensor.IAT to 29.0,
                    ObdSensor.MAF to (13.0 + 6.5 * sin(step / 3.2)),
                    ObdSensor.THROTTLE to (18.0 + 12.0 * sin(step / 3.3)),
                    ObdSensor.TIMING to 12.5,
                    ObdSensor.STFT to (2.0 * sin(step / 4.2)),
                    ObdSensor.LTFT to -1.6,
                    ObdSensor.ECU_VOLTAGE to 14.1
                )
                mutableState.update {
                    it.copy(rpm = value, coolant = coolant, speed = speed, sensorValues = demo,
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
                val info = withContext(Dispatchers.IO) {
                    elm.connect(device.address)
                    elm.initialize()
                }
                if (generation.get() != token) return@launch
                mutableState.update { it.copy(status = ConnectionStatus.CONNECTED, supportedPids = info.supportedPids,
                    elmVersion = info.adapterVersion, obdProtocol = info.protocol, error = null) }
                startPolling(token)
            } catch (_: TimeoutCancellationException) {
                if (generation.get() == token) {
                    elm.close()
                    mutableState.update { it.copy(status = ConnectionStatus.DISCONNECTED,
                        error = "El ELM327 ha agotado el tiempo de espera durante la conexión.") }
                }
            } catch (_: CancellationException) {
                if (generation.get() == token) elm.close()
            } catch (e: Exception) {
                if (generation.get() == token) {
                    elm.close()
                    mutableState.update { it.copy(status = ConnectionStatus.DISCONNECTED,
                        error = "Error de conexión: ${e.message ?: "sin respuesta"}") }
                }
            }
        }
    }

    private fun startPolling(token: Int) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Una única petición a la vez. Sin enviar comandos simultáneos al clon ELM.
                // Los 3 sensores básicos se consultan también para mantener el panel original.
                var index = 0
                while (isActive && generation.get() == token) {
                    val snapshot = state.value
                    val needed = (ObdSensor.defaults.take(3) + snapshot.selectedGauges).distinct()
                        .filter { snapshot.supportedPids?.contains(it.pid) != false }
                    if (needed.isEmpty()) {
                        delay(700)
                        continue
                    }
                    val sensor = needed[index % needed.size]
                    index++
                    val response = elm.execute(sensor.command, 6_000)
                    val result = ObdParser.value(response, sensor)
                    if (generation.get() != token) break
                    mutableState.update { previous ->
                        // Ausente = sin valor; se evita dibujar como cero.
                        val nextValues = previous.sensorValues.toMutableMap()
                        if (result != null) nextValues[sensor] = result else nextValues.remove(sensor)
                        previous.copy(
                            sensorValues = nextValues,
                            rpm = if (sensor == ObdSensor.RPM) result?.toInt() else previous.rpm,
                            coolant = if (sensor == ObdSensor.COOLANT) result?.toInt() else previous.coolant,
                            speed = if (sensor == ObdSensor.SPEED) result?.toInt() else previous.speed,
                            rpmHistory = if (sensor == ObdSensor.RPM && result != null)
                                (previous.rpmHistory + result.toInt()).takeLast(48) else previous.rpmHistory
                        )
                    }
                    delay(100) // No saturar adaptadores económicos.
                }
            } catch (_: TimeoutCancellationException) {
                if (generation.get() == token) {
                    elm.close()
                    mutableState.update { it.copy(status = ConnectionStatus.DISCONNECTED,
                        error = "El ELM327 ha dejado de responder (timeout).") }
                }
            } catch (_: CancellationException) {
                // Desconexión o pausa para leer DTC.
            } catch (e: Exception) {
                if (generation.get() == token) {
                    elm.close()
                    mutableState.update { it.copy(status = ConnectionStatus.DISCONNECTED,
                        error = "Comunicación interrumpida: ${e.message ?: "error desconocido"}") }
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
                mutableState.update { it.copy(error = "Tiempo de espera agotado leyendo DTC.") }
            } catch (_: CancellationException) {
                // Se desconectó el adaptador.
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
        mutableState.update { it.copy(status = ConnectionStatus.DISCONNECTED, deviceName = "", rpm = null,
            coolant = null, speed = null, rpmHistory = emptyList(), sensorValues = emptyMap(),
            supportedPids = null, elmVersion = "", obdProtocol = "", dtcs = emptyList(),
            readingDtcs = false, dtcsRead = false, error = null) }
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
