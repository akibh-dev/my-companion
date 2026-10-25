package com.akib.mycompanion.context.screenon

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akib.mycompanion.core.util.enumOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Keep-screen-on's own choices (the feature switch itself lives in core's settings). */
interface ScreenOnPreferences {
    val sensitivity: Flow<Sensitivity>

    suspend fun setSensitivity(sensitivity: Sensitivity)
}

private val Context.screenOnDataStore: DataStore<Preferences> by preferencesDataStore(name = "screen_on")

@Singleton
class DataStoreScreenOnPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : ScreenOnPreferences {
    private val store = context.screenOnDataStore

    override val sensitivity: Flow<Sensitivity> = store.data
        .map { prefs -> enumOf<Sensitivity>(prefs[SENSITIVITY]) ?: Sensitivity.NORMAL }
        .distinctUntilChanged()

    override suspend fun setSensitivity(sensitivity: Sensitivity) {
        store.edit { it[SENSITIVITY] = sensitivity.name }
    }

    private companion object {
        val SENSITIVITY = stringPreferencesKey("sensitivity")
    }
}
