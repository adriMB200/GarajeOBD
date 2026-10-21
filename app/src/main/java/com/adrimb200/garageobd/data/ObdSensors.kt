package com.adrimb200.garageobd.data

import java.util.Locale

/** Sensores OBD-II genéricos (servicio 01); no son PIDs propietarios del fabricante. */
enum class ObdSensor(
    val pid: Int,
    val label: String,
    val unit: String,
    val low: Float,
    val high: Float,
    val decimals: Int = 0
) {
    RPM(0x0C, "Revoluciones", "rpm", 0f, 7000f),
    COOLANT(0x05, "Refrigerante", "°C", -40f, 130f),
    SPEED(0x0D, "Velocidad ECU", "km/h", 0f, 240f),
    LOAD(0x04, "Carga motor", "%", 0f, 100f),
    MAP(0x0B, "Presión colector", "kPa abs", 0f, 255f),
    IAT(0x0F, "Temp. admisión", "°C", -40f, 130f),
    MAF(0x10, "Caudal de aire", "g/s", 0f, 250f, 2),
    THROTTLE(0x11, "Acelerador", "%", 0f, 100f),
    TIMING(0x0E, "Avance encendido", "°", -64f, 64f, 1),
    STFT(0x06, "Corrección corta B1", "%", -100f, 100f, 1),
    LTFT(0x07, "Corrección larga B1", "%", -100f, 100f, 1),
    ECU_VOLTAGE(0x42, "Tensión ECU", "V", 0f, 20f, 2);

    val command: String get() = "01%02X".format(Locale.ROOT, pid)

    fun format(value: Double?): String = when {
        value == null -> "—"
        decimals == 0 -> "%.0f".format(Locale.getDefault(), value)
        else -> "%.${decimals}f".format(Locale.getDefault(), value)
    }

    companion object {
        val defaults = listOf(RPM, COOLANT, SPEED, LOAD)
        const val MAX_GAUGES = 6
    }
}
