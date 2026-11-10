package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A figure's stance at one moment of a rep, as the angles of its joints in degrees, seen from the side with
 * the figure facing right. [shin], [thigh] and [torso] lean from straight up; [upperArm] and [forearm] hang
 * from straight down. Positive is toward the way the figure faces, so a hinge is a negative [thigh] (the
 * hips back) under a positive [torso]. A straight arm has the same [upperArm] and [forearm]; a forearm
 * folded up past the horizontal is over 90.
 *
 * [bell] is where the bell sits from the hand, from straight down like an arm: along the forearm unless said
 * otherwise, so a swung bell hangs past the grip. A bell in the rack or locked out rests on the back of the
 * forearm, and a bottoms-up one stands over the fist (180). It turns as the joints do, so a clean rolls it
 * around the wrist; and a bell that goes right round the hand in a rep, as a snatch's does, ends it a whole
 * turn (360) on from where it started.
 *
 * The far side: [farShin] and [farThigh] are the far leg's, standing on [farFoot] (the foot's pitch, positive
 * with the toes down, as on the back foot of a lunge); [farUpperArm], [farForearm] and [farBell] the far arm's
 * and the bell in it, when it has one. Each is the near side's unless said otherwise. The near foot stands flat
 * unless [foot] pitches it, as on the toes of a calf raise or a push-up.
 *
 * [reach] is how much of the arms' length shows, both arms': all of it (1) unless they point toward the eye or
 * away, as arms spread out to the sides do, seen from the side.
 */
data class Pose(
    val shin: Float,
    val thigh: Float,
    val torso: Float,
    val upperArm: Float,
    val forearm: Float,
    val bell: Float = forearm,
    val farShin: Float = shin,
    val farThigh: Float = thigh,
    val farFoot: Float = 0f,
    val farUpperArm: Float = upperArm,
    val farForearm: Float = forearm,
    val farBell: Float = bell,
    val foot: Float = 0f,
    val reach: Float = 1f,
) {
    internal fun angles(): FloatArray = floatArrayOf(
        shin,
        thigh,
        torso,
        upperArm,
        forearm,
        bell,
        farShin,
        farThigh,
        farFoot,
        farUpperArm,
        farForearm,
        farBell,
        foot,
        reach,
    )

    internal companion object {
        const val JOINTS = 14

        /** The pose whose joints are [angles], in the order [Pose.angles] gives them. */
        fun of(angles: FloatArray): Pose {
            @Suppress("detekt.MagicNumber") // Each joint's place in the array.
            return Pose(
                shin = angles[0],
                thigh = angles[1],
                torso = angles[2],
                upperArm = angles[3],
                forearm = angles[4],
                bell = angles[5],
                farShin = angles[6],
                farThigh = angles[7],
                farFoot = angles[8],
                farUpperArm = angles[9],
                farForearm = angles[10],
                farBell = angles[11],
                foot = angles[12],
                reach = angles[13],
            )
        }
    }
}

/**
 * What the hands hold: [BothHands] one bell between them, the far arm hidden behind the near one; [NearHand]
 * one bell in the near hand, the far arm free; [Pair] a bell in each; [Bar] a barbell, both hands on it, seen
 * end on as the near plate, its sleeve in the hands; [Free] nothing, both arms free.
 */
enum class Hold { BothHands, NearHand, Pair, Bar, Free }

/**
 * What stays put, at the demo's anchor: [Ankle] the near ankle, for a lift done on the spot; [Toe] the near toe,
 * for one up on the toes; [Hand] the near hand, for one hung from a bar or propped on a bench; [Hips] the hips
 * (across), over whatever of the figure is lowest (resting on the floor), for a walk or a lift done lying down.
 */
enum class Root { Ankle, Toe, Hand, Hips }

/** What a stretch of the rep is, which the bell's colour says: setting up, driving the bell, or easing it back. */
enum class DemoBeat { Set, Drive, Ease }

/** A stretch of the rep up to [until] (a share of it), which the exercise's [cue]th cue speaks to. */
data class DemoPhase(val until: Float, val beat: DemoBeat, val cue: Int)

