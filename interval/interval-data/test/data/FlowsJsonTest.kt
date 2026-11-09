package dev.justinsmith.interval.data

import dev.justinsmith.interval.timer.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlowsJsonTest {
    @Test
    fun `a move's exercise is kept with it and a row from before it was kept reads as none`() {
        val moves = listOf(
            Move("The hundred", 100, "Pump the arms.", held = false, exercise = "Hundred"),
            Move("Deep squat", 60, "Heels down."),
        )
        assertEquals(moves, decodeMoves(encodeMoves(moves)))

        val old = """[{"name":"Plank","seconds":30,"cue":"Long line.","sided":false,"held":true}]"""
        assertNull(decodeMoves(old).single().exercise)
        val blank = """[{"name":"Plank","seconds":30,"cue":"","sided":false,"held":true,"exercise":""}]"""
        assertNull(decodeMoves(blank).single().exercise)
    }
}
