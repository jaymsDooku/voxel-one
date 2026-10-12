# Orbital cache-miss revision

Reviewer defect: the geographic polar quads became a dark radial fan when the camera reached upper atmosphere before cache frames arrived. They also overwrote the atmospheric pass with opaque unattenuated colors.

Replaced the projection with bounded screen-space sphere rays, wrapped atlas/pole sampling and the same anchored landing patch. Shared landing mesh overlaps from 60,000 m. Cold Canvas, cached Canvas and WebGL2 compose clouds in linear light before aerial transmittance/radiance and one tone map. Paused Canvas reuses its frozen world pass, while late cache/fine results invalidate it.

Recorded review base: `1d2c3b3266c9e2d9c82c2217594690c0a62334ef`. All proposed files audited against it; no file deletions. Intended removed code: old polar-quad fallback and its opaque atmosphere overwrite. Sandbox/recipe book features retained. Source modules and generated website bundle match (17-file manifest in `orbital-source-audit.json`).

Environment: Linux x86_64 VPS, headless software Chromium/WebKit, isolated synthetic game profiles, local HTTP port 8779. Canvas Medium, UI DPR capped at 2. Software/browser recording measurements do not establish physical-device performance.

Unit command: `node --test games/space-invaders/*.test.mjs` — 35 passed, including 45 Java reference comparisons, ray coverage/finite pole geometry, wrapped atlas/poles and anchored patch checks. Report: `orbital-revision-unit-tests.txt`.

Playtest: Worker failure and missing cache. Command: `PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_orbital_cache_failure.cjs`. Abort the entry Worker module, fire at an isolated synthetic final fighter through the real keyboard/frame loop, force cache empty at 4.5 seconds, inspect 3/4.5/6/9/11.5-second frames, pause/rotate, resume through waking, fly/fire, restart. Expected: retained lit surface/landing region through haze, no radial fan, frozen pause time, score 130/lives 2, successful handoff and restart. Observed: PASS on final source.

Playtest: normal-speed late-cache descent. Command per width: `ORBITAL_WIDTH=WIDTH PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_orbital_descent.cjs`, with WIDTH 1280, 390, 844 and 568. Each command runs Chromium and WebKit. Actual final keyboard kill, full descent/blackout/waking, score/lives, flight/fire, pause/rotation, skip twice and restart. Expected: continuous geography without fan during cache misses, one story, score 130/lives 2, frozen pause and one skip completion. Observed: PASS on final source.

Earlier long matrix/diagnostic commands were terminated with exit 143. Two subsequent attempts failed with `net::ERR_CONNECTION_REFUSED` after the local server ended. No pass is claimed for these commands; source was then locked and the local server restarted for bounded reruns. Those bounded reruns completed successfully.

Limits remain: cold coarse sampling and vertical atmosphere interpolation, reduced cloud occlusion on fine faces, software frame targets unmet. Advanced rendering remains opt-in. Physical Safari iPhone/named GPU tests remain unexecuted follow-up.


Final results: all eight browser/viewport normal-playback cases passed. Both Worker-failure cases passed. Expected and observed score 130/lives 2; pause/rotation scene time unchanged; skip completes once; normal handoff reaches waking and active level 3; flight/fire and restart assertions pass. Inspected normal-speed MP4 at 3-second intervals and both browser failure images at 4.5 seconds: no radial fan; pale basin/ridges/crystals remain identifiable from upper approach through local terrain and waking. Failure video includes deliberate paused inspection frames, not a claim of uninterrupted playback.

Playtest: `PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_orbital_diagnostics.cjs` passed final-source WebGL2 fixed frames, GL error check, real context loss while paused, Canvas recovery at unchanged scene time, reduced-motion UI, airless, lab lock/retry. Launcher Playtest: `PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node .tmp/check-launcher.cjs` passed generated route/start/move/fire.

Video files: `orbital-normal.mp4` 596,490 bytes; `orbital-cache-failure.mp4` 656,398 bytes. Browser recordings, not Java F10. Both under 6 MB. Source/media hashes in `orbital-source-audit.json`. No source changes after final runs. Generated source and bundle match.

Software performance targets fail. Concurrent software browser workloads/recording were used. Desktop Chromium p50 166.6 ms, p95 450.1 ms, p99/worst 549.9 ms; desktop WebKit p50 568 ms, p95 1580 ms, p99/worst 1899 ms. Full viewport frame/preparation/cache metrics remain in `orbital-playtest.json`. Peak main JS heap observation excludes workers/GPU/native allocations and is not total peak memory. Prior regression reports cover unchanged game/collision/input/lab logic; the new matrix and diagnostics exercise the changed renderer. Ready receipt requests independent review, not self-approval or hardware performance sign-off.


Superseded renderer/media validation: latest authoritative results are in `orbital-camera-performance-validation.md`. Physical hardware checks remain follow-up.
