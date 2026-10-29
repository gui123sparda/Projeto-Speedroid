package com.example.tunerstudioandroid

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.matcher.RootMatchers.isDialog
import android.widget.EditText

@RunWith(AndroidJUnit4::class)
class MapAndSettingsTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun portraitMapFitsAndEditsOriginalOffset() = checkMap(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    @Test fun landscapeMapFitsAndEditsOriginalOffset() = checkMap(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

    private fun checkMap(orientation: Int) {
        activity.scenario.onActivity { it.requestedOrientation = orientation }
        val expected = if (orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) 1 else 2
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        var ready = false
        while (!ready && android.os.SystemClock.elapsedRealtime() < deadline) {
            activity.scenario.onActivity { ready = it.resources.configuration.orientation == expected }
            if (!ready) android.os.SystemClock.sleep(50)
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue("Requested orientation must be applied", ready)
        activity.scenario.recreate()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500,5000)
        var tableTitle = ""
        var firstCell = ""
        var otherCell = 0.0
        var firstIndex = 0
        var otherIndex = 1
        activity.scenario.onActivity { a ->
            val s = ViewModelProvider(a)[StudioModel::class.java].session
            val t = s.ini.tables.values.first { it.zBins == "veTable" }
            tableTitle = t.title
            val c = s.ini.constant(t.zBins)!!
            val reversed = orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            for (i in 0 until c.cols()) s.ecu.set(t.xBins, i, 500.0 + 500*(if(reversed) c.cols()-1-i else i))
            for (i in 0 until c.rows()) s.ecu.set(t.yBins, i, 20.0 + 10*(if(reversed) c.rows()-1-i else i))
            firstIndex = if(reversed) 255 else 0
            otherIndex = if(reversed) 254 else 1
            firstCell = "veTable, ${t.xLabel} 500.0, ${t.yLabel} 20.0"
            otherCell = s.ecu.get(c, otherIndex)
        }
        onView(withText("Mapas")).perform(click())
        onView(withText(tableTitle)).perform(scrollTo(), click())
        onView(withText("Fechar")).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            val cells = descendants(view.rootView).filter { it.contentDescription?.startsWith("veTable, ") == true }
            assertEquals(256, cells.size)
            cells.forEach { cell ->
                val visible = Rect()
                assertTrue("Every cell must be on screen", cell.getGlobalVisibleRect(visible))
                assertEquals("Cell must not be horizontally clipped", cell.width, visible.width())
                assertEquals("Cell must not be vertically clipped", cell.height, visible.height())
            }
            val first = cells.first { it.contentDescription.toString() == firstCell }
            val positions = cells.map { IntArray(2).also(it::getLocationOnScreen) }
            val origin = IntArray(2).also(first::getLocationOnScreen)
            assertEquals(positions.minOf { it[0] }, origin[0])
            assertEquals(positions.maxOf { it[1] }, origin[1])
            val bitmap = android.graphics.Bitmap.createBitmap(view.rootView.width,view.rootView.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.rootView.draw(android.graphics.Canvas(bitmap))
            java.io.File(view.context.filesDir,"map-review-$orientation.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            bitmap.recycle()
        }
        onView(withContentDescription(firstCell)).perform(click())
        onView(isAssignableFrom(EditText::class.java)).perform(replaceText("37"), closeSoftKeyboard())
        onView(withText("Aplicar")).perform(click())
        activity.scenario.onActivity { a ->
            val ecu = ViewModelProvider(a)[StudioModel::class.java].session.ecu
            assertEquals(37.0, ecu.get("veTable", firstIndex), 0.0)
            assertEquals(otherCell, ecu.get("veTable", otherIndex), 0.0)
        }
    }

    @Test fun settingsDoNotListInternalPanelsAsSeparateFeatures() {
        onView(withText("Ajustes")).perform(click())
        activity.scenario.onActivity { a ->
            val buttons = descendants(a.window.decorView).filterIsInstance<Button>()
            assertFalse("Internal panels must stay within their parent dialog", buttons.any { it.text.toString() == "engine_constants_west" })
        }
    }

    @Test fun requiredFuelCommandOpensTheLocalCalculator() {
        onView(withText("Ajustes")).perform(click())
        onView(withHint("Buscar parâmetro ou diálogo")).perform(scrollTo(), replaceText("Fuel and engine setup"), closeSoftKeyboard())
        onView(org.hamcrest.Matchers.allOf(withText("Fuel and engine setup"),isAssignableFrom(Button::class.java))).perform(scrollTo(), click())
        onView(withText("Calculate Required Fuel...")).perform(scrollTo(), click())
        onView(withContentDescription("Cilindrada do motor")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()))
    }

    @Test fun nestedCurvePanelsOpenEditors() {
        activity.scenario.onActivity { a ->
            val ecu = ViewModelProvider(a)[StudioModel::class.java].session.ecu
            ecu.set("flexEnabled", 1.0)
            for (i in 0..5) ecu.set("flexFuelBins", i, i*20.0)
        }
        onView(withText("Ajustes")).perform(click())
        onView(withHint("Buscar parâmetro ou diálogo")).perform(scrollTo(), replaceText("flexFuel"), closeSoftKeyboard())
        onView(withText("Fuel Sensor Settings")).perform(scrollTo(), click())
        onView(withText("Flex Fuel Adjustments")).perform(scrollTo(), click())
        onView(withContentDescription("flexFuelBins [0]")).check { view, error ->
            if (error != null) throw error
            val cells = descendants(view.rootView).filter { it.contentDescription?.startsWith("flexFuelBins [") == true || it.contentDescription?.startsWith("flexFuelAdj [") == true }
            assertEquals(12, cells.size)
            cells.forEach { cell ->
                val rect = Rect()
                assertTrue(cell.getGlobalVisibleRect(rect))
                assertEquals(cell.height, rect.height())
                assertEquals(cell.width, rect.width())
            }
            val ys = cells.filter { it.contentDescription?.startsWith("flexFuelBins [") == true }
                .map { IntArray(2).also(it::getLocationOnScreen)[1] }
            assertEquals(ys.maxOrNull(), IntArray(2).also(view::getLocationOnScreen)[1])
        }
    }
}
