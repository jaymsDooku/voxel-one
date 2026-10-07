# Voxel Space Invaders validation

Implementation: standalone browser game under games/space-invaders, linked from the Voxel One landing page. No Java/GLFW or native iOS package was added. Mobile delivery targets iPhone/iPad browsers. Physical device installation, calls, thermal behavior and real iOS Safari remain untested.

Environment: Linux, Node, Playwright 1.63.0 WebKit 2359; headless synthetic browser contexts, no personal profiles/accounts. Desktop 1280x900; mobile touch emulation 390x844 at device scale 2, then 844x390 landscape. Native graphical harnesses and DISPLAY/XAUTHORITY were not used because this feature runs in a browser.

Commands:

```
node --test games/space-invaders/game.test.mjs
python3 deploy/build_site.py
python3 -m http.server 8766 --bind 127.0.0.1 --directory website/dist
TMPDIR="$PWD/.test-tmp" PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers node deploy/test_space_invaders.cjs
node --test website/test/*.test.mjs
git diff --check
```

Playtest: open the actual built landing page, follow Play Voxel Space Invaders, Start game, hold keyboard movement and Space; mobile uses a real emulated touchscreen tap plus dispatched simultaneous touch-pointer holds/releases/cancel. Expected: ship moves, bullets destroy aliens, score rises, shields erode. Observed: desktop score 80 and touch score 110 in the initial successful run, three lives, rendered voxel fleet, ship, projectiles and damaged shields. Final run: desktop score 80 and mobile score 100, both with three lives. A prior successful full browser run also observed a natural enemy hit, mobile score 90 with two lives. Actual HUD values are in space-invaders-browser.json; random enemy selection and render timing can vary scores.

Playtest edge cases: Pause/Resume and blur interruption; expected frozen score/lives, cleared held inputs and resumed play only after Resume. Check landscape touch controls remain inside viewport. Regression: navigate from the existing landing page into the game; inject a single alien at the boundary in the running production game to verify reversal/descent, inject a final-alien bullet collision to verify Wave 2, then inject a last-life enemy shot to verify Game over and Play again resetting score/lives/wave. These deterministic state setups are distinct from natural input gameplay. No unexecuted physical-device checks are claimed.

Rule tests: eight passed. Cover distinct voxel models, movement limits, cooldown, pause/restart, single-score last-alien kill, wave reset, both sides damaging shields, life loss/immunity, invasion, enemy bottom-row firing, saucer bonus, bounded elapsed time, projectile cleanup and swept collision at slow frame rates. Existing website regression tests: ten passed. Test output saved separately. Harness timing assertions were corrected with waits for animation-frame UI updates and a HUD snapshot taken after pause. The final-wave browser scenario also caught a real projectile tunneling bug at slow frame rates; collision now checks the full projectile path and has a focused regression test; An incomplete synthetic alien fixture lacked a render model type; the fixture now supplies the production alien type. Final browser result records executed outcomes.

Fresh media: space-invaders-desktop.png, space-invaders-touch.png and space-invaders-landscape.png captured from the tested implementation. Pending controller publication on the assigned feature branch. Images contain only synthetic gameplay. No old or stock media used.

Diff audit: no existing files deleted. Only game source, tests, sanitized evidence, a local work-item entry, builder copy/link, and generated landing/game assets changed. Existing game engine and other developers' features preserved. Recorded review base has not been supplied; audit used current HEAD. Controller must integrate latest master and rerun meaningful checks/media if source changes before review. Landscape screenshot review found overlapping header/HUD text; layout was corrected and the full browser workflow rerun with fresh captures. No commit, push, PR, merge or deployment performed.
