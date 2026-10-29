package com.example.tunerstudioandroid

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import android.widget.EditText
import org.hamcrest.Matchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioFlowTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)
    @Test fun offlineAndDemoRemainExplicit() {
        onView(withText("OFFLINE")).check(matches(isDisplayed()))
        onView(withText("Conexão")).perform(click())
        onView(withText("Iniciar demonstração")).perform(scrollTo(), click())
        Thread.sleep(1800)
        onView(withText("DEMONSTRAÇÃO")).check(matches(isDisplayed()))
        onView(withText("Painel")).perform(click())
        onView(withContentDescription(startsWith("Painel .dash"))).check(matches(isDisplayed()))
        onView(withText("Mapas")).perform(click())
        onView(withText("Mapas e curvas")).check(matches(isDisplayed()))
        onView(withText("Ajustes")).perform(click())
        onView(withHint("Buscar parâmetro ou diálogo")).perform(scrollTo(), replaceText("Fuel and engine setup"), closeSoftKeyboard())
        onView(allOf(withText("Fuel and engine setup"), isAssignableFrom(android.widget.Button::class.java))).perform(scrollTo(),click())
        onView(withText(startsWith("Required Fuel (ms):"))).perform(scrollTo(),click())
        onView(isAssignableFrom(EditText::class.java)).perform(replaceText("7.5"),closeSoftKeyboard())
        onView(withText("Aplicar")).perform(click())
        onView(withText(allOf(startsWith("Required Fuel (ms):"),containsString("7.5")))).check(matches(isDisplayed()))
    }
}
