package dev.justinsmith.interval.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Fixed design tokens: the same in light and dark. Names mirror the Interval design system
// (`space-16` → Spacing.space16, `radius-18` → Radius.radius18, `size-stepper` → Sizes.stepper).

/** Gaps and padding: the design system's `space-*` tokens, on a 4dp grid. */
object Spacing {
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space20 = 20.dp
    val space24 = 24.dp
    val space32 = 32.dp
}

/** Corner radii: the design system's `radius-*` tokens. Small for controls, large for cards. */
object Radius {
    /** Steppers, pills, value pills, workout pills, the options inside the theme picker. */
    val radius12 = 12.dp

    /** Cards, preset cards, the theme picker, workout controls, Start workout. */
    val radius18 = 18.dp

    /** A sheet's rounded corners, and the app's corners while a back swipe previews the page under it. */
    val radius24 = 24.dp
}

/** The [Radius] tokens as shapes, plus `radius-full`. */
object Corners {
    val radius12 = RoundedCornerShape(Radius.radius12)
    val radius18 = RoundedCornerShape(Radius.radius18)

    /** Progress ring caps, timelines, dots. */
    val full = RoundedCornerShape(50)

    /** A bottom sheet: rounded on top only. */
    val sheet = RoundedCornerShape(topStart = Radius.radius24, topEnd = Radius.radius24)

    /** A side sheet: rounded on the edge facing the app, which flips with the layout direction. */
    val sideSheet = RoundedCornerShape(topStart = Radius.radius24, bottomStart = Radius.radius24)
}

/** Fixed dimensions: the design system's `size-*` tokens, mostly touch targets. */
object Sizes {
    /** Maximum width of the setup column. */
    val content = 460.dp

    /** Maximum width of the Timer's column (its right-hand column sideways), when the window has room. */
    val contentWide = 760.dp

    /** Narrowest column of the Presets and History grids; wider windows fit more columns. */
    val listColumn = 360.dp

    /** Maximum width of the Presets and History grids: two columns at most, so rows stay readable. */
    val listMax = 960.dp

    /** Narrowest window that gets a navigation rail instead of a bottom bar, as Material's medium width. */
    val railMinWidth = 600.dp

    /** The line between the content and the bottom navigation bar. */
    val hairline = 1.dp

    /**
     * Width of the fixed column on a screen laid out sideways: the Timer's total and Start, a session's clock
     * and Log set, an EMOM's controls, a summary's way out.
     */
    val setupColumnLandscape = 380.dp

    /** A workout control (Pause, End, Skip), and the wider Back and +10s beside them. */
    val control = 76.dp
    val controlSide = 84.dp

    /** A − / + stepper button. */
    val stepper = 54.dp

    /** A pill-shaped button: its narrowest, and its height (a touch target's). */
    val pillWidth = 72.dp
    val pillHeight = 48.dp

    /** A progress bar's height: a program's days done, a kind's share of the week. */
    val progressBar = 6.dp

    /** The timeline on a preset chip or card, and the gap between its segments. */
    val timelineMini = 6.dp
    val timelineMiniGap = 1.dp

    /** A day with no workout in the week chart. */
    val weekBarEmpty = 6.dp

    /** A planned or missed day in the week chart: an outlined or filled block this tall. */
    val weekBarMark = 32.dp

    /** A swatch in the week chart's key. */
    val legendSwatch = 10.dp

    /** The tallest bar of All time's weekly chart. */
    val weeksChart = 96.dp

    /** The small glyph inside a chip, such as the streak's flame. */
    val chipIcon = 16.dp

    /** One choice of a segmented picker, such as the theme's. */
    val segment = 48.dp

    /** A bottom sheet's drag handle: the area that takes the drag, round the small bar drawn in it. */
    val dragHandleWidth = 48.dp
    val dragHandleHeight = 24.dp

    /** The bar drawn in a bottom sheet's drag handle. */
    val handleWidth = 36.dp
    val handleHeight = 5.dp

    /** The narrowest a text field in a dialog or sheet gets. */
    val fieldMin = 230.dp

    /** A [ToggleRow]'s switch: the track, and the thumb inset inside it. */
    val switchWidth = 46.dp
    val switchHeight = 28.dp
    val switchThumb = 22.dp

    /** A big full-width button: Save, Repeat, Done. */
    val save = 56.dp

    /** The narrowest overflow menu. */
    val menu = 200.dp

    /** A text field, such as a preset's name. */
    val field = 52.dp

    /** The workout's progress ring: its stroke, and its widest on a big window. */
    val ring = 22.dp
    val ringMax = 420.dp

