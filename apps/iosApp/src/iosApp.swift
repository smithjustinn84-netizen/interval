import KotlinModules
import UIKit
import UserNotifications

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

/// `PlanReminders.ios.kt`'s `REQUEST_PREFIX`: each plan reminder's identifier starts with it.
private let planReminderPrefix = "plan-reminder-"

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        show(in: windowScene)
    }

    func show(in scene: UIWindowScene) {
        guard window == nil else { return }
        let window = UIWindow(windowScene: scene)
        window.rootViewController = ViewControllerKt.ViewController()
        window.makeKeyAndVisible()
        self.window = window
    }
}
