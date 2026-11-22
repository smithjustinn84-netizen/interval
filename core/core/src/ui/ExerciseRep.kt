package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

// A rep as keyframes: the stances it goes through, how long each takes to reach, and what that part of it is called.

/**
 * What a part of a rep is called, for a label that follows it. Screens put each into words; a rep names only the
 * parts worth telling apart.
 */
enum class RepPhase {
    HikePass,
    HipSnap,
    Float,
    Backswing,
    Pull,
    Rack,
    Drop,
    HighPull,
    PunchThrough,
    Lockout,
    Press,
    Lower,
    Descend,
    Bottom,
    Drive,
    Stand,
    FloorPress,
    RollToElbow,
    TallSit,
    HighBridge,
    SweepToKneeling,
    StandUp,
    StepBack,
    HandDown,
    SweepThrough,
    DownToElbow,
    RollDown,
    LowerBell,
}

/** What a stretch of a rep is doing, which colours the weight or mat: setting up, driving effort, or easing back. */
enum class RepBeat { Set, Drive, Ease }

/**
 * What is done once before the reps: the figure holds the rep's first pose for [seconds] while the exercise's
 * [cue]th written cue is lit, and the reps loop after it.
 */
@Immutable
data class RepSetUp(val cue: Int, val seconds: Float = SECONDS) {
    companion object {
        /** Long enough to read a cue's line. */
        const val SECONDS = 1.6f
    }
}

/** Something the figure uses, drawn around it in normalized standing-height coordinates. */
sealed interface RepProp

/** A bench or a box whose top is [height] off the floor from [from] to [to]. */
@Immutable
data class RepBench(val from: Float, val to: Float, val height: Float) : RepProp

/** A wall standing at [x]. */
@Immutable
data class RepWall(val x: Float) : RepProp

/** A bar to hang from or pull to, seen end on [at] where it is. */
@Immutable
data class RepRail(val at: Offset) : RepProp

/** A resistance band stretched from [from] to [to], drawn in the beat's colour as a bell is. */
@Immutable
data class RepBand(val from: RepBandEnd, val to: RepBandEnd) : RepProp

/** Where a band is held, stood on or looped round, or tied to a post. */
sealed interface RepBandEnd {
    data object Hand : RepBandEnd
    data object FarHand : RepBandEnd
    data object Foot : RepBandEnd
    data object Knee : RepBandEnd
    data object FarKnee : RepBandEnd
    data object Neck : RepBandEnd

    /** Tied to a post, standing on the floor, [at] this position in standing-height coordinates. */
    @Immutable
    data class Post(val at: Offset) : RepBandEnd
}

/**
 * One stance of a rep. The figure comes to [pose] from the keyframe before over [weight] of the rep's time, as a
 * share of all its keyframes' weights. It comes to rest there unless it is [passing] through on its way to the next
 * it rests in; two keyframes running with one pose hold it. [easing] is the pace of the whole stretch into a stance
 * from the one last rested in.
 *
 * [phase] is what the stretch that arrives here is called. [beat] colours the effort of the stretch, and [cueIndex]
 * optionally ties the stretch to one of the exercise's written cues.
 *
 * A rep's first keyframe is where it starts, so its weight and phase count for nothing.
 */
@Immutable
data class ExerciseKeyframe(
    val pose: BodyPose,
    val weight: Float = 0f,
    val phase: RepPhase? = null,
    val easing: Easing = StandardEasing,
    val passing: Boolean = false,
    val beat: RepBeat = RepBeat.Drive,
    val cueIndex: Int? = null,
)

/** The stretch of a rep called [phase]: [from] this share of the way through it [until] that one. */
@Immutable
data class PhaseSpan(val phase: RepPhase, val from: Float, val until: Float) {
    /** Where a rep held to show this stretch stands: just inside it, where its own name shows. */
    val start: Float get() = from + INSIDE

    private companion object {
        const val INSIDE = 0.001f
    }
}

