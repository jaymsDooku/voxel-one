import Foundation
import CoreGraphics

// Change only an advertised mode on this assignment's ephemeral Actions desktop.
let environment = ProcessInfo.processInfo.environment
let branch = "refs/heads/feature/queue-62383835386364312d346130642d346262652d383736632d393534373062343337356161"
guard environment["GITHUB_ACTIONS"] == "true",
      environment["GITHUB_REPOSITORY"] == "jaymsDooku/voxel-one",
      environment["GITHUB_REF"] == branch else {
    print("Fenced ephemeral Mac desktop required")
    exit(1)
}
let display = CGMainDisplayID()
guard let initial = CGDisplayCopyDisplayMode(display) else { exit(1) }
var report: [String: Any] = ["before": [initial.width, initial.height], "required": [2560, 1600],
    "limitation": "Resolution setup only; no privacy permission or approval control is changed."]
let modes = (CGDisplayCopyAllDisplayModes(display, nil) as? [CGDisplayMode]) ?? []
if initial.width >= 2560 && initial.height >= 1600 {
    report["status"] = "already sufficient"
} else if let choice = modes.filter({ $0.width >= 2560 && $0.height >= 1600 }).min(by: {
    $0.width * $0.height < $1.width * $1.height
}) {
    let result = CGDisplaySetDisplayMode(display, choice, nil)
    report["changeResult"] = result.rawValue
    report["status"] = result == .success ? "changed" : "unsupported"
} else {
    report["status"] = "unsupported"
}
guard let actual = CGDisplayCopyDisplayMode(display) else { exit(1) }
report["after"] = [actual.width, actual.height]
report["sufficient"] = actual.width >= 2560 && actual.height >= 1600
let platform = environment["PLATFORM"] == "mac-arm" ? "mac-arm" : "mac-intel"
let directory = URL(fileURLWithPath: "dashboard/evidence/hub-scoped-" + platform)
try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]).write(to: directory.appendingPathComponent("desktop.json"))
