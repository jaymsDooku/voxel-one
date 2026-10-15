# Level 3 ground combat validation — 2026-10-15

Ready for independent review of the local implementation. Reviewed orbital descent PR 63 is now integrated in controller-synchronized master/base `9b069b275ab4d1d85518366ce969cd1225e64dbd` (HEAD equals origin/master). No commit, push, merge or deployment was performed by the developer. All feature media/source URLs are pending controller publication.

## Executed checks

- `node --test games/space-invaders/*.test.mjs`: **73 passed, 0 failed** on the final source. Includes classic/fighter/cockpit rules, terrain determinism/seams, four gallery seeds and reachable safe spawns, timestep/diagonal/large-delta collision, jump/head clearance, world/muzzle occlusion, navigation budget/support/avoidance/patrol/gravity, perception, ammunition/pickups, fall/health/life/score/completion deduplication, stale callbacks/revisions/restart and the reviewed-profile coordinate/lighting contract. Output: `ground-unit-tests.txt`.

- `TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH="$PWD/.tmp/browsers" node deploy/test_space_invaders_ground.cjs`: **passed**. Report: `ground-browser-report.json`.

- `GROUND_ORBITAL=1 TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH="$PWD/.tmp/browsers" node deploy/test_space_invaders_ground.cjs`: **passed**. Report: `ground-orbital-browser-report.json`.

- `TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH="$PWD/.tmp/browsers" node deploy/test_space_invaders_ground_controls.cjs`: **passed**. Report: `ground-controls-report.json`.

- `TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH="$PWD/.tmp/browsers" node deploy/test_space_invaders_ground_edges.cjs`: **passed**. Report: `ground-edge-report.json`.

- `TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH="$PWD/.tmp/browsers" node deploy/test_space_invaders_ground_regression.cjs`: **passed**. Report: `ground-regression-report.json`.

- `TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH="$PWD/.tmp/browsers" PLAYWRIGHT_MODULE="$PWD/.tmp/qa/node_modules/playwright" node deploy/test_space_invaders_collisions.cjs`: **passed**. Report: `ground-classic-collision.json`.

**Playtest:** six fresh Chromium/WebKit contexts at 1280×720, 390×844 and 667×375. Real keyboard fire kills a synthetic final level-2 fighter. Desktop watches ordinary descent/blackout/waking; phones exercise Skip and repeated Skip without duplicate missions. Score/lives carry and pilots remain idle through waking/pause. Explicit Resume, walking/strafe, vertical aim, simultaneous touch move/look/fire, cancellation, actual blaster kill, enemy windup/plasma/reposition, reload from zero, pickups, lethal respawn, zero-life restart, last-kill latch and Continue to level 4 pass. Duplicate Continue and late old-canvas context events do not reset/pause level 4; cockpit fire still works. Synthetic lethal/completion fixtures test integration, not mission difficulty.

**Playtest:** one additional Chromium desktop run with `GROUND_ORBITAL=1` passes the full workflow using the explicit reviewed orbital preview. Its actual final-fighter kill leads through the 12-second descent and waking into FPS. The frozen profile, seed, score/lives and 50:1 cartographic transform are asserted. Preview Worker/profile resources clear at ground entry; the mission retains its immutable visual profile. A first-frame null-profile error found during this run was fixed by preparing the preview from the frozen world even if the initial survey scene is not ready, plus guarding the cockpit read. The passing rerun captures this path.

**Playtest:** two-engine controls suite verifies touch Resume makes no lock request even under a fine-pointer query, non-promise requests/errors, menu input suppression, Escape from a focused slider, fire remapping, rotation and reachable 48 px targets at 568×320. Chromium CDP captures three trusted independent touch pointers for move/look/fire and cancellation. WebKit simultaneous touch remains synthetic. Native Resume clicks use a checked visible/reachable centre where WebKit locator stability waits can stall.

**Playtest:** actual outpost awning stops head ascent; actual wreck stops movement/perception/both shot directions; a camera-visible target receives no damage when the muzzle is obstructed. A 37 metre fall consumes exactly one life and respawns safely with immunity. Positions are synthetic fixtures; geometry, input and collision are implementation code. Fresh edge screenshots included.

**Playtest:** built-site launcher → game, gallery/Generate, last classic-alien kill, cockpit movement/pause, frozen glass-sea snapshot/lab rejection, remapping forward to I, actual movement and blur pause all pass. WebKit `selectOption` visibility waits stalled despite an open visible lab; a checked native click plus Home/ArrowDown/Enter selected glass-sea and the resulting value was asserted. Twenty classic real-frame hit/miss cases pass at 844×390 and 390×844. Historical collision artifacts were restored; fresh copies use the `ground-classic-collision` prefix.

