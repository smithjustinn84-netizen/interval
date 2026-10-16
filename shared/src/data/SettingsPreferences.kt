package dev.justinsmith.interval.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.justinsmith.interval.timer.IntervalConfig

// How the settings map onto DataStore preference keys. A missing key reads as the data class's default.

private val PREP = intPreferencesKey("prep")
private val WORK = intPreferencesKey("work")
private val REST = intPreferencesKey("rest")
private val ROUNDS = intPreferencesKey("rounds")

private val CUE_SOUND = booleanPreferencesKey("cue_sound")
private val CUE_VOICE = booleanPreferencesKey("cue_voice")
private val CUE_HALFWAY = booleanPreferencesKey("cue_halfway")
private val CUE_VIBRATE = booleanPreferencesKey("cue_vibrate")

private val THEME = stringPreferencesKey("theme")
private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

internal fun Preferences.toConfig(): IntervalConfig {
    val defaults = IntervalConfig()
    return IntervalConfig(
        prepSeconds = this[PREP] ?: defaults.prepSeconds,
        workSeconds = this[WORK] ?: defaults.workSeconds,
        restSeconds = this[REST] ?: defaults.restSeconds,
        rounds = this[ROUNDS] ?: defaults.rounds,
    )
}

internal fun MutablePreferences.write(config: IntervalConfig) {
    this[PREP] = config.prepSeconds
    this[WORK] = config.workSeconds
    this[REST] = config.restSeconds
    this[ROUNDS] = config.rounds
}

internal fun Preferences.toCueSettings(): CueSettings {
    val defaults = CueSettings()
    return CueSettings(
        sound = this[CUE_SOUND] ?: defaults.sound,
        voice = this[CUE_VOICE] ?: defaults.voice,
        halfway = this[CUE_HALFWAY] ?: defaults.halfway,
        vibrate = this[CUE_VIBRATE] ?: defaults.vibrate,
    )
}

internal fun MutablePreferences.write(cues: CueSettings) {
    this[CUE_SOUND] = cues.sound
    this[CUE_VOICE] = cues.voice
    this[CUE_HALFWAY] = cues.halfway
    this[CUE_VIBRATE] = cues.vibrate
}

internal fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        // A theme this build doesn't know (renamed, or written by a newer one) falls back to the default.
        theme = ThemeMode.entries.firstOrNull { it.name == this[THEME] } ?: defaults.theme,
        keepScreenOn = this[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
    )
}

internal fun MutablePreferences.write(app: AppSettings) {
    this[THEME] = app.theme.name
    this[KEEP_SCREEN_ON] = app.keepScreenOn
}
