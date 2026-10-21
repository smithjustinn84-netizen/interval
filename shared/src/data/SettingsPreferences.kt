package dev.justinsmith.interval.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WeightUnit

// How the settings map onto DataStore preference keys. A missing key reads as the data class's default.

private val PREP = intPreferencesKey("prep")
private val WORK = intPreferencesKey("work")
private val REST = intPreferencesKey("rest")
private val ROUNDS = intPreferencesKey("rounds")
private val ACTIVITY = stringPreferencesKey("activity")

private val CUE_SOUND = booleanPreferencesKey("cue_sound")
private val CUE_VOICE = booleanPreferencesKey("cue_voice")
private val CUE_HALFWAY = booleanPreferencesKey("cue_halfway")
private val CUE_VIBRATE = booleanPreferencesKey("cue_vibrate")

private val THEME = stringPreferencesKey("theme")
private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
private val WEEKLY_GOAL = intPreferencesKey("weekly_goal")

private val BODY_WEIGHT_KG = doublePreferencesKey("body_weight_kg")
private val BODY_UNIT = stringPreferencesKey("body_unit")
private val BODY_EFFORT = stringPreferencesKey("body_effort")

// A missing weight reads as the default, so calories turned off are stored as such.
private val CALORIES_OFF = booleanPreferencesKey("calories_off")

internal fun Preferences.toConfig(): IntervalConfig {
    val defaults = IntervalConfig()
    return IntervalConfig(
        prepSeconds = this[PREP] ?: defaults.prepSeconds,
        workSeconds = this[WORK] ?: defaults.workSeconds,
        restSeconds = this[REST] ?: defaults.restSeconds,
        rounds = this[ROUNDS] ?: defaults.rounds,
        activity = Activity.named(this[ACTIVITY]),
    )
}

internal fun MutablePreferences.write(config: IntervalConfig) {
    this[PREP] = config.prepSeconds
    this[WORK] = config.workSeconds
    this[REST] = config.restSeconds
    this[ROUNDS] = config.rounds
    this[ACTIVITY] = config.activity.name
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
        body = toBodySettings(),
        // A goal out of range (written by a build with other limits) is clamped rather than dropped.
        weeklyGoal = this[WEEKLY_GOAL]?.coerceIn(AppSettings.WEEKLY_GOAL_RANGE),
    )
}

internal fun MutablePreferences.write(app: AppSettings) {
    this[THEME] = app.theme.name
    this[KEEP_SCREEN_ON] = app.keepScreenOn
    val goal = app.weeklyGoal
    if (goal == null) remove(WEEKLY_GOAL) else this[WEEKLY_GOAL] = goal
    write(app.body)
}

private fun Preferences.toBodySettings(): BodySettings {
    val defaults = BodySettings()
    return BodySettings(
        weightKg = if (this[CALORIES_OFF] == true) null else this[BODY_WEIGHT_KG] ?: defaults.weightKg,
        // As for the theme: a name this build doesn't know falls back to the default.
        unit = WeightUnit.entries.firstOrNull { it.name == this[BODY_UNIT] } ?: defaults.unit,
        effort = Effort.entries.firstOrNull { it.name == this[BODY_EFFORT] } ?: defaults.effort,
    )
}

private fun MutablePreferences.write(body: BodySettings) {
    val weight = body.weightKg
    if (weight == null) {
        remove(BODY_WEIGHT_KG)
        this[CALORIES_OFF] = true
    } else {
        this[BODY_WEIGHT_KG] = weight
        remove(CALORIES_OFF)
    }
    this[BODY_UNIT] = body.unit.name
    this[BODY_EFFORT] = body.effort.name
}
