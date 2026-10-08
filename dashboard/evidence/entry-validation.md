First-person planet entry: final validation

Validated game source at 6dd31674cc91b35c478bf95547a5a81d30298452. Review base: a851730b411275ef50b09b4a28f0c9e9393c99b4.

Playtest: Linux headless WebKit; fresh synthetic contexts; exact assigned worktree HTTP server at 127.0.0.1:8779. Existing server reused after a new bind returned OSError: [Errno 98] Address already in use. HTTP app.mjs and game.mjs bytes checked against local files: identical. No private profile used.

`PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_entry.cjs`: PASS at 1280×900, 390×844 and 844×390. Real Fire input kills a synthetic final level 2 fighter. Expected and observed: cockpit approach, atmosphere, passing clouds, voxel descent and impact in order; pause freezes cloud sceneTime; blackout and waking preserve score 130/lives 2; level 3 flight/fire works; blur pauses waking; restart restores wave 1, score 0, lives 3. No page errors.

`PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node .tmp/entry-regression.cjs`: PASS at all three sizes. Executed harness retained as deploy/test_space_invaders_entry_regression.cjs. Expected and observed: real level 1 final kill, camera blend midpoint, frozen combat/input during blend, pause/resume, cockpit combat, restart during blend, classic touch/cancel, instrument text and alpha at midpoint/endpoint. No page errors.

`node --check games/space-invaders/app.mjs`, `node --test games/space-invaders/game.test.mjs` (13/13), and `git diff --check`: PASS. Source/dist app.mjs and game.mjs are identical. Fresh desktop descent PNG and decoded recording frame visually inspected. Media and source hashes are in entry-playtest.json. Fresh PNG/WebM artifacts remain below 6 MB each and await controller publication. No physical iOS validation claimed.

Entire proposed diff audited against the review base. No deleted files. Old external crash ship animation was intentionally replaced. Cockpit drawing moved unchanged into shared cockpit(); upstream camera blend, instrument opacity, projection, caption and dataset remain. Unrelated historical marker text matches the review base and was left unchanged. No commit, push, merge or deployment performed. Earlier reports/media were refreshed on this resulting source.
