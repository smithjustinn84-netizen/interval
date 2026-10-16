package dev.justinsmith.interval.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/** The cue players speak these words; nothing else checks them, since common tests record [Speech] only. */
class SpeechTextTest {
    @Test
    fun `each line has its words`() = runBlocking {
        val lines = listOf(
            Speech.Countdown(3) to "3",
            Speech.GetReady to "Get ready",
            Speech.Work(lastRound = false) to "Work",
            Speech.Work(lastRound = true) to "Last round. Work",
            Speech.Rest to "Rest",
            Speech.Halfway to "Halfway",
            Speech.Finished to "Done. Nice work",
        )
        for ((speech, words) in lines) assertEquals(words, speech.text())
    }

    @Test
    fun `a line that fails to speak doesn't stop the next`() = runBlocking {
        val tried = MutableStateFlow(emptyList<String>())
        val queue = SpeechQueue(Dispatchers.Default) { text ->
            tried.value += text
            check(text != "Rest") { "speech engine gone" }
        }
        queue.speak(Speech.Rest)
        withTimeout(5_000) { tried.first { it == listOf("Rest") } }
        queue.speak(Speech.Finished)
        withTimeout(5_000) { tried.first { it == listOf("Rest", "Done. Nice work") } }
        queue.close()
    }
}
