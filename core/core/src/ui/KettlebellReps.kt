package dev.justinsmith.interval.ui

// The kettlebell lifts as keyframes: the stances each rests in or passes through (KettlebellPoses, GetUpPoses) and
// the reps put together from them (KettlebellReps). The angles are worked out so that what should touch does: a
// shin upright over the foot, a knee, a hand or an elbow on the floor, the bell's arm plumb.

/** The stances a kettlebell lift done standing rests in or passes through, which a rep is put together from. */
object KettlebellPoses {
    /**
     * The hike pass, the bottom of the hinge: the hips sent back under a trunk at half a right angle, the shins
     * upright, the arms on the ribs and the bell behind the knees.
     */
    val Hinge = BodyPose(hipAngleDeg = 70f, kneeAngleDeg = 26f, torsoAngleDeg = 46f, shoulderAngleDeg = 2f)

    /** The hips through: standing tall already, the arm only now leaving the body. A swing passes through it. */
    val Drive = BodyPose(shoulderAngleDeg = 24f)

    /** The top of a swing: a standing plank, the bell floating at chest height at the end of a long arm. */
    val Floating = BodyPose(shoulderAngleDeg = 84f, elbowAngleDeg = 6f)

    /**
     * The pull of a clean: the elbow pinned back by the ribs and half bent, so the bell comes straight up the
     * body rather than swinging out round the elbow. A clean passes through it, and a snatch on its way down.
     */
    val Pull = BodyPose(shoulderAngleDeg = -22f, elbowAngleDeg = 78f, bellAngleDeg = -50f)

    /** The rack: the elbow tucked, the forearm upright, the fist under the chin and the bell on the forearm. */
    val Rack = BodyPose(shoulderAngleDeg = 14f, elbowAngleDeg = 160f, bellAngleDeg = -170f)

    /**
     * Halfway up a press: the elbow as high as the shoulder and the forearm tipped back over it, so the bell goes
     * up past the face, over the feet, rather than out ahead of them. A press passes through it.
     */
    val Press = BodyPose(shoulderAngleDeg = 100f, elbowAngleDeg = 110f, bellAngleDeg = -225f)

    /**
     * Locked out overhead: the arm straight up beside the ear, the bell lying on the back of the forearm, and the
     * wrist, elbow, shoulder, hip and ankle on one plumb line.
     */
    val Lockout = BodyPose(shoulderAngleDeg = Angles.HALF_TURN, bellAngleDeg = LOCKED_OUT_BELL)

    /** A snatch leaving the hinge: standing tall, the elbow already drawing back. A snatch passes through it. */
    val SnatchPull = BodyPose(shoulderAngleDeg = -30f, elbowAngleDeg = 60f, bellAngleDeg = -20f)

    /**
     * The high pull: the elbow high and back, the fist by the shoulder and the bell just ahead of it, weightless.
     * A snatch passes through it.
     */
    val HighPull = BodyPose(shoulderAngleDeg = -80f, elbowAngleDeg = 165f, bellAngleDeg = -15f)

    /**
     * The punch through: the elbow come under the fist, which has not left the shoulder, and the bell gone round
     * to the back of the forearm. From here the arm only has to straighten. A snatch passes through it.
     */
    val Turnover = BodyPose(shoulderAngleDeg = 60f, elbowAngleDeg = 170f, bellAngleDeg = LOCKED_OUT_BELL)

    /** Standing with the bell held by its horns at the chest, the elbows down. */
    val Goblet = BodyPose(shoulderAngleDeg = 22f, elbowAngleDeg = 148f, bellAngleDeg = -170f)

    /**
     * The bottom of a goblet squat: sat between the heels, the hips below the knees and the trunk still nearly
     * upright, the knees gone forward over the toes and the elbows down inside them.
     */
    val GobletBottom = BodyPose(
        hipAngleDeg = 120f,
        kneeAngleDeg = 130f,
        torsoAngleDeg = 20f,
        shoulderAngleDeg = 52f,
        elbowAngleDeg = 140f,
        bellAngleDeg = -172f,
    )

    /**
     * This stance half kneeling: the near foot planted with its shin upright and its thigh level, the far knee
     * down under the hip with its shin along the floor behind, and the far arm hanging.
     */
    fun BodyPose.halfKneeling(): BodyPose = copy(
        hipAngleDeg = Angles.QUARTER_TURN,
        kneeAngleDeg = Angles.QUARTER_TURN,
        farHipAngleDeg = 0f,
        farKneeAngleDeg = Angles.QUARTER_TURN,
        farShoulderAngleDeg = 0f,
        farElbowAngleDeg = 0f,
    )
}

