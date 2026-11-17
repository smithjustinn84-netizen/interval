import ActivityKit
import SwiftUI
import WidgetKit

// The workout and Lift's timers on the Lock Screen and in the Dynamic Island, Android's Live Updates on iOS. The
// app, suspended off screen, can't change an activity, so everything that moves is the system's own timer text and
// progress: the interval counting down, and under it the whole workout's time left over its bar, which stays right
// after the interval has ended. Past that end (the activity's stale date) the phase is no longer known, so its line
// says to open the app; the workout's clock and bar carry on.

typealias ActivityState = IntervalActivityAttributes.ContentState

struct WorkoutLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: IntervalActivityAttributes.self) { context in
            ActivityLockScreen(state: context.state, stale: context.isStale)
                .activityBackgroundTint(Palette.card)
                .activitySystemActionForegroundColor(Palette.onGround)
        } dynamicIsland: { context in
            let state = context.state
            let stale = context.isStale
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    ActivityTitle(state: state, stale: stale)
                        .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    IntervalClock(state: state, stale: stale)
                        .font(.system(size: 32, weight: .semibold, design: .rounded))
                        .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 10) {
                        RunLine(state: state)
                        WorkoutButtons(state: state)
                    }
                    .padding(.horizontal, 4)
                }
            } compactLeading: {
                Circle()
                    .fill(state.tint)
                    .frame(width: 10, height: 10)
            } compactTrailing: {
                CompactClock(state: state, stale: stale)
                    .foregroundStyle(state.tint)
            } minimal: {
                CompactClock(state: state, stale: stale)
                    .foregroundStyle(state.tint)
            }
            .keylineTint(state.tint)
        }
    }
}

extension ActivityState {
    var tint: Color { color.map(Color.init(argb:)) ?? Palette.done }
}

struct ActivityLockScreen: View {
    let state: ActivityState
    let stale: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center) {
                ActivityTitle(state: state, stale: stale)
                Spacer(minLength: 8)
                IntervalClock(state: state, stale: stale)
                    .font(.system(size: 40, weight: .semibold, design: .rounded))
            }
            RunLine(state: state)
            WorkoutButtons(state: state)
        }
        .padding(16)
    }
}

/// The phase or the run, and the round under it; once stale, the line that says to open the app.
struct ActivityTitle: View {
    let state: ActivityState
    let stale: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if stale, let staleTitle = state.staleTitle {
                Text(staleTitle)
                    .font(.headline)
                    .foregroundStyle(Palette.onGround)
            } else {
                Text(state.title)
                    .font(.headline)
                    .foregroundStyle(state.tint)
                if let detail = state.detail {
                    Text(detail)
                        .font(.subheadline)
                        .foregroundStyle(Palette.onGroundDim)
                }
            }
        }
        .lineLimit(1)
    }
}

/// The interval's time left: the system's countdown, or where it stopped while paused or done; nothing once stale.
struct IntervalClock: View {
    let state: ActivityState
    let stale: Bool

    var body: some View {
        Group {
            if !state.paused, !stale, let interval = state.interval {
                Text(timerInterval: interval, countsDown: true)
            } else if state.paused || state.interval == nil, let clock = state.pausedClock {
                Text(clock)
            }
        }
        .monospacedDigit()
        .multilineTextAlignment(.trailing)
        .foregroundStyle(Palette.onGround)
        .frame(maxWidth: 120, alignment: .trailing)
    }
}

/// The Dynamic Island's own corner: the interval while it is known, then the whole workout's time left.
struct CompactClock: View {
    let state: ActivityState
    let stale: Bool

    var body: some View {
        Group {
            if state.paused, let clock = state.pausedClock {
                Text(clock)
            } else if !stale, let interval = state.interval {
                Text(timerInterval: interval, countsDown: true)
            } else if let run = state.run {
                Text(timerInterval: run, countsDown: true)
            }
        }
        .monospacedDigit()
        .font(.caption.weight(.semibold))
        .frame(maxWidth: 48)
    }
}

/// The bar of the whole run, and the workout's time left beside its name.
struct RunLine: View {
    let state: ActivityState

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            SegmentBar(state: state)
                .frame(height: 6)
            if let label = state.runLabel, !state.paused, let run = state.run {
                HStack {
                    Text(label)
                        .foregroundStyle(Palette.onGroundDim)
                    Spacer()
                    Text(timerInterval: run, countsDown: true)
                        .monospacedDigit()
                        .multilineTextAlignment(.trailing)
                        .foregroundStyle(Palette.onGround)
                        .frame(maxWidth: 80, alignment: .trailing)
                }
                .font(.footnote)
            }
        }
    }
}

/// Each segment in its colour, the part gone bright and the rest dim: the system's progress over the run (or the
/// interval, for a Lift run) is the mask, so it moves with the app suspended. Paused, the mask is where it stopped.
struct SegmentBar: View {
    let state: ActivityState

    var body: some View {
        ZStack {
            segments.opacity(0.3)
            segments.mask(alignment: .leading) { progress }
        }
        .clipShape(Capsule())
    }

    private var segments: some View {
        GeometryReader { geometry in
            let total = max(1, state.segments.reduce(0) { $0 + $1.length })
            let gaps = CGFloat(max(0, state.segments.count - 1)) * 2
            let width = max(0, geometry.size.width - gaps)
            HStack(spacing: 2) {
                if state.segments.isEmpty {
                    Rectangle().fill(state.tint)
                }
                ForEach(Array(state.segments.enumerated()), id: \.offset) { _, segment in
                    Rectangle()
                        .fill(segment.color.map(Color.init(argb:)) ?? state.tint)
                        .frame(width: width * CGFloat(segment.length) / CGFloat(total))
                }
            }
        }
    }

    @ViewBuilder private var progress: some View {
        if !state.paused, let span = state.run ?? state.interval {
            ProgressView(timerInterval: span, countsDown: false) {
                EmptyView()
            } currentValueLabel: {
                EmptyView()
            }
            .progressViewStyle(.linear)
            .tint(.white)
            .scaleEffect(x: 1, y: 3, anchor: .center)
        } else {
            GeometryReader { geometry in
                Rectangle().frame(width: geometry.size.width * min(1, max(0, state.progress)))
            }
        }
    }
}

/// Pause (or Resume) and End, when the workout has them (`WorkoutIntents.swift`): they act in the app's process,
/// which changes the activity as it does.
struct WorkoutButtons: View {
    let state: ActivityState

    var body: some View {
        if let toggle = state.toggleLabel, let end = state.endLabel {
            HStack(spacing: 8) {
                Button(intent: ToggleWorkoutPauseIntent()) {
                    Text(toggle)
                        .frame(maxWidth: .infinity)
                }
                .tint(state.paused ? Palette.done : Palette.upcoming)
                Button(intent: EndWorkoutIntent()) {
                    Text(end)
                        .frame(maxWidth: .infinity)
                }
                .tint(Palette.upcoming)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Palette.onGround)
        }
    }
}
