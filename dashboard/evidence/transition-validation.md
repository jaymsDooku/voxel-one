Current recovery status: browser evidence below is historical and predates restored crash/planet integration. Final-source transition, cockpit and planet workflows and fresh captures remain required after the controller completes the rebase.

Space Invader transition: final integrated validation

Tested game source from 7633b3928aadf3615a36b3312704923eec9c2673 on 2026-10-08 after controller rebase continuation. Later edits only strengthen the transition test and refresh evidence/progress; game source is unchanged.

The level 1 to level 2 camera moves for 1.6 seconds using smoothstep interpolation. The ship and classic deck fade out. The restored solid canopy, sloped console and HULL/ALT/SECTOR screens fade in together. Combat, input and damage hold during the move. Pause freezes it; restart clears it. Later alternating perspectives and fighter combat remain.

Environment: Linux headless Playwright WebKit; fresh isolated synthetic browser contexts, no accounts. Served this exact worktree using:
python3 -m http.server 8778 --bind 127.0.0.1

Executed commands:
node --test games/space-invaders/game.test.mjs
PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_transition.cjs
PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_cockpit.cjs
PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_cockpit_edges.cjs

Rules: 12/12 passed, including transition midpoint/endpoints, combat hold, pause, restart, collision sweeps, bounds, level alternation and aimed fighter attacks. Output: transition-unit-tests.txt.

Playtest: Transition workflow passed at 1280x900, 390x844 and 844x390. Start level 1; arrange one synthetic final alien; kill it with the real Fire key and application frame loop; inspect intermediate projection. Pause at a measured midpoint through the game pause method and verify elapsed time holds; resume with P. Hold movement/fire before completion and verify player/enemies remain fixed with no shots; after completion observe movement, fire and cockpit camera. Restart during a second transition and observe classic wave 1, score 0, lives 3. Touch left/cancel still works. HULL/ALT/SECTOR labels and values 3 / 3, 10, 02 were drawn at the transition alpha at midpoint and at alpha 1 after completion. No page errors. Report: transition-playtest.json.

Playtest: Preserved cockpit workflow passed at all three sizes. Real final-kill progression, two-axis flight and momentum, independent fighter paths, player fire, aimed enemy fire, altitude hit, pause/resume, death/restart, touch cancellation, wave 3 score/lives carry and flight bounds all passed. Report: invaders-levels-playtest.json.
Playtest: Preserved cockpit edge harness passed: ALT 250/10/0 drawn; portrait-to-landscape resize retains cockpit; touch up/down/fire cancellation and pause/resume work.
Expected behavior matched observed results for all workflows.

Fresh screenshots replace earlier evidence. Inspected portrait completed cockpit and landscape paused midpoint: solid canopy and all three instruments are present, and the midpoint instruments fade with the canopy. Decoded fresh desktop recording to PNG frames with a full FFmpeg build and inspected classic start, intermediate camera/canopy fade, completed cockpit, and restart. transition-playtest.mp4 is an H.264/yuv420p export of that session, trimmed by 3 seconds to remove blank browser startup, below 6 MB. Browser canvas has no engine F10 recorder; Playwright recorded the live application. No stock or historical media. Controller publication pending.

Build/parity: python3 deploy/build_site.py passed. cmp games/space-invaders/app.mjs website/dist/games/space-invaders/app.mjs and corresponding game.mjs passed. git diff --check passed. Unrelated generated website/dist/progress.json restored.

Audit: entire proposed diff checked against 584854454e2407b66af23ff636f982a77e5fcef3. No deleted files or removed milestone entries. Differences include the retained upstream canopy, instruments, cockpit harnesses and media, plus transition source/tests/media and matching generated game copies. Replaced renderer lines implement interpolation/fades and preserve the newer cockpit. Shared cockpit milestone remains intact; this task is appended. Existing invaders-level-2 images and cockpit reports are intentionally refreshed by the preserved workflow, not deleted. No other features removed. No commit, push, merge, deployment or review approval performed. No physical-device test claimed.

Audited the proposed diff against recorded base 584854454e2407b66af23ff636f982a77e5fcef3: no deleted files. Upstream cockpit source, regression harnesses, media and shared milestone remain. Intended replaced lines implement camera interpolation and fade, not removal of cockpit features. Existing browser reports and transition media above describe the pre-recovery source; they must be replaced by final-source playtests after the controller stages resolutions and continues the rebase. No fresh browser playtest claimed for this recovery handoff.

Second recovery, 2026-10-08:
Resolved preserved conflicts with newer crash/planet work. Level 1 completion starts the 1.6-second camera transition. Level 2 completion still starts crash (2 seconds), blackout (1.2 seconds), then level 3 waking (3 seconds). Both update branches freeze combat and pause naturally; restart clears both state fields. Preserved planet rendering, both moons, story HUD/ARIA, canopy/instruments, planet regression harness, unit test, evidence and dashboard milestone. The cockpit harness now waits for planet awakening to finish before asserting wave 3 combat.

Executed on resolved contents: node --check games/space-invaders/app.mjs; node --test games/space-invaders/game.test.mjs (13/13 passed); python3 deploy/build_site.py; source/dist app.mjs and game.mjs comparisons; git diff --check. All passed. No deleted files against review base 31cf8bceb3f9888d84edf696bba9cce9fb29cb97. Replaced lines combine both update and presentation paths rather than removing concurrent behavior. Retained upstream planet evidence; earlier transition media/report describe older source and require fresh captures after rebase completion. No fresh browser playtest claimed at this recovery handoff. Controller must stage resolutions and continue Git; no commit, reset, abort, push or deployment performed.

New-base recovery: e18722a660b6a9132023f95a53f2416f1092823b.
Resolved the sole current conflict, dashboard/progress.json updatedAt, retaining the later timestamp. No game source changed in this resolution. Verified all new-base milestone entries are unchanged, no files are deleted against the new base, and crash/blackout/waking plus planet rendering remain. Executed node --test games/space-invaders/game.test.mjs (13/13 passed), app syntax check, source/dist comparisons and git diff --check; all passed. Controller must stage/continue the pending rebase. Final-head workflow checks and media await completion; prior reports/media are historical until revalidated.

The cockpit JSON retains its earlier report under previousValidation, labeled historical. Its current test fields and SHA-256 values describe this integrated run.

Additional recovery: resolved only report/progress conflicts. Retained the newer integration checkpoint and historical cockpit report. No game source changed in this conflict step.

Report-only recovery: retained both historical report context and the current new-base recovery checkpoint. Final-head browser validation remains pending controller rebase completion.
