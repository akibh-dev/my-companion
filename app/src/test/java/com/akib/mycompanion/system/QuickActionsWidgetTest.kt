package com.akib.mycompanion.system

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.MainActivity
import com.akib.mycompanion.R
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.widget.QuickActionsWidget
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickActionsWidgetTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun render(settings: Settings): View =
        QuickActionsWidget.views(app, settings).apply(app, FrameLayout(app))

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    @Test
    fun `buttons say what a tap will do`() {
        val running = render(Settings())
        assertThat(running.text(R.id.widget_pause)).isEqualTo("Pause")
        assertThat(running.text(R.id.widget_voice)).isEqualTo("Voice off")

        val paused = render(Settings(paused = true, enabled = setOf(Feature.VOICE_MODE)))
        assertThat(paused.text(R.id.widget_pause)).isEqualTo("Resume")
        assertThat(paused.text(R.id.widget_voice)).isEqualTo("Voice on")
    }

    @Test
    fun `capture uses the shared capture action and stays inside the app`() {
        val intent = QuickActionsWidget.captureIntent(app)

        assertThat(intent.action).isEqualTo("com.akib.mycompanion.action.CAPTURE")
        assertThat(intent.`package`).isEqualTo(app.packageName)
    }

    @Test
    fun `the vault button opens the vault screen`() {
        val intent = MainActivity.openRouteIntent(app, QuickActionsWidget.VAULT_ROUTE)

        assertThat(intent.getStringExtra(MainActivity.EXTRA_ROUTE)).isEqualTo("vault")
    }
}