/**
 * The stances of a get-up, from the floor to standing. The near foot is planted from first to last, the far hand
 * from the roll to the elbow until the trunk comes up over the knee, and the bell's arm is plumb throughout,
 * whatever the trunk under it does. Seen from the side it is flattened: the roll and the sweep really go across
 * the body, which a profile can only show as going along it.
 */
object GetUpPoses {
    /** On the back, the near knee bent and its foot flat, the bell at the chest on an upright forearm. */
    val Floor = getUp(torso = -90f, hip = 47.5f, knee = 102.6f, farHip = -1.1f, farKnee = 4f, farShoulder = -5.6f)
        .copy(shoulderAngleDeg = 0f, elbowAngleDeg = Angles.QUARTER_TURN, bellAngleDeg = -175f)

    /** The floor press: the bell pressed straight up over the shoulder. */
    val Pressed = getUp(torso = -90f, hip = 47.5f, knee = 102.6f, farHip = -1.1f, farKnee = 4f, farShoulder = -5.6f)

    /** Rolled up onto the far elbow, the forearm flat on the floor. */
    val Elbow = getUp(
        torso = -62.4f,
        hip = 75.1f,
        knee = 102.6f,
        farHip = 26.5f,
        farKnee = 4f,
        farShoulder = -48.8f,
        farElbow = 76.4f,
    )

    /** The tall sit: up onto the hand, the far arm straight and the chest proud. */
    val TallSit = getUp(
        torso = -10.9f,
        hip = 126.6f,
        knee = 102.6f,
        farHip = 78f,
        farKnee = 4f,
        farShoulder = -21.6f,
        farElbow = 18.2f,
    )

    /** The hips leaving the floor, the trunk tipping back over the hand. The bridge passes through it. */
    val Rising = getUp(
        torso = -42.2f,
        hip = 87f,
        knee = 111f,
        farHip = 50.3f,
        farKnee = 25.6f,
        farShoulder = -34.2f,
        farElbow = 18.1f,
    )

    /** The hips most of the way up. The bridge passes through it. */
    val Risen = getUp(
        torso = -66.3f,
        hip = 39.8f,
        knee = 114.5f,
        farHip = 15.3f,
        farKnee = 28.4f,
        farShoulder = -57.2f,
        farElbow = 18.3f,
    )

    /** The high bridge: the hips driven up off the floor between the planted foot, the far heel and the hand. */
    val Bridge = getUp(
        torso = -80.3f,
        hip = 4.1f,
        knee = 107.4f,
        farHip = -17f,
        farKnee = 10.4f,
        farShoulder = -76.7f,
        farElbow = 18.1f,
    )

    /** The sweep begun: the far heel drawn in under the bridge. The sweep passes through it. */
    val Drawn = Bridge.copy(farHipAngleDeg = 4.2f, farKneeAngleDeg = 99.9f)

    /** The sweep halfway: the far leg folded and passing under the hips. The sweep passes through it. */
    val Tucked = getUp(
        torso = -81.8f,
        hip = 2.4f,
        knee = 104.5f,
        farHip = -46.8f,
        farKnee = 135f,
        farShoulder = -76.2f,
        farElbow = 18.1f,
    )

    /** The sweep done: the far knee down under the hip, the hand still on the floor. */
    val Swept = getUp(
        torso = -80.2f,
        hip = 7f,
        knee = 105.3f,
        farHip = -80.2f,
        farKnee = Angles.QUARTER_TURN,
        farShoulder = -73f,
        farElbow = 18.1f,
    )

    /** Half kneeling: the trunk come up over the knee, the hand off the floor. */
    val Kneeling = getUp(torso = 0f, hip = 87.2f, knee = 105.3f, farKnee = Angles.QUARTER_TURN)

    /** Standing, the bell locked out overhead. */
    val Standing = getUp(torso = 0f)

    /**
     * A stance of the get-up under a trunk leaning [torso]: the bell's arm straight and plumb, so raised from the
     * trunk by as much as the trunk leans back, with the bell on the back of its forearm.
     */
    private fun getUp(
        torso: Float,
        hip: Float = 0f,
        knee: Float = 0f,
        farHip: Float = 0f,
        farKnee: Float = 0f,
        farShoulder: Float = 0f,
        farElbow: Float = 0f,
    ) = BodyPose(
        hipAngleDeg = hip,
        kneeAngleDeg = knee,
        torsoAngleDeg = torso,
        shoulderAngleDeg = Angles.HALF_TURN + torso,
        bellAngleDeg = LOCKED_OUT_BELL,
        farHipAngleDeg = farHip,
        farKneeAngleDeg = farKnee,
        farShoulderAngleDeg = farShoulder,
        farElbowAngleDeg = farElbow,
    )
}

