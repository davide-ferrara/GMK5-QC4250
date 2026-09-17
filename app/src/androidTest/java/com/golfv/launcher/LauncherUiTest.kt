package com.golfv.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class LauncherUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dockShowsFiveActionsAndOpensDrawer() {
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Android Auto").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Radio").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("OEM settings").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Android settings").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Applications").performClick()
        composeRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }
}
