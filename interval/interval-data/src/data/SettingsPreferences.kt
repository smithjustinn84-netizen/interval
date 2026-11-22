package dev.justinsmith.interval.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.platform.MAX_BPM_RANGE
import dev.justinsmith.interval.platform.RESTING_BPM_RANGE
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WeightUnit

// How the settings map onto DataStore preference keys. A missing key reads as the data class's default.

private val PREP = intPreferencesKey("prep")
private val WORK = intPreferencesKey("work")
private val REST = intPreferencesKey("rest")
private val ROUNDS = intPreferencesKey("rounds")
private val WARM_UP = intPreferencesKey("warm_up")
private val COOL_DOWN = intPreferencesKey("cool_down")
private val SETS = intPreferencesKey("sets")
private val SET_REST = intPreferencesKey("set_rest")
private val WORK_STEP = intPreferencesKey("work_step")
private val THEN_BLOCKS = stringPreferencesKey("then_blocks")
private val BLOCK_REST = intPreferencesKey("block_rest")
private val CAPPED = booleanPreferencesKey("capped")
private val ACTIVITY = stringPreferencesKey("activity")

private val CUE_SOUND = booleanPreferencesKey("cue_sound")
private val CUE_VOICE = booleanPreferencesKey("cue_voice")
private val CUE_HALFWAY = booleanPreferencesKey("cue_halfway")
private val CUE_VIBRATE = booleanPreferencesKey("cue_vibrate")
private val CUE_HR_ALERTS = booleanPreferencesKey("cue_hr_alerts")

private val THEME = stringPreferencesKey("theme")
private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
private val WEEKLY_GOAL = intPreferencesKey("weekly_goal")
private val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
private val SESSION_MINUTES = intPreferencesKey("session_minutes")
private val SETUP_OFFERED = booleanPreferencesKey("setup_offered")
private val FIRST_WEEK = booleanPreferencesKey("first_week")
private val PROGRAM_PUT_OFF = longPreferencesKey("program_put_off_day")
private val LAST_BACKUP = longPreferencesKey("last_backup")
private val BACKUP_ASKED = longPreferencesKey("backup_asked")

private val BODY_WEIGHT_KG = doublePreferencesKey("body_weight_kg")
private val BODY_UNIT = stringPreferencesKey("body_unit")
private val BODY_EFFORT = stringPreferencesKey("body_effort")
private val BODY_MAX_HR = intPreferencesKey("body_max_hr")
private val BODY_RESTING_HR = intPreferencesKey("body_resting_hr")

// A missing weight reads as the default, so calories turned off are stored as such.
private val CALORIES_OFF = booleanPreferencesKey("calories_off")

internal fun Preferences.toConfig(): IntervalConfig {
    val defaults = IntervalConfig()
    return IntervalConfig.sanitized(
        prepSeconds = this[PREP] ?: defaults.prepSeconds,
        workSeconds = this[WORK] ?: defaults.workSeconds,
        restSeconds = this[REST] ?: defaults.restSeconds,
        rounds = this[ROUNDS] ?: defaults.rounds,
        warmUpSeconds = this[WARM_UP] ?: defaults.warmUpSeconds,
        coolDownSeconds = this[COOL_DOWN] ?: defaults.coolDownSeconds,
        activity = Activity.named(this[ACTIVITY]),
        sets = this[SETS] ?: defaults.sets,
        setRestSeconds = this[SET_REST] ?: defaults.setRestSeconds,
        workStepSeconds = this[WORK_STEP] ?: defaults.workStepSeconds,
        then = decodeBlocks(this[THEN_BLOCKS]),
        blockRestSeconds = this[BLOCK_REST] ?: defaults.blockRestSeconds,
        capped = this[CAPPED] ?: defaults.capped,
    )
}

internal fun MutablePreferences.write(config: IntervalConfig) {
    this[PREP] = config.prepSeconds
    this[WORK] = config.workSeconds
    this[REST] = config.restSeconds
    this[ROUNDS] = config.rounds
    this[WARM_UP] = config.warmUpSeconds
    this[COOL_DOWN] = config.coolDownSeconds
    this[ACTIVITY] = config.activity.name
    this[SETS] = config.sets
    this[SET_REST] = config.setRestSeconds
    this[WORK_STEP] = config.workStepSeconds
    this[THEN_BLOCKS] = encodeBlocks(config.then)
    this[BLOCK_REST] = config.blockRestSeconds
    this[CAPPED] = config.capped
}

