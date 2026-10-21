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
    }

    @Test fun recognisesEcu() {
        assertTrue(ObdParser.hasEngineResponse("SEARCHING...\r41 00 BE 3F A8 13>"))
        assertFalse(ObdParser.hasEngineResponse("UNABLE TO CONNECT>"))
    }

    @Test fun decodesFaultCodes() {
        assertEquals(listOf("P0133", "P0420"), ObdParser.storedDtcs("43 01 33 04 20 00 00"))
        assertEquals(emptyList<String>(), ObdParser.storedDtcs("NO DATA"))
    }
}
