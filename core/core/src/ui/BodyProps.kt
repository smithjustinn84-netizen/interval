package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// Sculpted cast-iron equipment (kettlebell, barbell bumper plate), props (bench, wall, rail, bands), soft radial
// ground & per-contact shadows, mats, smooth radial drive glow, and tapered motion ribbon trail.

/** Whether a bell at [bell] lies back along the forearm from [elbow] to [hand] (rack / lockout) rather than hanging. */
internal fun bellRestsOnForearm(elbow: Offset, hand: Offset, bell: Offset): Boolean {
    val forearm = hand - elbow
    val reach = bell - hand
    return forearm.x * reach.x + forearm.y * reach.y < 0f
}

/**
 * A sculpted cast-iron kettlebell gripped at [hand] with its body centred at [body]: arched handle with subtly
 * flared horns and top grip highlight, flat-bottomed cast sphere shaded with a world-aligned studio radial gradient,
 * and specular rim reflection. When cutting, clears a refined hairline gap round its silhouette.
 */
@Suppress("detekt.MagicNumber") // Sculpted kettlebell geometry & cast-iron lighting ratios.
internal fun DrawScope.drawSculptedKettlebell(pen: BodyPen, hand: Offset, body: Offset) {
    val s = pen.scale
    val dist = (body - hand).getDistance().coerceAtLeast(MIN_STEP)
    val along = (body - hand) / dist
    val across = Offset(-along.y, along.x)
    val gripHalf = across * (BELL_HANDLE_HALF * s)
    val shoulderHalf = across * (BELL_SHOULDER_HALF * s)
    val sides = along * (BELL_HANDLE_SIDES * s)
    val corner = across * (BELL_HANDLE_CORNER * s)
    val path = pen.path
    path.rewind()
    val rightBase = hand + shoulderHalf + sides
    val rightCorner = hand + gripHalf
    val rightGrip = hand + corner
    val leftGrip = hand - corner
    val leftCorner = hand - gripHalf
    val leftBase = hand - shoulderHalf + sides
    path.moveTo(rightBase.x, rightBase.y)
    path.quadraticTo(rightCorner.x, rightCorner.y, rightGrip.x, rightGrip.y)
    path.lineTo(leftGrip.x, leftGrip.y)
    path.quadraticTo(leftCorner.x, leftCorner.y, leftBase.x, leftBase.y)
    val radius = BodyProportions.BELL_RADIUS * s
    if (pen.outlining) {
        // Whole in the second pass alone: a gap cleared round it, then the handle and the body in the one ink.
        if (!pen.rimming) {
            val gap = BodyBuild.GAP * s
            val around = Stroke(BELL_HANDLE_STROKE * s + 2f * gap, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(path, Color.Black, style = around, blendMode = BlendMode.Clear)
            drawCircle(Color.Black, radius + gap, body, blendMode = BlendMode.Clear)
            val handle = Stroke(BELL_HANDLE_STROKE * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(path, pen.outlineInk, style = handle)
            drawCircle(pen.outlineInk, radius, body)
        }
        return
    }
    val handleWidth = BELL_HANDLE_STROKE * s + if (pen.cutting) 2f * pen.pad else 0f
    drawPath(
        path,
        pen.color,
        style = Stroke(handleWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = pen.blend,
    )
    if (!pen.cutting && pen.detail) {
        // Subtle metallic specular line along the upper handle grip bar.
        drawLine(
            color = Color.White.copy(alpha = HANDLE_SHEEN_ALPHA),
            start = leftGrip - along * (BELL_HANDLE_STROKE * 0.24f * s),
            end = rightGrip - along * (BELL_HANDLE_STROKE * 0.24f * s),
            strokeWidth = HANDLE_SHEEN_STROKE * s,
            cap = StrokeCap.Round,
        )
    }

    if (pen.cutting) {
        drawCircle(pen.color, radius + pen.pad, body, blendMode = pen.blend)
        return
    }
    drawKettlebellSphere(pen, body, along, radius)
}

@Suppress("detekt.MagicNumber") // Flat-bottomed cast-iron sphere shading and specular reflection.
private fun DrawScope.drawKettlebellSphere(pen: BodyPen, body: Offset, along: Offset, radius: Float) {
    val s = pen.scale
    val path = pen.path
    path.rewind()
    path.arcTo(
        Rect(Offset.Zero, radius),
        Angles.QUARTER_TURN + BASE_HALF_DEGREES,
        Angles.FULL_TURN - BASE_HALF_DEGREES * 2f,
        forceMoveTo = true,
    )
    path.close()
    val angleDeg = Angles.degrees(atan2(along.y, along.x)) - Angles.QUARTER_TURN
    val invRad = -Angles.radians(angleDeg)
    val cosI = cos(invRad)
    val sinI = sin(invRad)
    val localLightX = STUDIO_LIGHT_X * cosI - STUDIO_LIGHT_Y * sinI
    val localLightY = STUDIO_LIGHT_X * sinI + STUDIO_LIGHT_Y * cosI
    val bodyBrush = if (pen.detail) {
        Brush.radialGradient(
            0f to lerp(pen.color, Color.White, BELL_LIT_MIX),
            0.40f to pen.color,
            0.84f to lerp(pen.color, Color.Black, BELL_SHADE_MIX),
            1f to lerp(pen.color, Color.Black, BELL_EDGE_MIX),
            center = Offset(localLightX * radius * BELL_LIGHT_OFFSET, localLightY * radius * BELL_LIGHT_OFFSET),
            radius = radius * BELL_LIGHT_REACH,
        )
    } else {
        null
    }
    withTransform({
        translate(body.x, body.y)
        rotate(angleDeg, pivot = Offset.Zero)
    }) {
        if (bodyBrush != null) drawPath(path, bodyBrush) else drawPath(path, pen.color)
        if (pen.detail) {
            val baseY = radius * cos(Angles.radians(BASE_HALF_DEGREES))
            val baseHalf = radius * sin(Angles.radians(BASE_HALF_DEGREES)) * 0.85f
            drawLine(
                color = Color.Black.copy(alpha = BELL_BASE_CHAMFER_ALPHA),
                start = Offset(-baseHalf, baseY - BELL_CHAMFER_INSET * s),
                end = Offset(baseHalf, baseY - BELL_CHAMFER_INSET * s),
                strokeWidth = BELL_CHAMFER_STROKE * s,
                cap = StrokeCap.Round,
            )
        }
    }
    if (pen.detail) {
        val detailR = radius * BELL_DETAIL_RATIO
        drawArc(
            color = Color.White.copy(alpha = BELL_HIGHLIGHT_ALPHA),
            startAngle = BELL_HIGHLIGHT_START,
            sweepAngle = BELL_HIGHLIGHT_SWEEP,
            useCenter = false,
            topLeft = Offset(body.x - detailR, body.y - detailR),
            size = Size(detailR * 2f, detailR * 2f),
            style = Stroke(BELL_HIGHLIGHT_STROKE * s, cap = StrokeCap.Round),
        )
    }
}

/** A machined barbell bumper plate seen end on: shaded rubber disc, beveled outer rim, hub ring, and steel axle. */
@Suppress("detekt.MagicNumber") // Bumper plate radial gradient stops and rim proportions.
internal fun DrawScope.drawBarbellPlate(sleeve: Offset, scale: Float, color: Color, axle: Color) {
    val plateR = BodyProportions.PLATE_RADIUS * scale
    val rim = BodyProportions.PLATE_RIM * scale
    val discR = plateR - rim * HALF
    val discBrush = Brush.radialGradient(
        0f to color.copy(alpha = PLATE_FILL_ALPHA * 1.45f),
        0.65f to color.copy(alpha = PLATE_FILL_ALPHA),
        1f to lerp(color, Color.Black, 0.35f).copy(alpha = PLATE_FILL_ALPHA * 1.6f),
        center = sleeve + Offset(STUDIO_LIGHT_X, STUDIO_LIGHT_Y) * (discR * 0.3f),
        radius = discR * 1.35f,
    )
    drawCircle(discBrush, discR, sleeve)
    drawCircle(color, discR, sleeve, style = Stroke(rim))
    drawArc(
        color = Color.White.copy(alpha = PLATE_RIM_SHEEN_ALPHA),
        startAngle = BELL_HIGHLIGHT_START,
        sweepAngle = 80f,
        useCenter = false,
        topLeft = Offset(sleeve.x - discR, sleeve.y - discR),
        size = Size(discR * 2f, discR * 2f),
        style = Stroke(rim * 0.28f, cap = StrokeCap.Round),
    )
    drawCircle(
        color.copy(alpha = PLATE_INNER_ALPHA),
        plateR * PLATE_INNER_RATIO,
        sleeve,
        style = Stroke(PLATE_INNER_STROKE * scale),
    )
    val sleeveR = BodyProportions.SLEEVE * scale
    drawCircle(
        brush = Brush.radialGradient(
            0f to lerp(color, Color.White, 0.25f),
            1f to lerp(color, Color.Black, 0.25f),
            center = sleeve + Offset(STUDIO_LIGHT_X, STUDIO_LIGHT_Y) * (sleeveR * 0.35f),
            radius = sleeveR * 1.3f,
        ),
        radius = sleeveR,
        center = sleeve,
    )
    drawCircle(axle.copy(alpha = PLATE_AXLE_ALPHA), PLATE_AXLE_RADIUS * scale, sleeve)
}

/** A silky radial glow behind the weight that flares wider and brighter as a [RepBeat.Drive] stretch starts. */
@Suppress("detekt.MagicNumber") // Smooth radial glow falloff stops.
internal fun DrawScope.drawWeightGlow(
    hold: RepHold,
    hand: Offset,
    bell: Offset,
    pulse: Float,
    scale: Float,
    color: Color,
) {
    if (hold == RepHold.Free) return
    val bar = hold == RepHold.Bar
    val at = if (bar) hand else bell
    val base = if (bar) {
        BodyProportions.PLATE_RADIUS * GLOW_PLATE_REACH
    } else {
        BodyProportions.BELL_RADIUS * GLOW_BELL_REACH
    }
    val radius = base * scale * (1f + pulse * GLOW_FLARE)
    if (radius <= MIN_STEP) return
    val peak = (GLOW_ALPHA + pulse * GLOW_PULSE_ALPHA).coerceIn(0f, 1f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = peak),
            0.42f to color.copy(alpha = peak * 0.48f),
            0.78f to color.copy(alpha = peak * 0.12f),
            1f to Color.Transparent,
            center = at,
            radius = radius,
        ),
        radius = radius,
        center = at,
    )
}

/** Tapered motion ribbon behind [tip] with a brighter inner core streak. */
internal fun DrawScope.drawMotionTrail(pen: BodyPen, tip: Offset, color: Color) {
    val count = pen.trailCount
    if (count == 0) return
    val s = pen.scale
    drawTrailRibbon(pen, tip, color, TRAIL_TAIL * s, TRAIL_HEAD * s, TRAIL_OUTER_ALPHA)
    drawTrailRibbon(
        pen,
        tip,
        color,
        TRAIL_TAIL * s * TRAIL_CORE_RATIO,
        TRAIL_HEAD * s * TRAIL_CORE_RATIO,
        TRAIL_CORE_ALPHA,
    )
}

private fun DrawScope.drawTrailRibbon(
    pen: BodyPen,
    tip: Offset,
    color: Color,
    tailRadius: Float,
    headRadius: Float,
    maxAlpha: Float,
) {
    val count = pen.trailCount
    val path = pen.path
    var prevPoint = Offset(pen.trail[0], pen.trail[1])
    var prevNormal = trailNormal(pen, tip, 0)
    var prevRadius = tailRadius
    for (i in 1..count) {
        val point = if (i < count) Offset(pen.trail[2 * i], pen.trail[2 * i + 1]) else tip
        val normal = trailNormal(pen, tip, i)
        val share = i.toFloat() / count
        val radius = tailRadius + (headRadius - tailRadius) * share
        path.rewind()
        path.moveTo(prevPoint.x + prevNormal.x * prevRadius, prevPoint.y + prevNormal.y * prevRadius)
        path.lineTo(point.x + normal.x * radius, point.y + normal.y * radius)
        path.lineTo(point.x - normal.x * radius, point.y - normal.y * radius)
        path.lineTo(prevPoint.x - prevNormal.x * prevRadius, prevPoint.y - prevNormal.y * prevRadius)
        path.close()
        drawPath(path, color.copy(alpha = maxAlpha * share * share))
        prevPoint = point
        prevNormal = normal
        prevRadius = radius
    }
}

private fun trailNormal(pen: BodyPen, tip: Offset, index: Int): Offset {
    val count = pen.trailCount
    val prev = when {
        index <= 0 -> Offset(pen.trail[0], pen.trail[1])
        index - 1 < count -> Offset(pen.trail[2 * (index - 1)], pen.trail[2 * (index - 1) + 1])
        else -> tip
    }
    val next = when {
        index >= count -> tip
        index + 1 < count -> Offset(pen.trail[2 * (index + 1)], pen.trail[2 * (index + 1) + 1])
        else -> tip
    }
    val dx = next.x - prev.x
    val dy = next.y - prev.y
    val len = hypot(dx, dy)
    return if (len > MIN_STEP) Offset(-dy / len, dx / len) else Offset.Zero
}

/**
 * Fading floor line, feathered radial ambient shadow + concentrated per-contact ground occlusion pools tracking
 * planted feet, knees, hips, and hands, and (for unweighted exercises with no bands) a mat in [beatColor].
 */
@Suppress("detekt.LongParameterList", "detekt.MagicNumber") // Floor, mat, and contact-shadow staging inputs.
internal fun DrawScope.drawFloorAndShadow(
    frame: BodyFrame,
    floorY: Float,
    floorBrush: Brush,
    floorFrom: Float,
    floorTo: Float,
    matRange: ClosedFloatingPointRange<Float>?,
    matFromAbove: Rect?,
    scale: Float,
    beatColor: Color,
    shadowColor: Color,
    detail: Boolean,
    place: (Offset) -> Offset,
) {
    if (matFromAbove != null) {
        val corner = CornerRadius(BodyProportions.MAT_CORNER * scale)
        drawRoundRect(beatColor.copy(alpha = MAT_FILL_ALPHA), matFromAbove.topLeft, matFromAbove.size, corner)
        drawRoundRect(
            beatColor.copy(alpha = MAT_EDGE_ALPHA),
            matFromAbove.topLeft,
            matFromAbove.size,
            corner,
            style = Stroke(BodyProportions.MAT_EDGE * scale),
        )
        return
    }
    drawLine(floorBrush, Offset(floorFrom, floorY), Offset(floorTo, floorY), FLOOR_STROKE * scale, StrokeCap.Round)

    if (shadowColor.alpha > 0f) {
        val toe = place(frame.toe)
        val heel = place(frame.heel)
        val farToe = place(frame.farToe)
        val farHeel = place(frame.farHeel)
        val knee = place(frame.knee)
        val farKnee = place(frame.farKnee)
        val hip = place(frame.hip)
        val shoulder = place(frame.shoulder)
        val head = place(frame.headCenter)
        val hand = place(frame.kettlebellAnchor)
        val farHand = place(frame.farBellAnchor)
        val minX =
            minOf(toe.x, heel.x, farToe.x, farHeel.x, knee.x, farKnee.x, hip.x, shoulder.x, head.x, hand.x, farHand.x)
        val maxX =
            maxOf(toe.x, heel.x, farToe.x, farHeel.x, knee.x, farKnee.x, hip.x, shoulder.x, head.x, hand.x, farHand.x)
        val lowestY =
            maxOf(toe.y, heel.y, farToe.y, farHeel.y, knee.y, farKnee.y, hip.y, shoulder.y, head.y, hand.y, farHand.y)
        val clearance = ((floorY - lowestY) / scale).coerceAtLeast(0f)
        val elevation = (1f - clearance / SHADOW_FADE_HEIGHT).coerceIn(SHADOW_MIN_ALPHA_FACTOR, 1f)
        val span = ((maxX - minX) * SHADOW_SPAN_RATIO + SHADOW_PADDING * scale)
            .coerceIn(SHADOW_MIN_WIDTH * scale, SHADOW_MAX_WIDTH * scale)
        val outerW = span * (1f + clearance / SHADOW_SPREAD_HEIGHT)
        val ambient = shadowColor.copy(alpha = shadowColor.alpha * elevation * SHADOW_OUTER_ALPHA)
        drawSoftShadowEllipse(
            (minX + maxX) * HALF,
            floorY + SHADOW_DROP * scale,
            outerW,
            SHADOW_HEIGHT * scale,
            ambient,
        )

        if (detail) {
            val contactY = floorY + CONTACT_DROP * scale
            val contactH = CONTACT_HEIGHT * scale
            fun contact(x: Float, y: Float, widthS: Float) {
                val gap = ((floorY - y) / scale).coerceAtLeast(0f)
                if (gap > CONTACT_MAX_GAP) return
                val grip = 1f - gap / CONTACT_MAX_GAP
                val tint = shadowColor.copy(alpha = shadowColor.alpha * CONTACT_ALPHA * grip * grip)
                drawSoftShadowEllipse(x, contactY, widthS * scale * (1f + gap * 2f), contactH, tint)
            }
            contact((heel.x + toe.x) * HALF, max(heel.y, toe.y), CONTACT_FOOT_WIDTH)
            if (frame.showsFarSide || frame.front) {
                contact((farHeel.x + farToe.x) * HALF, max(farHeel.y, farToe.y), CONTACT_FOOT_WIDTH)
            }
            contact(knee.x, knee.y, CONTACT_JOINT_WIDTH)
            contact(farKnee.x, farKnee.y, CONTACT_JOINT_WIDTH)
            contact(hip.x, hip.y, CONTACT_HIP_WIDTH)
            contact(hand.x, hand.y, CONTACT_JOINT_WIDTH)
            contact(farHand.x, farHand.y, CONTACT_JOINT_WIDTH)
        }
    }

    if (matRange != null) {
        val matPx = BodyProportions.MAT * scale
        val matY = floorY + matPx * HALF
        drawLine(beatColor, Offset(matRange.start, matY), Offset(matRange.endInclusive, matY), matPx, StrokeCap.Round)
        if (detail) {
            drawLine(
                color = Color.White.copy(alpha = MAT_HIGHLIGHT_ALPHA),
                start = Offset(matRange.start + matPx * HALF, matY - matPx * 0.25f),
                end = Offset(matRange.endInclusive - matPx * HALF, matY - matPx * 0.25f),
                strokeWidth = matPx * 0.22f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Draws a smoothly feathered radial-gradient ellipse centered at ([centerX], [centerY]) with zero hard rim. */
@Suppress("detekt.MagicNumber") // Smooth radial shadow falloff curve.
private fun DrawScope.drawSoftShadowEllipse(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    if (width <= MIN_STEP || height <= MIN_STEP || color.alpha <= 0f) return
    val rx = width * HALF
    val ry = height * HALF
    withTransform({
        translate(centerX, centerY)
        scale(rx, ry, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to color,
                0.45f to color.copy(alpha = color.alpha * 0.58f),
                0.80f to color.copy(alpha = color.alpha * 0.14f),
                1f to Color.Transparent,
                center = Offset.Zero,
                radius = 1f,
            ),
            radius = 1f,
            center = Offset.Zero,
        )
    }
}

/** Draws resistance bands either [behind] the figure (anchored to a post behind the hip) or in front. */
internal fun DrawScope.drawRepBands(
    bands: List<RepBand>,
    frame: BodyFrame,
    scale: Float,
    color: Color,
    behind: Boolean,
    place: (Offset) -> Offset,
) {
    for (i in bands.indices) {
        val band = bands[i]
        val isBehind = (band.from is RepBandEnd.Post && band.from.at.x < frame.hip.x) ||
            (band.to is RepBandEnd.Post && band.to.at.x < frame.hip.x)
        if (isBehind != behind) continue
        val a = place(frame.bandPlace(band.from))
        val b = place(frame.bandPlace(band.to))
        drawLine(color, a, b, BodyProportions.BAND * scale, StrokeCap.Round)
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx, dy)
        if (len > MIN_BAND_PX) {
            val offset = Offset(-dy / len * BAND_STRAND_OFFSET * scale, dx / len * BAND_STRAND_OFFSET * scale)
            drawLine(
                color.copy(alpha = BAND_TWIN_ALPHA),
                a + offset,
                b + offset,
                BAND_TWIN_STROKE * scale,
                StrokeCap.Round,
            )
        }
        if (band.from is RepBandEnd.Post) drawCircle(color, BAND_KNOT_RADIUS * scale, a)
        if (band.to is RepBandEnd.Post) drawCircle(color, BAND_KNOT_RADIUS * scale, b)
    }
}

/** Draws a prop behind the figure: padded bench, wall with gradient backing, machined rail, or band anchor post. */
@Suppress("detekt.MagicNumber") // Metallic rail sheen and post collar proportions.
internal fun DrawScope.drawRepProp(
    prop: RepProp,
    floorY: Float,
    topY: Float,
    scale: Float,
    color: Color,
    place: (Offset) -> Offset,
) = when (prop) {
    is RepBench -> drawRepBench(prop, floorY, scale, color, place)

    is RepWall -> drawRepWall(prop, floorY, topY, scale, color, place)

    is RepRail -> {
        val at = place(prop.at)
        val r = BodyProportions.RAIL * scale
        val rim = RAIL_RIM_STROKE * scale
        drawCircle(
            brush = Brush.radialGradient(
                0f to lerp(color, Color.White, 0.25f).copy(alpha = RAIL_FILL_ALPHA),
                1f to color.copy(alpha = RAIL_FILL_ALPHA),
                center = at + Offset(STUDIO_LIGHT_X, STUDIO_LIGHT_Y) * (r * 0.35f),
                radius = r * 1.3f,
            ),
            radius = r,
            center = at,
        )
        drawCircle(color, r - rim * HALF, at, style = Stroke(rim))
        drawCircle(Color.White.copy(alpha = RAIL_CORE_ALPHA), RAIL_CORE_RADIUS * scale, at)
    }

    is RepBand -> {
        for (end in arrayOf(prop.from, prop.to)) {
            if (end !is RepBandEnd.Post) continue
            val at = place(end.at)
            val post = BodyProportions.POST * scale
            drawLine(color, Offset(at.x, floorY), Offset(at.x, at.y - post * 2f), post, StrokeCap.Round)
            val halfBase = POST_BASE_HALF * scale
            drawLine(
                color,
                Offset(at.x - halfBase, floorY),
                Offset(at.x + halfBase, floorY),
                POST_BASE_STROKE * scale,
                StrokeCap.Round,
            )
            drawCircle(color, POST_COLLAR_RADIUS * scale, at)
        }
    }
}

/** Support upright or ceiling bracket holding a [RepRail] behind the figure. */
internal fun DrawScope.drawRepRailStand(
    rail: RepRail,
    floorY: Float,
    topY: Float,
    scale: Float,
    color: Color,
    place: (Offset) -> Offset,
) {
    val at = place(rail.at)
    val overhead = rail.at.y > OVERHEAD_RAIL_Y
    val end = if (overhead) Offset(at.x, topY) else Offset(at.x, floorY)
    drawLine(color.copy(alpha = RAIL_STAND_ALPHA), at, end, BodyProportions.POST * scale, StrokeCap.Round)
    val halfBase = POST_BASE_HALF * scale
    drawLine(
        color.copy(alpha = RAIL_STAND_ALPHA),
        Offset(end.x - halfBase, end.y),
        Offset(end.x + halfBase, end.y),
        POST_BASE_STROKE * scale,
        StrokeCap.Round,
    )
}

@Suppress("detekt.MagicNumber") // Bench cushion & steel frame lighting ratios.
private fun DrawScope.drawRepBench(
    bench: RepBench,
    floorY: Float,
    scale: Float,
    color: Color,
    place: (Offset) -> Offset,
) {
    val left = place(Offset(bench.from, bench.height))
    val right = place(Offset(bench.to, bench.height))
    val top = left.y
    val legInset = ((right.x - left.x) * BENCH_LEG_INSET_RATIO).coerceAtMost(BENCH_LEG_MAX_INSET * scale)
    val leftLeg = left.x + legInset
    val rightLeg = right.x - legInset
    val topThick = BodyProportions.BENCH_TOP * scale
    val frameY = top + topThick * BENCH_FRAME_DROP
    drawLine(
        color.copy(alpha = BENCH_FRAME_ALPHA),
        Offset(leftLeg, frameY),
        Offset(rightLeg, frameY),
        BENCH_FRAME_STROKE * scale,
    )
    drawLine(
        color.copy(alpha = BENCH_LEG_ALPHA),
        Offset(leftLeg, top),
        Offset(leftLeg, floorY),
        BodyProportions.BENCH_LEG * scale,
        StrokeCap.Round,
    )
    drawLine(
        color.copy(alpha = BENCH_LEG_ALPHA),
        Offset(rightLeg, top),
        Offset(rightLeg, floorY),
        BodyProportions.BENCH_LEG * scale,
        StrokeCap.Round,
    )
    // Grounded rubber foot caps on bench legs.
    val footHalf = BodyProportions.BENCH_LEG * 1.15f * scale
    val footStroke = BENCH_FOOT_STROKE * scale
    drawLine(color, Offset(leftLeg - footHalf, floorY), Offset(leftLeg + footHalf, floorY), footStroke, StrokeCap.Round)
    drawLine(
        color,
        Offset(rightLeg - footHalf, floorY),
        Offset(rightLeg + footHalf, floorY),
        footStroke,
        StrokeCap.Round,
    )

    val padBrush = Brush.verticalGradient(
        0f to lerp(color, Color.White, 0.14f),
        0.55f to color,
        1f to lerp(color, Color.Black, 0.22f),
        startY = top - topThick * HALF,
        endY = top + topThick * HALF,
    )
    drawLine(padBrush, left, right, topThick, StrokeCap.Round)
    val inset = BENCH_HIGHLIGHT_INSET * scale
    val lift = BENCH_HIGHLIGHT_LIFT * scale
    drawLine(
        Color.White.copy(alpha = BENCH_HIGHLIGHT_ALPHA),
        Offset(left.x + inset, top - lift),
        Offset(right.x - inset, top - lift),
        BENCH_HIGHLIGHT_STROKE * scale,
        StrokeCap.Round,
    )
}

@Suppress("detekt.MagicNumber") // Wall backing fade gradient.
private fun DrawScope.drawRepWall(
    wall: RepWall,
    floorY: Float,
    topY: Float,
    scale: Float,
    color: Color,
    place: (Offset) -> Offset,
) {
    val x = place(Offset(wall.x, 0f)).x
    val backW = WALL_BACKING_WIDTH * scale
    val backingLeft = if (wall.x < 0f) x - backW else x
    val fadeBrush = if (wall.x < 0f) {
        Brush.horizontalGradient(
            0f to Color.Transparent,
            1f to color.copy(alpha = WALL_BACKING_ALPHA),
            startX = backingLeft,
            endX = x,
        )
    } else {
        Brush.horizontalGradient(
            0f to color.copy(alpha = WALL_BACKING_ALPHA),
            1f to Color.Transparent,
            startX = x,
            endX = x + backW,
        )
    }
    drawRect(fadeBrush, Offset(backingLeft, topY), Size(backW, floorY - topY))
    drawLine(color, Offset(x, floorY), Offset(x, topY), BodyProportions.WALL * scale, StrokeCap.Round)
}

private const val HALF = 0.5f
private const val MIN_STEP = 0.001f
private const val MIN_BAND_PX = 1f
private const val OVERHEAD_RAIL_Y = 0.6f

private const val BELL_HANDLE_HALF = 0.036f
private const val BELL_SHOULDER_HALF = 0.048f
private const val BELL_HANDLE_SIDES = 0.048f
private const val BELL_HANDLE_CORNER = 0.018f
private const val BELL_HANDLE_STROKE = 0.020f
private const val HANDLE_SHEEN_ALPHA = 0.28f
private const val HANDLE_SHEEN_STROKE = 0.0045f
private const val BASE_HALF_DEGREES = 32f
private const val BELL_LIT_MIX = 0.34f
private const val BELL_SHADE_MIX = 0.28f
private const val BELL_EDGE_MIX = 0.36f
private const val BELL_LIGHT_OFFSET = 0.36f
private const val BELL_LIGHT_REACH = 1.45f
private const val BELL_BASE_CHAMFER_ALPHA = 0.22f
private const val BELL_CHAMFER_INSET = 0.005f
private const val BELL_CHAMFER_STROKE = 0.0055f
private const val BELL_DETAIL_RATIO = 0.74f
private const val BELL_HIGHLIGHT_ALPHA = 0.36f
private const val BELL_HIGHLIGHT_STROKE = 0.008f
private const val BELL_HIGHLIGHT_START = 235f
private const val BELL_HIGHLIGHT_SWEEP = 75f

private const val PLATE_FILL_ALPHA = 0.16f
private const val PLATE_RIM_SHEEN_ALPHA = 0.28f
private const val PLATE_INNER_RATIO = 0.45f
private const val PLATE_INNER_STROKE = 0.007f
private const val PLATE_INNER_ALPHA = 0.44f
private const val PLATE_AXLE_RADIUS = 0.0084f
private const val PLATE_AXLE_ALPHA = 0.75f

private const val GLOW_BELL_REACH = 2.35f
private const val GLOW_PLATE_REACH = 1.4f
private const val GLOW_FLARE = 0.35f
private const val GLOW_ALPHA = 0.18f
private const val GLOW_PULSE_ALPHA = 0.22f

private const val TRAIL_TAIL = 0.004f
private const val TRAIL_HEAD = 0.030f
private const val TRAIL_CORE_RATIO = 0.42f
private const val TRAIL_OUTER_ALPHA = 0.34f
private const val TRAIL_CORE_ALPHA = 0.55f

private const val FLOOR_STROKE = 0.005f
private const val MAT_FILL_ALPHA = 0.16f
private const val MAT_EDGE_ALPHA = 0.7f
private const val MAT_HIGHLIGHT_ALPHA = 0.24f
private const val SHADOW_SPAN_RATIO = 0.92f
private const val SHADOW_PADDING = 0.13f
private const val SHADOW_MIN_WIDTH = 0.31f
private const val SHADOW_MAX_WIDTH = 0.94f
private const val SHADOW_FADE_HEIGHT = 0.58f
private const val SHADOW_SPREAD_HEIGHT = 1.16f
private const val SHADOW_MIN_ALPHA_FACTOR = 0.22f
private const val SHADOW_HEIGHT = 0.048f
private const val SHADOW_DROP = 0.011f
private const val SHADOW_OUTER_ALPHA = 0.56f
private const val CONTACT_MAX_GAP = 0.06f
private const val CONTACT_DROP = 0.005f
private const val CONTACT_HEIGHT = 0.022f
private const val CONTACT_ALPHA = 0.72f
private const val CONTACT_FOOT_WIDTH = 0.22f
private const val CONTACT_JOINT_WIDTH = 0.14f
private const val CONTACT_HIP_WIDTH = 0.24f

private const val BAND_STRAND_OFFSET = 0.010f
private const val BAND_TWIN_ALPHA = 0.5f
private const val BAND_TWIN_STROKE = 0.007f
private const val BAND_KNOT_RADIUS = 0.017f
private const val POST_BASE_HALF = 0.035f
private const val POST_BASE_STROKE = 0.013f
private const val POST_COLLAR_RADIUS = 0.015f
private const val BENCH_LEG_INSET_RATIO = 0.12f
private const val BENCH_LEG_MAX_INSET = 0.045f
private const val BENCH_FRAME_DROP = 0.7f
private const val BENCH_FRAME_ALPHA = 0.55f
private const val BENCH_FRAME_STROKE = 0.011f
private const val BENCH_LEG_ALPHA = 0.8f
private const val BENCH_FOOT_STROKE = 0.009f
private const val BENCH_HIGHLIGHT_ALPHA = 0.22f
private const val BENCH_HIGHLIGHT_INSET = 0.013f
private const val BENCH_HIGHLIGHT_LIFT = 0.006f
private const val BENCH_HIGHLIGHT_STROKE = 0.006f
private const val WALL_BACKING_WIDTH = 0.052f
private const val WALL_BACKING_ALPHA = 0.20f
private const val RAIL_FILL_ALPHA = 0.58f
private const val RAIL_STAND_ALPHA = 0.7f
private const val RAIL_RIM_STROKE = 0.0084f
private const val RAIL_CORE_RADIUS = 0.0077f
private const val RAIL_CORE_ALPHA = 0.55f