/**
 * What is done once before the reps: the figure holds the rep's first pose for [seconds] while the exercise's
 * [cue]th cue (taking the bell, anchoring the band, lying down) is lit, and the reps loop after it.
 */
data class SetUp(val cue: Int, val seconds: Float = SECONDS) {
    companion object {
        /** Long enough to read a cue's line. */
        const val SECONDS = 1.6f
    }
}

/** The figure's [pose] [at] this share of the rep. */
data class Keyframe(val at: Float, val pose: Pose)

/** Something the figure uses, drawn behind it. */
sealed interface Prop

/** A bench or a box whose top is [height] off the floor from [from] to [to]. */
data class Bench(val from: Float, val to: Float, val height: Float) : Prop

/** A wall standing at [x]. */
data class Wall(val x: Float) : Prop

/** A bar to hang from or pull to, seen end on [at] where it is. */
data class Rail(val at: Offset) : Prop

/** A resistance band stretched from [from] to [to], drawn in the beat's colour as a bell is. */
data class Band(val from: BandEnd, val to: BandEnd) : Prop

/** Where a band is held, stood on or looped round, or tied to a post. */
sealed interface BandEnd {
    /** In the near hand. */
    data object Hand : BandEnd

    /** In the far hand. */
    data object FarHand : BandEnd

    /** Under the near foot, stood on. */
    data object Foot : BandEnd

    /** Round the near knee, or the far one: a loop round both. */
    data object Knee : BandEnd

    data object FarKnee : BandEnd

    /** Across the back of the neck. */
    data object Neck : BandEnd

    /** Tied to a post, standing on the floor, [at] this height. */
    data class Post(val at: Offset) : BandEnd
}

/**
 * One exercise drawn as a figure doing it: [keyframes] from 0 to 1, the last the same as the first (give or
 * take whole turns) so the rep loops; [phases] to 1, each naming one of the exercise's cues. A rep takes
 * [secondsPerRep]; a still picture of it (reduced motion, or a list) shows it at [still]. It is drawn in [view]
 * (in [Rig] units), held as [hold] says and grounded as [root] says at [anchor], with [props] round it.
 *
 * Each joint follows a smooth curve through its keyframes (a monotone cubic, which the loop closes): it keeps
 * its speed through a pose it only passes, comes to rest where it turns round, stays put between two
 * keyframes that hold one pose, and never swings past the poses it moves between.
 *
 * A swung bell hangs from the hands rather than standing on them: it points where it would have [bellLag] of
 * a rep ago, so it trails the arms up and floats on after them. It leaves a trail [trail] of a rep long (none
 * for 0), whose spacing shows its speed.
 *
 * With a [setUp], the demo starts with it before the first rep: a place in the demo from -1 to 0 is setting up
 * (the figure still in the rep's first pose, the set-up's cue lit), from 0 to 1 a rep. No phase of the rep names
 * the set-up's cue, so it is lit only while setting up.
 */
