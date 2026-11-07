package dev.justinsmith.interval.platform

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * The device's voice: Android's text-to-speech engine, iOS's speech synthesizer, nothing on the desktop. One per
 * process, which both sides speak through, the interval side's cues and Lift's coaching alike; each puts its own
 * lines into words and keeps its own switch (the voice in Settings governs both). A line cuts short the one still
 * being said. Implementations swallow platform failures: a missing line must never stop a workout.
 */
interface Speaker {
    /** Says [text] now, over whatever was being said. Called on the main thread. */
    fun speak(text: String)

    /** Lets go of the engine. The container calls it on close. */
    fun release() = Unit
}

/** A platform with no voice: the desktop's. */
object NoSpeaker : Speaker {
    override fun speak(text: String) = Unit
}

/**
 * Puts each line, of any kind [T], into [words] off the caller (reading strings suspends) and hands it to [say] on
 * [dispatcher], the main thread by default, one at a time and in order. Only the newest line waits its turn: one
 * overtaken by the next is stale, just as the speaker cuts a line short when the next one starts.
 */
class SpokenLines<T : Any>(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val words: suspend (T) -> String,
    private val say: (String) -> Unit,
) {
    private val log = Logger.withTag("SpokenLines")
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lines = Channel<T>(Channel.CONFLATED)

    init {
        scope.launch {
            for (line in lines) speakLogged(line)
        }
    }

    // A line that fails is logged and skipped, never thrown: as for every cue, a missing one must not
    // crash a workout, nor stop the lines after it.
    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun speakLogged(line: T) {
        try {
            say(words(line))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Couldn't speak $line" }
        }
    }

    fun speak(line: T) {
        lines.trySend(line)
    }

    /** Stops speaking: lines still waiting are dropped. */
    fun close() {
        scope.cancel()
    }
}
