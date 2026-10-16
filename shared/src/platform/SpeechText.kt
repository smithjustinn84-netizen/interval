package dev.justinsmith.interval.platform

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.cue_finished
import dev.justinsmith.interval.resources.cue_get_ready
import dev.justinsmith.interval.resources.cue_halfway
import dev.justinsmith.interval.resources.cue_last_round_work
import dev.justinsmith.interval.resources.cue_rest
import dev.justinsmith.interval.resources.cue_work
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** The words for this line, in the app's language: "Last round. Work", or the countdown's number. */
internal suspend fun Speech.text(): String = when (this) {
    is Speech.Countdown -> secondsLeft.toString()
    Speech.GetReady -> getString(Res.string.cue_get_ready)
    is Speech.Work -> getString(if (lastRound) Res.string.cue_last_round_work else Res.string.cue_work)
    Speech.Rest -> getString(Res.string.cue_rest)
    Speech.Halfway -> getString(Res.string.cue_halfway)
    Speech.Finished -> getString(Res.string.cue_finished)
}

/**
 * Puts each [Speech] into words off the caller (reading strings suspends) and hands it to [say] on the
 * main thread, one at a time and in order. Only the newest line waits its turn: one overtaken by the
 * next is stale, just as the players cut a line short when the next one starts.
 */
internal class SpeechQueue(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val say: (String) -> Unit,
) {
    private val log = Logger.withTag("SpeechQueue")
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lines = Channel<Speech>(Channel.CONFLATED)

    init {
        scope.launch {
            for (speech in lines) speakLogged(speech)
        }
    }

    // A line that fails is logged and skipped, never thrown: as for every cue, a missing one must not
    // crash a workout, nor stop the lines after it.
    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun speakLogged(speech: Speech) {
        try {
            say(speech.text())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Couldn't speak $speech" }
        }
    }

    fun speak(speech: Speech) {
        lines.trySend(speech)
    }

    /** Stops speaking: lines still waiting are dropped. */
    fun close() {
        scope.cancel()
    }
}
