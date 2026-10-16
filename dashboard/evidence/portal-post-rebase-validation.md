# Portal final-source validation after recovery

Tested head: `c6891730521eee1da73a21bb9a0756977173ffda`. Recorded review base: `3760edffdbcddc758f88c834ab03dbd600b4aa8a`. The recovered index is clear of unmerged paths. All browser game source hashes remain identical to the earlier fully validated implementation. The exact file hashes are in `portal-post-rebase-source.json`.

## Commands and results

Environment: Linux VPS, Node v22.14.0, sandboxed headless Chromium/WebKit, software rendering, isolated synthetic profiles. Source server: `python3 -m http.server 8779 --bind 127.0.0.1`.

- `node --test games/space-invaders/*.test.mjs > dashboard/evidence/portal-post-rebase-tests.txt`: 86 passed, zero failed.
- Created `.tmp/portal-post-rebase.cjs` from `deploy/test_space_invaders_portal.cjs`, replacing only artifact prefix `dashboard/evidence/portal-` with `dashboard/evidence/portal-post-rebase-` and the reported command text. The checked-in original harness remains the reproducible entry point.
- `TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers node .tmp/portal-post-rebase.cjs`: all four cases passed: Chromium and WebKit at 1280×720 desktop and 390×844 touch. Machine report: `portal-post-rebase-browser-report-all-0.json`.
- `python3 deploy/build_site.py`: passed. Generated game bytes and source manifest checked separately.
- `TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers node deploy/test_portal_launcher.cjs`: fresh and cached launches passed; result recorded in `portal-launcher-report.json`.

Playtest: Expected a real final-pilot shot to award score once and open a reachable portal without losing movement, then a physical crossing into occupied alternate 1964 with retained score/lives/health/ammunition. Observed all assertions pass. Eleven prior pilot kills are explicit fixture setup; final kill, traversal, firing, interactions and district movement use controls. Shooting the portal did not transfer. Touch used simultaneous move/look and cancellation. Arrival required Take control rather than an automatic pointer-lock request. Walked to Mara, read the newspaper, encountered occupation fire, killed a guard, crossed the checkpoint and disabled the relay. Chapter completion appeared. Failed preparation retained source identity and position; cancel/reactivate/retry passed. Pause during loading, rotation/context recovery, reduced settings, restart while loading, destination death and game-over restart passed.

The route test uses explicit temporary immunity and camera steering. It is not a natural-combat difficulty benchmark. The separate default-rules fight and broad earlier classic/fighter/descent/ground/collision/terrain/lab checks in `portal-validation.md` remain applicable to the identical source hashes; they are not claimed as fresh executions in this turn. This turn adds fresh unit, four browser/viewport workflow cases and launcher checks.

## Media and audit

Fresh `portal-post-rebase-journey.mp4` is a normal-speed silent H.264 browser recording: 48.00 seconds, 960×540, 1,259,432 bytes. It shows final kill, portal, arrival and actual combat. Decoded frames were visually inspected. Source, arrival, combat, contact, completion, failure, retry and reduced-motion screenshots use `portal-post-rebase-` names. All clips are below 6 MB. New artifacts await controller publication on the assigned feature branch.

The entire diff against the recorded review base has zero deleted files. Intentional code replacements remain the documented removal of the old cockpit Continue handoff and wave-parity mode choice. Upstream entries/features are preserved; only this task's progress entry is updated. No game-code change was needed after recovery. A specific failing reviewer command/error has not been supplied; local passing checks do not replace independent review.

Physical iPhone/touch capture, named GPU performance, audible output and production publication remain follow-up as declared in the main report. This is a session-only campaign; reload restoration is not claimed. No commit, push, merge, deploy, master mutation or other-worktree edit was performed.
