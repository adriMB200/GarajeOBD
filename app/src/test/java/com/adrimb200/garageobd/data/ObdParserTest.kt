package com.adrimb200.garageobd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdParserTest {
    @Test fun parsesRpmSpaced() {
        assertEquals(1726, ObdParser.rpm("010C\r41 0C 1A F8\r>"))
    }
    @Test fun parsesRpmCompact() {
        assertEquals(1726, ObdParser.rpm("410C1AF8"))
    }
    @Test fun parsesTemperature() {
        assertEquals(85, ObdParser.coolant("41 05 7D"))
    }
    @Test fun parsesSpeed() {
        assertEquals(40, ObdParser.speed("410D28"))
    }
    @Test fun correctlyHandlesNoData() {
        assertNull(ObdParser.rpm("NO DATA >"))
        assertNull(ObdParser.coolant("410D28"))
        assertNull(ObdParser.value("?", ObdSensor.LOAD))
    }
    @Test fun recognisesEcu() {
        assertTrue(ObdParser.hasEngineResponse("SEARCHING...\r41 00 BE 3F A8 13>"))
        assertFalse(ObdParser.hasEngineResponse("UNABLE TO CONNECT>"))
    }
    @Test fun readsSupportedPidMask() {
        // Solo PID 01 y 0C admitidos; 0C es el bit 12 de la máscara.
        val pids = ObdParser.supportedPidPage("41 00 80 10 00 00", 0)!!
        assertEquals(setOf(0x01, 0x0C), pids)
        assertNull(ObdParser.supportedPidPage("NO DATA", 0))
        assertEquals(setOf(0x21), ObdParser.supportedPidPage("41 20 80 00 00 00", 0x20))
    }
    @Test fun readsAdditionalSensors() {
        assertEquals(100.0, ObdParser.value("4104FF", ObdSensor.LOAD)!!, 0.001)
        assertEquals(80.0, ObdParser.value("410B50", ObdSensor.MAP)!!, 0.001)
        assertEquals(25.0, ObdParser.value("410F41", ObdSensor.IAT)!!, 0.001)
        assertEquals(12.34, ObdParser.value("411004D2", ObdSensor.MAF)!!, 0.001)
        assertEquals(14.2, ObdParser.value("41 42 37 78", ObdSensor.ECU_VOLTAGE)!!, 0.001)
        assertEquals(0.0, ObdParser.value("410680", ObdSensor.STFT)!!, 0.001)
        assertEquals(0.0, ObdParser.value("410E80", ObdSensor.TIMING)!!, 0.001)
    }
    @Test fun decodesFaultCodes() {
        assertEquals(listOf("P0133", "P0420"), ObdParser.storedDtcs("43 01 33 04 20 00 00"))
        assertEquals(emptyList<String>(), ObdParser.storedDtcs("NO DATA"))
    }
}
