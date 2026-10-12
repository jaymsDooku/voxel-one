# External combat camera and Canvas performance

After waking, the camera now moves over 0.8 seconds from the impact view to an external view 1,800 m above the same landing anchor. Terrain, profile revision, sun, moon directions/phases and FOV remain fixed. Wreckage projects through the current camera. Pause freezes the handoff. Combat coordinates, collisions and flight/fire are unchanged.

Canvas Low is now the default reduced-detail path. Parent tiles cover the whole landing patch; sparse child tiles overlap them. Each tile retains one tall landmark at its original world position. Plans are built per terrain/quality revision, not every frame. Low/Medium/High cap attempted terrain faces at 640/2,048/4,096. Measured preparation peaks were 315 faces Low and 787 Medium; High's worst-case plan bounds are unit-tested, not a browser performance claim.

World buffers cap at 320/480/640 pixels wide (height at most 3/4 of that). Cockpit/HUD remain on the main Canvas. Worker world images and bounded terrain faces are composed once during prewarming and blended during playback. The cache stays capped at 49 images. Missing assets retain the bounded cold surface. Unchanged HUD/accessibility text no longer causes repeated DOM writes.

Environment: Linux x86_64 VPS, 4 logical CPUs, Intel Core Processor (Haswell, no TSX). Chromium 153.0.8010.12 and software WebKit 26.6. Fresh isolated synthetic profiles, desktop 1280x900, Canvas, no video or concurrent test workload from this work item during isolated benchmarks. Shared VPS background load is not controlled. Browser version report: `orbital-browser-versions.json`.

## Isolated benchmarks

Actual commands: `ORBITAL_BROWSER=Chromium|WebKit ORBITAL_QUALITY=Low|Medium ORBITAL_PREWARM=ready PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_orbital_benchmark.cjs`, one browser/quality per invocation. Omit `ORBITAL_PREWARM` for the three-second-prewarm Low runs. Six completed runs are saved as `orbital-isolated-*.json`. Fully ready means all 49 frames existed before the final kill. Three-second runs deliberately exercise assets still being prepared.

Playtest: start, set an isolated synthetic final level 2 fighter with score 90/lives 2, prewarm, fire with the real Space key and animation loop, watch descent/blackout/waking, wait for the external camera. Expected: score 130/lives 2, same profile, a different external pose, bounded per-pass face work. Observed: all six workflow/face-bound assertions passed. Timing values are measurements, not blanket performance passes.

All times below are milliseconds. World p95 measures renderer CPU time. Preparation face peak counts the bounded mesh work performed while filling the cache.

| Browser | Quality | Prewarm | Frame p50 | p95 | p99 | Worst | World p95 | Prep face peak |
|---|---|---|---:|---:|---:|---:|---:|---:|
| Chromium | Low | 3seconds | 16.7 | 33.4 | 49.9 | 50.0 | 18.3 | 315 |
| Chromium | Low | ready | 16.7 | 16.7 | 16.8 | 33.3 | 0.6 | 315 |
| Chromium | Medium | ready | 16.7 | 16.8 | 16.8 | 33.3 | 1.0 | 787 |
| WebKit | Low | 3seconds | 68.0 | 104.0 | 197.0 | 284.0 | 34.0 | 315 |
| WebKit | Low | ready | 38.0 | 49.0 | 95.0 | 244.0 | 1.0 | 315 |
| WebKit | Medium | ready | 37.0 | 45.0 | 59.0 | 260.0 | 1.0 | 787 |

Prepared Chromium Low/Medium are near 60 FPS. Cold Chromium and software WebKit still miss the proposed frame targets. Prepared WebKit's world pass is p95 1 ms, but the whole browser frame remains slower. These results do not establish physical Safari iPhone or named desktop GPU performance. Advanced WebGL2 remains opt-in. Physical-device/GPU checks remain unexecuted follow-up. Main-thread preparation and cache-composition worst times are in each JSON; they are not total worker preparation time.

## Memory

