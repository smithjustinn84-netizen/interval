package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AbcProtocolTest {
    private val onTheMinute = program(ProgramId.BjjKettlebell).day(1).blocks.single()

    @Test
    fun `the ABC on the minute runs as many as you can for the same minutes and back`() {
        assertEquals(AbcProtocol.OnTheMinute, onTheMinute.abcProtocol)
        val capped = onTheMinute.otherAbcProtocol() ?: error("No other protocol")
        assertEquals(BlockKind.Density, capped.kind)
        assertEquals(15 to 1, capped.rounds to capped.reps)
        assertEquals(AbcProtocol.AsManyAsYouCan, capped.abcProtocol)
        assertEquals(onTheMinute, capped.otherAbcProtocol())
    }

    @Test
    fun `a cap is kept within a density block's even from a longer EMOM`() {
        val long = onTheMinute.copy(rounds = 40)
        assertEquals(30, long.otherAbcProtocol()?.rounds)
    }

    @Test
    fun `anything but the ABC on the minute or in a cap has no other protocol`() {
        assertNull(
            Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 10, kind = BlockKind.Emom).otherAbcProtocol(),
        )
        assertNull(onTheMinute.copy(kind = BlockKind.Sets).otherAbcProtocol())
    }
}
