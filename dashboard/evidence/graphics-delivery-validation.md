# Graphics validation — native check pending

Source: assigned feature worktree, local base `1ca97ff1b2c62484346425f60d756a1c7d3ca02b`. Source/package SHA-256 binding is in `graphics-source-manifest.json`. No final controller commit or integrated review base is available yet. All media in this report came from this implementation and isolated synthetic profiles. Publication is pending the controller.

## Executed checks

- `mvn -q -Dmaven.repo.local=target/maven-repository -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=DetailedMeshSchedulerTest,RenderingAlgorithmsTest,DistantTerrainTest,GraphicsProfileTest,GraphicsTransactionTest,GraphicsDisplayRecoveryTest,PerformanceRecorderTest,GpuTimestampRingTest package`: 34 tests, zero failures/errors. Covers immutable surfaces, edit/unload/revision fencing, queue bounds, opaque border closure, incremental column invalidation, LOD/lighting rules, profile validation/migration/presets/capabilities, transaction rollback/confirmation, crash recovery, capture percentiles and unavailable GPU queries.
- `python3 -m unittest discover -s tools/ios -p 'test_*.py' -q`: 53 mock contract/security tests passed. These do not compile Swift or run the native client.
- `python3 ios/make-project.py`, `bash -n ios/check-simulator.sh`, Python compile checks: passed. Actual Xcode build and XCTest remain unexecuted locally.
- `git diff --check`: passed. Unmerged index is empty. No file deletions appear against the local base. The final integrated review-base audit remains required.

## Playtest: desktop Graphics and regression

Commands: `python3 deploy/run_graphics_playtest.py --display "$DISPLAY" --gl33` and the same command with `--city`. Environment: assigned inherited X11 display; Linux Mesa 22.3.6 llvmpipe, OpenGL 3.3; isolated synthetic profiles; production Main/GLFW/input/UI/render pipeline.

Expected: menu captures input; drafts stay local until Apply; Cancel keeps the renderer; Apply changes actual scene resolution and persists; allocation failure retains working resources; risky display changes allow Keep/Revert; controls remain usable.

Observed: Sandbox and City flows passed. City final run exercised 38 frames: actual Escape/G navigation, Cancel, render scale 0.85 Apply, Custom preset, atomic save/reload, borderless explicit Revert and Keep, oversized-target rejection, F9 start/stop, odd 333x271 window, Controls exit and F6 camera regression. No GL errors. Simulated 200% content scale is readable and scrollable; it is not a Windows scaling test. Screenshots were inspected: `graphics-applied.png`, `graphics-confirm.png`, `graphics-small.png`, `graphics-display-200-percent.png`, `graphics-performance.png`. Small layouts truncate long help lines; F1 opens wrapped help.

## Playtest: actual renderer and F10

Command: `python3 deploy/run_rendering_main_playtest.py --display "$DISPLAY" --gl33`. Expected: asynchronous gallery meshes and lighting become usable, W moves the camera, F10 records, F6 changes camera, resize and Escape remain safe. Observed: passed 43 production frames; LED gallery, particles, water, real W movement, F10 recording, isometric mode, 333x271 temporal upscale and Escape menu; no GL errors or hidden detailed-upload errors. Ordinary draw backend remained active. `graphics-render-playtest.mp4` is the 274063-byte engine F10 capture. Frames decoded with the packaged JCodec library were inspected. `graphics-current-isometric.png` records the camera regression.

Command: `python3 deploy/run_rendering_playtest.py --display "$DISPLAY" --gl33`. Observed: passed. Cold GPU values remain unavailable; completed shadow/probe/frame timings become finite after eight frames; GL errors are absent. Fixture-only `glFinish` forces query readiness; this is not production timing or a baseline. `graphics-current-gl33-gallery.png` was inspected for fractional/LED/material rendering.

## Playtest: recorder integration and CLI

Commands: `python3 deploy/run_rendering_benchmark.py --display "$DISPLAY" --route GROUND --preset LOW --runs 3 --warmup 12 --frames 24 --gl33`; `python3 deploy/compare_performance.py target/performance-replay/GROUND/run-*/performance-*.json --output dashboard/evidence/graphics-software-comparison.json`.

Expected: fixed-seed render-only route, three nonempty exports, comparable settings, bounded mesh queue and no GL errors. Observed: all three runs exited zero, each exported 24 frames after 12 warm-up frames, comparison accepted. Median p50/p95/p99: 1221.089/1572.551/1765.508 ms; median 1% low: 0.566 FPS. These short Low software runs validate recording, not desktop performance, stable warm-up, long-travel bounds or optimization gains. Recorder-copy overhead is separate in each JSON. Driver cache was not cleared. CSV/JSON and the comparison table are attached.

