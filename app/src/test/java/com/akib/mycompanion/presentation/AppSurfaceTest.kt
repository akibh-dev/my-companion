package com.akib.mycompanion.presentation

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.AppSurface
import com.akib.mycompanion.ui.theme.CompanionTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSurfaceTest {

    @get:Rule
    val compose = createComposeRule()

    private fun contentColorIn(dark: Boolean): Pair<Color, Color> {
        var content = Color.Unspecified
        var expected = Color.Unspecified
        compose.setContent {
            CompanionTheme(darkTheme = dark) {
                expected = MaterialTheme.colorScheme.onBackground
                AppSurface { content = LocalContentColor.current }
            }
        }
        compose.waitForIdle()
        return content to expected
    }

    @Test
    fun `text without its own colour is readable in dark mode`() {
        val (content, onBackground) = contentColorIn(dark = true)

        assertThat(content).isEqualTo(onBackground)
        assertThat(content).isNotEqualTo(Color.Black)
    }

    @Test
    fun `and in light mode`() {
        val (content, onBackground) = contentColorIn(dark = false)

        assertThat(content).isEqualTo(onBackground)
    }
}
