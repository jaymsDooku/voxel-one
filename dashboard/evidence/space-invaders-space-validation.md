# Full-screen voxel space validation — collision correction

Linux VPS, Node.js and Playwright WebKit 26.6, fresh synthetic browser contexts. Mobile touch checks use emulation; physical iOS devices are untested. This browser game does not use the native engine's F10 recorder. Playwright recorded the running game instead. All listed screenshots and the WebM were refreshed from the corrected source.

## Fix

Positions still span the viewport and voxel faces stay square. Collision boxes now derive from occupied model cells, including cube front, top and side faces. The game converts those visible bounds through the same viewport scale used by the renderer. Swept projectile bounds include their visible size and offset. Alien, ship, saucer, shield and alien/shield overlap checks use these bounds. Projectile offsets now scale with their cubes rather than viewport width. Bounding boxes cover the model silhouette; collisions are not per-pixel.

## Executed checks

```
python3 deploy/build_site.py
node --test games/space-invaders/game.test.mjs
python3 -m http.server 8766 --directory website/dist
TMPDIR="$PWD/target/space-qa" PLAYWRIGHT_BROWSERS_PATH="$PWD/target/space-qa/browsers" node deploy/test_space_invaders_collisions.cjs
TMPDIR="$PWD/target/space-qa" PLAYWRIGHT_BROWSERS_PATH="$PWD/target/space-qa/browsers" node deploy/test_space_invaders.cjs
TMPDIR="$PWD/target/space-qa" PLAYWRIGHT_BROWSERS_PATH="$PWD/target/space-qa/browsers" node deploy/test_space_invaders_layout.cjs
TMPDIR="$PWD/target/space-qa" PLAYWRIGHT_BROWSERS_PATH="$PWD/target/space-qa/browsers" node deploy/capture_space_invaders.cjs
git diff --check
```

Build passed. Eight rules tests passed; output is in `space-invaders-collision-fix-rules.txt`. Diff whitespace check passed.

Playtest: 20 running-game hit/miss cases passed at 844×390 landscape and 390×844 portrait. Each case sets an isolated combat scenario, then lets requestAnimationFrame execute the collision/update loop. Alien and saucer hits award points; misses award none. Ship hits remove one life; misses preserve three lives. Both player and enemy shots destroy a shield when visibly overlapping it and leave it intact when outside it. The landscape alien miss places the shot 15 logical units, or 26.375 CSS pixels, from the initial center. It now scores 0. The visible crab half-width is about 6.29 CSS pixels. Portrait tests also cover vertical gaps previously accepted by the stretched hitboxes. `space-invaders-collision-fix.json` records setup and observed values for all cases.

An initial 20-case run passed. Later reruns exposed a timing flaw in the new test harness: it read a scenario before a combat frame ran. The final harness explicitly polls that scenario's projectile position/removal before asserting its result. The final 20-case run passed with no page errors. No failed run is counted as a pass.

Playtest: Started through the rendered button, held keyboard or touch movement and fire, paused/resumed, cancelled/released held touch input, and paused on blur. Desktop scored 70 and mobile scored 60; both had three lives in the scored capture. Navigation, boundary reversal, wave completion, collision-driven game-over and restart passed. Deterministic boundary, last-alien and enemy-hit setups were resolved by the running update loop. No page errors.

Playtest: All 16 layout checks passed at 568×320, 667×375, 844×390 and 390×844 in start, playing, paused and game-over states. The arena fills the viewport; HUD and touch buttons overlay the scene, stay in bounds and remain reachable. All buttons are at least 44×44. No scroll overflow. Overlay content fits.

Playtest: Recorded live movement and firing in the corrected game. Paused and resized 1280×900 to 390×844. Score, lives, wave and player position stayed unchanged; the canvas filled the new viewport. Returned to desktop and resumed. The refreshed WebM is below 6 MB. Inspected fresh landscape and portrait PNGs: stars, nebula clouds, block worlds, shaded voxel fleet/ship and deck are visible behind the overlay HUD.

## Review-base audit

Audited the entire tracked diff against `701118f1c3209dacb44c32128ee597f17626f640`, plus the new collision harness, reports and PNGs. Changes stay within Space Invaders renderer/layout/rules/docs, affected browser checks, capture harness, local progress and evidence, and generated game copies. The bounded arena and three-column landscape CSS were intentionally replaced by full viewport and overlay rules. Fixed-radius collisions were intentionally replaced by visible geometry checks. No unrelated source or file deletions. Preserved other merged features. Restored the unrelated generated progress snapshot after each site build.

Media and reports await controller publication on the assigned feature branch. No deployment, commit, push, PR edit or merge was performed. If later integration changes source, tests and media need rerunning on that source.