/** One contiguous stretch of a rep on the timeline, carrying its [beat], optional [phase], and optional [cueIndex]. */
@Immutable
data class RepSpan(
    val from: Float,
    val until: Float,
    val beat: RepBeat,
    val phase: RepPhase? = null,
    val cueIndex: Int? = null,
) {
    /** Where a rep held to show this stretch stands: just inside it. */
    val start: Float get() = from + INSIDE

    internal fun continuesAs(phase: RepPhase?, beat: RepBeat, cueIndex: Int?, prevEnd: Float): Boolean =
        this.phase == phase && this.beat == beat && this.cueIndex == cueIndex && until == prevEnd

    private companion object {
        const val INSIDE = 0.001f
    }
}

/**
 * A rep as its [keyframes], in order, from the first at 0 to the last at 1, over [seconds] when it runs by itself.
 * The last is in the first's stance (modulo whole turns), so a rep goes round without a seam. [stillAt] is the
 * keyframe it stands at when it mustn't move: its most telling moment.
 */
@Immutable
data class ExerciseRep(
    val keyframes: List<ExerciseKeyframe>,
    val seconds: Float,
    val stillAt: Int = 0,
    val hold: RepHold = RepHold.NearHand,
    val facing: RepFacing = RepFacing.Side,
    val root: RepRoot = RepRoot.Ankle,
    val anchor: Offset = Offset.Zero,
    val props: List<RepProp> = emptyList(),
    val setUp: RepSetUp? = null,
    val bellLag: Float = 0f,
    val showsTrajectory: Boolean = hold != RepHold.Free && facing == RepFacing.Side,
    val showsTrail: Boolean = false,
    val crop: Rect? = null,
    val follows: Boolean = false,
    val continuousLoop: Boolean = false,
    val explicitSpans: List<RepSpan> = emptyList(),
) {
    init {
        require(keyframes.size > 2 && BodyPose.sameStance(keyframes.first().pose, keyframes.last().pose)) {
            "A rep is three keyframes or more, the last in the first's stance"
        }
        require(continuousLoop || (!keyframes.first().passing && !keyframes.last().passing)) {
            "A rep starts and ends at rest"
        }
        require(keyframes.drop(1).all { it.weight > 0f }) { "Every keyframe after the first takes some time to reach" }
        require(stillAt in keyframes.indices) { "A rep stands still at one of its keyframes" }
    }

    /** Where the rep can be: from -1 with a set-up, else from 0, to 1. */
    val earliest: Float get() = if (setUp == null) 0f else SETTING_UP

    /** How far through the rep each keyframe is, from 0 at the first to 1 at the last. */
    private val times: FloatArray = run {
        val whole = keyframes.drop(1).sumOf { it.weight.toDouble() }.toFloat()
        var spent = 0f
        FloatArray(keyframes.size) { index ->
            if (index > 0) spent += keyframes[index].weight
            if (index == keyframes.lastIndex) 1f else spent / whole
        }
    }

    /** The rep cut at each stance it rests in, or one closed spline when [continuousLoop] is set. */
    private val stretches: List<Stretch> = if (continuousLoop) {
        listOf(
            Stretch(
                poses = keyframes.map { it.pose },
                times = times,
                easing = LinearEasing,
                closedLoop = true,
            ),
        )
    } else {
        keyframes.indices.filter { !keyframes[it].passing }.zipWithNext { from, to ->
            Stretch(
                poses = keyframes.subList(from, to + 1).map { it.pose },
                times = times.copyOfRange(from, to + 1),
                easing = keyframes[to].easing,
                closedLoop = false,
            )
        }
    }

    /** The rep's named stretches, in order. Two keyframes running that share a name are one stretch. */
    val phases: List<PhaseSpan> = buildList {
        for (index in 1..keyframes.lastIndex) {
            val phase = keyframes.drop(index).firstNotNullOfOrNull { it.phase } ?: continue
            val last = lastOrNull()
            if (last != null && last.phase == phase && last.until == times[index - 1]) {
                set(lastIndex, last.copy(until = times[index]))
            } else {
                add(PhaseSpan(phase, from = times[index - 1], until = times[index]))
            }
        }
    }

    /** Every timeline stretch of the rep, from [explicitSpans] when supplied or derived from [keyframes]. */
    val spans: List<RepSpan> = if (explicitSpans.isNotEmpty()) {
        explicitSpans
    } else {
        buildList {
            for (index in 1..keyframes.lastIndex) {
                val kf = keyframes[index]
                val phase = keyframes.drop(index).firstNotNullOfOrNull { it.phase }
                val beat = kf.beat
                val cue = kf.cueIndex
                val last = lastOrNull()
                if (last != null && last.continuesAs(phase, beat, cue, times[index - 1])) {
                    set(lastIndex, last.copy(until = times[index]))
                } else {
                    add(
                        RepSpan(
                            from = times[index - 1],
                            until = times[index],
                            beat = beat,
                            phase = phase,
                            cueIndex = cue,
                        ),
                    )
                }
            }
        }
    }

    /**
     * The rep's checkpoints, in order: where one stretch of it hands over to the next, which is the stance the
     * stretch before arrives in (the rack, the lockout). The rep's own start and end are none: nothing is passed
     * there. A scrub along [PhaseTrack] clicks at each.
     */
    val checkpoints: List<Float> = spans.dropLast(1).map { it.until }

    /** Props split by draw order: behind the body, overhead/low rails over fists, and bands. */
    internal val propsBehind: List<RepProp> = props.filter { it !is RepRail }
    internal val rails: List<RepRail> = props.filterIsInstance<RepRail>()
    internal val bands: List<RepBand> = props.filterIsInstance<RepBand>()

    /** How far through the rep it stands when it mustn't move. */
    val still: Float get() = times[stillAt]

    /** How far through the rep the keyframe at [index] is. */
    fun timeOf(index: Int): Float = times[index]

    /**
     * This rep, set up first with its [cue]th cue for [seconds]: each span of the rep that named that cue gives
     * its stretch to the one beside it, and a rep left with a set-up's beat in it takes Drive then Ease in turn.
     */
    fun settingUp(cue: Int = 0, seconds: Float = RepSetUp.SECONDS): ExerciseRep {
        val kept = mutableListOf<RepSpan>()
        spans.forEach { span ->
            when {
                span.cueIndex != cue -> kept += span
                kept.isNotEmpty() -> kept[kept.lastIndex] = kept.last().copy(until = span.until)
            }
        }
        val normalized = if (kept.isEmpty()) {
            listOf(RepSpan(from = 0f, until = 1f, beat = RepBeat.Drive, cueIndex = null))
        } else {
            val rebased = kept.mapIndexed { i, span ->
                span.copy(
                    from = if (i == 0) 0f else kept[i - 1].until,
                    until = if (i == kept.lastIndex) 1f else span.until,
                )
            }
            if (rebased.none { it.beat == RepBeat.Set }) {
                rebased
            } else {
                rebased.mapIndexed { i, span ->
                    span.copy(beat = if (i == 0) RepBeat.Drive else RepBeat.Ease)
                }
            }
        }
        return copy(setUp = RepSetUp(cue, seconds), explicitSpans = normalized)
    }

    /**
     * Where the rep is [elapsedSeconds] after [from], played on: through what is left of set-up, then rep after rep.
     */
    fun after(from: Float, elapsedSeconds: Float): Float {
        val su = setUp
        if (from >= 0f || su == null) return looped(from.coerceAtLeast(0f) + elapsedSeconds / seconds)
        val left = -from * su.seconds
        return if (elapsedSeconds < left) {
            from + elapsedSeconds / su.seconds
        } else {
            looped((elapsedSeconds - left) / seconds)
        }
    }

    /**
     * The stance [progress] of the way through the rep. With a [setUp], negative progress holds the first stance;
     * otherwise a rep goes round, so 1 is 0 again and anything past either end wraps.
     */
    fun poseAt(progress: Float): BodyPose = BodyPose.of(FloatArray(BodyPose.JOINTS).also { anglesAt(progress, it) })

    /**
     * Where the joints are at [progress], respecting [root], [anchor], [facing], [hold], and [bellLag].
     */
    fun jointsAt(progress: Float): BodyJoints {
        val angles = FloatArray(BodyPose.JOINTS).also { anglesAt(progress, it) }
        val lagBell: Float
        val lagFarBell: Float
        if (bellLag > 0f && progress >= 0f) {
            val lagged = FloatArray(BodyPose.JOINTS).also { anglesAt(looped(progress - bellLag), it) }
            lagBell =
                lagged[BodyPose.SHOULDER] - lagged[BodyPose.TORSO] + lagged[BodyPose.ELBOW] + lagged[BodyPose.BELL]
            lagFarBell = lagged[BodyPose.FAR_SHOULDER] - lagged[BodyPose.TORSO] +
                lagged[BodyPose.FAR_ELBOW] + lagged[BodyPose.FAR_BELL]
        } else {
            lagBell = Float.NaN
            lagFarBell = Float.NaN
        }
        val frame = BodyFrame()
        frame.place(angles, root, anchor, facing, hold, lagBell, lagFarBell)
        return frame.joints()
    }

    /**
     * Fills [into] with each joint's angle [progress] of the way through the rep, in [BodyPose.angles]' order:
     * [poseAt] for a diagram that runs, which keeps one array and makes nothing a frame.
     */
    internal fun anglesAt(progress: Float, into: FloatArray) {
        val at = if (progress < 0f && setUp != null) 0f else progress - floor(progress)
        var index = stretches.lastIndex
        while (index > 0 && stretches[index].from > at) index--
        stretches[index].anglesAt(at, into)
    }

    /**
     * What the rep is doing [progress] of the way through it, if that part of it has a name.
     */
    fun phaseAt(progress: Float): RepPhase? {
        if (progress < 0f && setUp != null) return null
        val at = if (progress == 1f) 1f else progress - floor(progress)
        for (index in phases.indices) {
            val span = phases[index]
            if (at <= span.until) return span.phase.takeIf { at > span.from || (at == 0f && span.from == 0f) }
        }
        return null
    }

    /** The [RepSpan] at [progress], or null while setting up. */
    fun spanAt(progress: Float): RepSpan? {
        if (progress < 0f && setUp != null) return null
        val at = if (progress == 1f) 1f else progress - floor(progress)
        for (index in spans.indices) {
            val span = spans[index]
            if (at <= span.until) return span
        }
        return spans.lastOrNull()
    }

    /** The [RepBeat] at [progress]; [RepBeat.Set] while setting up. */
    fun beatAt(progress: Float): RepBeat =
        if (progress < 0f && setUp != null) RepBeat.Set else spanAt(progress)?.beat ?: RepBeat.Drive

    /** The written cue index at [progress], or the set-up's cue while setting up. */
    fun cueAt(progress: Float): Int? =
        if (progress < 0f && setUp != null) setUp.cue else spanAt(progress)?.cueIndex

    /** Where the first stretch for [cue] starts (-1 for set-up), or null if none does. */
    fun startOf(cue: Int): Float? = when {
        cue == setUp?.cue -> SETTING_UP
        else -> spans.firstOrNull { it.cueIndex == cue }?.start
    }

    /**
     * Where the next stretch after [at] starts, cycling round to the first. Uses [phases] when named, else [spans].
     */
    fun stepAfter(at: Float): Float = when {
        at < 0f -> if (phases.isNotEmpty()) phases.first().start else spans.firstOrNull()?.start ?: 0f

        phases.isNotEmpty() ->
            phases.firstOrNull { it.from >= at && !(at == 0f && it.from == 0f) }?.start ?: phases.first().start

        else -> spans.firstOrNull { it.from > at + STEP_SLACK }?.start ?: spans.firstOrNull()?.start ?: at
    }

    /**
     * Where the last stretch to start before [at] starts, cycling round to the last.
     */
    fun stepBefore(at: Float): Float = if (phases.isNotEmpty()) {
        phases.lastOrNull { it.start < at - STEP_SLACK }?.start ?: phases.last().start
    } else {
        spans.lastOrNull { it.start < at - STEP_SLACK }?.start ?: spans.lastOrNull()?.start ?: at
    }

    companion object {
        /** Where a set-up starts, before the rep's 0. */
        const val SETTING_UP = -1f

        /** How near a stretch's start still counts as there, so a step never stays put. */
        private const val STEP_SLACK = 0.001f
    }
}

