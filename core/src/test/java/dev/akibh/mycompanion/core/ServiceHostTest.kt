package dev.akibh.mycompanion.core

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.core.context.ContextStatus
import dev.akibh.mycompanion.core.host.HostService
import dev.akibh.mycompanion.core.host.HostedComponent
import dev.akibh.mycompanion.core.host.ServiceHost
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.core.settings.Settings
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
    fun `sync starts it again once the running service has decided to stop`() {
        host.setRunning(true)
        // Everything was paused: the service marks itself not running before it stops.
        host.setRunning(false)

        // Resumed before the old instance is gone.
        host.sync(Settings(enabled = setOf(Feature.CONTEXT_RULES)))

        assertThat(shadowOf(app).nextStartedService?.component?.className).isEqualTo(HostService::class.java.name)
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
