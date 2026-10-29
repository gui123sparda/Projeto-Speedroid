package com.example.tunerstudioandroid

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tunerstudioandroid.vss.*
import org.clonetuner.comm.SimLink
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GpsVssSessionTest {
    @Test fun gpsActuallyReachesControllerAndMissingFixSendsInvalidState() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val folder=File(app.cacheDir,"gps-session-${System.nanoTime()}").apply { mkdirs() }
        val context=object:ContextWrapper(app) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir():File=folder
        }
        lateinit var session:StudioSession
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            session=StudioSession(context)
            session.selectGpsVss(true)
            session.gpsFix=GpsFix(87.5,SystemClock.elapsedRealtime())
            session.connect(SimLink(session.ini))
        }
        try {
            val deadline=SystemClock.elapsedRealtime()+1400
            while(SystemClock.elapsedRealtime()<deadline && session.ecu.channels()["vss"]!=88.0)SystemClock.sleep(30)
            assertEquals(88.0,session.ecu.channels()["vss"]!!,0.01)
            assertEquals(GpsVssBridge.Status.ACTIVE,session.gpsVssStatus)
            session.gpsFix=null
            val invalidDeadline=SystemClock.elapsedRealtime()+800
            while(SystemClock.elapsedRealtime()<invalidDeadline && session.ecu.channels()["vss"]!=0.0)SystemClock.sleep(30)
            assertEquals(0.0,session.ecu.channels()["vss"]!!,0.01)
            assertEquals(GpsVssBridge.Status.NO_FIX,session.gpsVssStatus)
        } finally {
            instrumentation.runOnMainSync { session.selectGpsVss(false);session.close(false) }
        }
    }
}
