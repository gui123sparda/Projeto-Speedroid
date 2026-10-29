package com.example.tunerstudioandroid

import org.junit.Assert.*
import org.junit.Test

class SpeedEstimatorTest {
    @Test fun gpsAnchorsSpeedAndExpires() {
        val speed = SpeedEstimator()
        assertNull(speed.reading(1000))
        assertTrue(speed.gps(10.0, 1.0, 1000))
        assertEquals(36.0, speed.reading(1000)!!.kmh, 0.001)
        assertNull(speed.reading(7001))
    }
    @Test fun badFixDoesNotReplaceLastGoodFix() {
        val speed = SpeedEstimator()
        speed.gps(10.0, 0.5, 1000)
        assertFalse(speed.gps(90.0, 9.0, 2000))
        assertFalse(speed.gps(Double.NaN, 0.5, 2000))
        assertFalse(speed.gps(20.0, 0.5, 900))
        assertEquals(36.0, speed.reading(2000)!!.kmh, 0.001)
    }
    @Test fun signedAccelerationBridgesOnlyShortGpsGaps() {
        val speed = SpeedEstimator()
        speed.acceleration(2.0, 1000)
        assertNull(speed.reading(1000))
        speed.gps(10.0, 0.5, 1000)
        speed.acceleration(2.0, 1100)
        speed.acceleration(-2.0, 1200)
        assertEquals(36.0, speed.reading(1200)!!.kmh, 0.001)
        speed.acceleration(10.0, 5000)
        assertEquals(36.0, speed.reading(5000)!!.kmh, 0.001)
        assertEquals("GPS", speed.reading(5000)!!.source)
    }
    @Test fun stationaryFixDoesNotDriftFromVibration() {
        val speed = SpeedEstimator()
        speed.gps(0.0, 0.5, 1000)
        speed.acceleration(0.05, 1100)
        assertEquals(0.0, speed.reading(1100)!!.kmh, 0.001)
    }
}
