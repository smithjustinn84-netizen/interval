package dev.justinsmith.interval.nav

import androidx.navigation3.runtime.NavKey
import dev.justinsmith.interval.nav.About
import dev.justinsmith.interval.nav.Licenses
import dev.justinsmith.interval.navConfiguration
import dev.justinsmith.lift.nav.ExerciseLibrary
import dev.justinsmith.lift.nav.LiftDetail
import dev.justinsmith.lift.nav.LiftTab
import dev.justinsmith.lift.nav.Lifting
import dev.justinsmith.lift.nav.OwnedBells
import dev.justinsmith.lift.nav.ProgramEditor
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
            TimerTab,
            LiftTab,
            HistoryTab,
            Settings,
            Workout(1_700_000_000_000),
            About,
            Licenses,
            WorkoutDetail(7),
            Plan,
            Presets,
            Lifting,
            Programs,
            ProgramEditor(),
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
    fun `a back stack saved before Lift's package was renamed still restores`() {
        val saved = """{"type":"dev.justinsmith.bells.nav.LiftDetail","uid":"b1","fromHistory":true}"""
        assertEquals<NavKey>(LiftDetail("b1", fromHistory = true), json.decodeFromString(serializer, saved))
        val tab = """{"type":"dev.justinsmith.bells.nav.LiftTab"}"""
        assertEquals<NavKey>(LiftTab, json.decodeFromString(serializer, tab))
    }
}