class ExerciseDemo(
    val keyframes: List<Keyframe>,
    val phases: List<DemoPhase>,
    val hold: Hold,
    val secondsPerRep: Float,
    val still: Float,
    val bellLag: Float = 0f,
    val trail: Float = 0f,
    val root: Root = Root.Ankle,
    val view: Rect = Rig.view,
    val props: List<Prop> = emptyList(),
    val anchor: Offset = Offset(Rig.ANKLE_X, Rig.FLOOR),
    val setUp: SetUp? = null,
) {
    init {
        require(keyframes.first().at == 0f && keyframes.last().at == 1f) { "A rep runs from 0 to 1" }
        require(sameStance(keyframes.first().pose, keyframes.last().pose)) { "A rep ends as it starts" }
        require(keyframes.zipWithNext().all { (a, b) -> a.at < b.at }) { "Keyframes out of order" }
        require(phases.last().until >= 1f) { "Phases stop short of the rep's end" }
        require(phases.none { it.cue == setUp?.cue }) { "The set-up's cue is lit in the rep" }
    }

    /** Where the demo can be: from -1 with a set-up, else from the rep's start, to its end. */
    val earliest: Float get() = if (setUp == null) 0f else SETTING_UP

    /**
     * This demo, set up first with its [cue]th cue for [seconds]: each phase of the rep that named that cue gives
     * its stretch to the one before it (the first, to the one after), and a rep left with a set-up's beat in it
     * takes Drive then Ease in turn, as a rep after a set-up does.
     */
    fun settingUp(cue: Int = 0, seconds: Float = SetUp.SECONDS): ExerciseDemo {
        val kept = mutableListOf<DemoPhase>()
        phases.forEach { phase ->
            when {
                phase.cue != cue -> kept += phase
                kept.isNotEmpty() -> kept[kept.lastIndex] = kept.last().copy(until = phase.until)
            }
        }
        val rep = if (kept.none { it.beat == DemoBeat.Set }) {
            kept
        } else {
            kept.mapIndexed { i, phase -> phase.copy(beat = if (i == 0) DemoBeat.Drive else DemoBeat.Ease) }
        }
        return ExerciseDemo(
            keyframes, rep, hold, secondsPerRep, still, bellLag, trail, root, view, props, anchor, SetUp(cue, seconds),
        )
    }

    /** Where the demo is [seconds] after [from], played on: through what is left of the set-up, then rep after rep. */
    fun after(from: Float, seconds: Float): Float {
        val setUp = setUp
        if (from >= 0f || setUp == null) return looped(from.coerceAtLeast(0f) + seconds / secondsPerRep)
        val left = -from * setUp.seconds
        return if (seconds < left) from + seconds / setUp.seconds else looped((seconds - left) / secondsPerRep)
    }

    private val times = FloatArray(keyframes.size) { keyframes[it].at }
    private val angles = keyframes.map { it.pose.angles() }

    /** Each joint's speed at each keyframe, in degrees a rep. */
    private val speeds = keyframes.indices.map { key ->
        // The rep loops, so its first and last keyframes are one moment: the stretch before it is the rep's last.
        val before = if (key == 0) keyframes.lastIndex - 1 else key - 1
        val after = if (key == keyframes.lastIndex) 0 else key
        FloatArray(Pose.JOINTS) { joint ->
            speedThrough(slope(before, joint), span(before), slope(after, joint), span(after))
        }
    }

    /** The pose [progress] (0 to 1) through the rep. */
    fun poseAt(progress: Float): Pose {
        val t = progress.coerceIn(0f, 1f)
        val to = times.indexOfFirst { it >= t }.coerceAtLeast(1)
        val from = to - 1
        val span = span(from)
        val share = (t - times[from]) / span
        return Pose.of(
            FloatArray(Pose.JOINTS) { joint ->
                hermite(
                    from = angles[from][joint],
                    leaving = speeds[from][joint] * span,
                    to = angles[to][joint],
                    arriving = speeds[to][joint] * span,
                    share = share,
                )
            },
        )
    }

    /** The phase [progress] (0 to 1) is in; while setting up, the set-up's. */
    fun phaseAt(progress: Float): DemoPhase {
        val setUp = setUp
        if (progress < 0f && setUp != null) return DemoPhase(until = 0f, beat = DemoBeat.Set, cue = setUp.cue)
        return phases.first { progress < it.until || it === phases.last() }
    }

    /** Each phase with where in the rep it starts, in order: the stretches a timeline draws. */
    val stretches: List<Pair<Float, DemoPhase>>
        get() = phases.mapIndexed { i, phase -> (if (i == 0) 0f else phases[i - 1].until) to phase }

    /** Where the first stretch that [cue] speaks to starts (-1 for the set-up's), or null if none does. */
    fun startOf(cue: Int): Float? =
        if (cue == setUp?.cue) SETTING_UP else stretches.firstOrNull { (_, phase) -> phase.cue == cue }?.first

    /** The figure at [progress], its bells hanging where they were a moment before, or a bar's sleeve in the hands. */
    fun figureAt(progress: Float): Figure {
        val pose = poseAt(progress)
        // Setting up, the figure stands still: nothing swings, so no bell lags.
        val before = if (bellLag == 0f || progress < 0f) pose else poseAt(looped(progress - bellLag))
        val figure = pose.figure(root, anchor, hangs = before.bell, farHangs = before.farBell)
        return when (hold) {
            Hold.Bar, Hold.Free -> figure.copy(bell = figure.hand, farBell = figure.farHand)
            else -> figure
        }
    }

    /** Where the bell was over the last [trail] of a rep before [progress], oldest first; empty with no trail. */
    fun trailAt(progress: Float): List<Offset> {
        val places = FloatArray(2 * TRAIL_STEPS)
        return List(trailInto(progress, places)) { Offset(places[2 * it], places[2 * it + 1]) }
    }

    /**
     * [trailAt] written into [places] as x, y pairs rather than a list, for a figure drawing every frame: how many
     * places it wrote, [TRAIL_STEPS] or none.
     */
    fun trailInto(progress: Float, places: FloatArray): Int {
        if (trail == 0f || progress < 0f) return 0
        for (i in 0 until TRAIL_STEPS) {
            val bell = figureAt(looped(progress - trail * (TRAIL_STEPS - i) / TRAIL_STEPS)).bell
            places[2 * i] = bell.x
            places[2 * i + 1] = bell.y
        }
        return TRAIL_STEPS
    }

    /** The props drawn behind the figure, every one but a bar to hang from, which goes over the fists. */
    internal val propsBehind: List<Prop> = props.filter { it !is Rail }

    internal val rails: List<Rail> = props.filterIsInstance<Rail>()

    internal val bands: List<Band> = props.filterIsInstance<Band>()

    private fun span(stretch: Int): Float = times[stretch + 1] - times[stretch]

    private fun slope(stretch: Int, joint: Int): Float =
        (angles[stretch + 1][joint] - angles[stretch][joint]) / span(stretch)

    companion object {
        /** Where a set-up starts, before the rep's 0. */
        const val SETTING_UP = -1f

        /** How many places a trail remembers. */
        const val TRAIL_STEPS = 9
    }
}

