import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window:UIWindow?
    func application(_ application:UIApplication,didFinishLaunchingWithOptions launchOptions:[UIApplication.LaunchOptionsKey:Any]?=nil)->Bool {
        let window=UIWindow(frame:UIScreen.main.bounds)
        window.rootViewController=GameController();window.makeKeyAndVisible();self.window=window
        return true
    }
    func applicationWillResignActive(_ application:UIApplication) { (window?.rootViewController as? GameController)?.suspend() }
    func applicationDidBecomeActive(_ application:UIApplication) { (window?.rootViewController as? GameController)?.becameActive() }
    func applicationDidEnterBackground(_ application:UIApplication) { (window?.rootViewController as? GameController)?.saveOffline() }
}
