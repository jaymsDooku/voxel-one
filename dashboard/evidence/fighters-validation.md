# Level 2 fighter combat validation

Implementation: the existing Voxel One browser Space Invaders client. Level 1 keeps its 45-alien formation, shields, descent and bonus saucer. From level 2 onward, the ship flies horizontally and vertically with normalized input, acceleration, drift and bounds. Keyboard arrows/WASD and touch direction buttons work with held Fire. Sixteen jets start level 2 on staggered paths, weave independently, swoop into the player's flight region, fire aimed projectiles and cause contact damage. Later levels keep fighter rules and increase enemy count, capped at 28. The existing even-level cockpit and odd-level external views remain. Score and lives carry forward; restart restores level 1.

## Commands and environment

Linux; headless Playwright WebKit; fresh synthetic browser contexts; no account data. Browser source served by `python3 -m http.server 8767 --bind 127.0.0.1`. No physical iOS device tested. This change extends the existing browser client, not the native iOS client.

- `node --test games/space-invaders/game.test.mjs`: PASS, 11 tests. Includes classic rules, fighter momentum, diagonal speed cap, bounds, independent paths, aimed projectile velocity, altitude collision, pause and restart. Report: `fighters-unit-tests.txt`.
- `python3 deploy/build_site.py`: PASS. Static game files rebuilt. Generated fallback `website/dist/progress.json` restored from HEAD to avoid an unrelated dashboard snapshot change.
- `PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_fighters.cjs`: report in `fighters-playtest.json`.
- `PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node .tmp/fighters-layout.cjs`: PASS, 16 viewport/state checks at 568x320, 667x375, 844x390 and 390x844. This temporary copy of `deploy/test_space_invaders_layout.cjs` changes only port 8766 to 8767 and evidence prefix `space-invaders-space-` to `fighters-layout-`. Report: `fighters-layout-layout.json`.
- `PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node .tmp/fighters-collisions.cjs`: PASS, 20 live hit/miss cases in 844x390 and 390x844. This temporary copy of `deploy/test_space_invaders_collisions.cjs` changes only port 8766 to 8767 and evidence prefix `space-invaders-collision-fix` to `fighters-collision-regression`. Report: `fighters-collision-regression.json`.
- `git diff --check`: PASS.

## Playtest

Playtest: start level 1; fire through the real frame loop to kill a synthetic final alien; enter level 2; fly up/right and fire; compare independent jet positions; observe aimed enemy fire; resolve a synthetic projectile hit at the ship's current altitude; pause/resume; lose the last life and restart; cancel touch movement; clear a synthetic final fighter by firing; check score/lives in level 3; fly by touch and test upper/right bounds. Desktop 1280x900, portrait 390x844 and landscape 844x390 use separate profiles.

Expected: level 2 has no shields or shared formation movement; flight changes both axes with drift; aimed fire and hits use current altitude; pause freezes combat; restart restores level 1; level 3 retains flight combat and score/lives; cancelled controls clear; player stays within flight bounds.

Observed results and page-error checks are recorded per viewport in `fighters-playtest.json`. Media files are fresh captures of this implementation. Synthetic final-enemy and hit setups speed up transitions and loss checks; movement, firing, collision, UI and wave changes execute in the running application.

## Diff audit and handoff

Audited against the worktree's starting HEAD, `7df8c29`. No review-base override or synchronized head was supplied this turn. No unrelated source files were deleted. The prior level test body is intentionally replaced by an entry-point wrapper for the expanded fighter test, preserving the old command. Gameplay changes are mirrored in generated `website/dist/games/space-invaders/` files. Other game features remain untouched. No commit, push, deploy or merge performed. The controller must publish evidence on the assigned feature branch and integrate latest master before review; changed source needs fresh validation.

Final fighter browser result: PASS at all three viewports, including aimed enemy fire and a hit at the flown altitude; no page errors. Inspected fresh desktop and portrait screenshots and sampled MP4 frames showing the classic-to-cockpit transition and moving jets. MP4 is encoded from the actual desktop browser recording, with 3 seconds of startup trimmed, and is below 6 MB. All evidence URLs are pending controller publication on `feature/queue-61303435393238342d626561392d343965372d396138362d663030626530653839396437`.

## Rebase conflict recovery

Resolved `dashboard/progress.json` by retaining every upstream entry unchanged and adding the assigned fighter milestone from the incoming commit. JSON parses and all milestone IDs are unique. Audited the proposed file list against recorded review base `cb79fa71fd7052f8ae0d311659bcb72882fb0318`: no deleted files or unrelated changes. The intended removal of the old level-test body remains an entry-point wrapper for the expanded fighter checks. No review feedback was supplied for this recovery turn.

Recovery checks: `node --test games/space-invaders/game.test.mjs` passed all 11 tests; `git diff --check` passed; changed text files contain no conflict markers; all four generated game files match source. These checks precede controller rebase continuation. Existing playtest reports and media describe the earlier tested source; final-head playtests and media still require a resumed validation turn after the controller stages and continues the rebase. No staging, rebase continuation, commit, push or deployment performed.
