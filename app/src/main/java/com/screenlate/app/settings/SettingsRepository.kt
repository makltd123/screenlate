package com.screenlate.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository private constructor(private val context: Context) {

    private val data: Flow<Preferences> = context.dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    /** Whether translation runs while the accessibility service is on. */
    val enabled: Flow<Boolean> = data.map { it[ENABLED] ?: true }.distinctUntilChanged()

    /** The user confirmed the vendor background settings, which the app cannot check by itself. */
    val vendorStepsDone: Flow<Boolean> = data.map { it[VENDOR_STEPS_DONE] ?: false }.distinctUntilChanged()

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[ENABLED] = value }
    }

    suspend fun setVendorStepsDone(value: Boolean) {
        context.dataStore.edit { it[VENDOR_STEPS_DONE] = value }
    }

    companion object {
        private val ENABLED = booleanPreferencesKey("translation_enabled")
        private val VENDOR_STEPS_DONE = booleanPreferencesKey("vendor_steps_done")

        fun get(context: Context) = SettingsRepository(context.applicationContext)
    }
}
