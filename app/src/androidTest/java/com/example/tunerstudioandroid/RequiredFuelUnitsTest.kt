package com.example.tunerstudioandroid

import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.`is`
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class RequiredFuelUnitsTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)

    @Test fun equivalentDisplacementAndFlowUnitsProduceSameFuelTime() {
        onView(withText("Ajustes")).perform(click())
        onView(withText("Calculadora de combustível requerido")).perform(scrollTo(), click())
        onView(withContentDescription("Cilindrada do motor")).perform(scrollTo(), replaceText("2000"), closeSoftKeyboard())
        onView(withContentDescription("Vazão do injetor")).perform(scrollTo(), replaceText("315"), closeSoftKeyboard())
        onView(withText("Calcular")).perform(scrollTo(), click())
        onView(withText(String.format(Locale.getDefault(), "%.1f ms", 10.7))).check(matches(isDisplayed()))

        onView(withContentDescription("Unidade da cilindrada")).perform(scrollTo(), click())
        onData(`is`("L")).inRoot(isPlatformPopup()).perform(click())
        onView(withContentDescription("Cilindrada do motor")).perform(scrollTo(), replaceText("2,0"), closeSoftKeyboard())
        onView(withContentDescription("Unidade da vazão do injetor")).perform(scrollTo(), click())
        onData(`is`("lb/h")).inRoot(isPlatformPopup()).perform(click())
        onView(withContentDescription("Vazão do injetor")).perform(scrollTo(), replaceText("30"), closeSoftKeyboard())
        onView(withText("Calcular")).perform(scrollTo(), click())
        onView(withText(String.format(Locale.getDefault(), "%.1f ms", 10.7))).check(matches(isDisplayed()))

        onView(withContentDescription("Vazão do injetor")).perform(scrollTo(), replaceText("60"), closeSoftKeyboard())
        onView(withText("Calcular")).perform(scrollTo(), click())
        onView(withText(String.format(Locale.getDefault(), "%.1f ms", 5.3))).check(matches(isDisplayed()))
    }
}
