package com.kismet.mobile.ui.devices

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kismet.mobile.MainActivity
import com.kismet.mobile.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DevicesFragmentTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun verifyProximityButton_isDisplayedAndClickable() {
        // Navigate to Devices tab
        onView(withId(R.id.devicesFragment)).perform(click())

        // Check if the verify proximity button is displayed
        onView(withId(R.id.btn_verify_proximity))
            .check(matches(isDisplayed()))

        // Perform click to ensure it triggers the latch logic (permission prompt)
        onView(withId(R.id.btn_verify_proximity)).perform(click())
    }
}
