# Portal review reproduction check

Source head: `f8ea0fb55989fb8105878d5b26d0aa29d5f5e66c` (PR 68). All game-source hashes still match `portal-source-sha256.json`. No game-code change was made. The supplied review status did not identify a command or error, and PR 68 had no review comments when checked. This check found no reproducible game failure; it does not replace independent review.

Executed on Linux VPS with Node v22.14.0, sandboxed headless Chromium, software rendering and a new synthetic profile:

- `node --test games/space-invaders/*.test.mjs`: 86 passed, zero failed. TAP saved as `portal-review-recheck-tests.txt`.
- `python3 -m http.server 8779 --bind 127.0.0.1` served this worktree.
- Generated `.tmp/portal-review-recheck.cjs` from `deploy/test_space_invaders_portal.cjs` by replacing artifact prefix `dashboard/evidence/portal-` with `dashboard/evidence/portal-recheck-`. Only output paths and reported command text changed.
- `TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers PORTAL_ENGINE=chromium PORTAL_TOUCH=0 node .tmp/portal-review-recheck.cjs`: PASS. The durable original command is `node deploy/test_space_invaders_portal.cjs` with the same environment and filters; it uses the original output paths.

Playtest: Expected a real final-pilot shot to open one portal, walking to transfer retained resources, occupation combat and relay completion, then failure/retry and lifecycle checks. Observed PASS for every assertion. The test uses explicit eleven-pilot setup and temporary route immunity, scripts camera steering, and uses actual movement/fire/interaction controls. Failure, damage and context-loss fixtures are synthetic. Desktop denied-lock fallback, pause, destination death and restart passed. Fresh screenshots and normal-speed silent video use the `portal-recheck-` prefix. The separate default-rules combat evidence remains available in the main validation report.

Fetched `portal-journey.mp4` and `portal-arrival-chromium-desktop.png` through the GitHub contents API at exact head `f8ea0fb55989fb8105878d5b26d0aa29d5f5e66c`; `cmp` confirmed both match local published evidence byte for byte. Fresh recheck artifacts await controller publication. The recheck MP4 was decoded and visually inspected. All new clips are below 6 MB. Physical-device, real GPU and audio validation remain follow-up.

No failed CI conclusion or specific failing reviewer command was available. If the reviewer command still fails, the controller should attach its exact command, exit code and a sanitized error excerpt; no credentials or full runtime logs are needed. No speculative game changes were made.
