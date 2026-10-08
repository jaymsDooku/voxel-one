# Full-screen voxel space validation

Linux VPS, Node.js tests and Playwright WebKit 26.6, fresh synthetic browser contexts. No accounts or personal profiles. Mobile checks emulate touch; physical iOS devices were not tested. Browser game: native engine F10 recording does not apply. Playwright recorded the browser game instead.

Commands executed:

```
python3 deploy/build_site.py
node --test games/space-invaders/game.test.mjs
python3 -m http.server 8766 --directory website/dist
TMPDIR="$PWD/.tmp" PLAYWRIGHT_BROWSERS_PATH="$PWD/.tmp/browsers" node deploy/test_space_invaders_layout.cjs
TMPDIR="$PWD/.tmp" PLAYWRIGHT_BROWSERS_PATH="$PWD/.tmp/browsers" node deploy/test_space_invaders.cjs
TMPDIR="$PWD/.tmp" PLAYWRIGHT_BROWSERS_PATH="$PWD/.tmp/browsers" node .tmp/capture.cjs
git diff --check
```

Playtest: Started the game through its rendered button, held move and fire, then paused and resumed. Expected a viewport-filling space scene with HUD and touch controls over it. Observed full-screen stars, purple/blue nebula clouds, distant block worlds, cube fleet and ship, and a voxel deck. Desktop earned 110 points; mobile earned 30 points. Both retained three lives during the scored run. No page errors. Screenshots show this implementation, not historical media.

Edge case: Checked 568×320, 667×375, 844×390, and 390×844 in start, playing, paused, and game-over states. All 16 checks passed: full viewport arena, no scroll overflow, readable overlay content, reachable buttons at least 44×44, HUD and controls within bounds. Rotated the mobile session. During recorded play, resized a paused 1280×900 game to 390×844; score, lives, wave, and player position stayed unchanged and the canvas filled the new viewport. Returned to desktop and resumed.

Regression: Actual keyboard and touch move/fire, pointer release/cancel, pause/resume, blur pause, navigation from the landing page, boundary reversal, wave completion, enemy-hit game-over, and restart all passed. Deterministic boundary, last-alien, and enemy-hit setups were injected into the running game, then resolved by its update loop. Eight rules tests passed. Website build passed. `git diff --check` passed.

The initial WebKit launch failed because the browser binary was absent. Installed WebKit in the assigned worktree and reran successfully. The first recording attempt found the local server stopped; restarting it allowed the recording/resize check to pass. MP4 conversion was unavailable in the bundled limited FFmpeg; the original WebM and PNG evidence are retained.

Diff audit: Changes are limited to the game renderer/layout/docs, affected browser checks, a capture harness, progress, generated game copies, and new evidence. Removed bounded-arena CSS intentionally to replace it with viewport and overlay rules. Combat rules are unchanged. Restored the unrelated generated progress snapshot after the website build. No unrelated source deletions. Controller must supply the synchronized review base and rerun checks if integration changes source.

Media and reports await controller publication on the assigned feature branch. No deployment, commit, push, PR, or merge was performed.

Browser downloads and synthetic temporary profiles were moved into ignored `target/space-qa` after testing. The retained capture harness is `deploy/capture_space_invaders.cjs`.

Retained capture harness rerun: `TMPDIR="$PWD/target/space-qa" PLAYWRIGHT_BROWSERS_PATH="$PWD/target/space-qa/browsers" node deploy/capture_space_invaders.cjs` passed against the same local HTTP server. It writes the final WebM directly into `dashboard/evidence`.
