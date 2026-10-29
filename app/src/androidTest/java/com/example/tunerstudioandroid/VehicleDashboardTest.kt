package com.example.tunerstudioandroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tunerstudioandroid.dashboard.vehicle.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VehicleDashboardTest {
    @Test fun customArtworkRendersAtTwoTimesScaleWithRealNeedlesAndOfflineStates() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val folder=File(app.filesDir,"vehicle-review").apply { mkdirs() }
        val data=mutableMapOf<String,Double>()
        val telemetry=VehicleTelemetry({data[it]},{when(it){"vss"->"km/h";"batteryVoltage"->"V";else->""}})
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for(model in VehicleModel.entries) for(live in listOf(false,true)) {
                data.clear()
                if(live)data.putAll(mapOf("rpm" to 3500.0,"vss" to 110.0,"coolantRaw" to 130.0,"fuelLevel" to 50.0,"gear" to 3.0,"batteryVoltage" to 13.8))
                val view=VehiclePanelView(app,model,telemetry)
                view.layout(0,0,2400,1240)
                val bitmap=Bitmap.createBitmap(2400,1240,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                if(model==VehicleModel.ESCORT_ZETEC) {
                    val pixel=bitmap.getPixel(520,800)
                    assertTrue("Escort must preserve its light dial faces",android.graphics.Color.red(pixel)>180)
                }
                if(model==VehicleModel.MONDEO_1997) {
                    val pixel=bitmap.getPixel(820,800)
                    assertTrue("Mondeo must retain its dark dial faces",android.graphics.Color.red(pixel)<60)
                }
                assertTrue(view.diagnostics.isEmpty())
                File(folder,"${model.id}-${if(live) "live" else "offline"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                if(live)assertTrue(view.extrema().any { it.startsWith("Velocidade:") })
            }
        }
    }
}
