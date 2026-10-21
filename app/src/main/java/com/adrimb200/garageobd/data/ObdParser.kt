package com.adrimb200.garageobd.data

import java.util.Locale

/** Interpreta respuestas genéricas SAE J1979. No cubre protocolos del fabricante. */
object ObdParser {
    /** Admite `41 0C 1A F8` y `410C1AF8`, incluso con eco `010C`. */
    private fun pidBytes(response: String, pid: String, size: Int): List<Int>? {
        val clean = response.uppercase(Locale.ROOT)
        val pattern = Regex("41\\s*${pid.uppercase(Locale.ROOT)}((?:\\s*[0-9A-F]{2}){$size})")
        val found = pattern.find(clean) ?: return null
        val values = found.groupValues[1].replace(Regex("\\s"), "")
        if (values.length != size * 2) return null
        return (0 until size).map { i -> values.substring(i * 2, i * 2 + 2).toInt(16) }
    }

    fun rpm(response: String): Int? {
        val values = pidBytes(response, "0C", 2) ?: return null
        return ((values[0] * 256) + values[1]) / 4
    }

    fun coolant(response: String): Int? = pidBytes(response, "05", 1)?.firstOrNull()?.minus(40)

    fun speed(response: String): Int? = pidBytes(response, "0D", 1)?.firstOrNull()

    /** Para detectar que el adaptador ve la ECU: `41 00` con 4 bytes de capacidades. */
    fun hasEngineResponse(response: String): Boolean = pidBytes(response, "00", 4) != null

    /**
     * Respuestas MODE 03 sencillas: 43 01 33 00 00 -> P0133.
     * Puede haber encabezados o varias líneas, pero no se decodifica ISO-TP complejo.
     */
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
                    val code = "%c%d%01X%02X".format(Locale.ROOT, prefix, digit, first and 15, second)
                    all.add(code)
                }
            }
        }
        return all.distinct()
    }
}
