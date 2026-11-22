package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.BodyPose
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.KettlebellPoses
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.interval.ui.RepHold
import dev.justinsmith.interval.ui.RepPhase
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Fault

/**
 * The unified kinematic animation ([ExerciseRep]) for this exercise: every built-in has one, and a custom exercise
 * inherits that of its [CustomExercise.figure]. Used identically on the exercise page, active session cards,
 * mobility workouts, and thumbnails.
 */
val Exercise.rep: ExerciseRep?
    get() = builtInReps[(this as? CustomExercise)?.figure ?: this]

/**
 * Which of this lift's written cues (`cues()`, by index) each named [RepPhase] belongs to, so the cue is lit while
 * the rep is in that phase and jumps there on tap. Empty for custom exercises (whose cues are the user's own) and
 * for cue-indexed reps (which carry cue indices directly on their stretches).
 */
val Exercise.repCues: Map<RepPhase, Int>
    get() = (this as? BuiltInExercise)?.let(namedPhaseCues::get).orEmpty()

/**
 * This rep done wrong as [fault] makes it look, or null for a fault that cannot be shown visually.
 * For [Fault.SquattedHinge], replaces standard [KettlebellPoses.Hinge] stances with [KettlebellPoses.SquattedHinge]
 * (preserving far-arm positions), or proportionally squats any other hinged stance while keeping weights off the floor.
 */
fun ExerciseRep.doneWrong(fault: Fault): ExerciseRep? = when (fault) {
    Fault.SquattedHinge -> {
        val hasStandardHinge = keyframes.any { it.pose.matchesHinge() }
        when {
            hasStandardHinge -> copy(
                keyframes = keyframes.map { kf ->
                    if (kf.pose.matchesHinge()) {
                        kf.copy(
                            pose = kf.pose.copy(
                                hipAngleDeg = KettlebellPoses.SquattedHinge.hipAngleDeg,
                                kneeAngleDeg = KettlebellPoses.SquattedHinge.kneeAngleDeg,
                                torsoAngleDeg = KettlebellPoses.SquattedHinge.torsoAngleDeg,
                                farHipAngleDeg = KettlebellPoses.SquattedHinge.farHipAngleDeg,
                                farKneeAngleDeg = KettlebellPoses.SquattedHinge.farKneeAngleDeg,
                            ),
                        )
                    } else {
                        kf
                    }
                },
            )

            keyframes.any { it.pose.torsoAngleDeg > HINGE_FROM } -> {
                val clearance = when (hold) {
                    RepHold.Bar -> BAR_CLEARANCE
                    RepHold.Free -> HAND_CLEARANCE
                    else -> BELL_CLEARANCE
                }
                copy(
                    keyframes = keyframes.map { kf ->
                        val squatted = SQUAT_SHARES.map { kf.pose.squatted(it) }.firstOrNull { candidate ->
                            val j = candidate.joints(root, anchor, facing, hold)
                            j.kettlebellCenter.y >= clearance && j.farBellCenter.y >= clearance
                        } ?: kf.pose
                        kf.copy(pose = squatted)
                    },
                )
            }

            else -> null
        }
    }

    Fault.LateHand, Fault.DeathGrip, Fault.BentWrist -> null
}

private fun BodyPose.matchesHinge(): Boolean =
    hipAngleDeg == KettlebellPoses.Hinge.hipAngleDeg &&
        kneeAngleDeg == KettlebellPoses.Hinge.kneeAngleDeg &&
        torsoAngleDeg == KettlebellPoses.Hinge.torsoAngleDeg

private fun BodyPose.squatted(share: Float): BodyPose {
    val hinge = ((torsoAngleDeg - HINGE_FROM) / (HINGE_FULL - HINGE_FROM)).coerceIn(0f, 1f) * share
    if (hinge == 0f) return this
    val newTorso = torsoAngleDeg - CHEST_UP * hinge
    val thigh = torsoAngleDeg - hipAngleDeg
    val shin = thigh + kneeAngleDeg
    val farThigh = torsoAngleDeg - farHipAngleDeg
    val farShin = farThigh + farKneeAngleDeg
    val newThigh = thigh - HIPS_DOWN * hinge
    val newShin = shin + KNEES_FORWARD * hinge
    val newFarThigh = farThigh - HIPS_DOWN * hinge
    val newFarShin = farShin + KNEES_FORWARD * hinge
    val torsoDelta = newTorso - torsoAngleDeg
    return copy(
        torsoAngleDeg = newTorso,
        hipAngleDeg = newTorso - newThigh,
        kneeAngleDeg = newShin - newThigh,
        farHipAngleDeg = newTorso - newFarThigh,
        farKneeAngleDeg = newFarShin - newFarThigh,
        shoulderAngleDeg = shoulderAngleDeg + torsoDelta,
        farShoulderAngleDeg = farShoulderAngleDeg + torsoDelta,
    )
}

