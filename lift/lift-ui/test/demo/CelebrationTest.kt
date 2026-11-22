package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.BodyPose
import dev.justinsmith.interval.ui.Celebration
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.interval.ui.RepHold
import dev.justinsmith.interval.ui.celebrating
import dev.justinsmith.interval.ui.celebration
import dev.justinsmith.interval.ui.nearestTurn
import dev.justinsmith.lift.domain.BuiltInExercise
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CelebrationTest {
    private val reps = BuiltInExercise.entries.associateWith { it.rep!! }

    private val floorDrills = setOf(
        BuiltInExercise.SpinalRock,
        BuiltInExercise.ShoulderRoll,
        BuiltInExercise.SitUp,
        BuiltInExercise.CrossVUp,
        BuiltInExercise.HipEscape,
        BuiltInExercise.BridgeAndRoll,
        BuiltInExercise.TechnicalStandUp,
        BuiltInExercise.SitOut,
        BuiltInExercise.Butterfly,
        BuiltInExercise.FrogStretch,
    )

    @Test
    fun `a lift on its feet raises its load or bows and one lying or hanging or seen from the front gives none`() {
        val none = reps.filterValues { it.celebration == null }.keys
        val lying = setOf(
            BuiltInExercise.GetUp,
            BuiltInExercise.KettlebellDeadBug,
            BuiltInExercise.BarbellBenchPress,
            BuiltInExercise.GluteBridge,
            BuiltInExercise.SingleLegGluteBridge,
            BuiltInExercise.HollowHold,
            BuiltInExercise.DeadBug,
            BuiltInExercise.BandGluteBridge,
            BuiltInExercise.BandClamshell,
        )
        val onTheHands = setOf(
            BuiltInExercise.RenegadeRow,
            BuiltInExercise.PushUp,
            BuiltInExercise.InclinePushUp,
            BuiltInExercise.PikePushUp,
            BuiltInExercise.BenchDip,
            BuiltInExercise.PullUp,
            BuiltInExercise.ChinUp,
            BuiltInExercise.InvertedRow,
            BuiltInExercise.Plank,
            BuiltInExercise.SidePlank,
            BuiltInExercise.MountainClimber,
        )
        val seated = setOf(BuiltInExercise.WallSit, BuiltInExercise.BandSeatedRow)
        val onTheMat = setOf(
            BuiltInExercise.Hundred,
            BuiltInExercise.RollUp,
            BuiltInExercise.SingleLegCircles,
            BuiltInExercise.RollingLikeABall,
            BuiltInExercise.SingleLegStretch,
            BuiltInExercise.DoubleLegStretch,
            BuiltInExercise.Scissors,
            BuiltInExercise.CrissCross,
            BuiltInExercise.SpineStretchForward,
            BuiltInExercise.Saw,
            BuiltInExercise.Swan,
            BuiltInExercise.SingleLegKick,
            BuiltInExercise.SideKickSeries,
            BuiltInExercise.Teaser,
            BuiltInExercise.Swimming,
            BuiltInExercise.ShoulderBridge,
            BuiltInExercise.Seal,
            BuiltInExercise.LegPullFront,
        )
        val stretching = setOf(
            BuiltInExercise.HipSwitch,
            BuiltInExercise.HipFlexorLunge,
            BuiltInExercise.DeepSquat,
            BuiltInExercise.WorldsGreatestStretch,
            BuiltInExercise.ThreadTheNeedle,
            BuiltInExercise.PalmsDownFingersBack,
            BuiltInExercise.BacksOfTheHands,
            BuiltInExercise.CatCow,
            BuiltInExercise.ChildsPose,
            BuiltInExercise.Cobra,
            BuiltInExercise.DownwardDog,
            BuiltInExercise.HalfSplit,
            BuiltInExercise.Pigeon,
            BuiltInExercise.LyingTwist,
        )
        val fromTheFront = setOf(
            BuiltInExercise.WristCircles,
            BuiltInExercise.FingerPulls,
            BuiltInExercise.ForearmStretch,
            BuiltInExercise.Halo,
            BuiltInExercise.Windmill,
            BuiltInExercise.BandPullApart,
            BuiltInExercise.BandLateralWalk,
        )
        assertEquals(lying + onTheHands + seated + onTheMat + stretching + floorDrills + fromTheFront, none)
        reps.forEach { (exercise, rep) ->
            val celebration = rep.celebration ?: return@forEach
            val expected = if (rep.hold == RepHold.Free) Celebration.Bow else Celebration.RaisedBell
            assertEquals(expected, celebration, "$exercise")
        }
    }

    @Test
    fun `the gesture starts and ends where the figure was and holds in the middle`() {
        val at = 0.2f
        val gesture = assertNotNull(KettlebellReps.Swing.celebrating(at))
        val from = KettlebellReps.Swing.poseAt(at)
        assertSamePose(from, gesture.poseAt(0f))
        assertSamePose(gesture.poseAt(0f), gesture.poseAt(1f))
        assertSamePose(gesture.poseAt(0.4f), gesture.poseAt(0.6f))
        assertEquals(Celebration.SECONDS, gesture.seconds)
    }

    @Test
    fun `a figure still setting up celebrates from the rep's first pose`() {
        val airSquat = assertNotNull(BuiltInExercise.AirSquat.rep)
        val gesture = assertNotNull(airSquat.celebrating(ExerciseRep.SETTING_UP))
        assertSamePose(airSquat.poseAt(0f), gesture.poseAt(0f))
    }

    @Test
    fun `a raised bell is overhead and a bow leans forward`() {
        reps.forEach { (exercise, rep) ->
            val gesture = rep.celebrating(rep.still) ?: return@forEach
            val top = gesture.jointsAt(0.5f)
            when (rep.celebration) {
                Celebration.RaisedBell -> {
                    assertTrue(
                        top.kettlebellAnchor.y > top.headCenter.y,
                        "$exercise's hand is at ${top.kettlebellAnchor}, head at ${top.headCenter}",
                    )
                    if (rep.hold != RepHold.NearHand) {
                        assertTrue(top.farBellAnchor.y > top.headCenter.y, "$exercise's far hand")
                    }
                }

                Celebration.Bow -> {
                    val lean = top.shoulder.x - top.hip.x
                    assertTrue(lean > BOW_LEAN, "$exercise leans $lean")
                    assertTrue(top.shoulder.y - top.hip.y < BOW_MAX_HEIGHT, "$exercise stands too tall for a bow")
                }

                null -> error("$exercise")
            }
        }
    }

    @Test
    fun `nearestTurn picks the short way round`() {
        assertEquals(-10f, 40f.nearestTurn(350f))
        assertEquals(-178f, (-178f).nearestTurn(182f))
        assertEquals(182f, 170f.nearestTurn(182f))
    }

    private fun assertSamePose(expected: BodyPose, actual: BodyPose) {
        assertTrue(BodyPose.sameStance(expected, actual), "Expected $expected, got $actual")
        assertTrue(abs(expected.torsoAngleDeg - actual.torsoAngleDeg) < 0.01f)
    }

    private companion object {
        const val BOW_LEAN = 0.14f
        const val BOW_MAX_HEIGHT = 0.25f
    }
}
