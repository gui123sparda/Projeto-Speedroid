package com.example.tunerstudioandroid

import org.junit.Assert.*
import org.junit.Test

class TelemetryTest {
    @Test fun csvUsesEcuChannelNamesAndCelsiusInsteadOfDisplayLabels() {
        val channels=mapOf("rpm" to 1000.0,"map" to 35.0,"tps" to 2.0,"coolantRaw" to 126.0,
            "afr" to 14.8,"afrTarget" to 14.7,"egoCorrection" to 103.0)
        assertEquals(listOf(1.0,1000.0,35.0,2.0,86.0,14.8,14.7,103.0,null,0.2,36.2),
            Telemetry.logValues(1.0,{ channels[it] },null,0.2,36.2))
    }
    @Test fun predictionKeepsGpsReferenceSeparate() {
        val estimator=SpeedEstimator()
        estimator.gps(10.0,0.5,1000)
        estimator.acceleration(2.0,1100)
        assertEquals(36.0,estimator.gpsKmh(1100)!!,0.001)
        assertEquals(36.72,estimator.reading(1100)!!.kmh,0.001)
        assertNull(estimator.gpsKmh(7001))
    }
}
