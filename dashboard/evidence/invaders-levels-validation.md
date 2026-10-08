Space Invaders level perspectives — Linux browser validation

Source: assigned feature worktree. No deployment, commit or push performed.
Server commands: `python3 -m http.server 8767 --bind 127.0.0.1` and `python3 -m http.server 8766 --bind 127.0.0.1 --directory website/dist`.
Browser environment: headless Playwright WebKit, synthetic isolated contexts. Set `PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers` and `TMPDIR="$PWD/.tmp/browser"` for all browser commands. No private account data used. Physical iOS devices untested.

Executed checks:
- `node --test games/space-invaders/game.test.mjs`: 9 passed, 0 failed.
- `python3 deploy/build_site.py`: passed; generated game copies match source. Restored unrelated generated progress changes.
- `node deploy/test_space_invaders_levels.cjs`: detailed command, expected behavior and observations in invaders-levels-playtest.json.
- `node deploy/test_space_invaders_layout.cjs`: 16 viewport/state checks passed; touch navigation passed; no page errors.
- `node deploy/test_space_invaders_collisions.cjs`: 20 alien, ship, shield and saucer hit/miss checks passed at 844×390 and 390×844; no page errors.
- `git diff --check`: passed.

Playtest: Start classic level 1; use real Fire to clear a synthetic final alien; enter first-person level 2; strafe and fire; pause/resume; lose final life; restart to classic; cancel touch movement; fire in the cockpit to advance to level 3 with score and lives retained. Test at 1280×900, 390×844 and 844×390. Screenshots show this implementation. Desktop and landscape cockpit captures inspected visually: fleet, sight, windshield rim and controls visible. Media URLs remain pending controller publication.

Initial harness runs failed because WebKit's installed browser path was unset, a fire assertion waited until the shield had absorbed the shot, and a view assertion did not wait for rendering. The final harness sets the browser path, checks fire before impact, waits for the displayed perspective, and holds the synthetic final target still. No failed run is counted as passed.

Diff audit: changes cover level selection, cockpit rendering, player instructions, tests, generated game copies and this task's progress/evidence. No unrelated source deletions. Existing regression media restored after reruns; new reports use distinct filenames. Controller must integrate latest master and rerun checks if source changes before review.
