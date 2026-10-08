Space Invader transition: final-head validation

Source tested: 857698f87a1d3d7d9c68dd324b5645a79bc702cb on 2026-10-08, after controller completed the rebase onto e18722a660b6a9132023f95a53f2416f1092823b. No game-source edits during this run. Reports include tested head and source SHA-256 hashes.

Level 1 completion moves the camera into the first-person cockpit over 1.6 seconds with smoothstep interpolation. Ship/deck fade out; the full existing canopy, sloped console and HULL/ALT/SECTOR instruments fade in. Combat/input/damage hold until completion. Pause freezes the move and restart clears it.
Existing level 2 crash (2 seconds), blackout (1.2 seconds), level 3 awakening (3 seconds), two-moon voxel planet and fighter combat remain unchanged.

Environment: Linux headless Playwright WebKit; isolated synthetic browser contexts without accounts. Served the exact worktree with python3 -m http.server 8778 --bind 127.0.0.1 and another server on 8779 for the planet harness.

Executed commands:
node --test games/space-invaders/game.test.mjs
PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_transition.cjs
PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_cockpit.cjs
PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_cockpit_edges.cjs
PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_planet.cjs
SPACE_INVADERS_VIEWPORT_WIDTH=844 SPACE_INVADERS_URL=http://127.0.0.1:8778/games/space-invaders/index.html PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_fighters.cjs

Rules: 13/13 passed. Covers combat regressions, transition midpoint/endpoints/freeze/pause/restart and crash/blackout/waking with progress carry. Output: transition-unit-tests.txt.

Playtest: Transition passed at 1280x900, 390x844 and 844x390. Click Start; set one synthetic final alien; kill with real Space key and application frame loop. Observe moving projection. Pause via game pause method at a measured midpoint, verify elapsed time holds, resume with P. Hold right/fire during the move: player and enemies stay fixed and shots remain empty. After completion observe cockpit movement and fire. HULL/ALT/SECTOR labels and values 3 / 3, 10, 02 use transition alpha at midpoint and alpha 1 after completion. Restart during another move restores classic wave 1/score 0/lives 3; touch left and cancel still work. No page errors.

Playtest: Cockpit passed at all three sizes: real final kill, independent fighters, two-axis flight/momentum, player and aimed enemy fire, altitude collision, pause/resume, death/restart, touch cancel, wave 3 progress carry and bounds. It waits through planet awakening before checking level 3 combat.
Playtest: Cockpit edge harness passed ALT 250/10/0, portrait-to-landscape resize, touch up/down/fire cancellation and pause/resume.
Playtest: Preserved fighter harness passed at 844x390.

Playtest: Planet passed at all three sizes. Real final-fighter kill triggers crash; pause freezes story; blackout precedes waking beneath two moons. Score 130/lives 2 survive; level 3 flight/fire works; blur pauses waking; restart returns to classic space. No page errors.

Expected behavior matched observed results in every final run. Reports: transition-playtest.json, invaders-levels-playtest.json, planet-playtest.json and fighters-playtest.json. Historical report data remains labeled under previousValidation.

Fresh media: transition-start/mid/end PNGs; planet-crash/waking PNGs; transition-playtest.mp4 and planet-playtest.mp4. Inspected portrait cockpit and landscape awakening screenshots. Decoded both actual recordings to PNG frames with full FFmpeg and inspected intermediate canopy fade, crash, eyelid opening and both moons. MP4 exports use H.264/yuv420p, trim 3 seconds of browser startup and remain below 6 MB each. Playwright captured the actual browser canvas; this game has no engine F10 recorder. No stock or historical media supports these current claims. Publication is pending the controller.

Build/checks: python3 deploy/build_site.py passed. Source/dist app.mjs and game.mjs comparisons and git diff --check passed. Restored unrelated generated website/dist/progress.json.

Entire diff audited against e18722a660b6a9132023f95a53f2416f1092823b: no deleted files. All base milestone entries compare equal and remain unchanged. Planet progression, renderer, two moons, canopy/instruments, planet tests/evidence and dashboard milestone remain. Intentional replaced lines implement camera interpolation/fades and completion delay; existing scene progression is unchanged. Test harness changes wait for camera/story completion and rendered restart, with synchronous frame polling and correct two-axis velocity reset. New media/reports refresh affected regression evidence; historical report contents remain labeled. Generated game copies match source. No unrelated features removed. No commit, push, merge, deployment or independent review approval performed. No physical-device test claimed.