/** The figure's joints in [Rig] units, for drawing: the near side's, then the far side's. */
data class Figure(
    val toe: Offset,
    val heel: Offset,
    val ankle: Offset,
    val knee: Offset,
    val hip: Offset,
    val shoulder: Offset,
    val head: Offset,
    val elbow: Offset,
    val hand: Offset,
    val bell: Offset,
    val farToe: Offset,
    val farHeel: Offset,
    val farAnkle: Offset,
    val farKnee: Offset,
    val farElbow: Offset,
    val farHand: Offset,
    val farBell: Offset,
) {
    /** Every place drawn, to check a figure fits its view and stays off the floor. */
    val joints: List<Offset>
        get() = listOf(
            toe, heel, ankle, knee, hip, shoulder, head, elbow, hand, bell,
            farToe, farHeel, farAnkle, farKnee, farElbow, farHand, farBell,
        )

    internal fun moved(by: Offset) = Figure(
        toe = toe + by,
        heel = heel + by,
        ankle = ankle + by,
        knee = knee + by,
        hip = hip + by,
        shoulder = shoulder + by,
        head = head + by,
        elbow = elbow + by,
        hand = hand + by,
        bell = bell + by,
        farToe = farToe + by,
        farHeel = farHeel + by,
        farAnkle = farAnkle + by,
        farKnee = farKnee + by,
        farElbow = farElbow + by,
        farHand = farHand + by,
        farBell = farBell + by,
    )
}

/**
 * The figure's proportions, in the units of a [view] it is drawn in: the ankle on the floor at [ANKLE_X],
 * [FLOOR], the rest stacked on it.
 */
object Rig {
    const val SHIN = 72f
    const val THIGH = 78f
    const val TORSO = 112f
    const val NECK = 32f

    /** The head tips forward of the torso by this much. */
    const val HEAD_TILT = 8f
    const val UPPER_ARM = 50f
    const val FOREARM = 50f
    const val FOOT = 30f
    const val HEEL = 6f

    /** From the hands on the bell's handle to the middle of its body. */
    const val BELL_REACH = 26f

    const val ANKLE_X = 205f
    const val FLOOR = 360f

