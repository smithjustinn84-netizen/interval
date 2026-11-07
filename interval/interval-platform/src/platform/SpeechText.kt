package dev.justinsmith.interval.platform

import dev.justinsmith.interval.platform.resources.Res
import dev.justinsmith.interval.platform.resources.cue_begin
import dev.justinsmith.interval.platform.resources.cue_block_rest
import dev.justinsmith.interval.platform.resources.cue_block_work
import dev.justinsmith.interval.platform.resources.cue_cool_down
import dev.justinsmith.interval.platform.resources.cue_finished
import dev.justinsmith.interval.platform.resources.cue_get_ready
import dev.justinsmith.interval.platform.resources.cue_halfway
import dev.justinsmith.interval.platform.resources.cue_last_round_work
import dev.justinsmith.interval.platform.resources.cue_left_side
import dev.justinsmith.interval.platform.resources.cue_next_move
import dev.justinsmith.interval.platform.resources.cue_rest
import dev.justinsmith.interval.platform.resources.cue_right_side
import dev.justinsmith.interval.platform.resources.cue_set_rest
import dev.justinsmith.interval.platform.resources.cue_set_work
import dev.justinsmith.interval.platform.resources.cue_switch_hands_last_round
import dev.justinsmith.interval.platform.resources.cue_switch_hands_work
import dev.justinsmith.interval.platform.resources.cue_switch_sides
import dev.justinsmith.interval.platform.resources.cue_warm_up
import dev.justinsmith.interval.platform.resources.cue_work
import dev.justinsmith.interval.timer.FlowSide
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.getString

/** The words for this line, in the app's language: "Last round. Work", or the countdown's number. */
internal suspend fun Speech.text(): String = when (this) {
    is Speech.Countdown -> secondsLeft.toString()

    Speech.GetReady -> getString(Res.string.cue_get_ready)

    is Speech.Work -> {
        val set = set
        val block = block
        when {
            switchHands && lastRound -> getString(Res.string.cue_switch_hands_last_round)
            switchHands -> getString(Res.string.cue_switch_hands_work)
            lastRound -> getString(Res.string.cue_last_round_work)
            block != null -> getString(Res.string.cue_block_work, block)
            set != null -> getString(Res.string.cue_set_work, set)
            else -> getString(Res.string.cue_work)
        }
    }

    Speech.Rest -> getString(Res.string.cue_rest)

    Speech.SetRest -> getString(Res.string.cue_set_rest)

    is Speech.BlockRest -> getString(Res.string.cue_block_rest, block)

    Speech.WarmUp -> getString(Res.string.cue_warm_up)

    Speech.CoolDown -> getString(Res.string.cue_cool_down)

    Speech.Halfway -> getString(Res.string.cue_halfway)

    Speech.Finished -> getString(Res.string.cue_finished)

    is Speech.NextMove -> getString(Res.string.cue_next_move, name)

    is Speech.Side -> getString(if (side == FlowSide.Left) Res.string.cue_left_side else Res.string.cue_right_side)

    Speech.Begin -> getString(Res.string.cue_begin)

    Speech.SwitchSides -> getString(Res.string.cue_switch_sides)
}

/**
 * The interval side's [SpokenLines]: each [Speech] put into words (reading strings suspends) and handed to [say],
 * the process's [Speaker], on the main thread, one at a time and in order, the newest alone waiting its turn.
 */
internal class SpeechQueue(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    say: (String) -> Unit,
) {
    private val lines = SpokenLines<Speech>(dispatcher, words = { it.text() }, say = say)

    fun speak(speech: Speech) = lines.speak(speech)

    /** Stops speaking: lines still waiting are dropped. */
    fun close() = lines.close()
}
