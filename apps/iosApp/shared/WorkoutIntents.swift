import AppIntents

// The workout's Live Activity buttons, Pause (or Resume) and End, as Android's notification has them. A
// `LiveActivityIntent` runs in the app's process, woken from suspension if need be, even though the button is drawn by
// the widget extension: so both targets compile these, and only the app's copy ever performs.

/// What the buttons do, which the app sets at launch (`iosApp.swift`, through `WidgetFeedKt`). Nil in the widget
/// extension, which links no Kotlin and never performs them.
nonisolated enum WorkoutControls {
    nonisolated(unsafe) static var togglePause: (@MainActor @Sendable () -> Void)?
    nonisolated(unsafe) static var end: (@MainActor @Sendable () -> Void)?
}

nonisolated struct ToggleWorkoutPauseIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Pause or resume the workout"
    static let isDiscoverable = false

    init() {}

    func perform() async throws -> some IntentResult {
        await MainActor.run { WorkoutControls.togglePause?() }
        return .result()
    }
}

nonisolated struct EndWorkoutIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "End the workout"
    static let isDiscoverable = false

    init() {}

    func perform() async throws -> some IntentResult {
        await MainActor.run { WorkoutControls.end?() }
        return .result()
    }
}
