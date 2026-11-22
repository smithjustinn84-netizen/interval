package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals

class WeightUnitTest {
    @Test
    fun `kilograms and pounds convert and round trip`() {
        assertEquals(72, WeightUnit.Kg.fromKg(72.4))
        assertEquals(154, WeightUnit.Lb.fromKg(70.0))
        assertEquals(180, WeightUnit.Lb.fromKg(WeightUnit.Lb.toKg(180)))
        assertEquals(70.0, WeightUnit.Kg.toKg(70))
    }
}
