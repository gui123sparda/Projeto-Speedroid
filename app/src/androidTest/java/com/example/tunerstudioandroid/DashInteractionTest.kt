package com.example.tunerstudioandroid

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.lifecycle.ViewModelProvider
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashInteractionTest {
    @get:Rule val activity=ActivityScenarioRule(MainActivity::class.java)
    @Test fun embeddedLoggingActionWorksInFullscreenAndKeepsLayoutSelection() {
        onView(withContentDescription("Selecionar painel")).perform(click())
        onData(equalTo("LED")).perform(click())
        onView(withContentDescription("Painel .dash Speeduino_LED.dash")).check(matches(isDisplayed()))
        onView(withText("Tela cheia")).perform(scrollTo(),click())
        dismissSystemFullscreenEducation()
        val loggerTap=GeneralClickAction(Tap.SINGLE, { view ->
            val location=IntArray(2);view.getLocationOnScreen(location)
            val scale=minOf(view.width/1200f,view.height/620f)
            // Center of the original LED logging image, in its 1200 x 620 reference layout.
            floatArrayOf(location[0]+(view.width-1200*scale)/2+1128*scale,location[1]+(view.height-620*scale)/2+66*scale)
        },Press.FINGER)
        onView(withContentDescription("Painel .dash Speeduino_LED.dash")).inRoot(isDialog()).perform(loggerTap)
        Thread.sleep(450)
        activity.scenario.onActivity { assertTrue(ViewModelProvider(it)[StudioModel::class.java].session.recording) }
        onView(withContentDescription("Painel .dash Speeduino_LED.dash")).inRoot(isDialog()).perform(loggerTap)
        Thread.sleep(450)
        activity.scenario.onActivity { assertFalse(ViewModelProvider(it)[StudioModel::class.java].session.recording) }
        androidx.test.espresso.Espresso.pressBack()
        activity.scenario.recreate()
        onView(withContentDescription("Painel .dash Speeduino_LED.dash")).check(matches(isDisplayed()))
        // Restore the default for other UI flows.
        onView(withContentDescription("Selecionar painel")).perform(click())
        onData(equalTo("Sweep")).perform(click())
    }
    private fun dismissSystemFullscreenEducation() {
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline=android.os.SystemClock.elapsedRealtime()+3000
        fun dismiss(node:android.view.accessibility.AccessibilityNodeInfo?):Boolean {
            if(node==null)return false
            if(node.text?.toString() in listOf("Got it","Entendi"))
                return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            for(index in 0 until node.childCount) if(dismiss(node.getChild(index)))return true
            return false
        }
        while(android.os.SystemClock.elapsedRealtime()<deadline) {
            if(dismiss(automation.rootInActiveWindow)) { android.os.SystemClock.sleep(300);return }
            android.os.SystemClock.sleep(100)
        }
    }

}