    /**
     * The figure showing how an exercise goes, which both sides draw: on its page, tall enough to read a swing's
     * arc on a phone; as a band across a card or over the workout's ring, where the arc still reads; and a still
     * of it at the end of a list row.
     */
    val figurePage = 220.dp
    val figureBand = 140.dp
    val figureThumbWidth = 64.dp
    val figureThumbHeight = 48.dp

    /**
     * The lanes behind the Setup tabs: a track whose centre sits this far past the column's start
     * edge and below its bottom, so the lanes sweep in from the side and bend down; the innermost
     * lane's radius, the step out to the next, and the line between lanes.
     */
    val laneCentreOut = 700.dp
    val laneCentreBelow = 56.dp
    val laneFirst = 700.dp
    val laneStep = 44.dp
    val laneStroke = 1.5.dp

    /** The workout's lanes circle the ring: the gap from the ring's edge to the first, and the step between. */
    val workoutLaneGap = 27.dp
    val workoutLaneStep = 40.dp

    /** A comet: its stroke, how long its tail trails, and how far past the column's edge it starts and ends. */
    val cometStroke = 4.dp
    val cometTail = 160.dp
    val cometMargin = 80.dp

    /** The dim second comet on the workout's outer lane. */
    val chaseStroke = 3.dp
    val chaseTail = 120.dp

    /** The band of phase colour along the workout screen's top edge (its start edge in landscape). */
    val phaseBand = 10.dp

    /** An outline: a calendar day, a selected choice, a text field. */
    val border = 1.5.dp

    /** Line icons: tabs and top-bar actions. */
    val icon = 24.dp

    /** The round hit area of an icon-only top-bar action. */
    val iconButton = 48.dp

    /** The circle drawn inside that hit area: its fill and its ripple. */
    val iconButtonVisual = 40.dp

    /** A [Burst]'s rings: the gap between them, and their stroke as they set out. */
    val burstGap = 18.dp
    val burstStroke = 14.dp

    /** A [Burst] inside a list row, such as a record's, starts from a thinner stroke. */
    val rowBurstStroke = 6.dp

    /** The tinted ring round an [EmptyState]'s icon. */
    val emptyIconRing = 56.dp

    /** The empty-state icon drawn inside it, a size up from [icon]. */
    val emptyIcon = 28.dp

    /** Minimum height of a list row (presets, history, settings). */
    val listRow = 64.dp

    /**
     * The widest the bottom sheet grows. Only short windows (a phone on its side) are wider and
     * centre it: tablets and desktops get the side sheet instead.
     */
    val sheet = 640.dp

    /** A side sheet's width: about a phone's, the width the sheets' content is laid out for. */
    val sideSheet = 420.dp
}

/** The design system's `opacity-*` tokens. */
object Opacity {
    /** A disabled control: an icon action's glyph, a toggle row. */
    const val DISABLED = 0.45f

    /** A sheet's drag handle, present but quiet. */
    const val HANDLE = 0.45f

    /** A − or + glyph at the end of its range. */
    const val DISABLED_GLYPH = 0.5f

    /** The accent-coloured shadow under Start workout. */
    const val START_SHADOW = 0.35f

    /** The `ink` dim behind a dialog or sheet. */
    const val SCRIM = 0.55f

    /** A calendar day outside the month on show. */
    const val OUTSIDE_MONTH = 0.4f

    /** The lighter share of a bar: on the Lift tab's balance, the sets that only assisted a muscle. */
    const val LIGHT_SHARE = 0.4f
}

/**
 * Timings from the design system's Motion section. Three durations cover nearly everything: a
 * control answering its state ([STATE_MILLIS]), content changing in place ([CONTENT_MILLIS]) and
 * something arriving ([ENTER_MILLIS]); leaving is quicker than arriving. The easings and springs
 * live in `Motion.kt` beside the helpers that use them.
 */
object Motion {
    /** A control's colour following its state: selected, checked, enabled, the active round mark. */
    const val STATE_MILLIS = 180

    /** Content replaced in place: a value, the phase word. */
    const val CONTENT_MILLIS = 240

    /** Something arriving: a card, a dialog, a summary. */
    const val ENTER_MILLIS = 360

    /** Something leaving, which needs less ceremony. */
    const val EXIT_MILLIS = 200

    /** Pushing or popping a page. */
    const val PAGE_MILLIS = 320

    /** A pushed page slides in from this share of the width, and the page beneath drifts out by half of it. */
    const val PAGE_SLIDE_SHARE = 0.12f

    /** Settings opening beside the Timer slides in from about its own share of the window's width. */
    const val PANE_SLIDE_SHARE = 0.4f

