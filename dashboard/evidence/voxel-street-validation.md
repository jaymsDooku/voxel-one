# Voxel Street developer validation

Implementation: `games/voxel-street/`. Launch instructions are in its README.
Platform: Linux VPS, headless Chromium, software rendering, isolated synthetic
browser profile. No account data or external market data is used.

## Automated checks

- `node --test games/voxel-street/market.test.mjs games/space-invaders/game.test.mjs`: 20 passed, 0 failed. Includes seven trading tests and thirteen existing game regression tests. Full TAP report: `voxel-street-unit-tests.txt`.
- `node --check games/voxel-street/app.mjs`: passed.
- `node --check deploy/test_voxel_street.cjs`: passed.
- `git diff --check`: passed.

The trading tests cover whole-share validation, cash limits, overselling, market
boundaries, actual fills, weighted basis, unchanged-quote spread loss, positive
quotes across 100 seeds, exact event delivery, timed replay, position-independent
prices, carried positions, daily reconciliation, cash-only progression, seed
reset and four-day event/history transitions. A first run exposed a test-only
prototype comparison mismatch; the snapshot assertion was corrected and all
checks above were rerun.

## Playtest

Command: `TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers node deploy/test_voxel_street.cjs`.
Source served with `python3 -m http.server 8787 --bind 127.0.0.1`.

Playtest: The harness uses only the visible player controls and real wall time.
It enters the desk, reads the brief, previews exposure, dismisses guidance,
rejects fractional/unaffordable/oversell orders, buys 20 Harbor shares at ask,
observes the 1:00 earnings release, sells 10 at bid, leaves and returns to the
desk, observes the 2:00 survey, waits until 3:00 close, inspects the review,
enters day 2 with the same portfolio, and restarts at seed 95. Expected behavior:
quotes and account update immediately, events arrive once, orders stop at close,
the review exposes recorded facts and execution outcomes, and next day carries
holdings/basis while restart clears them. Actual per-step results are recorded
in `voxel-street-playtest.json`.

First-use assessment: The steady-demand description supports the Harbor choice;
the preview makes its exposure visible. Expected/reported earnings, recorded
impulses and executions let the player locate an explanation at close. This is
a scripted developer assessment, not an independent new-player usability study.
Profit is never required. Reviewers must independently exercise the final head.

The full real-time MP4 uses Playwright recording because this browser game does
not embed the native engine's F10 recorder. Physical GPU qualification remains
unperformed; no hardware frame-rate or stable rollout claim is made.

## Diff and handoff

Review correction: the prior statement that no tracked source was deleted was
incomplete. It compared only the original worktree baseline and missed unrelated
changes removed from the submitted PR relative to the later review base.
Independent review found removed music visualizer sources, launch integration,
tests, documentation and evidence, plus reverted sandbox smoke-coordinate fixes.
That submitted head must not be approved on the earlier validation claim.

Recovery audit against recorded base `26b8761ff9c355299aa1df83608a6f6ebcba7e0b`
and the current integration HEAD: the current tree retains the merged music
visualizer, its GameIdentity/Main/LoginDialog launch paths, shaders, tests,
documentation and evidence, and the sandbox smoke-test fixes. These inherited
changes are outside Voxel Street scope and must remain. No intended deletions
are part of this work. Only dashboard/progress.json had unresolved contents;
its resolution retains both work entries without changing the music entry.

This is a pending rebase, not a validated final head. The controller must stage
resolutions and continue Git. Fresh final-head checks and playtest/media remain
required after continuation. Earlier test and media results below are historical
and do not establish validation of the eventual recovered head.

All local media is from this implementation and is pending controller
publication. Commit, feature push, hosted checks, PR creation and independent
review remain controller/reviewer handoffs. This validation does not approve or
complete the queue item.

Final media check: `voxel-street-playthrough-linux.mp4` is 868,279 bytes,
00:03:07.25 long, H.264/yuv420p at 8 FPS. The entire MP4 decoded without errors.
A decoded frame at 182 seconds was visually inspected: the closing account,
company rows, factual explanation and execution timeline remain readable.
`voxel-street-media.json` records source and artifact SHA-256 hashes.
