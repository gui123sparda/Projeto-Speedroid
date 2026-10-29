package com.example.tunerstudioandroid.dashboard.vehicle

import org.junit.Assert.*
import org.junit.Test

class VehicleTelemetryTest {
    @Test fun speedAndSensorsUseCanonicalUnitsWithoutInventingMissingInputs() {
        val data=mapOf("vss" to 62.137119,"coolantRaw" to 130.0,"oilPressure" to 30.0,"fuelLevel" to 120.0)
        val telemetry=VehicleTelemetry({data[it]},{if(it=="vss")"mph" else if(it=="oilPressure")"psi" else ""})
        assertEquals(100.0,telemetry.speedKmh().value,0.001)
        assertEquals(90.0,telemetry.coolantC().value,0.001)
        assertEquals(206.842719,telemetry.pressureKpa("oilPressure").value,0.001)
        assertEquals(100.0,telemetry.fuelPercent().value,0.001)
        assertFalse(telemetry.read("odometer").available())
        assertEquals("--",telemetry.read("gearboxTemp").text(1))
    }
    @Test fun zeroSpeedIsValidButUnavailableGpsStaysUnavailable() {
        var speed:Double?=0.0
        val telemetry=VehicleTelemetry({if(it=="vss")speed else null},{"km/h"})
        assertEquals("0",telemetry.speedKmh().text(0))
        speed=null
        assertFalse(telemetry.speedKmh().available())
        assertEquals("--",telemetry.speedKmh().text(0))
    }
}