    /** The workout rises into place from this share of the window's height below it, and drops back as far. */
    const val WORKOUT_RISE_SHARE = 0.24f

    /** A new phase word rises from this share of its height below its place. */
    const val PHASE_WORD_RISE_SHARE = 0.5f

    /** Switching tabs: the old tab fades out quickly, then the new one fades in, grown from [FADE_THROUGH_SCALE]. */
    const val FADE_THROUGH_SCALE = 0.96f

    /**
     * How long the old page or tab fades before the new one starts to come in. The two never show at
     * once, half-seen, as a plain crossfade would.
     */
    const val FADE_THROUGH_GAP_MILLIS = 90

    /** A dialog grows in from this scale. */
    const val DIALOG_SCALE = 0.96f

    /** A pressed control shrinks to this, springing back on release. */
    const val PRESS_SCALE = 0.96f

    /** How long Undo stays offered after something is removed. */
    const val UNDO_MILLIS = 5_000L

    /** A notice with nothing to act on (Saved as Tabata) stays this long. */
    const val NOTICE_MILLIS = 2_500L

    /** The gap between items in a staggered entrance, such as the summary's lines or the week chart's bars. */
    const val STAGGER_MILLIS = 50

    /** Background crossfade between phases, and into and out of pause. */
    const val PHASE_CROSSFADE_MILLIS = 350

    /** The clock's pop when a new interval starts, from [CLOCK_POP_SCALE] back to 1. */
    const val CLOCK_POP_MILLIS = 300
    const val CLOCK_POP_SCALE = 1.06f

    /** The smaller pop on each of an interval's last seconds, in step with the countdown beeps. */
    const val COUNTDOWN_POP_SCALE = 1.03f

    /**
     * The ring glides to each tick over this long, so it turns rather than steps; the ViewModel ticks
     * every 100ms. A change past [RING_SNAP_SHARE] of the ring is a new interval, which snaps.
     */
    const val RING_GLIDE_MILLIS = 160
    const val RING_SNAP_SHARE = 0.3f

    /** An EMOM's ring glides tick to tick too; its ViewModel ticks every 250ms. */
    const val EMOM_RING_GLIDE_MILLIS = 250

    /** The summary's ring draws itself closed over this long. */
    const val SUMMARY_RING_MILLIS = 900

    /** A [Burst] of rings opens from the centre to past the corners in this long, as the intro's does. */
    const val BURST_MILLIS = 540

    /**
     * Start's launch: one ring of the accent sets out from the button as the workout rises, and is spent in the
     * time the rise takes ([PAGE_MILLIS]), so the workout's ring reads as what the button sent up.
     */
    const val LAUNCH_MILLIS = PAGE_MILLIS

    /** The resume hint breathes while paused: a cycle this long, down to this alpha. */
    const val BREATH_MILLIS = 1600
    const val BREATH_MIN_ALPHA = 0.5f

    /** A tag stamping in (Record): it lands from this scale, on a spring with this much give. */
    const val STAMP_SCALE = 1.4f
    const val STAMP_DAMPING = 0.5f

    /** The ring thickens to this on each of an interval's last seconds, with the clock's pop. */
    const val COUNTDOWN_RING_SCALE = 1.35f

    /** A record's two beats under the thumb are this far apart. */
    const val RECORD_BEAT_GAP_MILLIS = 140L

    /** The streak's flame flares to this as the streak grows. */
    const val FLAME_POP_SCALE = 1.25f

    /** The goal ring pops to this as it closes on the week's goal. */
    const val RING_CLOSE_POP_SCALE = 1.06f

    /** A Setup comet covers this much lane a second. */
    const val COMET_SPEED_DP_PER_SECOND = 150

    /** The workout's comets lap the ring in this long: the phase-coloured one, and the dim one further out. */
    const val LEAD_COMET_TURN_MILLIS = 12_000f
    const val CHASE_COMET_TURN_MILLIS = 22_000f

    /** The Setup comets stop where they are after this long without a touch or key, and run on at the next. */
    const val IDLE_MILLIS = 20_000L

    /** A held stepper starts repeating after this long… */
    const val STEPPER_HOLD_MILLIS = 420L

    /** …then steps this often. */
    const val STEPPER_REPEAT_MILLIS = 90L

    /** A page being swiped back shrinks to this, as in Android's own back preview. */
    const val BACK_PREVIEW_SCALE = 0.9f

    /** …and drifts toward the far edge by this share of its width. */
    const val BACK_PREVIEW_SHIFT = 0.05f

    /** The page beneath slides in from this share of its width, on the side being swiped from. */
    const val BACK_PREVIEW_REVEAL_SHIFT = 0.1f
}
