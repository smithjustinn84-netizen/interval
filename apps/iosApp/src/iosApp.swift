import KotlinModules
import UIKit
import UserNotifications
import WidgetKit

// UIKit rather than SwiftUI: iOS takes the status bar style from the window's root controller, and the
// app sets it to suit each screen (`StatusBarHost`). SwiftUI's own root would ignore it.
@main
final class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // A scene session restored from the SwiftUI shell keeps SwiftUI's delegate rather than Info.plist's,
        // and would open on an empty window. Adopt it.
        NotificationCenter.default.addObserver(forName: UIScene.willConnectNotification, object: nil, queue: nil) { note in
            guard let scene = note.object as? UIWindowScene, !(scene.delegate is SceneDelegate) else { return }
            let delegate = SceneDelegate()
            scene.delegate = delegate
            delegate.show(in: scene)
        }
        UNUserNotificationCenter.current().delegate = self
        // Before the container starts: a workout it restores shows its own.
        LiveActivities.endLeftovers()
        // The workout's Live Activity's buttons (`WorkoutIntents.swift`), which run here.
        WorkoutControls.togglePause = { WidgetFeedKt.toggleWorkoutPause() }
        WorkoutControls.end = { WidgetFeedKt.endWorkout() }
        // The Home Screen widget's week, kept current from here on (`WidgetFeed.kt`), and the workout's and Lift's
        // timers' Live Activities: WidgetKit and ActivityKit are Swift alone. Kotlin calls on the main thread; the
        // hop keeps each call in order behind the one before.
        WidgetFeedKt.startWidgetFeed(
            reload: { WidgetCenter.shared.reloadTimelines(ofKind: weekWidgetKind) },
            showActivity: { key, json in
                DispatchQueue.main.async { MainActor.assumeIsolated { LiveActivities.show(key: key, json: json) } }
            },
            endActivity: { key in
                DispatchQueue.main.async { MainActor.assumeIsolated { LiveActivities.end(key: key) } }
            }
        )
        return true
    }

    // A plan reminder due while the app is open shows as it would outside it. The workout's phase changes and the
    // rest's end stay silent here: the app on screen beeps and speaks them itself.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let planReminder = notification.request.identifier.hasPrefix(planReminderPrefix)
        completionHandler(planReminder ? [.banner, .list, .sound] : [])
    }
}

/// `WeekWidget.swift`'s kind.
private let weekWidgetKind = "WeekWidget"

/// `PlanReminders.ios.kt`'s `REQUEST_PREFIX`: each plan reminder's identifier starts with it.
private let planReminderPrefix = "plan-reminder-"

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        show(in: windowScene)
        // Launched by a quick action from the Home Screen: open it once the app is ready, as a running app does.
        if let item = connectionOptions.shortcutItem {
            _ = ViewControllerKt.openShortcut(type: item.type)
        }
        // Launched by the widget's Workout or Lift: the same, by its link.
        if let url = connectionOptions.urlContexts.first?.url {
            open(url)
        }
    }

    // The widget's Workout or Lift tapped while the app runs.
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        if let url = URLContexts.first?.url {
            open(url)
        }
    }

    /// `interval://shortcut/<id>`, Android's form: the quick action whose type is the id.
    private func open(_ url: URL) {
        guard url.scheme == "interval", url.host == "shortcut" else { return }
        _ = ViewControllerKt.openShortcut(type: url.lastPathComponent)
    }

    // A quick action chosen while the app runs, in the background or on screen. Its type is a `Shortcut.id`.
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(ViewControllerKt.openShortcut(type: shortcutItem.type))
    }

    func show(in scene: UIWindowScene) {
        guard window == nil else { return }
        let window = UIWindow(windowScene: scene)
        window.rootViewController = ViewControllerKt.ViewController()
        window.makeKeyAndVisible()
        self.window = window
    }
}
