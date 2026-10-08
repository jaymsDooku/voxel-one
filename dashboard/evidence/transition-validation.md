Current status: rebase continuation required. Browser reports/media below describe prior validation and must be checked against the completed rebase before review.

Space Invader transition: final integrated validation

Tested source: 04b2f8af0fbaa6b2ca0b4fd1270b9a891a201b59, 2026-10-08, after controller completed both preserved rebase steps. Game source did not change during this validation. Test-only fixes wait for rendered restart and for camera/story completion. Source hashes are in transition-playtest.json and planet-playtest.json.

Implemented level 1 to level 2 camera interpolation with a 1.6-second smoothstep move. Classic ship/deck fade out; solid canopy, sloped console and HULL/ALT/SECTOR instruments fade in. Combat/input/damage hold during the move. Pause freezes it; restart clears it.
Preserved level 2 completion: crash (2 seconds), blackout (1.2 seconds), level 3 awakening (3 seconds), then combat on a voxel planet with two moons. Restart clears both cinematic states.

Environment: Linux headless Playwright WebKit; isolated synthetic contexts with no accounts. Exact worktree served by python3 -m http.server 8778 --bind 127.0.0.1 and the same command on port 8779 for the planet harness.

Executed:
node --test games/space-invaders/game.test.mjs
PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_transition.cjs
PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_cockpit.cjs
PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_cockpit_edges.cjs
PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_planet.cjs
SPACE_INVADERS_VIEWPORT_WIDTH=844 SPACE_INVADERS_URL=http://127.0.0.1:8778/games/space-invaders/index.html PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_fighters.cjs

Rules: 13/13 passed. Transition hold/pause/end/restart, midpoint and endpoints, crash/blackout/waking, retained score/lives, collisions, bounds and fighter attacks passed. Output: transition-unit-tests.txt.

Playtest: Transition passed at 1280x900, 390x844 and 844x390. Start level 1; arrange a synthetic final alien; use real Fire key and application frame loop to kill it. Observe intermediate camera projection. Pause at a measured midpoint via game pause method, confirm time holds, resume with P. Hold right/fire before completion; observe fixed player/enemies and no shots. After completion observe cockpit movement/fire. Restart during another move; observe classic wave 1, score 0, lives 3. Touch left/cancel passes. HULL/ALT/SECTOR text and values 3 / 3, 10, 02 draw at cameraMix alpha at midpoint and at alpha 1 on completion. No page errors.

Playtest: Cockpit passed at all three sizes. Real final kill, two-axis flight/momentum, independent fighter paths, player and aimed enemy fire, altitude collision, pause/resume, death/restart, touch cancellation, level 3 score/lives carry and flight bounds pass. The test waits through awakening before checking planet combat. Edge harness passed ALT 250/10/0, portrait-to-landscape resize, touch up/down/fire cancel and pause/resume.
Playtest: Upstream fighter harness passed at 844x390, with camera/story delay waits added.

Playtest: Planet passed at all three sizes. Real final-fighter kill triggers crash; pause freezes story; blackout precedes waking under two moons; score 130/lives 2 survive; level 3 flight/fire works; blur pauses waking; restart returns to classic space. First attempt found a harness race: a fixed 100 ms wait read the previous rendered scene after restart. Replaced it with a rendered-state wait and reran the complete three-size workflow successfully. No game source fix needed.

Expected behavior matched observed results in all final runs. Reports: transition-playtest.json, invaders-levels-playtest.json, planet-playtest.json and fighters-playtest.json. Earlier cockpit/planet report data is retained under previousValidation and labeled historical.

Media: fresh transition and planet PNGs, transition-playtest.mp4 and planet-playtest.mp4. Inspected completed portrait canopy/instruments and landscape awakening with two moons. Decoded both fresh desktop videos with full FFmpeg to PNG frames; inspected intermediate cockpit fade, crash and awakening. MP4s use H.264/yuv420p, trimmed by 3 seconds to remove blank browser startup; each below 6 MB. Playwright records the actual browser game, which has no engine F10 recorder. No stock/old media used for these claims. Controller publication pending.

Build/checks: python3 deploy/build_site.py passed. Source/dist app.mjs and game.mjs comparisons and git diff --check passed. Restored unrelated generated website/dist/progress.json.

Second recovery, 2026-10-08:
Resolved preserved conflicts with newer crash/planet work. Level 1 completion starts the 1.6-second camera transition. Level 2 completion still starts crash (2 seconds), blackout (1.2 seconds), then level 3 waking (3 seconds). Both update branches freeze combat and pause naturally; restart clears both state fields. Preserved planet rendering, both moons, story HUD/ARIA, canopy/instruments, planet regression harness, unit test, evidence and dashboard milestone. The cockpit harness now waits for planet awakening to finish before asserting wave 3 combat.

Executed on resolved contents: node --check games/space-invaders/app.mjs; node --test games/space-invaders/game.test.mjs (13/13 passed); python3 deploy/build_site.py; source/dist app.mjs and game.mjs comparisons; git diff --check. All passed. No deleted files against review base 31cf8bceb3f9888d84edf696bba9cce9fb29cb97. Replaced lines combine both update and presentation paths rather than removing concurrent behavior. Retained upstream planet evidence; earlier transition media/report describe older source and require fresh captures after rebase completion. No fresh browser playtest claimed at this recovery handoff. Controller must stage resolutions and continue Git; no commit, reset, abort, push or deployment performed.

New-base recovery: e18722a660b6a9132023f95a53f2416f1092823b.
Resolved the sole current conflict, dashboard/progress.json updatedAt, retaining the later timestamp. No game source changed in this resolution. Verified all new-base milestone entries are unchanged, no files are deleted against the new base, and crash/blackout/waking plus planet rendering remain. Executed node --test games/space-invaders/game.test.mjs (13/13 passed), app syntax check, source/dist comparisons and git diff --check; all passed. Controller must stage/continue the pending rebase. Final-head workflow checks and media await completion; prior reports/media are historical until revalidated.

The cockpit JSON retains its earlier report under previousValidation, labeled historical. Its current test fields and SHA-256 values describe this integrated run.

Additional recovery: resolved only report/progress conflicts. Retained the newer integration checkpoint and historical cockpit report. No game source changed in this conflict step.

Report-only recovery: retained both historical report context and the current new-base recovery checkpoint. Final-head browser validation remains pending controller rebase completion.

Historical validation note from the replayed commit:
Diff audit against 31cf8bceb3f9888d84edf696bba9cce9fb29cb97: no deleted files. Every recorded-base milestone entry remains unchanged; planet milestone and this task are additional entries. Preserved canopy/instruments, crash/blackout/waking, planet renderer, both moons, upstream planet tests/evidence and fighter/cockpit tests. Intentional replaced lines combine interpolation with scene progression and presentation; no existing feature is removed. Fresh regression images/reports intentionally replace earlier captures; historical report contents remain labeled. Generated game files match source. Changes outside this work item's transition include retained concurrent planet implementation, not regressions. No commit, push, merge, deployment or independent review approval performed. No physical-device validation claimed.
