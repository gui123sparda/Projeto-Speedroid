package com.example.tunerstudioandroid.dashboard

import com.example.tunerstudioandroid.vss.GpsFix
import org.clonetuner.dashboard.DashboardDefinition.Component
import org.junit.Assert.*
import org.junit.Test

class DashTelemetryTest {
    @Test fun legacyChannelsAndFahrenheitUseRealInputs() {
        val channels=mapOf("coolantRaw" to 130.0,"crank" to 1.0,"tps" to 85.0,"rpm" to 200.0)
        val source=DashTelemetry({channels[it]},{mapOf("tpsflood" to 80.0,"crankRPM" to 400.0)[it]},{GpsFix(100.0,1000)},{1200},{true},{false},{100.0})
        assertEquals(194.0,source.read(Component("c","Basic Readout",mapOf("OutputChannel" to "coolant","Units" to "°F"))).value!!,0.001)
        assertEquals(1.0,source.read("CrankingOC")!!,0.001)
        assertEquals(62.137119,source.read("gpsSpeedMPH")!!,0.001)
        assertEquals(1.0,source.read("LPtpsGTtpsfloodRPANDANDLPrpmLTcrankRPMRP_OC")!!,0.001)
        assertNull(source.read("sensorThatDoesNotExist"))
    }
    @Test fun staleGpsNeverFallsBackToEcuSpeed() {
        val source=DashTelemetry({if(it=="vss")80.0 else null},{null},{GpsFix(100.0,1000)},{3000},{false},{false},{null})
        assertNull(source.read("gpsSpeedMPH"))
        assertEquals(0.0,source.read("gpsActive")!!,0.001)
    }
}