/** The built-ins whose first cue is part of every rep, so their cue-indexed reps loop from the start without set-up. */
internal val loopsFromTheStart: Set<BuiltInExercise> = setOf(
    BuiltInExercise.ArmorBuildingComplex,
    BuiltInExercise.BarbellClean,
    BuiltInExercise.BarbellCleanAndJerk,
    BuiltInExercise.BarbellJerk,
    BuiltInExercise.Burpee,
    BuiltInExercise.Clean,
    BuiltInExercise.CleanAndPress,
    BuiltInExercise.GetUp,
    BuiltInExercise.HalfSnatch,
    BuiltInExercise.HighPull,
    BuiltInExercise.IronCardio,
    BuiltInExercise.Jerk,
    BuiltInExercise.LongCycle,
    BuiltInExercise.OneHandSwing,
    BuiltInExercise.ReverseLunge,
    BuiltInExercise.ShoulderBridge,
    BuiltInExercise.Snatch,
    BuiltInExercise.Teaser,
    BuiltInExercise.TwoHandSwing,
)

private val swingCues = mapOf(
    RepPhase.HipSnap to 1,
    RepPhase.Float to 2,
    RepPhase.Backswing to 2,
    RepPhase.HikePass to 0,
)

private val namedPhaseCues: Map<BuiltInExercise, Map<RepPhase, Int>> = mapOf(
    BuiltInExercise.TwoHandSwing to swingCues,
    BuiltInExercise.OneHandSwing to swingCues,
    BuiltInExercise.Clean to mapOf(RepPhase.Pull to 0, RepPhase.Rack to 1, RepPhase.Drop to 2, RepPhase.HikePass to 2),
    BuiltInExercise.Snatch to mapOf(
        RepPhase.HipSnap to 0,
        RepPhase.HighPull to 0,
        RepPhase.PunchThrough to 1,
        RepPhase.Lockout to 2,
        RepPhase.Drop to 2,
        RepPhase.HikePass to 2,
    ),
    BuiltInExercise.Press to mapOf(RepPhase.Press to 1, RepPhase.Lockout to 2, RepPhase.Lower to 2, RepPhase.Rack to 0),
    BuiltInExercise.GobletSquat to mapOf(
        RepPhase.Descend to 1,
        RepPhase.Bottom to 1,
        RepPhase.Drive to 2,
        RepPhase.Stand to 0,
    ),
    BuiltInExercise.GetUp to mapOf(
        RepPhase.FloorPress to 0,
        RepPhase.RollToElbow to 1,
        RepPhase.TallSit to 1,
        RepPhase.HighBridge to 1,
        RepPhase.SweepToKneeling to 1,
        RepPhase.StandUp to 2,
        RepPhase.StepBack to 2,
        RepPhase.HandDown to 2,
        RepPhase.SweepThrough to 2,
        RepPhase.DownToElbow to 2,
        RepPhase.RollDown to 2,
        RepPhase.LowerBell to 2,
    ),
    BuiltInExercise.CleanAndPress to mapOf(
        RepPhase.Pull to 0,
        RepPhase.Rack to 0,
        RepPhase.Press to 1,
        RepPhase.Lockout to 1,
        RepPhase.Lower to 2,
        RepPhase.Drop to 2,
        RepPhase.HikePass to 2,
    ),
    BuiltInExercise.Deadlift to mapOf(
        RepPhase.HikePass to 0,
        RepPhase.Drive to 1,
        RepPhase.Stand to 1,
        RepPhase.Descend to 2,
    ),
    BuiltInExercise.HighPull to mapOf(
        RepPhase.HipSnap to 0,
        RepPhase.HighPull to 1,
        RepPhase.Drop to 2,
        RepPhase.HikePass to 2,
    ),
    BuiltInExercise.HalfSnatch to mapOf(
        RepPhase.HipSnap to 0,
        RepPhase.HighPull to 0,
        RepPhase.PunchThrough to 0,
        RepPhase.Lockout to 0,
        RepPhase.Lower to 1,
        RepPhase.Rack to 1,
        RepPhase.Drop to 2,
        RepPhase.HikePass to 2,
    ),
    BuiltInExercise.BottomsUpPress to mapOf(
        RepPhase.Rack to 0,
        RepPhase.Press to 1,
        RepPhase.Lockout to 2,
        RepPhase.Lower to 2,
    ),
    BuiltInExercise.PushPress to mapOf(
        RepPhase.Rack to 0,
        RepPhase.Descend to 1,
        RepPhase.Drive to 2,
        RepPhase.Lockout to 2,
        RepPhase.Lower to 0,
    ),
    BuiltInExercise.Jerk to mapOf(
        RepPhase.Rack to 0,
        RepPhase.Descend to 0,
        RepPhase.Drive to 1,
        RepPhase.Lockout to 1,
        RepPhase.Stand to 2,
        RepPhase.Lower to 2,
    ),
    BuiltInExercise.FrontSquat to mapOf(
        RepPhase.Rack to 0,
        RepPhase.Descend to 1,
        RepPhase.Bottom to 1,
        RepPhase.Drive to 2,
        RepPhase.Stand to 0,
    ),
    BuiltInExercise.Thruster to mapOf(
        RepPhase.Rack to 0,
        RepPhase.Descend to 1,
        RepPhase.Bottom to 1,
        RepPhase.Drive to 2,
        RepPhase.Press to 2,
        RepPhase.Lockout to 2,
        RepPhase.Lower to 0,
    ),
    BuiltInExercise.LongCycle to mapOf(
        RepPhase.Pull to 0,
        RepPhase.Rack to 0,
        RepPhase.Descend to 0,
        RepPhase.Drive to 0,
        RepPhase.Lockout to 0,
        RepPhase.Lower to 1,
        RepPhase.Drop to 1,
        RepPhase.HikePass to 1,
    ),
    BuiltInExercise.IronCardio to mapOf(
        RepPhase.Pull to 0,
        RepPhase.Rack to 0,
        RepPhase.Press to 0,
        RepPhase.Lockout to 0,
        RepPhase.Lower to 0,
        RepPhase.Descend to 1,
        RepPhase.Bottom to 1,
        RepPhase.Drive to 1,
        RepPhase.Drop to 2,
        RepPhase.HikePass to 2,
    ),
    BuiltInExercise.ArmorBuildingComplex to mapOf(
        RepPhase.Pull to 1,
        RepPhase.Rack to 1,
        RepPhase.Press to 0,
        RepPhase.Lockout to 0,
        RepPhase.Lower to 0,
        RepPhase.Descend to 0,
        RepPhase.Drive to 0,
        RepPhase.Drop to 2,
        RepPhase.HikePass to 2,
    ),
)

