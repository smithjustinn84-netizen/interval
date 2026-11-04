package dev.justinsmith.interval.platform

import dev.justinsmith.interval.timer.OutlinedInterval
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WorkoutOutline

/** A stretch of the bar, [length] seconds long, in [phase]'s colour; null is the notification's own colour. */
data class BarSegment(val length: Int, val phase: Phase?)

/**
 * The progress bar Android 16's promoted notification draws of a workout (`WorkoutService`'s Live Update), within
 * what the system draws: past [MAX_SEGMENTS] segments it merges them into one in its default colour, and it
 * draws the first [MAX_POINTS] points only. So a workout that fits shows each interval in its phase's colour,
 * and a longer one (a Tabata's sixteen) one segment in the notification's colour, which follows the phase.
 * Points mark where each round after the first begins, when there are few enough to mark them all. A workout of
 * sets runs past ten intervals at once, so it is drawn a segment per set in work's colour and one per rest between
 * sets (get ready and the bookends as themselves), when those fit, and its points mark where each set begins.
 */
data class StatusBar(val segments: List<BarSegment>, val points: List<Int>, val progress: Int) {
    companion object {
        const val MAX_SEGMENTS = 10
        const val MAX_POINTS = 4

        /** The bar of [outline], with its point at the elapsed time; null with nothing to draw. */
        fun of(outline: WorkoutOutline): StatusBar? {
            if (outline.totalSeconds <= 0) return null
            val sets = outline.intervals.any { it.set > 1 }
            val drawn = if (sets) {
                bySet(outline.intervals)
            } else {
                outline.intervals.map {
                    BarSegment(it.seconds, it.phase)
                }
            }
            val segments = drawn.takeIf { it.size <= MAX_SEGMENTS } ?: listOf(BarSegment(outline.totalSeconds, null))
            val starts = mutableListOf<Int>()
            var at = 0
            outline.intervals.forEach {
                val starting = it.phase == Phase.Work && if (sets) it.round == 1 && it.set > 1 else it.round > 1
                if (starting) starts += at
                at += it.seconds
            }
            val points = starts.takeIf { it.size <= MAX_POINTS }.orEmpty()
            return StatusBar(segments, points, outline.elapsedSeconds)
        }

        /** A segment per set (its rounds and the rests within it) and per rest between sets; the others as they are. */
        private fun bySet(intervals: List<OutlinedInterval>): List<BarSegment> = buildList {
            var drawing: Int? = null
            intervals.forEach {
                val inRounds = it.phase == Phase.Work || it.phase == Phase.Rest
                when {
                    inRounds && it.set == drawing -> set(lastIndex, BarSegment(last().length + it.seconds, Phase.Work))
                    inRounds -> add(BarSegment(it.seconds, Phase.Work))
                    else -> add(BarSegment(it.seconds, it.phase))
                }
                drawing = if (inRounds) it.set else null
            }
        }
    }
}
