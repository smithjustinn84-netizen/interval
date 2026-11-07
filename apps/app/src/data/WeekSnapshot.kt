package dev.justinsmith.interval.data

import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.weekStartOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The week as iOS's Home Screen widget reads it: a [WeekStanding] with the Monday of the week it stands for
 * ([weekStart], an epoch day), so the widget, which runs no Kotlin, can tell when the week has turned without
 * the app (`WeekWidget.swift` decodes the same names). The app writes it as JSON to the App Group's defaults.
 */
@Serializable
data class WeekSnapshot(val count: Int, val goal: Int?, val streak: Int, val weekStart: Long) {
    fun toJson(): String = Json.encodeToString(serializer(), this)
}

/** This standing as of [today], an epoch day. */
fun WeekStanding.snapshot(today: Long): WeekSnapshot = WeekSnapshot(count, goal, streak, weekStartOf(today))
