package com.golfv.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.Rule
import org.junit.Test

class LauncherUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dockShowsFiveActionsAndOpensDrawer() {
        composeRule.mainClock.advanceTimeBy(3_100)
        composeRule.waitForIdle()

        with(composeRule.activity) {
            composeRule.onNodeWithContentDescription(getString(R.string.android_auto)).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(getString(R.string.radio)).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(getString(R.string.oem_settings)).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(getString(R.string.android_settings)).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(getString(R.string.app_drawer))
                .performTouchInput { click() }
            composeRule.onNodeWithContentDescription(getString(R.string.back)).assertIsDisplayed()
        }
    }

    @Test
    fun tappingCarKeepsDockTouchable() {
        composeRule.mainClock.advanceTimeBy(3_100)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("carReplayArea").performTouchInput { click() }
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.app_drawer),
        ).performTouchInput { click() }
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.back),
        ).assertIsDisplayed()
    }
}
