# Alien terrain validation

Assigned feature worktree. Local edits are preserved. Publication and independent review remain with the controller. No commits, pushes, merges or deployments were performed.

## Executed checks

- `node --test games/space-invaders/game.test.mjs games/space-invaders/terrain.test.mjs games/space-invaders/terrain-scene.test.mjs`: 23 passed, 0 failed. This includes 13 existing game checks and 10 terrain/scene checks.
- Fixed seeds reproduce chunks in reverse order. Elevation, biome weights and volume materials match across shared borders, including negative coordinates and 64/80-cell intersections. Landmark spacing/suitability, hollow volumes, crater sizes/overlap/rims/peaks/erosion, vegetation limits and stable excavation geology passed.
- Connectivity validates safe ground, a maximum one-voxel step, four empty overhead voxels and all three navigation anchors. A deliberately removed start floor correctly reports all three anchors inaccessible.
- `PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_alien_terrain.cjs`: passed in isolated desktop Chromium and WebKit contexts at 1280×900. The harness owns its ephemeral loopback HTTP server and stops it after the check.
- Playtest: Start the application and enter a synthetic level 3. Inspect and regenerate all four fixed seeds. Reject scale 0 without replacing the last world. Apply scales 16 and 256 with roughness 2, crater density 1 and the cold palette. Survey signatures of all four biomes. Inspect elevation/biome/slope/cave/traversal overlays. Click a solid voxel in the geology section and check that it becomes air while lower geology stays unchanged. Race two Worker requests and confirm the latest scene wins. Use real keys to fly/fire, pause/resume and restart to classic level 1.
- Expected: deterministic fields, zero checked border mismatches, clear objective routes, distinct biome shapes/colours, real Worker generation, persistent geological layers under excavation, responsive animation during generation and retained game controls. Observed: all listed assertions passed; no browser page errors.
- Saved game/generator source hashes were checked on this continuation: unchanged. The existing 23-test report applies to that source. The changed desktop harness was rerun with enforced performance limits.
- `python3 deploy/build_site.py`: succeeded. Seven source and built files match byte-for-byte. Restored the builder's unrelated progress snapshot change. `git diff --check` passed.

## Desktop measurements

Linux x64 reference CPU: `Intel Core Processor (Haswell, no TSX)`. Chromium 153.0.8010.12 at 1280×900 is the performance reference. Limits: cold 16-voxel base chunk p95 ≤8 ms; 72×48-column Worker survey generation ≤1500 ms. Enforced reference checks: **PASS**. The harness verifies viewport, exact seed identities, survey dimensions, extreme settings and the 64-chunk sample count.

- chromium 153.0.8010.12: gallery generation 411.2–559.9 ms. Cold chunk p95 3.6 ms, max 5.5 ms across 64 chunks. 31 animation frames during 550.4 ms of Worker wall time. Uncached terrain draw-call p95 102.1 ms; cached drawImage call-recording p95 0.1 ms.
  Extreme scales: 16 voxels: 538.3 ms, 256 voxels: 586.6 ms. Checked seams and inaccessible counts were zero.
- webkit 26.6: gallery generation 653.0–1021.0 ms. Cold chunk p95 23.0 ms, max 35.0 ms across 64 chunks. 14 animation frames during 934.0 ms of Worker wall time. Uncached terrain draw-call p95 24.0 ms; cached drawImage call-recording p95 0.0 ms.
  Extreme scales: 16 voxels: 952.0 ms, 256 voxels: 851.0 ms. Checked seams and inaccessible counts were zero.

The performance gate applies only to the Chromium reference. WebKit passed workflow checks. Canvas call timings do not measure GPU completion; a 0 ms reading is below clock resolution. Animation continuity is not a 60 fps guarantee.

## Media and scope

Fresh screenshots are named `alien-terrain-{chromium,webkit}-*.png`. The fixed seed gallery, four biome signatures, five overlays and actual geology excavation are captured from this implementation. Selected media was inspected for gold enemy contrast, dark volcanic ridges, crystal clusters/arches/hollows, low acid/fungal basins, pale impact dust, strata and clear route markers. The controller must publish these artifacts before review.
The old unseeded decorative sine terraces in app.mjs were intentionally replaced. All existing flight, scoring, story, cockpit and touch code remains; the old game tests pass. No tracked file deletions or unrelated source changes were found in the local diff against HEAD. No synchronized review base has been supplied; the controller continuation must audit against that base and rerun checks if integration changes source.
The game remains airborne. Navigation anchors and hazard fields belong to the terrain generator; this change does not introduce ground missions or hazard damage rules. Caves and geology are visible in the section; raised volume spans display arches, hollows, overhangs and spires. Workers have a labelled main-thread fallback if the API is unavailable. The specified reference performance gate now passes. Publication and final-base validation remain with the controller.

## Source hashes

- `games/space-invaders/app.mjs`: `d9e90d3c8b976f8d0ddcc72371fb115ffa6793f72f900a687f636854eda59432`
- `games/space-invaders/game.mjs`: `f4cf0664a1861719710aca2f9d769b0ab600b4769e1b401a6e2d109e4da6a1c8`
- `games/space-invaders/index.html`: `309efdbeb34a383662f06f0a436f4ac3f2d24a2a813e816cc57fae3bde22ac75`
- `games/space-invaders/style.css`: `cd7311ee6eecdd503db56a40798918a0cb6f9e93b370d3c587ee6047c4c5231f`
- `games/space-invaders/terrain.mjs`: `1f14765c2ad7bcd34f3cff542d3c8b80b022b7e52e49c7031c1659ead0fc482d`
- `games/space-invaders/terrain-view.mjs`: `ab5e6817942124f35deb11a2c12768e36be984feb6875000a8adec9607ccb371`
- `games/space-invaders/terrain-scene.mjs`: `987ba986916f04635bca52a06d4381d49415a003eb30df29c339d92914f02412`
- `games/space-invaders/terrain-worker.mjs`: `852885762ff48bb061bcb8b82ebaa311855e8d8b1def67b0d1662ece7b28147a`
- `games/space-invaders/terrain.test.mjs`: `8efee22b0eaed6977f09e9739c1ab92e960a7772322c7320102049d66ce986d7`
- `games/space-invaders/terrain-scene.test.mjs`: `36d42e6db87bd10fb64a591c5689d41fa2251f1a43176793d9df4b150e7dc626`
- `games/space-invaders/TERRAIN.md`: `6aa1bf6cdd24821048199ba058f758c3ce1851ec5bb69a33520c542f45559346`
- `deploy/test_alien_terrain.cjs`: `070992ca152f897b8931582c36a2ca1dadf1927a7fc2eccc1161e4f8cdcfc0f1`