Actual command: `ORBITAL_BROWSER=Chromium ORBITAL_QUALITY=Low ORBITAL_PREWARM=ready ORBITAL_REPORT_SUFFIX=-memory PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_orbital_memory.cjs`. One fresh browser, no concurrent test/recording workload. CDP scoped the owned browser's processes; only numeric resident-page `/proc/<pid>/statm` fields were sampled every 200 ms. Observed maximum summed RSS: 581,652,480 bytes across 172 samples. Shared pages can be counted more than once, brief peaks may be missed, and this is not unique GPU/device memory. Report: `orbital-browser-memory.json`. End-of-run main JS heap snapshots are also reported but exclude worker/GPU/native memory.

## Workflow and visible evidence

Playtest: four bounded commands with `ORBITAL_WIDTH=1280`, `390`, `844`, `568` and `node deploy/test_orbital_descent.cjs`, each using the same `PLAYWRIGHT_BROWSERS_PATH`/`TMPDIR` as above. Eight Chromium/WebKit cases passed: real keyboard final kill, full sequence, score/lives, active flight/fire, pause/rotation, skip twice and restart. New regression compares the waking world bitmap and eye to the settled external bitmap and eye, asserts changed images/positions and stable profile revision, and captures `orbital-*-external.png`.

Observed: the same pale crater floor, ridges and crystal landmarks remain visible after the camera rises. Reviewed decoded normal-video frames at one-second intervals, including the waking/active transition and settled external camera. Extra Chromium desktop recording holds active combat for 1.5 seconds. Fresh normal-speed browser MP4: `orbital-normal.mp4`, 786,196 bytes, below 6 MB. No Java F10 claim.

Playtest: `node deploy/test_orbital_cache_failure.cjs` passed both Chromium/WebKit Worker-failure cases, fixed-altitude captures, pause/rotation, handoff, flight/fire and restart on the new reduced renderer. `node deploy/test_orbital_diagnostics.cjs` passed WebGL2 frames/error check, real context loss, Canvas recovery, reduced motion, airless and lab lock/retry. Launcher route/start/move/fire passed. All used the same local HTTP source and synthetic profiles.

## Regression checks

`node --test games/space-invaders/*.test.mjs`: 37 passed, including 45 Java reference comparisons, deterministic bounded detail plans/landmarks and external-camera continuity. Report: `orbital-camera-performance-unit-tests.txt`.

Existing collision, layout/touch, cockpit and Planet lab gallery/overlay/excavation harnesses passed. Fighter harness passed at 390x844. Scratch copies in `.tmp` change only local ports to 8779 and prefix artifact names with `orbital-camera-regression-`; assertions are retained. Actual commands: `node .tmp/test_space_invaders_collisions.cjs`, `node .tmp/test_space_invaders_layout.cjs`, `node .tmp/test_space_invaders_cockpit.cjs`, `SPACE_INVADERS_VIEWPORT_WIDTH=390 node .tmp/test_space_invaders_fighters.cjs`, `node .tmp/test_alien_terrain.cjs`, with the browser/TMPDIR variables above.

The initial visible-camera regression attempts failed: an asynchronous wait predicate resolved before handoff completion, and a screenshot could outlast waking. The test now captures the waking reference inside the frame loop and waits on a synchronous renderer reference. Those failed attempts are not counted as passes. The first memory attempt also failed with `net::ERR_CONNECTION_REFUSED`; the local server was restarted and the successful memory run completed.

## Source audit and limits

Recorded review base: `1d2c3b3266c9e2d9c82c2217594690c0a62334ef`. Entire proposed diff audited: no file deletions; Sandbox/recipe book and other merged features retained. Intended removed code is the old disc/gradient/conveyor descent, the polar fallback fan, unbounded per-frame terrain iteration and repeated unchanged HUD writes. Source/bundle checks and source/media SHA256 values are in `orbital-source-audit.json`. Controller owns the final commit and publication.

Low is visibly coarse and cached motion uses quarter-second image blending. Main UI stays crisp. Medium/High add detail; High browser performance and physical Safari/GPU checks remain follow-up. Ready means ready for independent exact-head review of the available validation, not self-approval or an all-platform performance claim.
