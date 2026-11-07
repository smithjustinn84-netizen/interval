package dev.justinsmith.lift.ui.demo

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** What each band lift's figure must show, beyond what every demo keeps to. */
class BandDemosTest {
    @Test
    fun `a band stretches as the lift drives against it`() {
        listOf(
            BandFacePullDemo,
            BandSeatedRowDemo,
            BandLatPulldownDemo,
            BandChestPressDemo,
            BandOverheadPressDemo,
            BandBicepsCurlDemo,
            BandTricepsPushdownDemo,
            BandPallofPressDemo,
            BandWoodchopDemo,
            BandClamshellDemo,
        ).forEach { demo ->
            val band = demo.props.filterIsInstance<Band>().single()
            val rest = length(band, demo.figureAt(0f))
            val worked = length(band, demo.figureAt(demo.still))
            assertTrue(worked > rest + 10f, "The band only goes from $rest to $worked")
        }
    }

    @Test
    fun `a band stood on stays under the foot`() {
        listOf(BandOverheadPressDemo, BandBicepsCurlDemo, BandSquatDemo, BandGoodMorningDemo).forEach { demo ->
            frames(demo).forEach { figure ->
                assertTrue(abs(figure.place(BandEnd.Foot).y - Rig.FLOOR) < 1f, "The band leaves the floor")
            }
        }
    }

    @Test
    fun `the pull apart spreads the hands out to the sides`() {
        val apart = BandPullApartDemo.figureAt(BandPullApartDemo.still)
        assertTrue((apart.hand - apart.shoulder).getDistance() < 20f, "The hands stay out in front at ${apart.hand}")
        val start = BandPullApartDemo.figureAt(0f)
        assertTrue(abs(start.hand.y - start.shoulder.y) < 5f, "The arms start away from shoulder height")
    }

    private fun length(band: Band, figure: Figure) = (figure.place(band.from) - figure.place(band.to)).getDistance()

    private fun frames(demo: ExerciseDemo) = (0..200).map { demo.figureAt(it / 200f) }
}
