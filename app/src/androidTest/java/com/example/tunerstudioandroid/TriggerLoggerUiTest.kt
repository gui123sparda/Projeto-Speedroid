package com.example.tunerstudioandroid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.clonetuner.model.TriggerCapture
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TriggerLoggerUiTest {
    @get:Rule val activity=ActivityScenarioRule(MainActivity::class.java)
    private fun session(block:(StudioSession)->Unit) = activity.scenario.onActivity { block(ViewModelProvider(it)[StudioModel::class.java].session) }
    private fun awaitCapture() {
        val deadline=SystemClock.elapsedRealtime()+5000
        var ready=false
        while(!ready && SystemClock.elapsedRealtime()<deadline) {
            session { ready=it.transport.triggerCapture()?.mode()==it.selectedTriggerMode && it.transport.triggerLoggerActive() && !it.busy }
            if(!ready) SystemClock.sleep(50)
        }
        assertTrue("Simulator must deliver an actual logger block",ready)
    }
    private fun awaitStopped() {
        val deadline=SystemClock.elapsedRealtime()+3000
        var stopped=false
        while(!stopped && SystemClock.elapsedRealtime()<deadline) {
            session { stopped=!it.transport.triggerLoggerActive() && !it.busy }
            if(!stopped) SystemClock.sleep(50)
        }
        assertTrue("Logger must stop in ECU",stopped)
    }
    @Test fun disconnectedLoggerShowsScopeButCannotStartCapture() {
        onView(withText("Logs")).perform(click())
        onView(withText("Tooth / Composite Logger")).perform(scrollTo(),click())
        onView(withContentDescription("Osciloscópio de rotação e fase")).check(matches(isDisplayed()))
        onView(withText("Iniciar captura")).check(matches(not(isEnabled())))
        onView(withText(containsString("Conecte uma ECU"))).check(matches(isDisplayed()))
    }
    @Test fun liveCompositeFreezesInspectsAndStopsWhenClosed() {
        onView(withText("Conexão")).perform(scrollTo(),click())
        onView(withText("Iniciar demonstração")).perform(scrollTo(),click())
        val deadline=SystemClock.elapsedRealtime()+5000
        var connected=false
        while(!connected && SystemClock.elapsedRealtime()<deadline) {
            session { connected=it.transport.isConnected && !it.busy }
            if(!connected) SystemClock.sleep(50)
        }
        assertTrue(connected)
        onView(withText("Logs")).perform(click())
        onView(withText("Tooth / Composite Logger")).perform(scrollTo(),click())
        onView(withText("Iniciar captura")).perform(click())
        awaitCapture()
        session { assertEquals(TriggerCapture.Mode.COMPOSITE,it.transport.triggerCapture().mode()); assertTrue(it.transport.triggerCapture().simulated()) }
        onView(withText("Congelar / parar")).perform(click())
        awaitStopped()
        onView(withContentDescription("Osciloscópio de rotação e fase")).perform(click()).check { view,error ->
            if(error!=null) throw error
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val colorful=(0 until bitmap.width step 3).sumOf { x -> (0 until bitmap.height step 3).count { y ->
                val c=bitmap.getPixel(x,y)
                android.graphics.Color.green(c)>130 && android.graphics.Color.red(c)<180
            } }
            assertTrue("Digital traces should be drawn",colorful>100)
            bitmap.recycle()
            val screenshot=Bitmap.createBitmap(view.rootView.width,view.rootView.height,Bitmap.Config.ARGB_8888)
            view.rootView.draw(Canvas(screenshot))
            File(view.context.filesDir,"trigger-logger-review.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
            screenshot.recycle()
        }
        onView(withText(containsString("Evento"))).check(matches(isDisplayed()))
        onView(withText("Zoom +")).perform(click())
        onView(withText("Ajustar")).perform(click())
        activity.scenario.recreate()
        onView(withContentDescription("Osciloscópio de rotação e fase")).check(matches(isDisplayed()))
        onView(withContentDescription("Modo do logger")).perform(click())
        onData(equalTo("Intervalos dos dentes")).inRoot(isPlatformPopup()).perform(click())
        onView(withText("Iniciar captura")).perform(click())
        awaitCapture()
        session { assertEquals(TriggerCapture.Mode.TOOTH,it.transport.triggerCapture().mode()); assertTrue(it.transport.triggerCapture().longIntervals()>0) }
        onView(withText("Congelar / parar")).perform(click())
        awaitStopped()
        onView(withContentDescription("Osciloscópio de rotação e fase")).check { view,error ->
            if(error!=null) throw error
            val bitmap=Bitmap.createBitmap(view.rootView.width,view.rootView.height,Bitmap.Config.ARGB_8888)
            view.rootView.draw(Canvas(bitmap))
            File(view.context.filesDir,"trigger-tooth-review.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        onView(withText("Iniciar captura")).perform(click())
        awaitCapture()
        pressBack()
        awaitStopped()
    }
}
