package dev.justinsmith.interval.data

import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.SessionDetail
import dev.justinsmith.core.sync.SessionRecord
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.defaultPresetName
import dev.justinsmith.interval.timer.matching
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A workout a sibling app recorded, as this app shows it: in History, the week, the streak and the goal.
 * It is the sibling's to change, so here it is read-only. [title] is in the sibling's own words.
 */
data class SiblingWorkout(
    val uid: String,
    val source: AppId,
    val finishedAtEpochMillis: Long,
    val elapsedSeconds: Int,
    /** Of [elapsedSeconds], the time spent working, when the sibling knows it. */
    val activeSeconds: Int?,
    val activity: Activity,
    val title: String,
) {
    /**
     * The estimated calories burned, or null without a body weight. Without [activeSeconds] the whole
     * session counts as work, as a kettlebell session's rests are short.
     */
    fun calories(body: BodySettings): Int? {
        val work = (activeSeconds ?: elapsedSeconds).coerceIn(0, elapsedSeconds)
        return body.calories(work, elapsedSeconds - work, activity)
    }
}

/** A row from an app this build doesn't know (a newer sibling) is left out. */
internal fun SiblingSessionEntity.toDomain(): SiblingWorkout? {
    val app = AppId.entries.firstOrNull { it.name == source } ?: return null
    return SiblingWorkout(
        uid,
        app,
        finishedAtEpochMillis,
        elapsedSeconds,
        activeSeconds,
        Activity.named(activity),
        title,
    )
}

internal fun SessionRecord.toSiblingEntity() = SiblingSessionEntity(
    uid = uid,
    source = source.name,
    finishedAtEpochMillis = finishedAtEpochMillis,
    elapsedSeconds = elapsedSeconds,
    activeSeconds = activeSeconds,
    activity = activity,
    title = title,
    detailJson = Json.encodeToString(JsonObject.serializer(), detail),
    updatedAtEpochMillis = updatedAtEpochMillis,
)

/**
 * A stored workout as Interval shares it, named as History names it: by its preset when one matches
 * [presets], else by its shape ("40/20 ×6").
 */
internal fun HistoryEntity.toRecord(presets: List<Preset>): SessionRecord {
    val entry = toDomain()
    return SessionRecord(
        uid = uid,
        source = AppId.Interval,
        finishedAtEpochMillis = finishedAtEpochMillis,
        elapsedSeconds = elapsedSeconds,
        activeSeconds = workActiveSeconds,
        activity = entry.config.activity.name,
        title = presets.matching(entry.config)?.name ?: entry.config.defaultPresetName(),
        detail = SessionDetail.Interval(prepSeconds, workSeconds, restSeconds, rounds, endedEarly).toJson(),
        updatedAtEpochMillis = updatedAtEpochMillis,
        deleted = deleted,
    )
}
