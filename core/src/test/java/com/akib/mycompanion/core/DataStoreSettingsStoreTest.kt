package com.akib.mycompanion.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.DataStoreSettingsStore
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One test on purpose: the DataStore behind the store is one per process, so a second test in this
 * module would see this one's file.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreSettingsStoreTest {

    @Test
    fun `switches are stored, read back and switched off again`() = runTest {
        val store = DataStoreSettingsStore(ApplicationProvider.getApplicationContext<Context>())
        assertThat(store.settings.first()).isEqualTo(Settings())

        store.setPaused(true)
        store.setEnabled(Feature.CAPTURE, true)
        store.setEnabled(Feature.VOICE_MODE, true)
        store.setEnabled(Feature.VOICE_MODE, true)
        store.setUseModel(AiEngine.SUMMARIZER, true)
        assertThat(store.settings.first()).isEqualTo(
            Settings(
                paused = true,
                enabled = setOf(Feature.CAPTURE, Feature.VOICE_MODE),
                modelEngines = setOf(AiEngine.SUMMARIZER),
            ),
        )

        store.setEnabled(Feature.CAPTURE, false)
        store.setEnabled(Feature.AUTO_REPLY, false)
        store.setUseModel(AiEngine.SUMMARIZER, false)
        store.setPaused(false)
        assertThat(store.settings.first()).isEqualTo(Settings(enabled = setOf(Feature.VOICE_MODE)))

        // Another instance (the next time the app starts) reads the same values.
        val again = DataStoreSettingsStore(ApplicationProvider.getApplicationContext<Context>())
        assertThat(again.settings.first().enabled).containsExactly(Feature.VOICE_MODE)
    }
}
