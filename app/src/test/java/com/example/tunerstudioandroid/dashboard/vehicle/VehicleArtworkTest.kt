package com.example.tunerstudioandroid.dashboard.vehicle

import org.junit.Assert.*
import org.junit.Test

class VehicleArtworkTest {
    @Test fun escortAndMondeoKeepDistinctFacesAndAllFourMovingNeedles() {
        val data=mutableMapOf("rpm" to 3500.0,"vss" to 110.0,"coolantRaw" to 130.0,"fuelLevel" to 50.0)
        for(model in listOf(VehicleModel.ESCORT_ZETEC,VehicleModel.MONDEO_1997)) {
            val artwork=VehicleArtwork(VehicleTelemetry({data[it]},{""}),model)
            val scene=artwork.draw()
            val needles=scene.filter { it.kind==VectorPainter.Kind.FILL_POLYGON && it.style.fill==VectorPainter.Color.web("#ee3228") }
            assertEquals(4,needles.size)
            val faces=scene.filter { it.kind==VectorPainter.Kind.FILL_OVAL && it.points[2]>150 && it.style.fill==VectorPainter.Color.web(if(model==VehicleModel.ESCORT_ZETEC)"#e3e5db" else "#121617") }
            assertEquals(if(model==VehicleModel.ESCORT_ZETEC)3 else 4,faces.size)
            val central=needles.first { kotlin.math.abs(it.style.transform.tx-(if(model==VehicleModel.ESCORT_ZETEC)600.0 else 790.0))<0.1 }
            val expected=if(model==VehicleModel.ESCORT_ZETEC) VehiclePoint(539.565040674,181.956313907) else VehiclePoint(760.983853776,193.371349481)
            val tip=central.style.transform.point(central.points[6],central.points[7])
            // Literal tips are checked against the original cluster geometry, not the renderer.
            assertEquals(expected.x,tip.x,0.02)
            assertEquals(expected.y,tip.y,0.02)
            data["rpm"]=1000.0
            val changed=artwork.draw().filter { it.kind==VectorPainter.Kind.FILL_POLYGON && it.style.fill==VectorPainter.Color.web("#ee3228") }
            val rpmX=if(model==VehicleModel.ESCORT_ZETEC)250.0 else 410.0
            assertNotEquals(needles.first { it.style.transform.tx==rpmX }.style.transform,changed.first { it.style.transform.tx==rpmX }.style.transform)
            data["rpm"]=3500.0
        }
    }
    @Test fun rallyShowsGearAndEightCellsAndOfflineNeverBecomesFakeZero() {
        val data=mutableMapOf("rpm" to 3500.0,"vss" to 110.0,"gear" to 3.0,"coolantRaw" to 130.0,"batteryVoltage" to 13.8)
        val artwork=VehicleArtwork(VehicleTelemetry({data[it]},{""}),VehicleModel.RALLY)
        val live=artwork.draw().filter { it.kind==VectorPainter.Kind.TEXT }.map { it.text }
        assertTrue(live.containsAll(listOf("3500","110","3","EXHAUST","OIL TEMP","COOLANT","GEARBOX","RAIL PRESS","FUEL PRESS","OIL PRESS","BATTERY")))
        assertTrue(live.contains("--"))
        data.clear()
        val offline=artwork.draw().filter { it.kind==VectorPainter.Kind.TEXT }.map { it.text }
        assertTrue(offline.contains("SEM DADOS DA ECU"))
        assertFalse(offline.contains("3500"))
    }
}
