package com.example.tunerstudioandroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tunerstudioandroid.dashboard.*
import com.example.tunerstudioandroid.vss.GpsFix
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DashRenderingTest {
    @Test fun originalDashboardsDecodeAndRenderTheirEmbeddedAssets() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val channels=mapOf("rpm" to 3500.0,"map" to 100.0,"tps" to 25.0,"throttle" to 25.0,"coolantRaw" to 130.0,"iatRaw" to 65.0,"afr" to 14.7,"advance" to 20.0,"pulseWidth" to 4.0,"egoCorrection" to 100.0,"dutyCycle" to 14.0,"running" to 1.0,"sync" to 1.0)
        val folder=File(app.filesDir,"dash-review").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val store=DashboardStore(app)
            for(entry in store.entries.filter { it.vehicle==null }) for(live in listOf(false,true)) {
                val signals=DashTelemetry({if(live)channels[it] ?: if(it in listOf("crank","warmup","error","nitrousOn","ase","tpsaccaen","tpsaccden","mapaccden","hardLimitOn","launchSoft","resetLockOn","DFCOOn","boostCutOut"))0.0 else null else null},{0.0},{if(live)GpsFix(100.0,1000) else null},{1000},{live},{false},{if(live)100.0 else null})
                val view=DashView(app,entry.definition,signals,{_,fallback->fallback},{})
                view.layout(0,0,1200,620)
                val bitmap=Bitmap.createBitmap(1200,620,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                assertTrue("${entry.id}: ${view.diagnostics}",view.diagnostics.isEmpty())
                val colored=(0 until bitmap.width step 8).sumOf { x -> (0 until bitmap.height step 8).count { y -> bitmap.getPixel(x,y) != android.graphics.Color.BLACK } }
                assertTrue("Dashboard was blank: ${entry.id}",colored>200)
                if(live && entry.id=="Speeduino_LED.dash") {
                    val rpmDigits=(500 until 700).sumOf { x -> (120 until 165).count { y ->
                        val color=bitmap.getPixel(x,y)
                        android.graphics.Color.green(color)>120 && android.graphics.Color.red(color)<100
                    } }
                    assertTrue("Digital RPM value must remain visible on the 180-degree analog gauge",rpmDigits>100)
                }
                File(folder,"${entry.id.removeSuffix(".dash")}-${if(live) "live" else "offline"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
        }
    }
}
