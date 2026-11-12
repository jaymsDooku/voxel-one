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
// Include supported low-resolution variants; never invent a display mode.
let options = [kCGDisplayShowDuplicateLowResolutionModes: true] as CFDictionary
let modes = (CGDisplayCopyAllDisplayModes(display, options) as? [CGDisplayMode]) ?? []
report["advertisedModes"] = Array(Set(modes.map { "\($0.width)x\($0.height)" })).sorted()
// 2560x1600 is preferred for scaled tests. A smaller advertised desktop can
// still unblock the unscaled 1280x800 case. Keep all unmet gates explicit.
// Reserve 100 logical pixels for the menu bar and window decorations.
// Do not shrink either current dimension when choosing a fallback.
let candidates = modes.filter {
    $0.width >= max(1280, initial.width) && $0.height >= max(900, initial.height)
}
let preferred = candidates.filter { $0.width >= 2560 && $0.height >= 1600 }
let choice = (preferred.isEmpty ? candidates : preferred).min {
    $0.width * $0.height < $1.width * $1.height
}
if initial.width >= 2560 && initial.height >= 1600 {
    report["status"] = "already sufficient"
} else if let choice = choice {
    // Keep the advertised mode for subsequent Java processes in this login
    // session. A process-scoped change can revert when this helper exits.
    // Never persist a permanent display preference.
    var configuration: CGDisplayConfigRef?
    var result = CGBeginDisplayConfiguration(&configuration)
    if result == .success, let configuration = configuration {
        result = CGConfigureDisplayWithDisplayMode(configuration, display, choice, nil)
        if result == .success {
            result = CGCompleteDisplayConfiguration(configuration, .forSession)
        } else {
            CGCancelDisplayConfiguration(configuration)
        }
    }
    report["configurationScope"] = "ephemeral login session"
    report["changeResult"] = result.rawValue
    report["status"] = result == .success ? "changed" : "unsupported"
} else {
    report["status"] = "unsupported"
}
guard let actual = CGDisplayCopyDisplayMode(display) else { exit(1) }
report["after"] = [actual.width, actual.height]
report["sufficient"] = actual.width >= 2560 && actual.height >= 1600
report["unscaledWindowCandidate"] = actual.width >= 1280 && actual.height >= 900
report["qualification"] = "Actual available window bounds are checked by the Swing playtest; mode selection alone is not a pass."
let platform = environment["PLATFORM"] == "mac-arm" ? "mac-arm" : "mac-intel"
let directory = URL(fileURLWithPath: "dashboard/evidence/hub-scoped-" + platform)
try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]).write(to: directory.appendingPathComponent("desktop.json"))
