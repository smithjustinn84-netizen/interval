package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.justinsmith.lift.domain.BuiltInExercise
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExerciseDemoTest {
    private val demos = BuiltInExercise.entries.mapNotNull { it.demo }

    @Test
    fun `every built-in lift is drawn`() {
        assertSame(TwoHandSwingDemo, BuiltInExercise.TwoHandSwing.demo)
        BuiltInExercise.entries.forEach { exercise -> assertNotNull(exercise.demo, "$exercise is not drawn") }
    }

    @Test
    fun `a close-up holds the still figure inside the view and is the same demo`() {
        demos.forEach { demo ->
            val close = demo.closeUp()
            val figure = close.figureAt(close.still)
            assertEquals(demo.figureAt(demo.still).joints, figure.joints)
            val drawn = if (demo.hold == Hold.Pair) figure.joints else figure.joints - figure.farBell
            drawn.forEach { joint -> assertTrue(close.view.contains(joint), "$joint is outside the close-up") }
            assertEquals(demo.view.intersect(close.view), close.view, "The close-up reaches past the view")
            assertEquals(demo.view.bottom, close.view.bottom, "The floor is not under it")
        }
    }

    @Test
    fun `a close-up of a figure crouched draws in and one with props keeps its view`() {
        val squat = assertNotNull(BuiltInExercise.DeepSquat.demo)
        assertTrue(squat.closeUp().view.height < squat.view.height * 0.8f)
        demos.filter { it.props.isNotEmpty() }.forEach { assertSame(it, it.closeUp()) }
    }

    @Test
    fun `a framed demo lowers the ceiling to its rep and keeps every moment of it in view`() {
        demos.forEach { demo ->
            val framed = demo.framed()
            (0..100).forEach { step ->
                val figure = framed.figureAt(step / 100f)
                assertEquals(demo.figureAt(step / 100f).joints, figure.joints)
                val drawn = if (demo.hold == Hold.Pair) figure.joints else figure.joints - figure.farBell
                drawn.forEach { joint -> assertTrue(framed.view.contains(joint), "$joint is outside the frame") }
            }
            assertEquals(demo.view.intersect(framed.view), framed.view, "The frame reaches past the view")
            assertEquals(demo.view.bottom, framed.view.bottom, "The floor is not under it")
            assertEquals(demo.view.left..demo.view.right, framed.view.left..framed.view.right)
        }
        // A swing never goes overhead, so it stands taller in its frame; a snatch needs all the room there is.
        assertTrue(TwoHandSwingDemo.framed().view.height < TwoHandSwingDemo.view.height * 0.9f)
        assertTrue(SnatchDemo.framed().view.height > SnatchDemo.view.height * 0.9f)
        demos.filter { it.props.isNotEmpty() }.forEach { assertSame(it, it.framed()) }
    }

    @Test
    fun `a figure with nothing in its hands has a mat under all of its rep and one with a weight has none`() {
        demos.forEach { demo ->
            val mat = demo.mat
            if (demo.facing == Facing.Above) {
                // Seen from above the mat is under the whole figure, not a line along the floor.
                assertNull(mat)
                val under = assertNotNull(demo.matFromAbove)
                (0..20).forEach { step ->
                    val figure = demo.figureAt(step / 20f)
                    listOf(figure.hips, figure.neck, figure.knee, figure.farKnee).forEach {
                        assertTrue(under.contains(it), "It lies at $it, off the mat $under")
                    }
                }
                return@forEach
            }
            assertNull(demo.matFromAbove, "A mat from above under a figure seen from the side")
            if (demo.hold != Hold.Free || demo.props.any { it is Band }) {
                assertNull(mat, "A mat under a figure that holds something")
                return@forEach
            }
            assertNotNull(mat)
            assertTrue(mat.start >= demo.view.left && mat.endInclusive <= demo.view.right, "The mat leaves the view")
            (0..20).forEach { step ->
                val figure = demo.figureAt(step / 20f)
                // Wherever the figure touches down it is on the mat, short of a view that ends first.
                listOf(figure.toe, figure.heel, figure.hand).filter { it.y > Rig.FLOOR - Rig.HAND - 1f }.forEach {
                    assertTrue(
                        it.x in mat.start - 0.5f..mat.endInclusive + 0.5f ||
                            it.x < demo.view.left + Rig.MAT_REACH ||
                            it.x > demo.view.right - Rig.MAT_REACH,
                        "It touches down at $it, off the mat $mat",
                    )
                }
            }
        }
        // One hanging from a bar takes little floor, and still has a mat's length under it.
        val hanging = assertNotNull(PullUpDemo.mat)
        assertTrue(hanging.endInclusive - hanging.start >= Rig.MAT_LENGTH - 0.5f)
    }

    @Test
    fun `a frame follows a figure that lies down and stands up and keeps all of it in sight`() {
        val following = demos.map { it.framed() }.filter { it.follows }
        val getUp = assertNotNull(BuiltInExercise.GetUp.demo).framed()
        assertTrue(getUp.follows, "The get-up keeps a still frame")
        assertTrue(getUp.ceilingAt(0f) > getUp.ceilingAt(getUp.still + 0.25f) + 100f, "It doesn't close in lying")
        listOf(TwoHandSwingDemo, SnatchDemo, PressDemo).forEach { assertFalse(it.framed().follows) }
        following.forEach { demo ->
            (0..100).forEach { step ->
                val at = step / 100f
                val figure = demo.figureAt(at)
                val drawn = if (demo.hold == Hold.Pair) figure.joints else figure.joints - figure.farBell
                drawn.forEach { assertTrue(it.y >= demo.ceilingAt(at), "$it is over the frame at $at") }
            }
        }
    }

    @Test
    fun `the breath loops with the rep and runs on from the set-up`() {
        demos.forEach { demo ->
            assertEquals(demo.breathAt(0f), demo.breathAt(1f), 0.001f)
            assertEquals(0f, demo.breathAt(0f), 0.001f)
            (0..40).forEach { step -> assertTrue(demo.breathAt(demo.earliest + step / 20f) in -1f..1f) }
        }
        // A quick rep takes one breath; a long hold takes several.
        val swing = (1..99).map { TwoHandSwingDemo.breathAt(it / 100f) }
        assertEquals(1, swing.zipWithNext().count { (a, b) -> a > 0f && b <= 0f })
        val plank = (1..99).map { PlankDemo.breathAt(it / 100f) }
        assertTrue(plank.zipWithNext().count { (a, b) -> a > 0f && b <= 0f } >= 1)
        val goblet = assertNotNull(BuiltInExercise.GobletSquat.demo)
        assertTrue(abs(goblet.breathAt(-0.01f)) < 0.05f, "The set-up's breath jumps into the rep's")
    }

    @Test
    fun `the drive pulses as it starts and is spent before the rep is`() {
        // The swing drives from the hike, at the very start of its rep.
        assertEquals(1f, TwoHandSwingDemo.pulseAt(0f))
        assertTrue(TwoHandSwingDemo.pulseAt(0.05f) in 0.2f..0.95f)
        assertEquals(0f, TwoHandSwingDemo.pulseAt(0.5f), "The float pulses")
        assertEquals(0f, TwoHandSwingDemo.pulseAt(0.9f), "The hike back pulses")
        val goblet = assertNotNull(BuiltInExercise.GobletSquat.demo)
        assertEquals(0f, goblet.pulseAt(-0.5f), "The set-up pulses")
        demos.forEach { demo ->
            (0..100).forEach { step ->
                val at = step / 100f
                val pulse = demo.pulseAt(at)
                assertTrue(pulse in 0f..1f)
                if (demo.phaseAt(at).beat != DemoBeat.Drive) assertEquals(0f, pulse, "A pulse off the drive at $at")
            }
        }
    }

    @Test
    fun `the short way between two places goes round the rep's end and never through the set-up`() {
        assertEquals(0.3f, placeBetween(0.2f, 0.4f, 0.5f), 0.001f)
        assertEquals(0.3f, placeBetween(0.4f, 0.2f, 0.5f), 0.001f)
        // From late in the rep to early in it: on through the end, not back through all of it.
        assertEquals(0f, placeBetween(0.9f, 0.1f, 0.5f), 0.001f)
        assertEquals(0.95f, placeBetween(0.9f, 0.1f, 0.25f), 0.001f)
        assertEquals(0.95f, placeBetween(0.1f, 0.9f, 0.75f), 0.001f)
        // Out of the set-up there is one way, forward.
        assertEquals(0.15f, placeBetween(-0.5f, 0.8f, 0.5f), 0.001f)
        listOf(0.2f to 0.4f, 0.9f to 0.1f, -1f to 0.6f, 0.3f to 1f).forEach { (from, to) ->
            assertEquals(from, placeBetween(from, to, 0f), 0.001f)
            assertEquals(looped(to), looped(placeBetween(from, to, 1f)), 0.001f)
        }
    }

    @Test
    fun `a rep ends where it starts so it loops without a jump`() {
        demos.forEach { demo ->
            demo.figureAt(0f).joints.zip(demo.figureAt(1f).joints).forEach { (start, end) ->
                assertTrue((start - end).getDistance() < 0.01f, "The rep ends at $end, not $start")
            }
        }
    }

    @Test
    fun `a keyframe is met exactly`() {
        demos.forEach { demo ->
            demo.keyframes.forEach { key -> assertEquals(key.pose, demo.poseAt(key.at)) }
        }
    }

    @Test
    fun `the figure moves smoothly through the rep`() {
        BuiltInExercise.entries.forEach { exercise ->
            val demo = exercise.demo ?: return@forEach
            // A frame at 120 a second: no faster than a hard-swung bell, about 7 metres a second at this scale.
            val steps = (demo.secondsPerRep * FRAMES_PER_SECOND).toInt()
            (0 until steps).forEach { i ->
                val a = demo.figureAt(i / steps.toFloat()).bell
                val b = demo.figureAt((i + 1) / steps.toFloat()).bell
                assertTrue((a - b).getDistance() < 16f, "$exercise's bell jumps at ${i / steps.toFloat()}")
            }
        }
    }

    @Test
    fun `the bell keeps its speed through a keyframe it only passes`() {
        // The snap is a keyframe on the way up, not a stop: the bell is as quick just after it as just before.
        val snap = TwoHandSwingDemo.keyframes[2].at
        val before = (TwoHandSwingDemo.figureAt(snap).bell - TwoHandSwingDemo.figureAt(snap - 0.01f).bell).getDistance()
        val after = (TwoHandSwingDemo.figureAt(snap + 0.01f).bell - TwoHandSwingDemo.figureAt(snap).bell).getDistance()
        assertTrue(before > 3f && after > 3f, "The bell stalls at the snap: $before then $after")
        assertTrue(abs(before - after) < before / 2, "The bell lurches at the snap: $before then $after")
    }

    @Test
    fun `no joint swings past the poses it moves between`() {
        demos.forEach { demo ->
            val keyed = demo.keyframes.map { it.pose.angles().toList() }
            (0..400).map { demo.poseAt(it / 400f).angles().toList() }.forEach { angles ->
                angles.forEachIndexed { joint, angle ->
                    val low = keyed.minOf { it[joint] }
                    val high = keyed.maxOf { it[joint] }
                    assertTrue(angle in low - 0.01f..high + 0.01f, "Joint $joint reaches $angle, past $low..$high")
                }
            }
        }
    }

    @Test
    fun `a pose held between two keyframes stays put`() {
        val bottom = GobletSquatDemo.figureAt(0.52f)
        val held = GobletSquatDemo.figureAt(0.56f)
        assertTrue((bottom.hip - held.hip).getDistance() < 0.01f, "The squat's bottom drifts")
    }

    @Test
    fun `every pose stays in its view resting on the floor and nothing through it`() {
        BuiltInExercise.entries.forEach { exercise ->
            val demo = exercise.demo ?: return@forEach
            (0..100).forEach { step ->
                val at = "$exercise at ${step / 100f}"
                val figure = demo.figureAt(step / 100f)
                val drawn = if (demo.hold == Hold.Pair) figure.joints else figure.joints - figure.farBell
                drawn.forEach { joint -> assertTrue(demo.view.contains(joint), "$at: $joint is outside the view") }
                demo.props.filterIsInstance<Band>().flatMap { listOf(it.from, it.to) }.forEach { end ->
                    assertTrue(demo.view.contains(figure.place(end)), "$at: the band's $end is outside the view")
                }
                // Seen from above there is no floor to stand on or go through, only the view to stay in.
                if (demo.facing == Facing.Above) return@forEach
                drawn.forEach { joint -> assertTrue(joint.y <= Rig.FLOOR + 0.5f, "$at: $joint is through the floor") }
                val weight = when (demo.hold) {
                    Hold.Bar -> Rig.PLATE
                    Hold.Free -> Rig.HAND
                    else -> Rig.BELL
                }
                assertTrue(figure.bell.y + weight <= Rig.FLOOR + 0.5f, "$at: the weight is through the floor")
                if (demo.root != Root.Hand) {
                    // It rests on the floor, or on the box or bench it stands on.
                    val surfaces =
                        listOf(Rig.FLOOR) + demo.props.filterIsInstance<Bench>().map { Rig.FLOOR - it.height }
                    val resting = drawn.any { joint -> surfaces.any { it - joint.y in -0.5f..Rig.TRUNK } }
                    assertTrue(resting, "$at: the figure floats off the floor")
                }
                if (demo.root == Root.Ankle) assertEquals(demo.anchor, figure.ankle)
            }
        }
    }

    @Test
    fun `a figure seen from the front has a hip and a shoulder each side and its shoulders square to its spine`() {
        val stance =
            Pose(shin = -10f, thigh = -10f, torso = 0f, upperArm = 0f, forearm = 0f, farShin = 10f, farThigh = 10f)
        val tall = stance.figure(facing = Facing.Front)
        assertTrue(tall.front)
        assertEquals(2 * Rig.HIP_HALF, tall.hip.x - tall.farHip.x, 0.01f)
        assertEquals(2 * Rig.SHOULDER_HALF, tall.shoulder.x - tall.farShoulder.x, 0.01f)
        assertEquals(tall.shoulder.y, tall.farShoulder.y, 0.01f)
        assertEquals(tall.hips.x, tall.head.x, 0.01f)
        assertEquals(Rig.FLOOR, tall.farAnkle.y, 0.01f)
        assertTrue(tall.farAnkle.x < tall.farHip.x && tall.ankle.x > tall.hip.x, "The feet aren't apart")
        val bent = stance.copy(torso = -90f).figure(facing = Facing.Front)
        assertEquals(bent.shoulder.x, bent.farShoulder.x, 0.01f)
        assertTrue(bent.shoulder.y < bent.farShoulder.y, "The near shoulder isn't uppermost")
    }

    @Test
    fun `a figure seen from the front lies down when turned and shows less of a leg that points at the eye`() {
        val pose = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f)
        val lying = pose.copy(turn = -90f).figure(root = Root.Hips, facing = Facing.Front)
        assertTrue(lying.head.x < lying.hips.x - 100f && lying.ankle.x > lying.hips.x + 100f, "It isn't lying")
        assertEquals(lying.hip.x, lying.farHip.x, 0.01f)
        assertTrue(lying.hip.y < lying.farHip.y, "The near side isn't uppermost")
        assertTrue(lying.farShoulder.y + Build.SHOULDERS <= Rig.FLOOR + 0.01f, "It lies through the floor")
        val standing = pose.figure(facing = Facing.Front)
        val short = pose.copy(legs = 0.5f, farLegs = 1f, trunk = 0.5f).figure(facing = Facing.Front)
        assertEquals((standing.hip - standing.ankle).getDistance() / 2, (short.hip - short.ankle).getDistance(), 0.01f)
        val farLeg = (standing.farHip - standing.farAnkle).getDistance()
        assertEquals(farLeg, (short.farHip - short.farAnkle).getDistance(), 0.01f)
        assertEquals((standing.neck - standing.hips).getDistance() / 2, (short.neck - short.hips).getDistance(), 0.01f)
    }

    @Test
    fun `a figure seen from above lies with its hips or its planted foot where the demo says`() {
        val pose = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f, turn = -90f)
        val anchor = Offset(200f, 250f)
        assertEquals(anchor, pose.figure(Root.Hips, anchor, facing = Facing.Above).hips)
        assertEquals(anchor, pose.figure(Root.Ankle, anchor, facing = Facing.Above).ankle)
    }

    @Test
    fun `a straight spine keeps the waist and chest on the line from the hips to the shoulders`() {
        demos.forEach { demo ->
            (0..20).map { demo.figureAt(it / 20f) }.filter { it.spine == 0f && !it.front }.forEach { figure ->
                val line = figure.shoulder - figure.hip
                assertEquals(figure.hip + line * Build.WAIST_AT, figure.waist())
                assertEquals(figure.hip + line * Build.CHEST_AT + figure.front() * Build.CHEST_FORWARD, figure.chest())
            }
        }
    }

    @Test
    fun `a rounded back bows away from the chest and lying on it stays on the mat`() {
        val straight = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f)
        val round = straight.copy(spine = 30f).figure()
        assertTrue(round.waist().x < straight.figure().waist().x - 10f, "The back bows to ${round.waist()}")
        assertTrue(straight.copy(spine = -20f).figure().waist().x > straight.figure().waist().x + 5f)
        listOf(RollingLikeABallDemo, SealDemo, SpinalRockDemo, RollUpDemo).forEach { demo ->
            (0..40).map { demo.figureAt(it / 40f) }.forEach { figure ->
                assertTrue(figure.waist().y + Build.WAIST <= Rig.FLOOR + 0.5f, "The back sinks into the mat")
            }
        }
    }

    @Test
    fun `hands round behind a figure seen from the front are drawn behind it`() {
        val pose = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f)
        assertFalse(pose.figure(facing = Facing.Front).behind)
        assertTrue(pose.copy(behind = 0.8f).figure(facing = Facing.Front).behind)
        assertFalse(pose.copy(behind = 0.8f).figure().front)
    }

    @Test
    fun `the swing floats the bell to chest height and hikes it behind the knees`() {
        val float = TwoHandSwingDemo.figureAt(TwoHandSwingDemo.still)
        val reach = abs(float.bell.y - float.shoulder.y)
        assertTrue(reach < 12f, "The bell floats to ${float.bell}, the shoulder at ${float.shoulder}")
        val hike = TwoHandSwingDemo.figureAt(0f)
        assertTrue(hike.bell.x < hike.knee.x, "The hiked bell is in front of the knee")
    }

    @Test
    fun `a swung bell lags the hands and a held one does not`() {
        val rising = 0.2f
        val rigid = TwoHandSwingDemo.poseAt(rising).figure()
        assertTrue(
            TwoHandSwingDemo.figureAt(rising).bell.y > rigid.bell.y,
            "The bell is level with the arms on the way up",
        )
        val held = GobletSquatDemo.poseAt(rising).figure()
        assertEquals(held.bell, GobletSquatDemo.figureAt(rising).bell)
    }

    @Test
    fun `the swing's bell leaves a trail that ends just behind it`() {
        val trail = TwoHandSwingDemo.trailAt(0.3f)
        assertEquals(ExerciseDemo.TRAIL_STEPS, trail.size)
        val bell = TwoHandSwingDemo.figureAt(0.3f).bell
        val gaps = (trail + bell).zipWithNext { a, b -> (a - b).getDistance() }
        assertTrue(gaps.all { it > 0f && it < 30f }, "The trail's steps are $gaps")
        assertTrue(GobletSquatDemo.trailAt(0.3f).isEmpty(), "A held bell leaves a trail")
    }

    @Test
    fun `a trail at the start of a rep reaches back into the last one`() {
        val trail = TwoHandSwingDemo.trailAt(0.02f)
        val lastRep = TwoHandSwingDemo.figureAt(1f - TwoHandSwingDemo.trail + 0.02f).bell
        assertTrue((lastRep - trail.first()).getDistance() < 0.5f, "The trail starts at ${trail.first()}, not $lastRep")
    }

    @Test
    fun `the goblet squat sits the hips below the knees and keeps the bell at the chest`() {
        val bottom = GobletSquatDemo.figureAt(GobletSquatDemo.still)
        assertTrue(bottom.hip.y > bottom.knee.y, "The hips stop above the knees")
        (0..20).map { GobletSquatDemo.figureAt(it / 20f) }.forEach { figure ->
            assertTrue(figure.bell.y < figure.hip.y, "The bell drops below the hips")
            assertTrue(figure.bell.x > figure.shoulder.x, "The bell sits behind the chest")
        }
    }

    @Test
    fun `a cue's stretch starts where the figure is doing what it says`() {
        demos.forEach { demo ->
            demo.phases.map { it.cue }.distinct().forEach { cue ->
                val start = assertNotNull(demo.startOf(cue))
                assertEquals(cue, demo.phaseAt(start).cue)
            }
            assertEquals(null, demo.startOf(CUES_NO_DEMO_NAMES))
        }
    }

    @Test
    fun `a lift is set up once unless its first cue is part of every rep`() {
        BuiltInExercise.entries.forEach { exercise ->
            val setUp = exercise.demo?.setUp
            if (exercise in loopsFromTheStart) {
                assertNull(setUp, "$exercise sets up")
            } else {
                assertEquals(0, setUp?.cue, "$exercise doesn't set up with its first cue")
            }
        }
    }

    @Test
    fun `setting up holds the rep's first pose with only the set-up lit`() {
        demos.filter { it.setUp != null }.forEach { demo ->
            val setUp = assertNotNull(demo.setUp)
            listOf(-1f, -0.5f, -0.01f).forEach { at ->
                assertEquals(demo.figureAt(0f).hip, demo.figureAt(at).hip)
                assertEquals(setUp.cue, demo.phaseAt(at).cue)
                assertEquals(DemoBeat.Set, demo.phaseAt(at).beat)
                assertTrue(demo.trailAt(at).isEmpty(), "A trail while setting up")
            }
            assertTrue(demo.phases.none { it.cue == setUp.cue }, "The rep lights the set-up's cue")
            assertTrue(demo.phases.none { it.beat == DemoBeat.Set }, "The rep sets up again")
            assertEquals(ExerciseDemo.SETTING_UP, demo.startOf(setUp.cue))
        }
    }

    @Test
    fun `played on a demo sets up once then loops its reps`() {
        val goblet = assertNotNull(BuiltInExercise.GobletSquat.demo)
        val setUp = assertNotNull(goblet.setUp)
        assertEquals(-0.5f, goblet.after(-1f, setUp.seconds / 2), 0.001f)
        assertEquals(0.25f, goblet.after(-1f, setUp.seconds + goblet.secondsPerRep / 4), 0.001f)
        assertEquals(0.25f, goblet.after(0.75f, goblet.secondsPerRep / 2), 0.001f)
        assertEquals(0.1f, TwoHandSwingDemo.after(0f, TwoHandSwingDemo.secondsPerRep * 0.1f), 0.001f)
    }

    @Test
    fun `a set-up takes the rep's set-up stretches into the stretches beside them`() {
        val goblet = GobletSquatDemo.settingUp()
        assertEquals(listOf(1, 2), goblet.phases.map { it.cue })
        assertEquals(listOf(0.6f, 1f), goblet.phases.map { it.until })
        val held = PlankDemo.settingUp()
        assertEquals(listOf(DemoBeat.Drive, DemoBeat.Ease), held.phases.map { it.beat })
    }

    @Test
    fun `the stretches run end to end over the whole rep`() {
        demos.forEach { demo ->
            val stretches = demo.stretches
            assertEquals(0f, stretches.first().first)
            stretches.zipWithNext().forEach { (a, b) -> assertEquals(a.second.until, b.first) }
            assertTrue(stretches.last().second.until >= 1f)
        }
    }

    @Test
    fun `the phases cover the rep and name the cues in their order`() {
        assertEquals(listOf(1, 2, 0), listOf(0.1f, 0.5f, 0.9f).map { TwoHandSwingDemo.phaseAt(it).cue })
        assertEquals(listOf(0, 1, 2, 0), listOf(0.02f, 0.4f, 0.8f, 0.99f).map { GobletSquatDemo.phaseAt(it).cue })
        demos.forEach { demo -> assertEquals(demo.phases.last(), demo.phaseAt(1f)) }
    }

    @Test
    fun `the beat colour glides across a phase change and settles on the new beat`() {
        val colors = FigureColors(
            body = Color.White,
            farLeg = Color.Gray,
            floor = Color.DarkGray,
            shadow = Color.Black,
            set = Color.Yellow,
            drive = Color.Red,
            ease = Color.Green,
        )
        // TwoHandSwing switches from Drive (Red) to Ease (Green) at 0.26f.
        val boundary = TwoHandSwingDemo.phases.first().until
        val blended = colors.at(TwoHandSwingDemo, boundary + 0.02f)
        assertTrue(blended != Color.Red && blended != Color.Green, "Expected blend, got $blended")
        assertTrue(blended.red > 0.1f && blended.green > 0.1f, "Expected mix of red and green, got $blended")
        assertEquals(Color.Green, colors.at(TwoHandSwingDemo, boundary + 0.1f))
        val goblet = assertNotNull(BuiltInExercise.GobletSquat.demo)
        assertEquals(Color.Yellow, colors.at(goblet, -0.5f))
    }
}

private const val FRAMES_PER_SECOND = 120

/** No exercise has this many cues. */
private const val CUES_NO_DEMO_NAMES = 99
