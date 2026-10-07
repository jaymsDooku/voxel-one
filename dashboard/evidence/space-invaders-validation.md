# Voxel Space Invaders validation

The small iPhone landscape layout now uses reserved grid columns for the header/HUD, canvas arena, and stacked touch controls. The canvas keeps its aspect ratio. Compact overlay spacing keeps Start, Resume and Play again inside the arena. Touch instructions no longer assume the controls sit below the game.

Environment: Linux, Node v22.14.0, installed Playwright WebKit (browser bundle webkit-2359), headless isolated synthetic contexts with mobile/touch emulation and device scale 2. No account data or personal browser profiles were used. This is a browser game, not a native iOS package. Physical iPhone/iPad Safari, calls and thermal behavior remain untested. Native DISPLAY/XAUTHORITY checks do not apply to this Canvas browser workflow.

Actual commands:

```
python3 deploy/build_site.py
node --test games/space-invaders/game.test.mjs website/test/*.test.mjs > dashboard/evidence/space-invaders-review-fix-unit-tests.txt
python3 -m http.server 8766 --bind 127.0.0.1 --directory website/dist
TMPDIR="$PWD/.test-tmp" PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers node deploy/test_space_invaders_layout.cjs
TMPDIR="$PWD/.test-tmp" PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers node deploy/test_space_invaders.cjs
git diff --check 5f1b711d467566491538d975d9de24d01f19bc1f
git diff --name-only --diff-filter=D 5f1b711d467566491538d975d9de24d01f19bc1f
```

Build passed. All four generated game assets match source byte-for-byte. Rule/website tests: 18 passed, zero failures (eight game rules, ten existing website tests). The current unit output is space-invaders-review-fix-unit-tests.txt. The older separate rule/website output files are historical from the initial implementation.

Playtest: run the built game at 568×320, 667×375, 844×390 and 390×844. Inspect start, playing, paused and game-over states. Tap Start, hold movement and Fire with dispatched simultaneous touch pointers, cancel/release, tap Pause, tap Resume, inject a last-life enemy bullet resolved by the running game, then tap Play again. Expected: all header/HUD/arena/control regions fit without overlap; overlay title/message/action stay inside the arena; buttons are at least 44×44 CSS pixels, reachable at their centers, and inside the viewport; no page scroll overflow; ship moves and restart resets score/lives/wave. Observed: all 16 viewport/state checks passed, all state actions worked, no page errors. Detailed rectangles and results are saved in space-invaders-layout.json. These checks catch the original 568×320 and 667×375 overlaps and clipped Start action.

Playtest regression: open the built landing page and follow Play Voxel Space Invaders in desktop 1280×900 and mobile 390×844/844×390 contexts. Start, move and fire; score rises and rendered shields erode. Pause freezes score/lives; blur pauses and clears held inputs. Resume, inject a single boundary alien to check reversal/descent, inject a final-alien shot to advance Wave 2, then inject a last-life enemy shot and restart. Expected: original combat and navigation still work. Observed: both profiles passed with no page errors; natural input gameplay reached desktop score 80 and mobile score 70, each with three lives. Detailed results are in space-invaders-browser.json. Deterministic combat setups are distinct from natural input gameplay. Physical device tests are not claimed.

Fresh captures from this working source: space-invaders-568x320-start.png, space-invaders-568x320-playing.png, space-invaders-568x320-paused.png, space-invaders-568x320-game-over.png, and the four matching space-invaders-667x375-*.png files. Desktop, touch and 844×390 landscape captures were also refreshed. Each PNG is below 6 MB. Start, play, pause and game-over screenshots were visually inspected. All media contain synthetic gameplay only. Source and media SHA-256 hashes are saved in space-invaders-hashes.json. URLs on the assigned feature branch are pending controller publication.

Diff audit against recorded review base 5f1b711d467566491538d975d9de24d01f19bc1f: no existing files deleted; no intended file deletions. The proposed change adds the browser game, its tests, sanitized evidence, task progress entry, and landing-page link/builder copy. The follow-up changes landscape CSS, direction-neutral touch instructions, documentation, layout checks and fresh evidence. Other game-engine features and merged work remain intact. The generated progress fallback was restored after the site build to avoid unrelated dashboard changes. git diff --check passed. The index has no unmerged entries. No commit, push, PR mutation, merge or deployment was performed. Controller owns source integration, publication, CI and independent review; source changes after integration require fresh meaningful checks and media.