    /** The far leg, a step behind the near one, and the far arm, a little behind the near one. */
    const val FAR_LEG_OFFSET = 9f
    const val FAR_ARM_OFFSET = 7f

    const val LEG = 14f
    const val FAR_LEG = 13f
    const val TRUNK = 17f
    const val ARM = 11f
    const val HEAD = 17f
    const val HAND = 7.5f

    /** The bell's body, and its handle: half its width, how far its sides run down to the body, its stroke. */
    const val BELL = 17f
    const val HANDLE_HALF = 9f
    const val HANDLE_SIDES = 12f
    const val HANDLE = 5f

    /** A barbell's plate, seen end on: its radius (a 45 cm plate at this scale), its rim and its sleeve. */
    const val PLATE = 40f
    const val PLATE_RIM = 7f
    const val SLEEVE = 6f

    /** A trail's dots, from the oldest to the one just behind the bell. */
    const val TRAIL_DOT_OLDEST = 2f
    const val TRAIL_DOT_NEWEST = 9f

    const val FLOOR_LINE = 1.5f
    const val SHADOW_WIDTH = 140f
    const val SHADOW_HEIGHT = 12f

    /** The shadow's middle, just under the floor line. */
    const val SHADOW_DROP = 4f

    /** The floor reaches this far either side of the ankle. */
    const val FLOOR_REACH = 121f
    const val FLOOR_REACH_FORWARD = 171f

    /** A bench's top and legs, a wall, and a bar seen end on. */
    const val BENCH_TOP = 8f
    const val BENCH_LEG = 5f
    const val WALL = 6f
    const val RAIL = 8f

    /** A band, and the post it is tied to. */
    const val BAND = 4f
    const val POST = 6f

    /**
     * The box every standing lift is drawn in, so they all share a scale: room for a bell held bottoms-up
     * overhead, and for a lunge's back foot.
     */
    val view = Rect(left = 20f, top = -70f, right = 390f, bottom = 380f)
}

/**
 * Where this pose puts each joint, grounded as [root] says at [anchor]. The bells hang from the hands at [hangs]
 * and [farHangs], which are the pose's own unless the demo says they lag it.
 */
fun Pose.figure(
    root: Root = Root.Ankle,
    anchor: Offset = Offset(Rig.ANKLE_X, Rig.FLOOR),
    hangs: Float = bell,
    farHangs: Float = farBell,
): Figure {
    val ankle = Offset(Rig.ANKLE_X, Rig.FLOOR)
    val knee = ankle.up(Rig.SHIN, shin)
    val hip = knee.up(Rig.THIGH, thigh)
    val shoulder = hip.up(Rig.TORSO, torso)
    val elbow = shoulder.down(Rig.UPPER_ARM * reach, upperArm)
    val hand = elbow.down(Rig.FOREARM * reach, forearm)
    val farKnee = hip.down(Rig.THIGH, -farThigh)
    val farAnkle = farKnee.down(Rig.SHIN, -farShin)
    val nearFoot = Offset(cos(foot.radians()), sin(foot.radians()))
    val farFoot = Offset(cos(farFoot.radians()), sin(farFoot.radians()))
    val farShoulder = shoulder - Offset(Rig.FAR_ARM_OFFSET, 0f)
    val farElbow = farShoulder.down(Rig.UPPER_ARM * reach, farUpperArm)
    val farHand = farElbow.down(Rig.FOREARM * reach, farForearm)
    val figure = Figure(
        toe = ankle + nearFoot * Rig.FOOT,
        heel = ankle - nearFoot * Rig.HEEL,
        ankle = ankle,
        knee = knee,
        hip = hip,
        shoulder = shoulder,
        head = shoulder.up(Rig.NECK, torso - Rig.HEAD_TILT),
        elbow = elbow,
        hand = hand,
        bell = hand.down(Rig.BELL_REACH, hangs),
        farToe = farAnkle + farFoot * Rig.FOOT,
        farHeel = farAnkle - farFoot * Rig.HEEL,
        farAnkle = farAnkle,
        farKnee = farKnee,
        farElbow = farElbow,
        farHand = farHand,
        farBell = farHand.down(Rig.BELL_REACH, farHangs),
    )
    return when (root) {
        Root.Ankle -> figure.moved(anchor - figure.ankle)
        Root.Toe -> figure.moved(anchor - figure.toe)
        Root.Hand -> figure.moved(anchor - figure.hand)
        Root.Hips -> figure.moved(Offset(anchor.x - hip.x, Rig.FLOOR - figure.lowest()))
    }
}

