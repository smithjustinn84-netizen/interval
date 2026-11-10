package dev.justinsmith.lift.ui.demo

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.justinsmith.interval.platform.prefersReducedMotion

/**
 * Where a demo is in its rep and whether it runs on: what its figure, its timeline and its cues all read, so
 * dragging along the timeline or picking a cue moves the figure with them. It goes no earlier than [earliest]:
 * a demo's [ExerciseDemo.earliest], -1 when it sets up first.
 */
@Stable
class DemoPlayer(progress: Float, playing: Boolean, private val earliest: Float = 0f) {
    /** How far through the rep it is, 0 to 1; below 0, how far through setting up, from -1. */
    var progress by mutableFloatStateOf(progress)
        private set

    /** Whether it runs on by itself. */
    var playing by mutableStateOf(playing)
        private set

    /**
     * Whether its figure is on screen: one scrolled away stops taking frames, but keeps [playing], so it runs on
     * from where it was as it comes back, and nothing reads it as paused.
     */
    internal var inView by mutableStateOf(true)

    /** Stops it [at] this share of the rep, as a drag along the timeline or a picked cue does. */
    fun seek(at: Float) {
        playing = false
        progress = at.coerceIn(earliest, 1f)
    }

    /** Sets up again from the start and plays on into the reps. */
    fun replaySetUp() {
        progress = earliest
        playing = true
    }

    fun toggle() {
        playing = !playing
    }

    /** Holds it where it is, as a figure does while it marks a record; [play] has it run on from there. */
    fun pause() {
        playing = false
    }

    fun play() {
        playing = true
    }

    /** Moves it on to [at] while it runs. */
    internal fun runTo(at: Float) {
        progress = at
    }

    internal companion object {
        val Saver = listSaver<DemoPlayer, Any>(
            save = { listOf(it.progress, it.playing, it.earliest) },
            restore = { DemoPlayer(progress = it[0] as Float, playing = it[1] as Boolean, earliest = it[2] as Float) },
        )
    }
}

/**
 * A player for [demo], kept across a rotation, setting up first if the demo does, then advancing a rep each
 * [ExerciseDemo.secondsPerRep] while it plays.
 * The frames come from an infinite animation, so UI tests (which cancel those) never wait on it. With reduced
 * motion it stands at [ExerciseDemo.still] and never runs by itself; it still moves where it is dragged.
 */
@Composable
fun rememberDemoPlayer(demo: ExerciseDemo): DemoPlayer {
    val still = prefersReducedMotion()
    val player = rememberSaveable(demo, saver = DemoPlayer.Saver) {
        DemoPlayer(progress = if (still) demo.still else demo.earliest, playing = !still, earliest = demo.earliest)
    }
    LaunchedEffect(player, still, player.playing, player.inView) {
        if (still || !player.playing || !player.inView) return@LaunchedEffect
        val from = player.progress
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            val elapsed = withInfiniteAnimationFrameMillis { it } - start
            player.runTo(demo.after(from, elapsed / MILLIS_PER_SECOND))
        }
    }
    return player
}

private const val MILLIS_PER_SECOND = 1000f
