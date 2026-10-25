package com.akib.mycompanion.core

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.core.context.ContextStatus
import com.akib.mycompanion.core.host.HostService
import com.akib.mycompanion.core.host.HostedComponent
import com.akib.mycompanion.core.host.ServiceHost
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ServiceHostTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private class Component(override val name: String, private val feature: Feature) : HostedComponent {
        override fun isWanted(settings: Settings) = settings.isActive(feature)

        override fun start(scope: CoroutineScope) = Unit
    }

    private val host = ServiceHost(
        app,
        setOf(Component("rules", Feature.CONTEXT_RULES), Component("voice", Feature.VOICE_MODE)),
    )

    @Test
    fun `needed while one of its features is on and nothing is paused`() {
        assertThat(host.isNeeded(Settings())).isFalse()
        assertThat(host.isNeeded(Settings(enabled = setOf(Feature.CONTEXT_RULES)))).isTrue()
        assertThat(host.isNeeded(Settings(enabled = setOf(Feature.AUTO_REPLY)))).isFalse()
        assertThat(host.isNeeded(Settings(paused = true, enabled = setOf(Feature.VOICE_MODE)))).isFalse()
    }

    @Test
    fun `sync starts the background service when a feature needs it`() {
        host.sync(Settings(enabled = setOf(Feature.VOICE_MODE)))

        val started = shadowOf(app).nextStartedService
        assertThat(started.component?.className).isEqualTo(HostService::class.java.name)
    }

    @Test
    fun `sync does nothing while nothing needs it`() {
        host.sync(Settings())
        host.sync(Settings(paused = true, enabled = setOf(Feature.CONTEXT_RULES)))

        assertThat(shadowOf(app).nextStartedService).isNull()
    }

    @Test
    fun `sync doesn't start it twice while it runs`() {
        host.setRunning(true)

        host.sync(Settings(enabled = setOf(Feature.CONTEXT_RULES)))

        assertThat(host.running.value).isTrue()
        assertThat(shadowOf(app).nextStartedService).isNull()
    }

    @Test
    fun `not running until the service says so`() {
        assertThat(host.running.value).isFalse()
        host.setRunning(true)
        host.setRunning(false)
        assertThat(host.running.value).isFalse()
    }

    @Test
    fun `without components the host is never needed`() {
        assertThat(ServiceHost(app, emptySet()).isNeeded(Settings(enabled = Feature.entries.toSet()))).isFalse()
    }

    @Test
    fun `context status shares the active rule names`() {
        val status = ContextStatus()
        assertThat(status.activeRules.value).isEmpty()

        status.setActiveRules(setOf("Driving", "Mosque"))
        assertThat(status.activeRules.value).containsExactly("Driving", "Mosque")

        status.setActiveRules(emptySet())
        assertThat(status.activeRules.value).isEmpty()
    }
}