- `python3 deploy/build_site.py`: passed. Every generated runtime asset matches canonical source. Unrelated generated progress fallback restored to its existing version.
- `git diff --check`: passed. Source/entry harness syntax checks passed. Older entry/planet/fighter harnesses were adapted to ground semantics but are not separately claimed as executed.

Browser commands ran sequentially with sandboxed Chromium and headless WebKit 27.2, fresh synthetic profiles, source HTTP localhost:8779 and built-site HTTP localhost:8766. A local server lifetime failure during an earlier attempt was corrected by owning the source server for the duration of the suite. No failed attempt is counted as passed.

## Handoff and diff audit

`ground-entry.mjs` retains the reviewed immutable profile/config/seed, anchor, atmosphere, sun/moons, frozen edits and terrain revision. It rejects mismatched seed/config. Orbital cells are 50 cartographic metres; ground cells are one authored gameplay metre. The payload explicitly records this scale transition; terrain indices, edits and landmark coordinates are preserved. The arena remains 160 metres per axis. The lab stays locked through combat. Ground rendering, collision, navigation and obstruction share one mission snapshot. Ordinary Canvas remains default; advanced orbital rendering stays opt-in.

The full proposed diff was audited against the synchronized reviewed master/base above. No file deletions or unrelated source changes remain. Upstream descent/atmosphere/launcher and other applications are retained. Intentional replacements: wave-3 ship flight and waking auto-resume become paused ground entry; wave-3 flight assertions become FPS/Continue assertions; the visible direct Skip replaces the old descent-only button while retaining its API. Ground source/docs/tests, generated game assets and assigned progress/evidence make up the diff. Final runtime SHA-256 values are in `ground-final-source-hashes.json`.

## Media and performance

`ground-playtest.mp4`: **50.24 seconds**, normal-speed default-path browser capture, 960×540. `ground-orbital-playtest.mp4`: **51.64 seconds**, normal-speed reviewed-preview capture, 960×540. Both show descent → waking → FPS, cover movement/aim, pilot movement/fire, a kill and pause/resume. Both are below 6 MB. Converted from browser WebM using `.tmp/qa/node_modules/ffmpeg-static/ffmpeg -y -i INPUT -an -c:v libx264 -preset fast -crf 30 -vf 'scale=960:-2' -movflags +faststart OUTPUT`; no speed change. Redundant WebMs removed. Desktop/phone before/after screenshots and sampled approach/cloud/ground video frames were visually inspected. Profiles and media contain synthetic game data only.

Host: KVM Linux VPS, 4 vCPUs, Intel Core Processor (Haswell, no TSX), software rendering. Each timing set uses 120 requestAnimationFrame intervals at Reduced graphics, DPR 1. These results do not prove named-hardware targets of 60 FPS desktop or stable 30 FPS phone.

| Path / engine / resolution | p50 / p95 / p99 ms | Hitches >50 ms | Preparation / largest job ms |
|---|---|---|---|
| Default / chromium / 1280×720 | 83.3 / 83.4 / 100.1 | 116 | 480.1 / 15.2 |
| Default / chromium / 390×844 | 33.3 / 33.4 / 50.0 | 1 | 1863.5 / 38.5 |
| Default / chromium / 667×375 | 16.7 / 33.4 / 33.4 | 0 | 1930.7 / 42.9 |
| Default / webkit / 1280×720 | 69.0 / 85.0 / 90.0 | 120 | 460.0 / 18.0 |
| Default / webkit / 390×844 | 44.0 / 59.0 / 75.0 | 21 | 1868.0 / 60.0 |
| Default / webkit / 667×375 | 42.0 / 51.0 / 58.0 | 8 | 2108.0 / 54.0 |
| Orbital / chromium / 1280×720 | 83.2 / 100.0 / 100.0 | 111 | 513.8 / 20.2 |

Observed total mesh buffers ≤2,839,680 bytes, including terrain, shared pilots, held weapon, coarse detail and dynamic geometry; hard mesh cap 16,777,216 bytes; 49 Reduced chunks; CPU terrain cache 250,000 cells. Heap bytes were not measured. JSON reports include preparation, coarse work and memory counts.

Chromium granted native pointer lock in headless automation; WebKit native lock was unavailable/denied. Both denied-lock fallback paths passed. Browser automation is not physical OS pointer-lock or iPhone validation. Physical hardware performance/controls remain follow-up. Independent review, actual CI, publication, deployment and cached-module reload verification remain controller/reviewer steps and are not claimed passed.

Media bytes: `ground-playtest.mp4` = 1,074,634; pending controller publication.

Media bytes: `ground-orbital-playtest.mp4` = 664,878; pending controller publication.