private val builtInReps: Map<Exercise, ExerciseRep> by lazy {
    drawnReps.mapValues { (exercise, rep) ->
        if (rep.phases.isNotEmpty() || exercise in loopsFromTheStart) rep else rep.settingUp()
    }
}

private val drawnReps: Map<Exercise, ExerciseRep> by lazy {
    mapOf(
        BuiltInExercise.TwoHandSwing to KettlebellReps.Swing,
        BuiltInExercise.GobletSquat to KettlebellReps.GobletSquat,
        BuiltInExercise.OneHandSwing to KettlebellReps.OneHandSwing,
        BuiltInExercise.Deadlift to DeadliftRep,
        BuiltInExercise.HighPull to HighPullRep,
        BuiltInExercise.Snatch to KettlebellReps.Snatch,
        BuiltInExercise.HalfSnatch to HalfSnatchRep,
        BuiltInExercise.Clean to KettlebellReps.Clean,
        BuiltInExercise.Press to KettlebellReps.Press,
        BuiltInExercise.BottomsUpPress to BottomsUpPressRep,
        BuiltInExercise.PushPress to PushPressRep,
        BuiltInExercise.Jerk to JerkRep,
        BuiltInExercise.FrontSquat to FrontSquatRep,
        BuiltInExercise.Thruster to ThrusterRep,
        BuiltInExercise.CleanAndPress to KettlebellReps.CleanAndPress,
        BuiltInExercise.LongCycle to LongCycleRep,
        BuiltInExercise.IronCardio to IronCardioRep,
        BuiltInExercise.ArmorBuildingComplex to ArmorBuildingComplexRep,
        BuiltInExercise.RackedLunge to RackedLungeDemo,
        BuiltInExercise.WalkingLunge to WalkingLungeDemo,
        BuiltInExercise.RenegadeRow to RenegadeRowDemo,
        BuiltInExercise.KettlebellDeadBug to KettlebellDeadBugDemo,
        BuiltInExercise.Row to RowDemo,
        BuiltInExercise.Windmill to WindmillDemo,
        BuiltInExercise.Halo to HaloDemo,
        BuiltInExercise.FarmersWalk to FarmersWalkDemo,
        BuiltInExercise.SuitcaseCarry to SuitcaseCarryDemo,
        BuiltInExercise.RackCarry to RackCarryDemo,
        BuiltInExercise.BottomsUpCarry to BottomsUpCarryDemo,
        BuiltInExercise.GetUp to KettlebellReps.GetUp,
        BuiltInExercise.BarbellSnatch to BarbellSnatchDemo,
        BuiltInExercise.BarbellCleanAndJerk to BarbellCleanAndJerkDemo,
        BuiltInExercise.BarbellClean to BarbellCleanDemo,
        BuiltInExercise.BarbellJerk to BarbellJerkDemo,
        BuiltInExercise.BarbellPowerSnatch to BarbellPowerSnatchDemo,
        BuiltInExercise.BarbellPowerClean to BarbellPowerCleanDemo,
        BuiltInExercise.BarbellHangSnatch to BarbellHangSnatchDemo,
        BuiltInExercise.BarbellHangClean to BarbellHangCleanDemo,
        BuiltInExercise.BarbellSnatchPull to BarbellSnatchPullDemo,
        BuiltInExercise.BarbellCleanPull to BarbellCleanPullDemo,
        BuiltInExercise.BarbellOverheadSquat to BarbellOverheadSquatDemo,
        BuiltInExercise.BarbellSnatchBalance to BarbellSnatchBalanceDemo,
        BuiltInExercise.BarbellPushPress to BarbellPushPressDemo,
        BuiltInExercise.BarbellBackSquat to BarbellBackSquatDemo,
        BuiltInExercise.BarbellFrontSquat to BarbellFrontSquatDemo,
        BuiltInExercise.BarbellDeadlift to BarbellDeadliftDemo,
        BuiltInExercise.BarbellRomanianDeadlift to BarbellRomanianDeadliftDemo,
        BuiltInExercise.BarbellBenchPress to BarbellBenchPressDemo,
        BuiltInExercise.BarbellOverheadPress to BarbellOverheadPressDemo,
        BuiltInExercise.BarbellRow to BarbellRowDemo,
        BuiltInExercise.PushUp to PushUpDemo,
        BuiltInExercise.InclinePushUp to InclinePushUpDemo,
        BuiltInExercise.PikePushUp to PikePushUpDemo,
        BuiltInExercise.BenchDip to BenchDipDemo,
        BuiltInExercise.PullUp to PullUpDemo,
        BuiltInExercise.ChinUp to ChinUpDemo,
        BuiltInExercise.InvertedRow to InvertedRowDemo,
        BuiltInExercise.AirSquat to AirSquatDemo,
        BuiltInExercise.SplitSquat to SplitSquatDemo,
        BuiltInExercise.ReverseLunge to ReverseLungeDemo,
        BuiltInExercise.BulgarianSplitSquat to BulgarianSplitSquatDemo,
        BuiltInExercise.StepUp to StepUpDemo,
        BuiltInExercise.GluteBridge to GluteBridgeDemo,
        BuiltInExercise.SingleLegGluteBridge to SingleLegGluteBridgeDemo,
        BuiltInExercise.WallSit to WallSitDemo,
        BuiltInExercise.CalfRaise to CalfRaiseDemo,
        BuiltInExercise.Plank to PlankDemo,
        BuiltInExercise.SidePlank to SidePlankDemo,
        BuiltInExercise.HollowHold to HollowHoldDemo,
        BuiltInExercise.DeadBug to DeadBugDemo,
        BuiltInExercise.Burpee to BurpeeDemo,
        BuiltInExercise.MountainClimber to MountainClimberDemo,
        BuiltInExercise.BandPullApart to BandPullApartDemo,
        BuiltInExercise.BandFacePull to BandFacePullDemo,
        BuiltInExercise.BandSeatedRow to BandSeatedRowDemo,
        BuiltInExercise.BandLatPulldown to BandLatPulldownDemo,
        BuiltInExercise.BandChestPress to BandChestPressDemo,
        BuiltInExercise.BandOverheadPress to BandOverheadPressDemo,
        BuiltInExercise.BandBicepsCurl to BandBicepsCurlDemo,
        BuiltInExercise.BandTricepsPushdown to BandTricepsPushdownDemo,
        BuiltInExercise.BandPallofPress to BandPallofPressDemo,
        BuiltInExercise.BandWoodchop to BandWoodchopDemo,
        BuiltInExercise.BandSquat to BandSquatDemo,
        BuiltInExercise.BandGoodMorning to BandGoodMorningDemo,
        BuiltInExercise.BandGluteBridge to BandGluteBridgeDemo,
        BuiltInExercise.BandLateralWalk to BandLateralWalkDemo,
        BuiltInExercise.BandClamshell to BandClamshellDemo,
        BuiltInExercise.Hundred to HundredDemo,
        BuiltInExercise.RollUp to RollUpDemo,
        BuiltInExercise.SingleLegCircles to SingleLegCirclesDemo,
        BuiltInExercise.RollingLikeABall to RollingLikeABallDemo,
        BuiltInExercise.SingleLegStretch to SingleLegStretchDemo,
        BuiltInExercise.DoubleLegStretch to DoubleLegStretchDemo,
        BuiltInExercise.Scissors to ScissorsDemo,
        BuiltInExercise.CrissCross to CrissCrossDemo,
        BuiltInExercise.SpineStretchForward to SpineStretchForwardDemo,
        BuiltInExercise.Saw to SawDemo,
        BuiltInExercise.Swan to SwanDemo,
        BuiltInExercise.SingleLegKick to SingleLegKickDemo,
        BuiltInExercise.SideKickSeries to SideKickSeriesDemo,
        BuiltInExercise.Teaser to TeaserDemo,
        BuiltInExercise.Swimming to SwimmingDemo,
        BuiltInExercise.ShoulderBridge to ShoulderBridgeDemo,
        BuiltInExercise.Seal to SealDemo,
        BuiltInExercise.LegPullFront to LegPullFrontDemo,
        BuiltInExercise.HipSwitch to HipSwitchDemo,
        BuiltInExercise.HipFlexorLunge to HipFlexorLungeDemo,
        BuiltInExercise.DeepSquat to DeepSquatDemo,
        BuiltInExercise.WorldsGreatestStretch to WorldsGreatestStretchDemo,
        BuiltInExercise.ThreadTheNeedle to ThreadTheNeedleDemo,
        BuiltInExercise.ShoulderCircles to ShoulderCirclesDemo,
        BuiltInExercise.WristCircles to WristCirclesDemo,
        BuiltInExercise.PalmsDownFingersBack to PalmsDownFingersBackDemo,
        BuiltInExercise.BacksOfTheHands to BacksOfTheHandsDemo,
        BuiltInExercise.FingerPulls to FingerPullsDemo,
        BuiltInExercise.ForearmStretch to ForearmStretchDemo,
        BuiltInExercise.CatCow to CatCowDemo,
        BuiltInExercise.ChildsPose to ChildsPoseDemo,
        BuiltInExercise.Cobra to CobraDemo,
        BuiltInExercise.DownwardDog to DownwardDogDemo,
        BuiltInExercise.HalfSplit to HalfSplitDemo,
        BuiltInExercise.ForwardFold to ForwardFoldDemo,
        BuiltInExercise.Pigeon to PigeonDemo,
        BuiltInExercise.LyingTwist to LyingTwistDemo,
        BuiltInExercise.SpinalRock to SpinalRockDemo,
        BuiltInExercise.ShoulderRoll to ShoulderRollDemo,
        BuiltInExercise.SitUp to SitUpDemo,
        BuiltInExercise.CrossVUp to CrossVUpDemo,
        BuiltInExercise.HipEscape to HipEscapeDemo,
        BuiltInExercise.BridgeAndRoll to BridgeAndRollDemo,
        BuiltInExercise.TechnicalStandUp to TechnicalStandUpDemo,
        BuiltInExercise.SitOut to SitOutDemo,
        BuiltInExercise.Butterfly to ButterflyDemo,
        BuiltInExercise.FrogStretch to FrogStretchDemo,
    )
}

private const val HINGE_FROM = 15f
private const val HINGE_FULL = 60f
private const val KNEES_FORWARD = 22f
private const val HIPS_DOWN = 18f
private const val CHEST_UP = 22f
private const val BAR_CLEARANCE = 0.129f
private const val BELL_CLEARANCE = 0.068f
private const val HAND_CLEARANCE = 0.024f
private val SQUAT_SHARES = listOf(1f, 0.75f, 0.5f, 0.25f, 0f)
