package com.screenlate.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** How plaques cover the original text. */
enum class DisplayMode { Replace, Overlay, Hint }

/** Plaque colors: following the phone's dark theme, or fixed. */
enum class PlaqueColor { System, Light, Dark }

data class AppSettings(
    val enabled: Boolean = true,
    val displayMode: DisplayMode = DisplayMode.Replace,
    val plaqueColor: PlaqueColor = PlaqueColor.System,
    val overlayOpacity: Float = 0.85f,
    val minTextSp: Int = 8,
    val excludedPackages: Set<String> = emptySet(),
    val economy: Boolean = false,
    val threads: Int = 2,
    val translateEditable: Boolean = false,
    val showBubble: Boolean = true,
    val vendorStepsDone: Boolean = false,
)

/** Where the floating button sits: which screen edge and how far down, as a share of the height. */
data class BubblePosition(val onRight: Boolean = true, val yFraction: Float = 0.6f)

class SettingsRepository private constructor(private val context: Context) {

    private val data: Flow<Preferences> = context.dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val settings: Flow<AppSettings> = data.map(::read).distinctUntilChanged()

    /** Whether translation runs while the accessibility service is on. */
    val enabled: Flow<Boolean> = settings.map { it.enabled }.distinctUntilChanged()

    /** The user confirmed the vendor background settings, which the app cannot check by itself. */
    val vendorStepsDone: Flow<Boolean> = settings.map { it.vendorStepsDone }.distinctUntilChanged()

    val bubblePosition: Flow<BubblePosition> = data.map { prefs ->
        BubblePosition(prefs[BUBBLE_RIGHT] ?: true, prefs[BUBBLE_Y] ?: 0.6f)
    }.distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    suspend fun setEnabled(value: Boolean) = update { it.copy(enabled = value) }

    suspend fun setVendorStepsDone(value: Boolean) = update { it.copy(vendorStepsDone = value) }

    suspend fun setBubblePosition(position: BubblePosition) {
        context.dataStore.edit {
            it[BUBBLE_RIGHT] = position.onRight
            it[BUBBLE_Y] = position.yFraction
        }
    }

    private fun read(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            enabled = prefs[ENABLED] ?: defaults.enabled,
            displayMode = prefs[DISPLAY_MODE]?.let { name -> DisplayMode.entries.find { it.name == name } } ?: defaults.displayMode,
            plaqueColor = prefs[PLAQUE_COLOR]?.let { name -> PlaqueColor.entries.find { it.name == name } } ?: defaults.plaqueColor,
            overlayOpacity = prefs[OVERLAY_OPACITY] ?: defaults.overlayOpacity,
            minTextSp = prefs[MIN_TEXT_SP] ?: defaults.minTextSp,
            excludedPackages = prefs[EXCLUDED] ?: defaults.excludedPackages,
            economy = prefs[ECONOMY] ?: defaults.economy,
            threads = prefs[THREADS] ?: defaults.threads,
            translateEditable = prefs[TRANSLATE_EDITABLE] ?: defaults.translateEditable,
            showBubble = prefs[SHOW_BUBBLE] ?: defaults.showBubble,
            vendorStepsDone = prefs[VENDOR_STEPS_DONE] ?: defaults.vendorStepsDone,
        )
    }

    private fun write(prefs: MutablePreferences, settings: AppSettings) {
        prefs[ENABLED] = settings.enabled
        prefs[DISPLAY_MODE] = settings.displayMode.name
        prefs[PLAQUE_COLOR] = settings.plaqueColor.name
        prefs[OVERLAY_OPACITY] = settings.overlayOpacity
        prefs[MIN_TEXT_SP] = settings.minTextSp
        prefs[EXCLUDED] = settings.excludedPackages
        prefs[ECONOMY] = settings.economy
        prefs[THREADS] = settings.threads
        prefs[TRANSLATE_EDITABLE] = settings.translateEditable
        prefs[SHOW_BUBBLE] = settings.showBubble
        prefs[VENDOR_STEPS_DONE] = settings.vendorStepsDone
    }

    companion object {
        private val ENABLED = booleanPreferencesKey("translation_enabled")
        private val DISPLAY_MODE = stringPreferencesKey("display_mode")
        private val PLAQUE_COLOR = stringPreferencesKey("plaque_color")
        private val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
        private val MIN_TEXT_SP = intPreferencesKey("min_text_sp")
        private val EXCLUDED = stringSetPreferencesKey("excluded_packages")
        private val ECONOMY = booleanPreferencesKey("economy")
        private val THREADS = intPreferencesKey("threads")
        private val TRANSLATE_EDITABLE = booleanPreferencesKey("translate_editable")
        private val SHOW_BUBBLE = booleanPreferencesKey("show_bubble")
        private val VENDOR_STEPS_DONE = booleanPreferencesKey("vendor_steps_done")
        private val BUBBLE_RIGHT = booleanPreferencesKey("bubble_right")
        private val BUBBLE_Y = floatPreferencesKey("bubble_y")

        fun get(context: Context) = SettingsRepository(context.applicationContext)
    }
}
