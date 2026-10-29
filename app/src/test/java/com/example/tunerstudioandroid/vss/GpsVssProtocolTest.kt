package com.example.tunerstudioandroid.vss

import org.junit.Assert.*
import org.junit.Test

class GpsVssProtocolTest {
    @Test fun encodesTenthsOfKmhAndNeverRepeatsExpiredFix() {
        assertArrayEquals(byteArrayOf(103,1,1,3,0xD2.toByte(),4), GpsVssProtocol.packet(true, GpsFix(123.4,1000),1200))
        assertArrayEquals(byteArrayOf(103,1,1,1,0,0), GpsVssProtocol.packet(true, GpsFix(123.4,1000),2501))
        assertArrayEquals(byteArrayOf(103,1,1,0,0,0), GpsVssProtocol.packet(false, GpsFix(123.4,1000),1200))
    }
    @Test fun capabilityIsExactAndInvalidGpsIsExplicitlyUnavailable() {
        assertArrayEquals(byteArrayOf(103,1,0),GpsVssProtocol.query)
        assertTrue(GpsVssProtocol.supported(byteArrayOf(71,80,83,86,83,83,1)))
        assertFalse(GpsVssProtocol.supported("speeduino 202501".toByteArray()))
        assertArrayEquals(byteArrayOf(103,1,1,1,0,0),GpsVssProtocol.packet(true,GpsFix(Double.NaN,1000),1000))
        assertArrayEquals(byteArrayOf(103,1,1,1,0,0),GpsVssProtocol.packet(true,GpsFix(40.0,2000),1000))
        assertArrayEquals(byteArrayOf(103,1,1,3,0,0),GpsVssProtocol.packet(true,GpsFix(0.0,1000),1000))
    }
}
