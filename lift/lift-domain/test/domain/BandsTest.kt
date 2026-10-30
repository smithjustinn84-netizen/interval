package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class BandsTest {
    @Test
    fun `the five starting bands stand until changed and the user's follow by position`() {
        assertEquals(BandLevel.entries.map { it.name }, bands(emptyList()).map { it.id })
        val renamed = Band.of(BandLevel.Medium).copy(name = "Green loop")
        val added = Band("b1", "Travel band", BandColour.Purple, 5)
        val all = bands(listOf(added, renamed))
        assertEquals(listOf("ExtraLight", "Light", "Medium", "Heavy", "ExtraHeavy", "b1"), all.map { it.id })
        assertEquals("Green loop", all[2].name)
        assertEquals(6, nextBandPosition(all))
    }

    @Test
    fun `a band moved is renumbered with the rest and stays put at either end`() {
        val all = bands(emptyList())
        val moved = all.moved("ExtraHeavy", by = -1)
        assertEquals(listOf("ExtraLight", "Light", "Medium", "ExtraHeavy", "Heavy"), moved.map { it.id })
        assertEquals(listOf(0, 1, 2, 3, 4), moved.map { it.position })
        assertEquals(all, all.moved("ExtraLight", by = -1))
        // Moved, its order is kept when read back over the starting five.
        assertEquals(moved.map { it.id }, bands(moved).map { it.id })
    }

    @Test
    fun `a set's band is found by id and one lost stands in as the heaviest`() {
        val all = bands(emptyList())
        assertEquals(Band.of(BandLevel.Light), bandOf("Light", all))
        val lost = bandOf("gone", all)
        assertEquals(Int.MAX_VALUE, lost.position)
        assertEquals("", lost.name)
    }

    @Test
    fun `only what I own counts the body and the floor always`() {
        val bands = bands(emptyList())
        assertEquals(setOf(Equipment.Bodyweight, Equipment.Mat, Equipment.Band), ownedEquipment(emptyList(), bands))
        val archived = bands.map { it.copy(archived = true) }
        assertEquals(
            setOf(Equipment.Bodyweight, Equipment.Mat, Equipment.Kettlebell),
            ownedEquipment(listOf(16.0), archived),
        )
        val owned = ownedEquipment(emptyList(), archived)
        val filters = ExerciseFilters(ownedOnly = true)
        assertEquals(false, filters.matches(BuiltInExercise.Press, owned))
        assertEquals(true, filters.matches(BuiltInExercise.PushUp, owned))
        assertEquals(1, filters.moreCount)
        assertEquals(false, Equipment.Barbell in ownedEquipment(listOf(16.0), bands))
        assertEquals(true, Equipment.Barbell in ownedEquipment(emptyList(), archived, bar = 20.0))
    }
}
