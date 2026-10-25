package com.akib.mycompanion.presentation

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.Screen
import com.akib.mycompanion.core.host.HostedComponent
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.featureRoute
import com.akib.mycompanion.messaging.MessageConsumer
import com.akib.mycompanion.presentation.home.HomeLink
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The real Hilt graph: the pieces every feature module contributes fit together. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class AppGraphTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var screens: Set<@JvmSuppressWildcards Screen>

    @Inject lateinit var links: Set<@JvmSuppressWildcards HomeLink>

    @Inject lateinit var requirements: Set<@JvmSuppressWildcards Requirement>

    @Inject lateinit var components: Set<@JvmSuppressWildcards HostedComponent>

    @Inject lateinit var consumers: Set<@JvmSuppressWildcards MessageConsumer>

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() = hilt.inject()

    private val routes get() = screens.map { it.route }

    @Test
    fun `every feature card opens a registered screen`() {
        Feature.entries.forEach { feature ->
            assertThat(routes).contains(featureRoute(feature))
        }
        assertThat(featureRoute(Feature.KEEP_SCREEN_ON)).isEqualTo("screen-on")
    }

    @Test
    fun `every home link opens a registered screen`() {
        assertThat(links).isNotEmpty()
        links.forEach { link -> assertThat(routes).contains(link.route) }
        assertThat(links.map { it.route }).containsAtLeast("vault", "capture")
    }

    @Test
    fun `screens have unique routes and a title`() {
        assertThat(routes).containsNoDuplicates()
        screens.forEach { assertThat(it.title).isNotEmpty() }
        assertThat(routes).containsAtLeast("rules", "rule/{id}", "vault-item/{id}", "capture/{id}", "export")
    }

    @Test
    fun `permissions have unique ids and can all be checked`() {
        assertThat(requirements.map { it.id }).containsNoDuplicates()
        assertThat(requirements.map { it.id })
            .containsAtLeast("notification_access", "dnd_access", "draw_over_apps", "capture_accessibility")
        requirements.forEach { requirement ->
            requirement.isGranted(app)
            assertThat(requirement.settingsIntent(app)).isNotNull()
            assertThat(requirement.why).isNotEmpty()
        }
    }

    @Test
    fun `each background part runs for its own feature only`() {
        val byName = components.associateBy { it.name }
        assertThat(byName.keys).containsExactly("context-rules", "voice-mode", "keep-screen-on")

        val owners = mapOf(
            "context-rules" to Feature.CONTEXT_RULES,
            "voice-mode" to Feature.VOICE_MODE,
            "keep-screen-on" to Feature.KEEP_SCREEN_ON,
        )
        owners.forEach { (name, feature) ->
            val component = byName.getValue(name)
            Feature.entries.forEach { other ->
                assertThat(component.isWanted(Settings(enabled = setOf(other)))).isEqualTo(other == feature)
            }
            assertThat(component.isWanted(Settings(paused = true, enabled = Feature.entries.toSet()))).isFalse()
        }
    }

    @Test
    fun `auto-reply and voice mode both hear chat messages`() {
        assertThat(consumers).hasSize(2)
    }
}
