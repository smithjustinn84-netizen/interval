package dev.justinsmith.interval.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    /** Maximum width of the Timer tab's column (its right-hand column sideways), when the window has room. */
    val contentWide = 760.dp

    /** Narrowest column of the Presets and History grids; wider windows fit more columns. */
    val listColumn = 360.dp

    /** Maximum width of the Presets and History grids: two columns at most, so rows stay readable. */
    val listMax = 960.dp

    /**
     * Width of a list beside its detail on a wide window (History beside a workout): a phone's, so the list
     * lays out as it does on a phone.
     */
    val listPane = 412.dp

    /** Width of Settings when it opens beside the Timer. */
    val settingsPane = 400.dp

    /**
     * Narrowest window that opens Settings beside the Timer rather than over it: room for the rail,
     * [settingsPane] and a Timer still as wide as a phone's.
     */
    val twoPaneMinWidth = 1000.dp

    /** Narrowest window that gets a navigation rail instead of a bottom bar, as Material's medium width. */
    val railMinWidth = 600.dp

    /** The line between the content and the bottom navigation bar. */
    val hairline = 1.dp

    /**
     * Width of the fixed column on a screen laid out sideways: the Timer's total and Start, a session's clock
     * and Log set, an EMOM's controls, a summary's way out.
     */
    val setupColumnLandscape = 380.dp

    /** Width of each side rail of workout controls in landscape. */
    val rail = 120.dp

    /** Start workout's height, and the Timer tab's square start button. */
    val start = 72.dp

    /** Elevation of Start workout, the only raised element (`shadow-start`). */
    val startElevation = 8.dp

    /** The play glyph inside Start workout. */
    val playIcon = 26.dp

    /** A workout control (Pause, End, Skip), and the wider Back and +10s beside them. */
    val control = 76.dp
    val controlSide = 84.dp

    /** A − / + stepper button. */
    val stepper = 54.dp

    /** A pill-shaped button: its narrowest, and its height (a touch target's). */
    val pillWidth = 72.dp
    val pillHeight = 48.dp

    /** The largest a Timer tab stepper value grows to when the row has room; it never shrinks below a stat. */
    val stepperValueMax = 72.sp

    /** A value in its phase-tinted pill on the Timer tab. */
    val valuePill = 48.dp

    /** The smaller value pill on a Settings row. */
    val valuePillSmall = 40.dp

    /** The hero card's workout timeline, and the gap between its segments. */
    val timeline = 12.dp
    val timelineGap = 2.dp

    /** The timeline on a preset chip or card, and the gap between its segments. */
    val timelineMini = 6.dp
    val timelineMiniGap = 1.dp

    /** The tallest bar of the History tab's week chart. */
    val weekBar = 56.dp

    /** A day with no workout in the week chart. */
    val weekBarEmpty = 6.dp

    /** A planned or missed day in the week chart: an outlined or filled block this tall. */
    val weekBarMark = 32.dp

    /**
     * A day on a program's map: a round mark, smaller than the week strip's so seven and their week's label fit
     * a phone's card.
     */
    val programDayMark = 20.dp

    /** The ring of this week's workouts against the weekly goal, and its stroke. */
    val goalRing = 76.dp
    val goalRingStroke = 8.dp

    /** A swatch in the week chart's key. */
    val legendSwatch = 10.dp

    /** A day on History's month calendar: the cell that takes the tap, and the round mark of its state. */
    val calendarCell = 48.dp
    val calendarDay = 34.dp

    /** The short bar under today on the calendar. */
    val todayMarkWidth = 14.dp
    val todayMarkHeight = 3.dp

    /** The tallest bar of All time's weekly chart. */
    val weeksChart = 96.dp

    /** The small glyph inside a chip, such as the streak's flame. */
    val chipIcon = 16.dp

    /** The pill of phase colour at the start of a History row. */
    val historyMarkHeight = 40.dp

    /** One choice of a segmented picker, such as the theme's. */
    val segment = 48.dp

    /** The stripe beside a value sheet's title, as tall as the title and its hint. */
    val sheetHeaderStripe = 48.dp

    /** A bottom sheet's drag handle: the area that takes the drag, round the small bar drawn in it. */
    val dragHandleWidth = 48.dp
    val dragHandleHeight = 24.dp

    /** The bar drawn in a bottom sheet's drag handle. */
    val handleWidth = 36.dp
    val handleHeight = 5.dp

    /** The narrowest a text field in a dialog or sheet gets. */
    val fieldMin = 230.dp

    /** Room the workout leaves under its ring for the "Tap anywhere to pause" line. */
    val tapHint = 48.dp

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

    /** A phase's colour stripe at the start of a field row, and a history mark's width. */
    val stripe = 6.dp

    /** A round's mark on the workout screen: a short bar, narrowing when many share a row. */
    val roundMarkWidth = 24.dp
    val roundMarkHeight = 6.dp

    /** The ring around Done on the summary. */
    val summaryRing = 260.dp

    /** The workout's progress ring: its stroke, and its widest on a big window. */
    val ring = 22.dp
    val ringMax = 420.dp

    /** An EMOM's ring at its widest on a tablet or a desktop, where the readout sits inside it at any turn. */
    val ringMaxRoomy = 560.dp

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

    /** The tinted ring round an [EmptyState]'s icon. */
    val emptyIconRing = 56.dp

    /** The empty-state icon drawn inside it, a size up from [icon]. */
    val emptyIcon = 28.dp

    /** The app's mark on the About screen: the launcher icon's coin, at a size that reads as a logo. */
    val aboutMark = 112.dp

    /** Minimum height of a list row (presets, history, settings). */
    val listRow = 64.dp

    /** Minimum height of a Timer tab interval row. */
    val intervalRow = 72.dp

    /** The edit sheet's − / + buttons, a size up from [stepper]. */
    val sheetStepper = 68.dp

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

    /** The resume hint breathes while paused: a cycle this long, down to this alpha. */
    const val BREATH_MILLIS = 1600
    const val BREATH_MIN_ALPHA = 0.5f

    /** A Setup comet covers this much lane a second. */
    const val COMET_SPEED_DP_PER_SECOND = 150

    /** The workout's comets lap the ring in this long: the phase-coloured one, and the dim one further out. */
    const val LEAD_COMET_TURN_MILLIS = 12_000f
    const val CHASE_COMET_TURN_MILLIS = 22_000f

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
