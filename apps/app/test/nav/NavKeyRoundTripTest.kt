package dev.justinsmith.interval.nav

import androidx.navigation3.runtime.NavKey
import dev.justinsmith.interval.nav.About
import dev.justinsmith.interval.nav.FlowEditor
import dev.justinsmith.interval.nav.Licenses
import dev.justinsmith.interval.nav.MobilityTab
import dev.justinsmith.interval.navConfiguration
import dev.justinsmith.interval.rootAtToday
import dev.justinsmith.interval.timerUnderTrain
import dev.justinsmith.lift.nav.ExerciseLibrary
import dev.justinsmith.lift.nav.LiftDetail
import dev.justinsmith.lift.nav.LiftTab
import dev.justinsmith.lift.nav.Lifting
import dev.justinsmith.lift.nav.OwnedBells
import dev.justinsmith.lift.nav.ProgramEditor
import dev.justinsmith.lift.nav.ProgramSheet
import dev.justinsmith.lift.nav.Programs
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Saving the back stack serializes each key as a [NavKey]. A route missing its `subclass(...)` in
 * `navConfiguration` fails here, on every platform, rather than only at runtime on iOS and desktop.
 * JSON with the same serializers module rather than `encodeToSavedState`: on Android a SavedState is a
 * Bundle, which local tests cannot create.
 */
class NavKeyRoundTripTest {
    private val serializer = PolymorphicSerializer(NavKey::class)
    private val json = Json { serializersModule = navConfiguration.serializersModule }

    @Test
    fun `every route survives saving and restoring`() {
        val routes = listOf<NavKey>(
            TodayTab,
            Timer,
            LiftTab,
            ConditioningTab,
            MobilityTab,
            HistoryTab,
            Settings,
            Workout(1_700_000_000_000),
            Workout(1_700_000_000_001, flow = "hips-shoulders"),
            About,
            Licenses,
            WorkoutDetail(7),
            Plan,
            WeekSetup,
            FlowEditor(),
            FlowEditor(id = 3),
            FlowEditor(copyOf = "hips-shoulders"),
            Presets,
            Lifting,
            Programs,
            ProgramEditor(),
            ProgramSheet("built-in:GripAndPull"),
            ProgramEditor("p1"),
            OwnedBells,
            ExerciseLibrary,
            LiftDetail("b1"),
            LiftDetail("b1", fromHistory = true),
        )
        for (route in routes) {
            assertEquals(route, json.decodeFromString(serializer, json.encodeToString(serializer, route)))
        }
    }

    @Test
    fun `a back stack saved when the timer was the first tab gets today under it`() {
        val saved = mutableListOf<NavKey>(Timer, HistoryTab, Plan)
        saved.rootAtToday()
        assertEquals(listOf<NavKey>(TodayTab, Timer, HistoryTab, Plan), saved)

        // One that has it already is left as it is.
        saved.rootAtToday()
        assertEquals(listOf<NavKey>(TodayTab, Timer, HistoryTab, Plan), saved)

        val fresh = mutableListOf<NavKey>(TodayTab)
        fresh.rootAtToday()
        assertEquals(listOf<NavKey>(TodayTab), fresh)
    }

    @Test
    fun `the timer saved as a tab restores as the timer`() {
        val saved = """{"type":"dev.justinsmith.interval.nav.TimerTab"}"""
        assertEquals<NavKey>(Timer, json.decodeFromString(serializer, saved))
    }

    @Test
    fun `a workout saved before there were flows restores as the timer's own`() {
        val saved = """{"type":"dev.justinsmith.interval.nav.Workout","id":7}"""
        assertEquals<NavKey>(Workout(7), json.decodeFromString(serializer, saved))
    }

    @Test
    fun `a back stack saved with the timer as a tab gets conditioning under it`() {
        assertEquals(listOf<NavKey>(TodayTab, ConditioningTab, Timer), underTrain(TodayTab, Timer))
        // Settings beside or over it stays over it.
        assertEquals(
            listOf<NavKey>(TodayTab, ConditioningTab, Timer, Settings),
            underTrain(TodayTab, Timer, Settings),
        )
        assertEquals(
            listOf<NavKey>(TodayTab, ConditioningTab, Timer, Workout(7)),
            underTrain(TodayTab, Timer, Workout(7)),
        )
    }

    @Test
    fun `the presets page pushed from the timer becomes conditioning under it`() {
        // Presets was the page on top: the Timer it was pushed from now sits over what Presets became.
        assertEquals(listOf<NavKey>(TodayTab, ConditioningTab, Timer), underTrain(TodayTab, Timer, Presets))
    }

    @Test
    fun `a timer left under another tab goes`() {
        // The root of a build older still, with History chosen over it: nothing shows the Timer there.
        val saved = mutableListOf<NavKey>(Timer, HistoryTab, Plan)
        saved.rootAtToday()
        saved.timerUnderTrain()
        assertEquals(listOf<NavKey>(TodayTab, HistoryTab, Plan), saved)
    }

    @Test
    fun `a back stack with the timer where it lives is left as it is`() {
        assertEquals(
            listOf<NavKey>(TodayTab, ConditioningTab, Timer, Settings),
            underTrain(TodayTab, ConditioningTab, Timer, Settings),
        )
        assertEquals(listOf<NavKey>(TodayTab, LiftTab, Lifting), underTrain(TodayTab, LiftTab, Lifting))
        assertEquals(listOf<NavKey>(TodayTab), underTrain(TodayTab))
        // Twice over changes nothing more.
        val twice = underTrain(TodayTab, Timer).toMutableList()
        twice.timerUnderTrain()
        assertEquals(listOf<NavKey>(TodayTab, ConditioningTab, Timer), twice)
    }

    private fun underTrain(vararg keys: NavKey): List<NavKey> {
        val stack = mutableListOf<NavKey>()
        stack.addAll(keys)
        stack.timerUnderTrain()
        return stack
    }

    @Test
    fun `a back stack saved before Lift's package was renamed still restores`() {
        val saved = """{"type":"dev.justinsmith.bells.nav.LiftDetail","uid":"b1","fromHistory":true}"""
        assertEquals<NavKey>(LiftDetail("b1", fromHistory = true), json.decodeFromString(serializer, saved))
        val tab = """{"type":"dev.justinsmith.bells.nav.LiftTab"}"""
        assertEquals<NavKey>(LiftTab, json.decodeFromString(serializer, tab))
    }
}
