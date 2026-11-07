package dev.justinsmith.lift.session

import dev.justinsmith.interval.platform.Speaker
import dev.justinsmith.interval.platform.SpokenLines
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.UnknownExercise
import dev.justinsmith.lift.domain.faults
import dev.justinsmith.lift.platform.Coaching
import dev.justinsmith.lift.platform.LiftVoice
import dev.justinsmith.lift.ui.cues
import dev.justinsmith.lift.ui.words
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray

/**
 * Lift's [LiftVoice]: each line put into words from the lift's cues and faults and said through the process's
 * [speaker], the one the interval side's cues use, while the voice switch the two share is on
 * ([IntervalActivity.voice]).
 * Lines go one at a time on [dispatcher], the newest alone waiting its turn; one with nothing to say is skipped.
 */
class SpokenCoaching(
    private val speaker: Speaker,
    private val intervals: IntervalActivity,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : LiftVoice {
    // Made at the first line, so a container that never speaks starts nothing on the main thread. Lines come
    // from the timers, on the main thread, so nothing races to make it.
    private var lines: SpokenLines<Coaching>? = null

    override fun say(line: Coaching) {
        if (!intervals.voice.value) return
        val spoken = lines ?: SpokenLines<Coaching>(dispatcher, words = { it.words() }) { text ->
            if (text.isNotBlank()) speaker.speak(text)
        }.also { lines = it }
        spoken.speak(line)
    }

    /** Stops speaking: a line still waiting is dropped. */
    fun close() {
        lines?.close()
    }
}

/** The words of this line: the lift's cue or fault it names, taken round as many as it has; empty for none. */
internal suspend fun Coaching.words(): String = when (this) {
    is Coaching.Cue -> when (val lift = exercise) {
        is BuiltInExercise -> getStringArray(lift.cues()).taken(index)

        // An exercise of the user's own has one block of cues: its first line is said.
        is CustomExercise -> lift.cues.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty).orEmpty()

        is UnknownExercise -> ""
    }

    is Coaching.Fault -> exercise.faults.let { if (it.isEmpty()) "" else getString(it[index.mod(it.size)].words()) }
}

private fun List<String>.taken(index: Int): String = if (isEmpty()) "" else this[index.mod(size)]
