package com.akib.mycompanion.context.screenon

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** One test on purpose: the DataStore is one per process, so a second test would see this one's file. */
@RunWith(AndroidJUnit4::class)
class ScreenOnPreferencesTest {

    @Test
    fun `sensitivity starts at normal and is remembered`() = runTest {
        val preferences = DataStoreScreenOnPreferences(ApplicationProvider.getApplicationContext<Context>())
        assertThat(preferences.sensitivity.first()).isEqualTo(Sensitivity.NORMAL)

        preferences.setSensitivity(Sensitivity.STRICT)
        assertThat(preferences.sensitivity.first()).isEqualTo(Sensitivity.STRICT)

        preferences.setSensitivity(Sensitivity.RELAXED)
        val again = DataStoreScreenOnPreferences(ApplicationProvider.getApplicationContext<Context>())
        assertThat(again.sensitivity.first()).isEqualTo(Sensitivity.RELAXED)
    }

    @Test
    fun `stricter sensitivities narrow the angles`() {
        val (relaxed, normal, strict) = Sensitivity.entries

        assertThat(relaxed.minPitch).isLessThan(normal.minPitch)
        assertThat(normal.minPitch).isLessThan(strict.minPitch)
        assertThat(relaxed.maxPitch).isGreaterThan(normal.maxPitch)
        assertThat(normal.maxPitch).isGreaterThan(strict.maxPitch)
        assertThat(relaxed.maxRoll).isGreaterThan(normal.maxRoll)
        assertThat(normal.maxRoll).isGreaterThan(strict.maxRoll)
        Sensitivity.entries.forEach { assertThat(it.minPitch).isLessThan(it.maxPitch) }
    }
}
