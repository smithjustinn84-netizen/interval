package dev.justinsmith.interval.data

import dev.justinsmith.interval.platform.LiveActivityContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A [LiveActivityContent] as iOS's Live Activity reads it: `IntervalActivity.swift`'s `ContentState` decodes the
 * same names, so a change here is a change there. Times are epoch milliseconds, colours signed ARGB.
 */
@Serializable
data class LiveActivityState(
    val title: String,
    val detail: String? = null,
    val staleTitle: String? = null,
    val color: Int? = null,
    val paused: Boolean = false,
    val pausedClock: String? = null,
    val intervalStart: Long? = null,
    val intervalEnd: Long? = null,
    val runStart: Long? = null,
    val runEnd: Long? = null,
    val runLabel: String? = null,
    val progress: Double = 0.0,
    val segments: List<Segment> = emptyList(),
    val toggleLabel: String? = null,
    val endLabel: String? = null,
) {
    @Serializable
    data class Segment(val length: Int, val color: Int? = null)

    fun toJson(): String = json.encodeToString(serializer(), this)

    private companion object {
        // Every field, nulls too: Swift's decoder wants each key its type has no default for.
        val json = Json { encodeDefaults = true }
    }
}

fun LiveActivityContent.state(): LiveActivityState = LiveActivityState(
    title = title,
    detail = detail,
    staleTitle = staleTitle,
    color = colorArgb,
    paused = paused,
    pausedClock = pausedClock,
    intervalStart = interval?.startMillis,
    intervalEnd = interval?.endMillis,
    runStart = run?.startMillis,
    runEnd = run?.endMillis,
    runLabel = runLabel,
    progress = progress,
    segments = segments.map { LiveActivityState.Segment(it.length, it.colorArgb) },
    toggleLabel = toggleLabel,
    endLabel = endLabel,
)
