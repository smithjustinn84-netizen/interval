package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryTest {
    private val plank = CustomExercise("p", "Copenhagen plank", Equipment.Bodyweight, Measure.Time, true, Focus.Core)

    @Test
    fun `any choice within a facet matches and every facet must`() {
        val bodyOrBand = ExerciseFilters(equipment = setOf(Equipment.Bodyweight, Equipment.Band))
        assertTrue(bodyOrBand.matches(BuiltInExercise.PushUp))
        assertTrue(bodyOrBand.matches(BuiltInExercise.BandPullApart))
        assertFalse(bodyOrBand.matches(BuiltInExercise.Press))
        val coreHolds = ExerciseFilters(
            equipment = setOf(Equipment.Bodyweight),
            focus = setOf(Focus.Core),
            measure = setOf(Measure.Time),
        )
        assertEquals(
            listOf(BuiltInExercise.Plank, BuiltInExercise.SidePlank, BuiltInExercise.HollowHold),
            BuiltInExercise.entries.filter(coreHolds::matches),
        )
    }

    @Test
    fun `only yours leaves the exercises the user added`() {
        val yours = ExerciseFilters(yoursOnly = true)
        assertTrue(yours.matches(plank))
        assertFalse(yours.matches(BuiltInExercise.Plank))
    }

    @Test
    fun `the filters button counts choices besides equipment`() {
        val filters = ExerciseFilters(
            equipment = setOf(Equipment.Mat),
            focus = setOf(Focus.Core, Focus.FullBody),
            yoursOnly = true,
        )
        assertEquals(3, filters.moreCount)
        assertFalse(filters.isEmpty)
        assertTrue(ExerciseFilters().isEmpty)
        assertEquals(setOf(Focus.FullBody), setOf(Focus.Core, Focus.FullBody).toggled(Focus.Core))
        assertEquals(setOf(Focus.Core), emptySet<Focus>().toggled(Focus.Core))
    }

    @Test
    fun `search ignores case and spaces and hyphens`() {
        assertTrue(nameMatches("Push-up", "pushup"))
        assertTrue(nameMatches("Push-up", "PUSH UP"))
        assertTrue(nameMatches("Bulgarian split squat", "split sq"))
        assertTrue(nameMatches("Plank", ""))
        assertFalse(nameMatches("Plank", "planks"))
    }

    @Test
    fun `recents are the last done first once each and no more than four`() {
        val offered = offeredExercises(listOf(plank))
        fun set(exercise: Exercise) = SetLog(0, exercise, 0.0, 1, null, 0)
        val sets = listOf(
            BuiltInExercise.Press,
            BuiltInExercise.PushUp,
            plank,
            BuiltInExercise.Press,
            BuiltInExercise.Hundred,
            UnknownExercise("Gone", Equipment.Mat, Measure.Reps),
            BuiltInExercise.Burpee,
        ).map(::set)
        assertEquals(
            listOf(BuiltInExercise.Burpee, BuiltInExercise.Hundred, BuiltInExercise.Press, plank),
            recentExercises(sets, offered),
        )
    }

    @Test
    fun `an archived exercise of yours is not offered`() {
        val archived = plank.copy(uid = "a", archived = true)
        val offered = offeredExercises(listOf(plank, archived))
        assertEquals(BuiltInExercise.entries.size + 1, offered.size)
        assertEquals(plank, offered.last())
    }

    @Test
    fun `a name of yours is taken by another of yours whatever its case`() {
        val taken = takenNames(listOf(plank), uid = null)
        assertEquals(setOf("copenhagen plank"), taken)
        assertFalse(ExerciseDraft().canSave(" COPENHAGEN plank ", taken))
        assertFalse(ExerciseDraft().canSave("   ", taken))
        assertTrue(ExerciseDraft().canSave("Copenhagen plank long lever", taken))
        // Changing it, its own name is free.
        assertTrue(ExerciseDraft().canSave("Copenhagen plank", takenNames(listOf(plank), uid = "p")))
    }
}
