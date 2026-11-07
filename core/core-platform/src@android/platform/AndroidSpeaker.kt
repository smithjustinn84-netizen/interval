package dev.justinsmith.interval.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import co.touchlab.kermit.Logger

/**
 * Android's text-to-speech engine, over the application's [context]. Lines play on the music stream's
 * neighbour, the engine's default, and each cuts short the one before.
 */
class AndroidSpeaker(context: Context) : Speaker {
    private val log = Logger.withTag("AndroidSpeaker")

    // A line asked for before the engine is ready is held and spoken once it is, so the first cue isn't
    // lost. Both fields are touched only on the main thread: lines are handed over there, and the init
    // callback, which may run on another thread, is posted there. Checking and holding a line on two
    // threads at once could drop it between the check and the hold.
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var pending: String? = null

    private val engine: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            ready = status == TextToSpeech.SUCCESS
            pending?.let { if (ready) say(it) }
            pending = null
        }
    }

    override fun speak(text: String) {
        if (ready) say(text) else pending = text
    }

    private fun say(text: String) {
        runCatching { engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue") }
            .onFailure { log.w(it) { "Speech failed" } }
    }

    override fun release() {
        runCatching { engine.shutdown() }
    }
}