/**
 * The kettlebell lifts as reps. Each starts where a set of it does and comes back there, so it goes round without
 * a seam; the weights are the lift's own rhythm, a snap quick and a lowering slow, with a hold where one is held.
 */
object KettlebellReps {
    /**
     * A swing: the hips snap out of the hike pass, then the bell floats at the chest; it falls back to the ribs,
     * and only then do the hips fold. The hips go back and forward and never down, which is what makes it a
     * hinge. The way down mirrors the way up, so the bell comes back along one arc.
     */
    val Swing = ExerciseRep(
        keyframes = listOf(
            from(KettlebellPoses.Hinge),
            through(KettlebellPoses.Drive, weight = 0.17f, RepPhase.HipSnap),
            to(KettlebellPoses.Floating, weight = 0.25f, RepPhase.Float),
            through(KettlebellPoses.Drive, weight = 0.345f, RepPhase.Backswing),
            to(KettlebellPoses.Hinge, weight = 0.235f, RepPhase.HikePass),
        ),
        seconds = 1.8f,
        stillAt = 2,
    )

    /**
     * A clean: out of the hike pass the elbow stays by the ribs and the bell is pulled straight up the body into
     * the rack, held a moment, then dropped back the way it came.
     */
    val Clean = ExerciseRep(
        keyframes = listOf(
            from(KettlebellPoses.Hinge),
            through(KettlebellPoses.Pull, weight = 0.18f, RepPhase.Pull),
            to(KettlebellPoses.Rack, weight = 0.14f, RepPhase.Rack),
            to(KettlebellPoses.Rack, weight = 0.26f, RepPhase.Rack),
            through(KettlebellPoses.Pull, weight = 0.18f, RepPhase.Drop),
            to(KettlebellPoses.Hinge, weight = 0.24f, RepPhase.HikePass),
        ),
        seconds = 2.6f,
        stillAt = 3,
    )

    /**
     * A snatch: the hips snap, the elbow pulls high and back, the fist punches up through the bell and the arm
     * locks out stacked over the shoulder, hip and ankle. After a pause it comes down close, past the ribs.
     */
    val Snatch = ExerciseRep(
        keyframes = listOf(
            from(KettlebellPoses.Hinge),
            through(KettlebellPoses.SnatchPull, weight = 0.13f, RepPhase.HipSnap),
            through(KettlebellPoses.HighPull, weight = 0.08f, RepPhase.HighPull),
            through(KettlebellPoses.Turnover, weight = 0.08f, RepPhase.PunchThrough),
            to(KettlebellPoses.Lockout, weight = 0.1f, RepPhase.PunchThrough),
            to(KettlebellPoses.Lockout, weight = 0.23f, RepPhase.Lockout),
            through(KettlebellPoses.Turnover, weight = 0.1f, RepPhase.Drop),
            through(KettlebellPoses.Pull, weight = 0.1f, RepPhase.Drop),
            to(KettlebellPoses.Hinge, weight = 0.18f, RepPhase.HikePass),
        ),
        seconds = 3.2f,
        stillAt = 5,
    )

    /** A press, standing: from the rack up past the face to the lockout, held, and pulled back down to the rack. */
    val Press = press { this }

    /** A press half kneeling: the same arm over a knee down, which leaves the trunk nothing to lean with. */
    val HalfKneelingPress = with(KettlebellPoses) { press { halfKneeling() } }

    /**
     * A goblet squat: down slowly between the heels with the trunk upright and the elbows inside the knees, a
     * moment at the bottom, then driven up to stand with the hips right through. The hips go down, not back: the
     * hinge's opposite.
     */
    val GobletSquat = ExerciseRep(
        keyframes = listOf(
            from(KettlebellPoses.Goblet),
            to(KettlebellPoses.GobletBottom, weight = 0.36f, RepPhase.Descend),
            to(KettlebellPoses.GobletBottom, weight = 0.1f, RepPhase.Bottom),
            to(KettlebellPoses.Goblet, weight = 0.26f, RepPhase.Drive),
            to(KettlebellPoses.Goblet, weight = 0.28f, RepPhase.Stand),
        ),
        seconds = 4.2f,
        stillAt = 2,
    )

