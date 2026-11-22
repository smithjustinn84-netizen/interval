package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
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

/**
 * One stance of a rep. The figure comes to [pose] from the keyframe before over [weight] of the rep's time, as a
 * share of all its keyframes' weights, so a snap is a small weight and a slow lowering a large one. It comes to
 * rest there unless it is [passing] through on its way to the next it rests in; two keyframes running with one
 * pose hold it. [easing] is the pace of the whole stretch into a stance from the one last rested in, however many
 * are passed through on the way; a stance passed through has no pace of its own.
 *
 * [phase] is what the stretch that arrives here is called. One left unnamed is part of the next that is named.
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
)

/** The stretch of a rep called [phase]: [from] this share of the way through it [until] that one. */
@Immutable
data class PhaseSpan(val phase: RepPhase, val from: Float, val until: Float)

/**
 * A rep as its [keyframes], in order, from the first at 0 to the last at 1, over [seconds] when it runs by itself.
 * The last is the first again, so a rep goes round without a seam. [stillAt] is the keyframe it stands at when it
 * mustn't move: its most telling moment.
 *
 * Between two stances it rests in, each joint's angle runs along a smooth curve through the stances passed on the
 * way, one that never swings past them: a joint turns no further than a stance says.
 */
@Immutable
data class ExerciseRep(val keyframes: List<ExerciseKeyframe>, val seconds: Float, val stillAt: Int = 0) {
    init {
        require(keyframes.size > 2 && keyframes.first().pose == keyframes.last().pose) {
            "A rep is three keyframes or more, the last in the first's stance"
        }
        require(!keyframes.first().passing && !keyframes.last().passing) { "A rep starts and ends at rest" }
        require(keyframes.drop(1).all { it.weight > 0f }) { "Every keyframe after the first takes some time to reach" }
        require(stillAt in keyframes.indices) { "A rep stands still at one of its keyframes" }
    }

    /** How far through the rep each keyframe is, from 0 at the first to 1 at the last. */
    private val times: FloatArray = run {
        val whole = keyframes.drop(1).sumOf { it.weight.toDouble() }.toFloat()
        var spent = 0f
        FloatArray(keyframes.size) { index ->
            if (index > 0) spent += keyframes[index].weight
            if (index == keyframes.lastIndex) 1f else spent / whole
        }
    }

    /** The rep cut at each stance it rests in. */
    private val stretches: List<Stretch> =
        keyframes.indices.filter { !keyframes[it].passing }.zipWithNext { from, to ->
            Stretch(
                poses = keyframes.subList(from, to + 1).map { it.pose },
                times = times.copyOfRange(from, to + 1),
                easing = keyframes[to].easing,
            )
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

    /** How far through the rep it stands when it mustn't move. */
    val still: Float get() = times[stillAt]

    /** How far through the rep the keyframe at [index] is. */
    fun timeOf(index: Int): Float = times[index]

    /**
     * The stance [progress] of the way through the rep. A rep goes round, so 1 is 0 again and anything past
     * either end wraps.
     */
    fun poseAt(progress: Float): BodyPose {
        val at = progress - floor(progress)
        return stretches.last { it.from <= at }.poseAt(at)
    }

    /**
     * What the rep is doing [progress] of the way through it, if that part of it has a name. A stretch's name
     * lasts up to and at the keyframe it arrives in, so a rep held at a stance says how it got there; at the very
     * start it is the first stretch's.
     */
    fun phaseAt(progress: Float): RepPhase? {
        val at = progress - floor(progress)
        val span = phases.firstOrNull { at <= it.until } ?: return null
        return span.phase.takeIf { at > span.from || (at == 0f && span.from == 0f) }
    }
}

/**
 * A rep from one stance it rests in to the next, through [poses] at [times]: the first and last are the rests, any
 * between are passed through. [easing] paces the whole of it.
 */
private class Stretch(poses: List<BodyPose>, private val times: FloatArray, private val easing: Easing) {
    val from = times.first()
    private val to = times.last()
    private val angles = poses.map { it.angles() }

    /** How fast each joint is turning as it goes through each stance: by joint, then by stance. */
    private val slopes = List(BodyPose.JOINTS) { joint -> slopes(FloatArray(poses.size) { angles[it][joint] }) }

    fun poseAt(at: Float): BodyPose {
        val paced = from + easing.transform((at - from) / (to - from)).coerceIn(0f, 1f) * (to - from)
        val index = times.indexOfLast { it <= paced }.coerceIn(0, times.size - 2)
        val span = times[index + 1] - times[index]
        val share = (paced - times[index]) / span
        return BodyPose.of(
            FloatArray(BodyPose.JOINTS) { joint ->
                hermite(
                    from = angles[index][joint],
                    leaving = slopes[joint][index] * span,
                    to = angles[index + 1][joint],
                    arriving = slopes[joint][index + 1] * span,
                    share = share,
                )
            },
        )
    }

    /**
     * How fast a joint whose angle is [values] at each stance turns through each: the plain rate between two at
     * either end, and in the middle Steffen's choice, the most that still keeps the curve from swinging past a
     * stance (none at all where the joint turns back, or stays put on one side).
     */
    private fun slopes(values: FloatArray): FloatArray {
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

    /** The cubic [from] one value [to] the next that is [leaving] the first and [arriving] at the second so fast. */
    @Suppress("detekt.MagicNumber") // Hermite's four cubics, as they are written everywhere.
    private fun hermite(from: Float, leaving: Float, to: Float, arriving: Float, share: Float): Float {
        val squared = share * share
        val cubed = squared * share
        return (2 * cubed - 3 * squared + 1) * from + (cubed - 2 * squared + share) * leaving +
            (3 * squared - 2 * cubed) * to + (cubed - squared) * arriving
    }
}
