package com.akib.mycompanion.presentation

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.core.health.HeartbeatLog
import com.akib.mycompanion.core.host.HostedComponent
import com.akib.mycompanion.core.host.ServiceHost
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.fakes.FakeClock
import com.akib.mycompanion.fakes.MainDispatcherRule
import com.akib.mycompanion.presentation.home.HomeViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private val settingsStore = object : SettingsStore {
        override val settings = MutableStateFlow(Settings())
        override suspend fun setPaused(paused: Boolean) {
            settings.value = settings.value.copy(paused = paused)
        }
        override suspend fun setEnabled(feature: Feature, enabled: Boolean) {
            val current = settings.value.enabled
            settings.value = settings.value.copy(enabled = if (enabled) current + feature else current - feature)
        }
        override suspend fun setUseModel(engine: AiEngine, useModel: Boolean) = Unit
    }

    private var dndGranted = false

    private fun requirement(id: String, feature: Feature?, granted: () -> Boolean) = Requirement(
        id = id,
        title = id,
        why = "because",
        feature = feature,
        isGranted = { granted() },
        settingsIntent = { Intent() },
    )

    private val requirements = setOf(
        requirement("notifications", null) { false },
        requirement("dnd", Feature.CONTEXT_RULES) { dndGranted },
        requirement("contacts", Feature.AUTO_REPLY) { false },
    )

    private val rules = object : HostedComponent {
        override val name = "context-rules"
        override fun isWanted(settings: Settings) = settings.isActive(Feature.CONTEXT_RULES)
        override fun start(scope: CoroutineScope) = Unit
    }

    private val clock = FakeClock()
    private val heartbeats = HeartbeatLog(app, clock)
    private val viewModel by lazy {
        HomeViewModel(app, settingsStore, requirements, ServiceHost(app, setOf(rules)), heartbeats)
    }

    /** Keeps the state flowing, unconfined so it's up to date as soon as this returns. */
    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `only permissions for features that are on are missing, plus the general ones`() = runTest {
        subscribe()

        assertThat(viewModel.uiState.value.missing.map { it.id }).containsExactly("notifications")

        viewModel.onFeatureChange(Feature.CONTEXT_RULES, true)
        assertThat(viewModel.uiState.first { it.settings.enabled.isNotEmpty() }.missing.map { it.id })
            .containsExactly("notifications", "dnd")
    }

    @Test
    fun `a permission granted in system settings is noticed on resume`() = runTest {
        settingsStore.settings.value = Settings(enabled = setOf(Feature.CONTEXT_RULES))
        subscribe()
        assertThat(viewModel.uiState.first { it.settings.enabled.isNotEmpty() }.missing.map { it.id })
            .contains("dnd")

        dndGranted = true
        viewModel.onResume()

        assertThat(viewModel.uiState.first { "dnd" !in it.missing.map { r -> r.id } }.missing.map { it.id })
            .containsExactly("notifications")
    }

    @Test
    fun `the background service is reported down when a feature needs it`() = runTest {
        subscribe()
        assertThat(viewModel.uiState.value.hostDown).isFalse()

        viewModel.onFeatureChange(Feature.CONTEXT_RULES, true)
        assertThat(viewModel.uiState.first { it.settings.enabled.isNotEmpty() }.hostDown).isTrue()

        viewModel.onPauseChange(true)
        val paused = viewModel.uiState.first { it.settings.paused }
        assertThat(paused.hostDown).isFalse()
        assertThat(settingsStore.settings.value.paused).isTrue()
    }

    @Test
    fun `the last heartbeat is shown after resume`() = runTest {
        heartbeats.beat()
        subscribe()
        assertThat(viewModel.uiState.value.lastHeartbeat).isNull()

        viewModel.onResume()

        assertThat(viewModel.uiState.first { it.lastHeartbeat != null }.lastHeartbeat).isEqualTo(clock.now)
    }

    @Test
    fun `switching a feature off takes its permissions off the list`() = runTest {
        settingsStore.settings.value = Settings(enabled = setOf(Feature.AUTO_REPLY))
        subscribe()
        assertThat(viewModel.uiState.first { it.settings.enabled.isNotEmpty() }.missing.map { it.id })
            .containsExactly("notifications", "contacts")

        viewModel.onFeatureChange(Feature.AUTO_REPLY, false)

        assertThat(viewModel.uiState.first { it.settings.enabled.isEmpty() }.missing.map { it.id })
            .containsExactly("notifications")
    }
}
