package com.example.tunerstudioandroid

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleDashboardFlowTest {
    @get:Rule val activity=ActivityScenarioRule(MainActivity::class.java)
    @Test fun allThreeCustomDashboardsCanBeSelectedAndRestoredInFullscreen() {
        for(label in listOf("Ford Escort Zetec 1.8","Ford Mondeo 2.0 (1997)","Rally")) {
            onView(withContentDescription("Selecionar painel")).perform(scrollTo(),click())
            onData(equalTo(label)).perform(click())
            onView(withContentDescription("Painel personalizado $label")).check(matches(isDisplayed()))
        }
        activity.scenario.recreate()
        onView(withContentDescription("Painel personalizado Rally")).check(matches(isDisplayed()))
        onView(withText("Tela cheia")).perform(scrollTo(),click())
        onView(withContentDescription("Painel personalizado Rally")).inRoot(isDialog()).check(matches(isDisplayed()))
        pressBack()
        onView(withContentDescription("Selecionar painel")).perform(scrollTo(),click())
        onData(equalTo("Sweep")).perform(click())
    }
}
