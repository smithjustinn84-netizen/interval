package dev.justinsmith.interval.history

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.fakes.sparringRecording
import dev.justinsmith.interval.model.ClassRecording
import dev.justinsmith.interval.model.ClassSample
import dev.justinsmith.interval.model.Sport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassDetailViewModelTest : MainDispatcherTest() {
    private val clock = noonOf(TODAY)
    private val started = noonOf(TODAY) - 7_200_000
    private val repository = FakeStorage().repository(now = { clock })

    /** Kept before the page opens: its ViewModel follows midnight for good, which a test's own scope would wait on. */
    private fun keep(sport: Sport, epochDay: Long, recording: ClassRecording) = runTest {
        repository.history.keepClass(sport, epochDay, recording)
    }

    private fun viewModel(startedAt: Long = started) =
        ClassDetailViewModel(startedAt, repository.history, FixedTimestampFormatter, { clock })

    @Test
    fun `a class shows when it was its length and its heart rate`() {
        keep(Sport.Bjj, TODAY, sparringRecording(rounds = 3, startedAtEpochMillis = started))

        val recorded = requireNotNull(viewModel().state.value.recorded)

        assertEquals(Sport.Bjj, recorded.sport)
        assertEquals(HistoryDay.Today, recorded.day)
        assertEquals(FixedTimestampFormatter.time(started), recorded.time)
        // Two easy stretches of ten minutes, and three rounds of five each with its minute after.
        assertEquals("38:00", recorded.length)
        assertEquals(170, recorded.peakBpm)
        assertTrue(requireNotNull(recorded.averageBpm) in 120..150)
    }

    @Test
    fun `each round says its length its heart rate and how far that fell`() {
        keep(Sport.Bjj, TODAY, sparringRecording(rounds = 3, startedAtEpochMillis = started))

        val rounds = requireNotNull(viewModel().state.value.recorded).rounds

        assertEquals(listOf(1, 2, 3), rounds.map { it.number })
        rounds.forEach { round ->
            assertEquals(170, round.peakBpm)
            assertTrue(round.averageBpm in 165..170)
            assertTrue(requireNotNull(round.drop) > 0)
        }
        // The last round's rest runs its whole minute; the ones before are cut by the next round.
        assertEquals(60, rounds.last().dropSeconds)
    }

    @Test
    fun `the line runs from the lowest reading to the highest with a band a round`() {
        keep(Sport.Bjj, TODAY, sparringRecording(rounds = 2, startedAtEpochMillis = started))

        val line = requireNotNull(viewModel().state.value.recorded).line

        assertEquals(0f, line.heights.first())
        assertEquals(1f, line.heights.filterNotNull().max())
        assertEquals(2, line.rounds.size)
        assertTrue(line.rounds.all { it.start in 0f..1f && it.endInclusive in 0f..1f && it.start < it.endInclusive })
    }

    @Test
    fun `a stretch the sensor read nothing in breaks the line`() {
        val samples = List(40) { ClassSample(if (it in 10..12) 0 else 100 + it, movement = 0.2f) }
        keep(Sport.Climbing, TODAY, ClassRecording(started, samples))

        val recorded = requireNotNull(viewModel().state.value.recorded)

        assertEquals(listOf(null, null, null), recorded.line.heights.subList(10, 13))
        assertEquals(emptyList(), recorded.rounds)
    }

    @Test
    fun `deleting takes the recording and leaves the day logged`() {
        repository.history.logSport(Sport.Bjj, TODAY)
        keep(Sport.Bjj, TODAY, sparringRecording(rounds = 3, startedAtEpochMillis = started))
        val viewModel = viewModel()

        viewModel.delete()

        assertNull(viewModel.state.value.recorded)
        assertEquals(emptyList(), repository.history.classes.value)
        assertEquals(1, repository.history.sportDays.value.size)
    }

    @Test
    fun `a class that is not there shows nothing`() {
        assertNull(viewModel(startedAt = 1).state.value.recorded)
    }
}
