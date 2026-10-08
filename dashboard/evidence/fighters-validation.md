# Level 2 fighter combat: final source validation

Tested rebased source HEAD: `316d24b6e4b69a3ac684fe77ff63f752a0a9b6fb`. Review base: `cb79fa71fd7052f8ae0d311659bcb72882fb0318`. Game source stayed unchanged during this validation turn. Added a browser edge-check harness and refreshed reports/media. `fighters-source-validation.json` records source and media SHA-256 hashes.

Level 1 keeps its formation, shields, descent and saucer. Level 2+ adds two-axis flight with momentum, keyboard arrows/WASD and touch directions, independent weaving/swooping jets, aimed shots and contact damage. Flight bounds are x=20..460 and y=280..530. Diagonal input is normalized; maximum speed is 280 world units/s. Level 2 starts with 16 jets; later levels increase the count to a maximum of 28. Existing even-level cockpit and odd-level external views remain. Score/lives carry forward; restart restores level 1.

## Executed checks

Environment: Linux, headless Playwright WebKit, fresh synthetic browser contexts, no account data. Source served with `python3 -m http.server 8767 --bind 127.0.0.1`. This is the existing browser client; no physical iOS device test.

- `node --test games/space-invaders/game.test.mjs`: PASS, 11 tests. Report: `fighters-unit-tests.txt`.
- `PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_fighters.cjs`: PASS at 1280x900, 390x844 and 844x390. Report: `fighters-playtest.json`.
- `PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_fighter_edges.cjs`: PASS at the same three viewports. Report: `fighters-edge-playtest.json`.
- `PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node .tmp/fighters-layout.cjs`: PASS, 16 viewport/state checks at 568x320, 667x375, 844x390 and 390x844. Temporary copy of `deploy/test_space_invaders_layout.cjs` changes only port 8766 to 8767 and evidence prefix `space-invaders-space-` to `fighters-layout-`. Report: `fighters-layout-layout.json`.
- `PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node .tmp/fighters-collisions.cjs`: PASS, 20 live level 1 hit/miss cases at 844x390 and 390x844. Temporary copy of `deploy/test_space_invaders_collisions.cjs` changes only port 8766 to 8767 and evidence prefix `space-invaders-collision-fix` to `fighters-collision-regression`. Report: `fighters-collision-regression.json`.
- `git diff --check`: PASS. All four generated game files match source byte for byte. No rebuild needed this turn: game and website source stayed unchanged. The initial implementation ran `python3 deploy/build_site.py` successfully.

## Playtest

Playtest: start level 1; fire through the actual frame loop to kill a synthetic final alien; enter level 2; fly up/right and fire; compare independent jet paths; observe aimed enemy fire; resolve a hit at the flown altitude; pause/resume; lose the final life; restart; cancel touch movement; shoot a synthetic final fighter; check score/lives and flight in level 3; test upper/right bounds.

Playtest edge checks: enter level 2 with a synthetic setup; hold keyboard left with touch down/fire; cancel touch and release keyboard; observe drift decay; pause and compare position, velocity and enemy age; resume; miss with a projectile at the old altitude; collide with a jet. Check level 2 HUD/control bounds, overlap, touch target sizes and reachability.

Expected: flight moves in both axes, drift decays, jets move independently, aimed fire uses current position, hits use current altitude, contact removes one life, pause freezes state, restart returns to level 1, score/lives survive level advancement, controls remain reachable and movement stays bounded.

Observed: all checks passed at all tested viewports; no browser page errors. Synthetic enemy/hit setups shorten transitions and loss tests. Movement, fire, collision, UI and wave changes execute in the running application. Fresh screenshots replace the earlier captures. The MP4 comes from this turn's actual desktop recording, with 3 seconds of startup trimmed; it is below 6 MB. Inspected portrait level 2 screenshot and sampled MP4 frames showing level 1, the cockpit transition and moving jets. Reports contain per-viewport results and expected/observed behavior.

## Diff audit and controller handoff

Audited the entire proposed file list and source changes against the recorded review base. No unrelated deletions or changes. All upstream progress entries remain unchanged; the assigned fighter entry is added. The old level-test body is intentionally replaced by a wrapper for expanded fighter checks, preserving the old command. Generated game changes match their source changes. No reviewer feedback was supplied.

The progress JSON conflict was resolved in the prior turn, and the controller continued the rebase. Final-source workflow tests and media above now replace the earlier evidence. No staging, commit, push, merge or deployment performed. Evidence URLs remain pending controller publication on `feature/queue-61303435393238342d626561392d343965372d396138362d663030626530653839396437`. Independent review remains required.
