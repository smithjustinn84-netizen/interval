import ActivityKit
import Foundation

/// The app's Live Activities, one a key, as Kotlin's `LiveActivities` (core-platform) asks for them through
/// `WidgetFeedKt.startWidgetFeed`: ActivityKit has no Objective-C face for Kotlin to call. iOS starts one only while
/// the app is on screen, which is when the workout and Lift's timers start; after that, changes and the end can come
/// at any time the app runs.
enum LiveActivities {
    private static var running: [String: Activity<IntervalActivityAttributes>] = [:]

    /// At launch: whatever a run before this one left showing (its task swiped away, a crash) is ended. A workout or
    /// session restored starts its own again.
    static func endLeftovers() {
        for activity in Activity<IntervalActivityAttributes>.activities {
            Task { await activity.end(nil, dismissalPolicy: .immediate) }
        }
    }

    /// Starts the activity under `key`, or changes it, to the `LiveActivityState` in `json`.
    static func show(key: String, json: String) {
        guard let state = try? JSONDecoder().decode(IntervalActivityAttributes.ContentState.self, from: Data(json.utf8))
        else {
            print("LiveActivities: couldn't read the state for \(key)")
            return
        }
        let content = ActivityContent(state: state, staleDate: state.staleDate)
        if let activity = running[key], activity.activityState == .active {
            Task { await activity.update(content) }
            return
        }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        do {
            running[key] = try Activity.request(
                attributes: IntervalActivityAttributes(key: key),
                content: content,
                pushType: nil
            )
        } catch {
            print("LiveActivities: couldn't start \(key): \(error)")
        }
    }

    /// Ends the activity under `key` at once: the app shows what came of it.
    static func end(key: String) {
        guard let activity = running.removeValue(forKey: key) else { return }
        Task { await activity.end(nil, dismissalPolicy: .immediate) }
    }
}
