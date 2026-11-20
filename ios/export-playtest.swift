// Host-side macOS utility. Export only the passing offline streaming test's clip.
// Raw suite video and timing stay in ignored ios/build; no external encoder needed.
import Foundation
import AVFoundation

struct ClipWindow:Decodable {var offsetSeconds:Double;var durationSeconds:Double}
enum ClipError:Error {case unsupportedHost,invalidRange,missingExporter,failedExport,oversized}

// Host uses macOS15+ / Xcode16+. The game itself still targets iOS16+.
// Apple: https://developer.apple.com/documentation/avfoundation/exporting-video-to-alternative-formats
@available(macOS 15.0, *)
func exportClip()async throws {
    guard CommandLine.arguments.count==4 else {throw ClipError.invalidRange}
    let input=URL(fileURLWithPath:CommandLine.arguments[1])
    let output=URL(fileURLWithPath:CommandLine.arguments[2])
    let timing=URL(fileURLWithPath:CommandLine.arguments[3])
    let window=try JSONDecoder().decode(ClipWindow.self,from:Data(contentsOf:timing))
    guard window.offsetSeconds.isFinite,window.durationSeconds.isFinite,
          (0...900).contains(window.offsetSeconds),(1...40).contains(window.durationSeconds) else {throw ClipError.invalidRange}
    let asset=AVURLAsset(url:input),duration=try await asset.load(.duration)
    let available=CMTimeGetSeconds(duration)-window.offsetSeconds
    guard available.isFinite,available>=1 else {throw ClipError.invalidRange}
    guard let exporter=AVAssetExportSession(asset:asset,presetName:AVAssetExportPreset640x480) else {throw ClipError.missingExporter}
    exporter.shouldOptimizeForNetworkUse=true;exporter.metadata=[];exporter.fileLengthLimit=5_500_000
    exporter.timeRange=CMTimeRange(start:CMTime(seconds:window.offsetSeconds,preferredTimescale:600),
                                  duration:CMTime(seconds:min(available,window.durationSeconds),preferredTimescale:600))
    try await exporter.export(to:output,as:.mp4)
    let bytes=try FileManager.default.attributesOfItem(atPath:output.path)[.size] as? NSNumber
    guard let bytes=bytes,bytes.intValue>16,bytes.intValue<6_000_000 else {throw ClipError.oversized}
}
if #available(macOS 15.0, *) {try await exportClip()}else{throw ClipError.unsupportedHost}