/** Where [end] of a band is on this figure, the far side drawn a step behind as it is. */
internal fun Figure.place(end: BandEnd): Offset = when (end) {
    BandEnd.Hand -> hand
    BandEnd.FarHand -> farHand
    BandEnd.Foot -> Offset((ankle.x + toe.x) / 2, (ankle.y + toe.y) / 2)
    BandEnd.Knee -> knee
    BandEnd.FarKnee -> farKnee + Offset(-Rig.FAR_LEG_OFFSET, 0f)
    BandEnd.Neck -> shoulder
    is BandEnd.Post -> end.at
}

/** Whether a bell at [bell] lies back along the forearm from [elbow] to [hand], rather than past the grip. */
internal fun rests(elbow: Offset, hand: Offset, bell: Offset): Boolean {
    val forearm = hand - elbow
    val reach = bell - hand
    return forearm.x * reach.x + forearm.y * reach.y < 0f
}

/** How far down the figure reaches, its trunk, head and limbs by their thickness and its feet by their soles. */
private fun Figure.lowest(): Float = maxOf(
    maxOf(toe.y, heel.y, farToe.y, farHeel.y),
    maxOf(knee.y, farKnee.y) + Rig.LEG / 2,
    maxOf(hip.y, shoulder.y) + Rig.TRUNK / 2,
    head.y + Rig.HEAD,
    maxOf(elbow.y, farElbow.y) + Rig.ARM / 2,
    maxOf(hand.y, farHand.y) + Rig.HAND,
)

/** Whether two poses are one stance: each joint the same, give or take whole turns. */
internal fun sameStance(a: Pose, b: Pose): Boolean = a.angles().zip(b.angles()).all { (x, y) ->
    val off = abs(x - y) % FULL_TURN
    off < SAME_ANGLE || FULL_TURN - off < SAME_ANGLE
}

private fun Offset.up(length: Float, degrees: Float): Offset {
    val angle = degrees.radians()
    return Offset(x + length * sin(angle), y - length * cos(angle))
}

private fun Offset.down(length: Float, degrees: Float): Offset {
    val angle = degrees.radians()
    return Offset(x + length * sin(angle), y + length * cos(angle))
}

private fun Float.radians(): Float = (this * PI / HALF_TURN).toFloat()

/** [progress] brought into a rep, so a moment before its start is one near the end of the rep before. */
internal fun looped(progress: Float): Float = ((progress % 1f) + 1f) % 1f

/**
 * A joint's speed at a keyframe it reaches at [slopeIn] over [spanIn] and leaves at [slopeOut] over [spanOut]:
 * none where it turns round or rests on either side, else between the two, leaning to the shorter stretch
 * (Fritsch and Carlson's weighted harmonic mean, which is what keeps the curve from overshooting).
 */
private fun speedThrough(slopeIn: Float, spanIn: Float, slopeOut: Float, spanOut: Float): Float {
    if (slopeIn * slopeOut <= 0f) return 0f
    val weightIn = 2 * spanOut + spanIn
    val weightOut = spanOut + 2 * spanIn
    return (weightIn + weightOut) / (weightIn / slopeIn + weightOut / slopeOut)
}

/** The cubic from [from] to [to] that sets off at [leaving] and comes in at [arriving], [share] of the way along. */
private fun hermite(from: Float, leaving: Float, to: Float, arriving: Float, share: Float): Float {
    val squared = share * share
    val cubed = squared * share
    return (2 * cubed - THREE * squared + 1) * from +
        (cubed - 2 * squared + share) * leaving +
        (THREE * squared - 2 * cubed) * to +
        (cubed - squared) * arriving
}

private const val HALF_TURN = 180.0
internal const val FULL_TURN = 360f
private const val SAME_ANGLE = 0.01f
private const val THREE = 3f
