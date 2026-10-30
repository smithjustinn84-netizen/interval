import KotlinModules
import UIKit

// UIKit rather than SwiftUI: iOS takes the status bar style from the window's root controller, and the
// app sets it to suit each screen (`StatusBarHost`). SwiftUI's own root would ignore it.
@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
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
        return true
    }
}

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
