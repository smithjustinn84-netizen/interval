import ActivityKit
import SwiftUI

// The Live Activity's type, the one file both targets compile (the synchronized `shared/` folder): the app starts,
// changes and ends activities of it (`LiveActivities.swift`), the widget extension draws them
// (`WorkoutLiveActivity.swift`). One type for the workout and Lift's timers, told apart by `key`.

/// A timer running off screen: the interval workout (`workout`) or a Lift run (`lift`), as `LiveActivityContent`
/// (core-platform) words it. Nonisolated: the app's code is the main actor's by default, and ActivityKit encodes
/// this anywhere.
nonisolated struct IntervalActivityAttributes: ActivityAttributes {
    /// `LiveActivityState` in apps/app/src/data/LiveActivityState.kt, as `WidgetFeed.kt` hands it over: the names
    /// are the JSON's. Times are epoch milliseconds, colours signed ARGB.
    struct ContentState: Codable, Hashable {
        var title: String
        var detail: String?
        var staleTitle: String?
        var color: Int?
        var paused: Bool
        var pausedClock: String?
        var intervalStart: Int64?
        var intervalEnd: Int64?
        var runStart: Int64?
        var runEnd: Int64?
        var runLabel: String?
        var progress: Double
        var segments: [Segment]
        /// The workout's buttons' words (`WorkoutIntents.swift`); nil for none.
        var toggleLabel: String?
        var endLabel: String?

        struct Segment: Codable, Hashable {
            var length: Int
            var color: Int?
        }
    }

    var key: String
}

extension IntervalActivityAttributes.ContentState {
    /// The interval now, which the system counts down; nil while paused or done.
    var interval: ClosedRange<Date>? { range(intervalStart, intervalEnd) }

    /// The whole workout; nil for a Lift run, which is all interval.
    var run: ClosedRange<Date>? { range(runStart, runEnd) }

    /// Past the interval's end the app, suspended, hasn't said what came next.
    var staleDate: Date? { interval?.upperBound }

    private func range(_ start: Int64?, _ end: Int64?) -> ClosedRange<Date>? {
        guard let start, let end, end > start else { return nil }
        return date(start)...date(end)
    }

    private func date(_ millis: Int64) -> Date { Date(timeIntervalSince1970: Double(millis) / 1000) }
}

extension Color {
    /// A colour as Kotlin's ARGB `Int`, which arrives signed.
    init(argb: Int) {
        let value = UInt32(truncatingIfNeeded: argb)
        self.init(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255,
            opacity: Double((value >> 24) & 0xFF) / 255
        )
    }
}
