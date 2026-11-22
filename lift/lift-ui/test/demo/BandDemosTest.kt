package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import dev.justinsmith.interval.ui.BodyJoints
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.RepBand
import dev.justinsmith.interval.ui.RepBandEnd
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** What each band lift's rep must show, beyond what every rep keeps to. */
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
        ).forEach { rep ->
            val band = rep.props.filterIsInstance<RepBand>().single()
            val rest = length(band, rep.jointsAt(0f))
            val worked = length(band, rep.jointsAt(rep.still))
            assertTrue(worked > rest + 0.03f, "The band only goes from $rest to $worked")
        }
    }

    @Test
    fun `a band stood on stays under the foot`() {
        listOf(BandOverheadPressDemo, BandBicepsCurlDemo, BandSquatDemo, BandGoodMorningDemo).forEach { rep ->
            frames(rep).forEach { joints ->
                assertTrue(abs(place(joints, RepBandEnd.Foot).y - 0.03f) < 0.03f, "The band leaves the floor")
            }
        }
    }

    @Test
    fun `the pull apart spreads the hands out to the sides`() {
        val apart = BandPullApartDemo.jointsAt(BandPullApartDemo.still)
        assertTrue(
            apart.kettlebellAnchor.x - apart.farBellAnchor.x > 0.6f,
            "Hands stay narrow at ${apart.kettlebellAnchor}",
        )
        val start = BandPullApartDemo.jointsAt(0f)
        assertTrue(
            start.kettlebellAnchor.x - start.farBellAnchor.x < 0.35f,
            "Hands start wide at ${start.kettlebellAnchor}",
        )
        assertTrue(abs(start.kettlebellAnchor.y - start.shoulder.y) < 0.05f, "Arms start away from shoulder height")
    }

    @Test
    fun `the lateral walk lifts each foot in turn against the band`() {
        val steps = frames(BandLateralWalkDemo)
        assertTrue(steps.any { it.ankle.y > 0.06f }, "The near foot never leaves the floor")
        assertTrue(steps.any { it.farAnkle.y > 0.06f }, "The far foot never leaves the floor")
        val band = BandLateralWalkDemo.props.filterIsInstance<RepBand>().single()
        assertTrue(
            steps.maxOf { length(band, it) } > steps.minOf { length(band, it) } + 0.04f,
            "The band never stretches",
        )
    }

    private fun place(j: BodyJoints, end: RepBandEnd): Offset = when (end) {
        RepBandEnd.Hand -> j.kettlebellAnchor
        RepBandEnd.FarHand -> j.farBellAnchor
        RepBandEnd.Foot -> Offset((j.ankle.x + j.toe.x) / 2f, (j.ankle.y + j.toe.y) / 2f)
        RepBandEnd.Knee -> j.knee
        RepBandEnd.FarKnee -> j.farKnee
        RepBandEnd.Neck -> j.neck
        is RepBandEnd.Post -> end.at
    }

    private fun length(band: RepBand, j: BodyJoints) = (place(j, band.from) - place(j, band.to)).getDistance()

    private fun frames(rep: ExerciseRep) = (0..200).map { rep.jointsAt(it / 200f) }
}