CLI edge checks constructed an empty capture and a changed-resolution capture from sanitized reports. Both returned exit 2 as expected. Browser playtesting does not apply to this native engine/report CLI. Mesh draw/triangle metrics include main/shadow/probe/water submissions, excluding fullscreen/UI/particles. VRAM is an estimate of geometry/targets, not all allocations or driver memory. GPU frame values are delayed EMA; no GPU percentile claim is made.

## Remaining gates

The controller must publish this feature source and run `ios/check-simulator.sh` through the trusted exact-source Mac client workflow. Swift build, native AA/frame-rate/detail Apply/Cancel, persistence, landscape touch layout and control/online regressions need actual returned reports and media inspection. Environment preflight alone is insufficient.

Windows packaged-client controls/inventory/all cameras/reconnect/F10, reference 1080p Balanced performance, observer on/off overhead, all deterministic routes and long travel/edit runs remain follow-up measurements. Presets and 60 FPS/16.7 ms are provisional goals, not achieved results. Physical iPhone GPU/thermal performance, signing and distribution remain separate gates; simulator timings cannot satisfy them.

Controller integration of latest master and the recorded review-base diff audit remain required before review. Any changed source needs refreshed meaningful checks and media. Existing physics, simulation/loading radii, authoritative edits and multiplayer state are not graphics preferences. Intended removals replace synchronous detailed meshing/full-list sorting/fixed upload counts, repeated uniform lookups, blocking production HiZ readback and the default per-mesh compute path. No unrelated deletion was found. Partial lighting uploads and batched submission remain measurement candidates; neither has a claimed gain. This report does not mark release validation complete.

Additional renderer check: `python3 deploy/run_rendering_playtest.py --display "$DISPLAY" --gl33 --msaa 4` passed on actual 4-sample Mesa GL 3.3 targets. Completed queries are finite; GL errors=0. This validates fixture allocation/drawing, not an MSAA performance gain.

Additional Playtest: `python3 deploy/run_rendering_benchmark.py --display "$DISPLAY" --route TELEPORT --preset LOW --runs 1 --warmup 12 --frames 24 --streaming --gl33` passed. It uses the real streamer and crosses a chunk boundary during the fixed route. Every frame checked the four-job bound, detailed upload error state and GL errors. The 24 measured frames and queue/memory values are in `graphics-streaming-teleport.csv/json`. This short run is not long-travel evidence and is not grouped with render-only reports.

## Rebase recovery — intermediate source

Recorded review base: `a7436345bfe20551935c4681331981619048b01a`. Resolved working-file conflicts in `dashboard/progress.json`, `Main.java`, `RenderPipeline.java` and `ControlsMenu.java`. Both task progress entries remain. Rendering (R), Graphics (G), Controls and City Saves remain reachable with separate button bounds. The Rendering menu follows the active renderer after Graphics swaps. Legacy particle/color/cloud controls survive renderer swaps; their extra preferences load without overriding Graphics-owned settings. Common legacy/Graphics preference migration and UI precedence need final integration validation after the controller continues the rebase.

Preserved upstream model shadow submissions, particle work gating and reflection-probe updates independent of world shadows. Removed the duplicate `shadows` field introduced by the textual merge. Intended code removals replace synchronous detailed meshing/sorting/fixed uploads, repeated uniform lookups, blocking production HiZ and default per-mesh compute dispatch. No deleted files were found against the recorded review base. Other merged feature files remain.

Intermediate compile and focused CPU checks are separate from final-head validation. Prior screenshots/videos and performance reports are historical for this integrated source until refreshed. Playtest: not rerun during the pending rebase; controller staging/continuation comes first, followed by exact resulting-source workflow tests and fresh media. Native Mac validation also remains pending. No Git staging, continuation, commit, reset, abort, push or merge was performed by the developer.

Recovery commands: `mvn -q -Dmaven.repo.local=target/maven-repository -DskipTests compile` passed. Initial focused test invocation failed because `/tmp` is read-only. Corrected command: `mvn -q -Dmaven.repo.local=target/maven-repository -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RenderingAlgorithmsTest,GraphicsProfileTest,GraphicsTransactionTest,GraphicsDisplayRecoveryTest,DetailedMeshSchedulerTest test` passed 22 tests with zero failures/errors. Conflict-marker check for all four files, progress JSON parsing and `git diff --check` passed. The index remains unmerged until controller staging.
