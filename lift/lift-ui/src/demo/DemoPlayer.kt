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
 * dragging along the timeline or picking a cue moves the figure with them.
 */
@Stable
class DemoPlayer(progress: Float, playing: Boolean) {
    /** How far through the rep it is, 0 to 1. */
    var progress by mutableFloatStateOf(progress)
        private set

    /** Whether it runs on by itself. */
    var playing by mutableStateOf(playing)
        private set

    /** Stops it [at] this share of the rep, as a drag along the timeline or a picked cue does. */
    fun seek(at: Float) {
        playing = false
        progress = at.coerceIn(0f, 1f)
    }

    fun toggle() {
        playing = !playing
    }

    /** Moves it on to [at] while it runs. */
    internal fun runTo(at: Float) {
        progress = at
    }

    internal companion object {
        val Saver = listSaver<DemoPlayer, Any>(
            save = { listOf(it.progress, it.playing) },
            restore = { DemoPlayer(progress = it[0] as Float, playing = it[1] as Boolean) },
        )
    }
}

/**
 * A player for [demo], kept across a rotation, advancing a rep each [ExerciseDemo.secondsPerRep] while it plays.
 * The frames come from an infinite animation, so UI tests (which cancel those) never wait on it. With reduced
 * motion it stands at [ExerciseDemo.still] and never runs by itself; it still moves where it is dragged.
 */
@Composable
fun rememberDemoPlayer(demo: ExerciseDemo): DemoPlayer {
    val still = prefersReducedMotion()
    val player = rememberSaveable(demo, saver = DemoPlayer.Saver) {
        DemoPlayer(progress = demo.still, playing = !still)
    }
    LaunchedEffect(player, still, player.playing) {
        if (still || !player.playing) return@LaunchedEffect
        val repMillis = demo.secondsPerRep * MILLIS_PER_SECOND
        val from = player.progress
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            val elapsed = withInfiniteAnimationFrameMillis { it } - start
            player.runTo((from + elapsed / repMillis) % 1f)
        }
    }
    return player
}

private const val MILLIS_PER_SECOND = 1000f