    /**
     * A get-up, a stance at a time and each held a moment: pressed from the floor, rolled to the elbow, up to
     * the hand, bridged, the leg swept under to half kneeling and stood up; then every step back down again.
     */
    val GetUp = ExerciseRep(
        keyframes = listOf(
            from(GetUpPoses.Floor),
            to(GetUpPoses.Pressed, weight = 1.2f, RepPhase.FloorPress),
            to(GetUpPoses.Elbow, weight = 1.4f, RepPhase.RollToElbow),
            to(GetUpPoses.TallSit, weight = 1.2f, RepPhase.TallSit),
            through(GetUpPoses.Rising, weight = 0.4f),
            through(GetUpPoses.Risen, weight = 0.4f),
            to(GetUpPoses.Bridge, weight = 0.4f, RepPhase.HighBridge),
            through(GetUpPoses.Drawn, weight = 0.5f),
            through(GetUpPoses.Tucked, weight = 0.5f),
            to(GetUpPoses.Swept, weight = 0.5f),
            to(GetUpPoses.Kneeling, weight = 1.2f, RepPhase.SweepToKneeling),
            to(GetUpPoses.Standing, weight = 1.4f, RepPhase.StandUp),
            to(GetUpPoses.Standing, weight = 0.8f, RepPhase.StandUp),
            to(GetUpPoses.Kneeling, weight = 1.4f, RepPhase.StepBack),
            to(GetUpPoses.Swept, weight = 1.2f, RepPhase.HandDown),
            through(GetUpPoses.Tucked, weight = 0.5f),
            through(GetUpPoses.Drawn, weight = 0.5f),
            through(GetUpPoses.Bridge, weight = 0.4f),
            through(GetUpPoses.Risen, weight = 0.3f),
            through(GetUpPoses.Rising, weight = 0.3f),
            to(GetUpPoses.TallSit, weight = 0.3f, RepPhase.SweepThrough),
            to(GetUpPoses.Elbow, weight = 1.2f, RepPhase.DownToElbow),
            to(GetUpPoses.Pressed, weight = 1.4f, RepPhase.RollDown),
            to(GetUpPoses.Floor, weight = 1.2f, RepPhase.LowerBell),
        ),
        seconds = 20f,
        stillAt = 6,
    )

    /** A clean and press: from the hinge to the rack, pressed to the lockout, and back the way it came. */
    val CleanAndPress = ExerciseRep(
        keyframes = listOf(
            from(KettlebellPoses.Hinge),
            through(KettlebellPoses.Pull, weight = 0.16f, RepPhase.Pull),
            to(KettlebellPoses.Rack, weight = 0.1f, RepPhase.Rack),
            to(KettlebellPoses.Rack, weight = 0.06f, RepPhase.Rack),
            through(KettlebellPoses.Press, weight = 0.13f, RepPhase.Press),
            to(KettlebellPoses.Lockout, weight = 0.13f, RepPhase.Press),
            to(KettlebellPoses.Lockout, weight = 0.08f, RepPhase.Lockout),
            through(KettlebellPoses.Press, weight = 0.12f, RepPhase.Lower),
            to(KettlebellPoses.Rack, weight = 0.12f, RepPhase.Lower),
            through(KettlebellPoses.Pull, weight = 0.09f, RepPhase.Drop),
            to(KettlebellPoses.Hinge, weight = 0.15f, RepPhase.HikePass),
        ),
        seconds = 4.6f,
        stillAt = 6,
    )

    /** A press with each of its stances stood as [stance] has it. */
    private fun press(stance: BodyPose.() -> BodyPose) = ExerciseRep(
        keyframes = listOf(
            from(KettlebellPoses.Rack.stance()),
            through(KettlebellPoses.Press.stance(), weight = 0.2f, RepPhase.Press),
            to(KettlebellPoses.Lockout.stance(), weight = 0.18f, RepPhase.Press),
            to(KettlebellPoses.Lockout.stance(), weight = 0.14f, RepPhase.Lockout),
            through(KettlebellPoses.Press.stance(), weight = 0.2f, RepPhase.Lower),
            to(KettlebellPoses.Rack.stance(), weight = 0.2f, RepPhase.Lower),
            to(KettlebellPoses.Rack.stance(), weight = 0.08f, RepPhase.Rack),
        ),
        seconds = 3.6f,
        stillAt = 3,
    )

    /** The keyframe a rep starts from. */
    private fun from(pose: BodyPose) = ExerciseKeyframe(pose)

    /** A keyframe the rep comes to rest in. */
    private fun to(pose: BodyPose, weight: Float, phase: RepPhase? = null) = ExerciseKeyframe(pose, weight, phase)

    /** A keyframe the rep passes through. */
    private fun through(pose: BodyPose, weight: Float, phase: RepPhase? = null) =
        ExerciseKeyframe(pose, weight, phase, passing = true)
}

/** How the bell lies on the back of the forearm once the arm is locked out. */
private const val LOCKED_OUT_BELL = -205f
