package dev.justinsmith.interval.platform

import dev.justinsmith.interval.timer.FlowSide
import dev.justinsmith.interval.timer.FlowStep
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.timer.Phase
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
            Speech.Work(lastRound = false, set = 2) to "Set 2. Work",
            Speech.Work(lastRound = false, switchHands = true) to "Switch hands. Work",
            Speech.Work(lastRound = true, switchHands = true) to "Last round. Switch hands",
            Speech.Rest to "Rest",
            Speech.SetRest to "Rest between sets",
            Speech.BlockRest(block = 2) to "Rest before block 2",
            Speech.Work(lastRound = false, set = 1, block = 3) to "Block 3. Work",
            Speech.WarmUp to "Warm up",
            Speech.CoolDown to "Cool down",
            Speech.Halfway to "Halfway",
            Speech.Finished to "Done. Nice work",
            Speech.NextMove("90/90 hip switch") to "Next: 90/90 hip switch",
            Speech.Side(FlowSide.Left) to "Left side",
            Speech.Side(FlowSide.Right) to "Right side",
            Speech.Begin to "Begin",
            Speech.SwitchSides to "Switch sides",
        )
        for ((speech, words) in lines) assertEquals(words, speech.text())
    }

    @Test
    fun `a flow's interval is its move in a notification`() = runBlocking {
        val pigeon = FlowStep(Move("Pigeon", seconds = 60, cue = "Fold forward.", sided = true), 4, 10, FlowSide.Right)
        val squat = FlowStep(Move("Deep squat", seconds = 60, cue = "Heels down."), 5, 10, side = null)
        assertEquals("Pigeon · Right side", flowTitle(Phase.Work, pigeon))
        assertEquals("Deep squat", flowTitle(Phase.Work, squat))
        assertEquals("Switch sides", flowTitle(Phase.Rest, pigeon))
        assertEquals("Next: Deep squat", flowTitle(Phase.SetRest, squat))
        assertEquals("Next: Pigeon", flowTitle(Phase.Prep, pigeon))
        assertEquals("Move 4 of 10", flowCount(pigeon))
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
