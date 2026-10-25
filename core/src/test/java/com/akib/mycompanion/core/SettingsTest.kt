package com.akib.mycompanion.core

import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsTest {

    @Test
    fun `a feature is active only while it's on and nothing is paused`() {
        val settings = Settings(enabled = setOf(Feature.AUTO_REPLY))

        assertThat(settings.isActive(Feature.AUTO_REPLY)).isTrue()
        assertThat(settings.isActive(Feature.VOICE_MODE)).isFalse()
        assertThat(settings.copy(paused = true).isActive(Feature.AUTO_REPLY)).isFalse()
    }

    @Test
    fun `a fresh install has everything off`() {
        val settings = Settings()

        assertThat(settings.paused).isFalse()
        Feature.entries.forEach { assertThat(settings.isActive(it)).isFalse() }
        AiEngine.entries.forEach { assertThat(settings.usesModel(it)).isFalse() }
    }

    @Test
    fun `pausing stops features but leaves the model choice alone`() {
        val settings = Settings(
            paused = true,
            enabled = Feature.entries.toSet(),
            modelEngines = setOf(AiEngine.SUMMARIZER),
        )

        Feature.entries.forEach { assertThat(settings.isActive(it)).isFalse() }
        assertThat(settings.usesModel(AiEngine.SUMMARIZER)).isTrue()
        assertThat(settings.usesModel(AiEngine.TRANSLITERATOR)).isFalse()
    }

    @Test
    fun `every feature and engine has words for the screens`() {
        Feature.entries.forEach {
            assertThat(it.title).isNotEmpty()
            assertThat(it.description).isNotEmpty()
        }
        AiEngine.entries.forEach { assertThat(it.title).isNotEmpty() }
        assertThat(Feature.entries.map { it.title }.toSet()).hasSize(Feature.entries.size)
    }
}
