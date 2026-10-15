package dev.justinsmith.interval.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Fixed design tokens: the same in light and dark. Names mirror the Interval design system
// (`space-16` → Spacing.space16, `radius-18` → Radius.radius18, `size-stepper` → Sizes.stepper).

/** Gaps and padding: the design system's `space-*` tokens. */
object Spacing {
    val space4 = 4.dp
    val space6 = 6.dp
    val space8 = 8.dp
    val space10 = 10.dp
    val space12 = 12.dp
    val space14 = 14.dp
    val space16 = 16.dp
    val space18 = 18.dp
    val space20 = 20.dp

    /** Room a custom preset card keeps clear for its delete button. */
    val space24 = 24.dp
    val space26 = 26.dp
    val space28 = 28.dp

    /** Bottom inset of the setup list, so the floating Start button never covers the last row. */
    val space112 = 112.dp
}

/** Corner radii: the design system's `radius-*` tokens. Radii grow with the element. */
object Radius {
    /** Stepper buttons, workout pills. */
    val radius12 = 12.dp

    /** Preset cards, the save controls. */
    val radius14 = 14.dp

    /** Workout bottom controls. */
    val radius16 = 16.dp

    /** Field cards, the history card, Start workout. */
    val radius18 = 18.dp
}

/** The [Radius] tokens as shapes, plus `radius-full`. */
object Corners {
    val radius12 = RoundedCornerShape(Radius.radius12)
    val radius14 = RoundedCornerShape(Radius.radius14)
    val radius16 = RoundedCornerShape(Radius.radius16)
    val radius18 = RoundedCornerShape(Radius.radius18)

    /** Progress bar track and fill. */
    val full = RoundedCornerShape(50)

    /** A bottom sheet: rounded on top only. */
    val sheet = RoundedCornerShape(topStart = Radius.radius18, topEnd = Radius.radius18)
}

/** Fixed dimensions: the design system's `size-*` tokens, mostly touch targets. */
object Sizes {
    /** Maximum width of the setup column. */
    val content = 460.dp

    /** Maximum width of the Timer tab's right-hand column of choices, when the window has room. */
    val contentWide = 760.dp

    /** Narrowest Intervals section that lays its rows out in two columns. */
    val intervalGridMin = 640.dp

    /** Narrowest column of the Presets and History grids; wider windows fit more columns. */
    val listColumn = 360.dp

    /** Maximum width of the Presets and History grids: two columns at most, so rows stay readable. */
    val listMax = 960.dp

    /** Narrowest window that gets a navigation rail instead of a bottom bar, as Material's medium width. */
    val railMinWidth = 600.dp

    /** The line between the content and the bottom navigation bar. */
    val hairline = 1.dp

    /**
     * Wider-than-tall windows get the landscape layouts; those shorter than this are phones held
     * sideways, which keep the workout clock at its smaller landscape cap.
     */
    val landscapeMaxHeight = 480.dp

    /** Width of the setup column holding the header, presets and Start in landscape. */
    val setupColumnLandscape = 380.dp

    /** Width of each side rail of workout controls in landscape. */
    val rail = 120.dp
    val start = 68.dp

    /** Elevation of Start workout, the only raised element (`shadow-start`). */
    val startElevation = 8.dp
    val control = 68.dp
    val controlLandscape = 54.dp
    val controlSide = 84.dp
    val stepper = 54.dp
    val pillWidth = 72.dp
    val pillHeight = 48.dp
    val toggleRow = 56.dp
    val save = 52.dp
    val delete = 32.dp
    val valueMin = 64.dp
    val stripe = 6.dp
    val dot = 10.dp

    /** Width of the ring around the current round dot. */
    val dotHalo = 3.dp
    val progress = 8.dp
    val border = 1.5.dp

    /** Line icons: tabs and top-bar actions. */
    val icon = 24.dp

    /** The round hit area of an icon-only top-bar action. */
    val iconButton = 48.dp

    /** Height of the bottom navigation bar, and width of the rail that replaces it sideways. */
    val navBar = 80.dp

    /** The pill behind the selected tab's icon. */
    val navIndicatorWidth = 64.dp
    val navIndicatorHeight = 32.dp

    /** A preset chip in the Timer tab's horizontal row. */
    val presetChip = 148.dp

    /** Minimum height of a list row (presets, history, settings). */
    val listRow = 64.dp

    /** Minimum height of a Timer tab interval row. */
    val intervalRow = 68.dp

    /** The edit sheet's − / + buttons, a size up from [stepper]. */
    val sheetStepper = 64.dp

    /** The edit sheet's drag handle. */
    val handleWidth = 32.dp
    val handleHeight = 4.dp
}

/** The design system's `opacity-*` tokens. */
object Opacity {
    /** The dashed save button when the current settings already match a preset. */
    const val DISABLED = 0.45f

    /** A − or + glyph at the end of its range. */
    const val DISABLED_GLYPH = 0.5f
}

/** Timings from the design system's Motion section. */
object Motion {
    /** Background crossfade between phases, and into and out of pause. */
    const val PHASE_CROSSFADE_MILLIS = 350

    /** The clock's pop when a new interval starts, from [CLOCK_POP_SCALE] back to 1. */
    const val CLOCK_POP_MILLIS = 300
    const val CLOCK_POP_SCALE = 1.06f

    /** A held stepper starts repeating after this long… */
    const val STEPPER_HOLD_MILLIS = 420L

    /** …then steps this often. */
    const val STEPPER_REPEAT_MILLIS = 90L
}
