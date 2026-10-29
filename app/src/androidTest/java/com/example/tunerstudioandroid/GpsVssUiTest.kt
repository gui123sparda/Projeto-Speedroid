package com.example.tunerstudioandroid

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import org.hamcrest.Matchers.equalTo
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpsVssUiTest {
    @get:Rule val activity=ActivityScenarioRule(MainActivity::class.java)
    @Test fun gpsIsAnAdditionalSourceAndPreservesAdjacentEcuBits() {
        activity.scenario.onActivity {
            val session=ViewModelProvider(it)[StudioModel::class.java].session
            session.selectGpsVss(false)
            session.ecu.set("vssPin",12.0)
        }
        onView(withText("Ajustes")).perform(click())
        onView(withText("VSS Mode e GPS")).perform(scrollTo(),click())
        onData(equalTo("GPS do celular → ECU")).perform(click())
        activity.scenario.onActivity {
            val session=ViewModelProvider(it)[StudioModel::class.java].session
            assertTrue(session.gpsVssEnabled)
            assertEquals(1.0,session.ecu.get("vssMode"),0.001)
            assertEquals(12.0,session.ecu.get("vssPin"),0.001)
            session.selectGpsVss(false)
        }
    }
}
