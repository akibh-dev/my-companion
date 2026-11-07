package com.akib.mycompanion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BanglishPreviewDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun previewRunsRealOfflineInferenceFromTheApp() {
        if (compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Skip").performClick()
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("AI engines"))
        compose.onNodeWithText("AI engines").performClick()
        compose.onNodeWithText("Banglish preview (experimental)").assertIsDisplayed()
        compose.onNodeWithText("Banglish message").performTextReplacement("ami")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Preview Bangla"))
        compose.onNodeWithText("Preview Bangla").performClick()
        compose.waitUntil(40_000) {
            val ready = compose.onAllNodesWithText("আমি").fetchSemanticsNodes().isNotEmpty()
            val failed = compose.onAllNodesWithText("Offline model unavailable", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
            ready || failed
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("আমি"))
        compose.onNodeWithText("আমি").assertIsDisplayed()
    }
}
