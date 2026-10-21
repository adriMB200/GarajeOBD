package com.adrimb200.garageobd.data

import java.util.Locale

/** PIDs genéricos SAE J1979, respuestas compactas y separadas por espacios. */
object ObdParser {
    private fun pidBytes(response: String, pid: Int, length: Int): List<Int>? {
        val clean = response.uppercase(Locale.ROOT)
        val hexPid = "%02X".format(Locale.ROOT, pid)
        val match = Regex("41\\s*$hexPid((?:\\s*[0-9A-F]{2}){$length})").find(clean) ?: return null
        val hex = match.groupValues[1].replace(Regex("\\s"), "")
        if (hex.length != length * 2) return null
        return (0 until length).map { i -> hex.substring(i * 2, i * 2 + 2).toInt(16) }
    }

    fun rpm(response: String): Int? = value(response, ObdSensor.RPM)?.toInt()
    fun coolant(response: String): Int? = value(response, ObdSensor.COOLANT)?.toInt()
    fun speed(response: String): Int? = value(response, ObdSensor.SPEED)?.toInt()
    fun hasEngineResponse(response: String): Boolean = pidBytes(response, 0x00, 4) != null

    fun value(response: String, sensor: ObdSensor): Double? {
        val bytes = pidBytes(response, sensor.pid, if (sensor == ObdSensor.MAF || sensor == ObdSensor.ECU_VOLTAGE || sensor == ObdSensor.RPM) 2 else 1)
            ?: return null
        val a = bytes[0]
        val b = bytes.getOrElse(1) { 0 }
        return when (sensor) {
            ObdSensor.RPM -> (256.0 * a + b) / 4.0
            ObdSensor.COOLANT, ObdSensor.IAT -> (a - 40).toDouble()
            ObdSensor.SPEED, ObdSensor.MAP -> a.toDouble()
            ObdSensor.LOAD, ObdSensor.THROTTLE -> a * 100.0 / 255.0
            ObdSensor.MAF -> (256.0 * a + b) / 100.0
            ObdSensor.TIMING -> a / 2.0 - 64.0
            ObdSensor.STFT, ObdSensor.LTFT -> 100.0 * a / 128.0 - 100.0
            ObdSensor.ECU_VOLTAGE -> (256.0 * a + b) / 1000.0
        }
    }

    /** Máscara de capacidades 0100/0120/0140. Bit más alto -> primer PID de la página. */
    fun supportedPidPage(response: String, base: Int): Set<Int>? {
        require(base == 0 || base == 0x20 || base == 0x40)
        val bytes = pidBytes(response, base, 4) ?: return null
        val mask = bytes.fold(0L) { acc, byte -> (acc shl 8) or byte.toLong() }
        return (1..32).filter { index ->
            mask and (1L shl (32 - index)) != 0L
        }.map { base + it }.toSet()
    }

    /** Respuestas de DTC genéricos simples; sin decodificación completa ISO-TP. */
    fun storedDtcs(response: String): List<String> {
        val all = mutableListOf<String>()
        response.uppercase(Locale.ROOT).replace("\r", "\n").lines().forEach { line ->
            val stripped = line.trim()
            if (stripped.contains("NO DATA") || stripped == "03") return@forEach
            val payload = Regex("(?<![0-9A-F])43(?:\\s+|(?=[0-9A-F]{2}))([0-9A-F ]+)")
                .find(stripped)?.groupValues?.getOrNull(1) ?: return@forEach
            val hex = payload.replace(" ", "").takeWhile { it in "0123456789ABCDEF" }
            if (hex.length < 4) return@forEach
            hex.chunked(4).forEach { block ->
                if (block.length == 4 && block != "0000") {
                    val first = block.substring(0, 2).toInt(16)
                    val second = block.substring(2, 4).toInt(16)
                    val prefix = "PCBU"[(first ushr 6) and 3]
                    val digit = (first ushr 4) and 3
                    all.add("%c%d%01X%02X".format(Locale.ROOT, prefix, digit, first and 15, second))
                }
            }
        }
        return all.distinct()
    }
}
