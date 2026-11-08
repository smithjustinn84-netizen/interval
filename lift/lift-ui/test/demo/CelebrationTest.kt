package dev.justinsmith.lift.ui.demo

import dev.justinsmith.lift.domain.BuiltInExercise
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CelebrationTest {
    private val demos = BuiltInExercise.entries.associateWith { it.demo!! }

    @Test
    fun `a lift on its feet raises what it holds or bows and one lying hanging or on its hands gives nothing`() {
        val none = demos.filterValues { it.celebration == null }.keys
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
        assertEquals(lying + onTheHands + seated + onTheMat, none)
        demos.forEach { (exercise, demo) ->
            val celebration = demo.celebration ?: return@forEach
            val expected = if (demo.hold == Hold.Free) Celebration.Bow else Celebration.RaisedBell
            assertEquals(expected, celebration, "$exercise")
        }
    }

    @Test
    fun `the gesture starts and ends where the figure was and holds in the middle`() {
        val at = 0.2f
        val gesture = assertNotNull(TwoHandSwingDemo.celebrating(at))
        val from = TwoHandSwingDemo.poseAt(at)
        val start = gesture.poseAt(0f)
        assertSamePose(from.copy(bell = start.bell, farBell = start.farBell), start)
        assertSamePose(gesture.poseAt(0f), gesture.poseAt(1f))
        assertSamePose(gesture.poseAt(0.4f), gesture.poseAt(0.6f))
        // A swung bell is drawn a moment behind the arms: the gesture starts from where it was drawn.
        assertEquals(TwoHandSwingDemo.figureAt(at).bell, gesture.figureAt(0f).bell)
        assertEquals(Celebration.SECONDS, gesture.secondsPerRep)
    }

    @Test
    fun `a figure still setting up celebrates from the rep's first pose`() {
        val gesture = assertNotNull(PressDemo.celebrating(ExerciseDemo.SETTING_UP))
        assertSamePose(PressDemo.poseAt(0f), gesture.poseAt(0f))
    }

    @Test
    fun `a raised bell is overhead and a bow leans forward`() {
        demos.forEach { (exercise, demo) ->
            val gesture = demo.celebrating(demo.still) ?: return@forEach
            val top = gesture.figureAt(0.5f)
            when (demo.celebration) {
                Celebration.RaisedBell -> {
                    assertTrue(top.hand.y < top.head.y, "$exercise's hand is at ${top.hand}, the head at ${top.head}")
                    if (demo.hold != Hold.Bar) assertTrue(top.bell.y < top.head.y, "$exercise's bell is at ${top.bell}")
                    if (demo.hold != Hold.NearHand) assertTrue(top.farHand.y < top.head.y, "$exercise's far hand")
                }

                Celebration.Bow -> {
                    val lean = top.shoulder.x - top.hip.x
                    assertTrue(lean > BOW_LEAN, "$exercise leans $lean")
                    assertTrue(top.hip.y - top.shoulder.y < Rig.TORSO - BOW_DIP, "$exercise stands too tall for a bow")
                }

                null -> error("$exercise")
            }
        }
    }

    @Test
    fun `a bell turned over the hand is raised without turning back`() {
        val lockout = SnatchDemo.keyframes.first { it.pose.bell == KettlebellPoses.RESTING_TURNED }.at
        val gesture = assertNotNull(SnatchDemo.celebrating(lockout))
        val from = gesture.poseAt(0f).bell
        val raised = gesture.poseAt(0.5f).bell
        assertTrue(abs(raised - from) <= HALF_TURN, "The bell turns from $from to $raised")
        assertEquals(KettlebellPoses.RESTING_TURNED, raised)
        assertEquals(-10f, 40f.nearestTurn(350f))
        assertEquals(-178f, (-178f).nearestTurn(182f))
        assertEquals(182f, 170f.nearestTurn(182f))
    }

    @Test
    fun `every celebrating figure stays in its view and on the floor`() {
        demos.forEach { (exercise, demo) ->
            listOf(0f, demo.still, 0.37f).forEach { from ->
                val gesture = demo.celebrating(from) ?: return@forEach
                listOf(0f, 0.15f, 0.5f, 0.85f, 1f).forEach { step ->
                    val at = "$exercise from $from at $step"
                    val figure = gesture.figureAt(step)
                    val drawn = if (demo.hold == Hold.Pair) figure.joints else figure.joints - figure.farBell
                    drawn.forEach { joint -> assertTrue(demo.view.contains(joint), "$at: $joint is outside the view") }
                    demo.props.filterIsInstance<Band>().flatMap { listOf(it.from, it.to) }.forEach { end ->
                        assertTrue(demo.view.contains(figure.place(end)), "$at: the band's $end is outside the view")
                    }
                    drawn.forEach { joint ->
                        assertTrue(joint.y <= Rig.FLOOR + 0.5f, "$at: $joint is through the floor")
                    }
                    val weight = when (demo.hold) {
                        Hold.Bar -> Rig.PLATE
                        Hold.Free -> Rig.HAND
                        else -> Rig.BELL
                    }
                    assertTrue(figure.bell.y + weight <= Rig.FLOOR + 0.5f, "$at: the weight is through the floor")
                    val surfaces =
                        listOf(Rig.FLOOR) + demo.props.filterIsInstance<Bench>().map { Rig.FLOOR - it.height }
                    val resting = drawn.any { joint -> surfaces.any { it - joint.y in -0.5f..Rig.TRUNK } }
                    assertTrue(resting, "$at: the figure floats off the floor")
                    if (demo.root == Root.Ankle) assertEquals(demo.anchor, figure.ankle)
                }
            }
        }
    }

    private fun assertSamePose(expected: Pose, actual: Pose) {
        expected.angles().zip(actual.angles()).forEachIndexed { joint, (a, b) ->
            assertTrue(abs(a - b) < 0.01f, "Joint $joint is $b, not $a")
        }
    }

    private companion object {
        const val HALF_TURN = 180f

        /** A bow brings the shoulder well ahead of the hip and the torso's height down. */
        const val BOW_LEAN = 50f
        const val BOW_DIP = 15f
    }
}