/**
 * A rep from one stance it rests in to the next, through [poses] at [times]: the first and last are the rests, any
 * between are passed through. [easing] paces the whole of it. When [closedLoop] is true, boundary slopes wrap
 * seamlessly across the 0/1 seam using Fritsch–Carlson's weighted harmonic mean.
 */
private class Stretch(
    poses: List<BodyPose>,
    private val times: FloatArray,
    private val easing: Easing,
    private val closedLoop: Boolean,
) {
    val from = times.first()
    private val to = times.last()
    private val angles = poses.map { it.angles() }

    /** How fast each joint is turning as it goes through each stance: by joint, then by stance. */
    private val slopes = List(BodyPose.JOINTS) { joint ->
        val values = FloatArray(poses.size) { angles[it][joint] }
        if (closedLoop) closedSlopes(values) else openSlopes(values)
    }

    /** Fills [into] with each joint's angle [at] this share of the rep, which is within this stretch. */
    fun anglesAt(at: Float, into: FloatArray) {
        val paced = from + easing.transform((at - from) / (to - from)).coerceIn(0f, 1f) * (to - from)
        var index = times.size - 2
        while (index > 0 && times[index] > paced) index--
        val span = times[index + 1] - times[index]
        val share = (paced - times[index]) / span
        for (joint in 0..<BodyPose.JOINTS) {
            into[joint] = hermite(
                from = angles[index][joint],
                leaving = slopes[joint][index] * span,
                to = angles[index + 1][joint],
                arriving = slopes[joint][index + 1] * span,
                share = share,
            )
        }
    }

    private fun openSlopes(values: FloatArray): FloatArray {
        val rates = FloatArray(values.size - 1) { (values[it + 1] - values[it]) / (times[it + 1] - times[it]) }
        return FloatArray(values.size) { index ->
            if (index == 0 || index == values.lastIndex) return@FloatArray rates[index.coerceAtMost(rates.lastIndex)]
            val before = rates[index - 1]
            val after = rates[index]
            val sinceLast = times[index] - times[index - 1]
            val untilNext = times[index + 1] - times[index]
            val mean = (before * untilNext + after * sinceLast) / (sinceLast + untilNext)
            (sign(before) + sign(after)) * minOf(abs(before), abs(after), abs(mean) / 2)
        }
    }

    private fun closedSlopes(values: FloatArray): FloatArray {
        val last = values.lastIndex
        val spans = FloatArray(last) { times[it + 1] - times[it] }
        val rates = FloatArray(last) { (values[it + 1] - values[it]) / spans[it] }
        return FloatArray(values.size) { index ->
            val before = if (index == 0) last - 1 else index - 1
            val after = if (index == last) 0 else index
            val slopeIn = rates[before]
            val slopeOut = rates[after]
            if (slopeIn * slopeOut <= 0f) return@FloatArray 0f
            val spanIn = spans[before]
            val spanOut = spans[after]
            val weightIn = 2 * spanOut + spanIn
            val weightOut = spanOut + 2 * spanIn
            (weightIn + weightOut) / (weightIn / slopeIn + weightOut / slopeOut)
        }
    }

    @Suppress("detekt.MagicNumber") // Hermite's four cubics, as they are written everywhere.
    private fun hermite(from: Float, leaving: Float, to: Float, arriving: Float, share: Float): Float {
        val squared = share * share
        val cubed = squared * share
        return (2 * cubed - 3 * squared + 1) * from + (cubed - 2 * squared + share) * leaving +
            (3 * squared - 2 * cubed) * to + (cubed - squared) * arriving
    }
}