internal fun Preferences.toCueSettings(): CueSettings {
    val defaults = CueSettings()
    return CueSettings(
        sound = this[CUE_SOUND] ?: defaults.sound,
        voice = this[CUE_VOICE] ?: defaults.voice,
        halfway = this[CUE_HALFWAY] ?: defaults.halfway,
        vibrate = this[CUE_VIBRATE] ?: defaults.vibrate,
        heartRateAlerts = this[CUE_HR_ALERTS] ?: defaults.heartRateAlerts,
    )
}

internal fun MutablePreferences.write(cues: CueSettings) {
    this[CUE_SOUND] = cues.sound
    this[CUE_VOICE] = cues.voice
    this[CUE_HALFWAY] = cues.halfway
    this[CUE_VIBRATE] = cues.vibrate
    this[CUE_HR_ALERTS] = cues.heartRateAlerts
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
        reminderMinuteOfDay = this[REMINDER_MINUTE]?.coerceIn(AppSettings.REMINDER_RANGE),
        // A length this build doesn't offer reads as none picked.
        sessionMinutes = this[SESSION_MINUTES]?.takeIf { it in AppSettings.SESSION_MINUTES },
        setupOffered = this[SETUP_OFFERED] ?: defaults.setupOffered,
        firstWeek = this[FIRST_WEEK] ?: defaults.firstWeek,
        programPutOffDay = this[PROGRAM_PUT_OFF],
        lastBackupEpochMillis = this[LAST_BACKUP],
        backupAskedEpochMillis = this[BACKUP_ASKED],
    )
}

internal fun MutablePreferences.write(app: AppSettings) {
    this[THEME] = app.theme.name
    this[KEEP_SCREEN_ON] = app.keepScreenOn
    val goal = app.weeklyGoal
    if (goal == null) remove(WEEKLY_GOAL) else this[WEEKLY_GOAL] = goal
    val reminder = app.reminderMinuteOfDay
    if (reminder == null) remove(REMINDER_MINUTE) else this[REMINDER_MINUTE] = reminder
    val session = app.sessionMinutes
    if (session == null) remove(SESSION_MINUTES) else this[SESSION_MINUTES] = session
    this[SETUP_OFFERED] = app.setupOffered
    this[FIRST_WEEK] = app.firstWeek
    val putOff = app.programPutOffDay
    if (putOff == null) remove(PROGRAM_PUT_OFF) else this[PROGRAM_PUT_OFF] = putOff
    val backup = app.lastBackupEpochMillis
    if (backup == null) remove(LAST_BACKUP) else this[LAST_BACKUP] = backup
    val asked = app.backupAskedEpochMillis
    if (asked == null) remove(BACKUP_ASKED) else this[BACKUP_ASKED] = asked
    write(app.body)
}

private fun Preferences.toBodySettings(): BodySettings {
    val defaults = BodySettings()
    return BodySettings(
        weightKg = if (this[CALORIES_OFF] == true) null else this[BODY_WEIGHT_KG] ?: defaults.weightKg,
        // As for the theme: a name this build doesn't know falls back to the default.
        unit = WeightUnit.entries.firstOrNull { it.name == this[BODY_UNIT] } ?: defaults.unit,
        effort = Effort.entries.firstOrNull { it.name == this[BODY_EFFORT] } ?: defaults.effort,
        maxHeartRate = this[BODY_MAX_HR]?.coerceIn(MAX_BPM_RANGE),
        restingHeartRate = this[BODY_RESTING_HR]?.coerceIn(RESTING_BPM_RANGE),
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
    val maxHr = body.maxHeartRate
    if (maxHr == null) remove(BODY_MAX_HR) else this[BODY_MAX_HR] = maxHr
    val restingHr = body.restingHeartRate
    if (restingHr == null) remove(BODY_RESTING_HR) else this[BODY_RESTING_HR] = restingHr
}
