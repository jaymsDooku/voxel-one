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

The controller completed the rebase. Recovered source HEAD is
`2285f5c61383ea1a5b5595d7e19142d86f977585`. There are no unmerged entries.
Fresh Node and Java checks were run from this source. See final-audit and
native-unit reports for scope and native graphical execution limits.

All local media is from this implementation and is pending controller
publication. Commit, feature push, hosted checks, PR creation and independent
review remain controller/reviewer handoffs. This validation does not approve or
complete the queue item.


## Post-recovery audit and checks

The full proposed diff was audited against both recorded base
`26b8761ff9c355299aa1df83608a6f6ebcba7e0b` and integration base
`b1d7b8d796b97a87a3d63bb08baf2bb9746b8ca6`. There are no deleted paths
against the recorded base. Relative to the integration base, only Voxel Street
files and its progress entry change. All 37 protected music/sandbox files match
the integration base byte-for-byte. All pre-existing progress entries remain
unchanged. This replaces the incomplete audit of the earlier submitted head.
Details: `voxel-street-final-audit.json`.

The 20 Node tests and both JavaScript syntax checks passed again. Maven compiled
the recovered native client and passed 16 tests: MusicAnalysisTest (6),
SandboxIdentityTest (5), ControlsTest (5). Command and sanitized Surefire results
are in `voxel-street-native-unit.json`.

Native graphical regression command:
`python3 deploy/run_music_regressions.py --display "$DISPLAY"`.
It refused preflight with `Inherited assigned DISPLAY required`: this turn has
neither inherited DISPLAY nor XAUTHORITY. No graphical music launch/return,
Sandbox, or City workflow ran in this turn. No other role display, authentication
cookie, or fabricated graphical session was used. These graphical checks remain
pending; the passing unit tests and byte-level preservation audit are not
claimed as graphical playtest results. Physical GPU qualification is also pending.

Fresh post-recovery Playtest: every player-control step passed again on recovered
source: desk, preparation preview, invalid orders, ask purchase, expected/reported
earnings, bid sale, desk return, broader event, closing review, day 2 carry, and
seed restart. Browser page errors: none. The losing day reconciled to -$0.96.
The fresh MP4 is 837,990 bytes, 00:03:06.13 long, H.264/yuv420p at 8 FPS.
Full decode passed. The new review PNG was visually inspected; a decoded frame
at 182 seconds was extracted and visually inspected. The account rows,
factual explanation and execution timeline remain readable. First-use assessment is
scripted, not a human study. Fresh publication and independent review are pending.

Required native graphical regression remains pending. To unblock without asking
for physical GPU or desktop access, the controller or independent reviewer can
run `python3 deploy/run_music_regressions.py --display "$DISPLAY"` against
source `2285f5c61383ea1a5b5595d7e19142d86f977585` on its assigned graphical
validation runner and return sanitized workflow results. No owner credentials
or full runtime logs are needed. Native unit checks alone do not satisfy that
graphical rerun. All local edits and new media remain preserved.

Native validation handoff: fresh results have not yet been supplied for recovered
source `2285f5c61383ea1a5b5595d7e19142d86f977585`. Existing music-regression reports
are inherited evidence, not a fresh execution on this source. Trading media
hashes were rechecked against the saved manifest; all match. Required validation
remains pending until actual native workflow results are inspected.

## Automated hosted native validation

`.github/workflows/voxel-street-native.yml` now runs on the assigned feature
branch push. The controller owns that push. The job uses an Ubuntu 22.04
GitHub-hosted runner, Java 17, Mesa software rendering and a disposable Xvfb
display on that hosted runner. It does not start a VPS display or use another
role's managed display. The job compiles the exact source, runs the 16 native
unit tests, then invokes `deploy/check_voxel_street_native.py` with the exact
expected commit and inherited hosted display. That wrapper executes the existing
Main/music, Sandbox and City harness without changing their sources.

The wrapper removes stale synthetic results first, restores the inherited music
report bytes in `finally`, and collects only allowlisted test text and PNGs into
`target/voxel-street-native-evidence`. No runtime logs, accounts, profiles,
credentials or authentication cookies are uploaded. All three actual workflow
results and a zero exit code are required for a pass. The report records the
exact checked-out source SHA. The workflow has read-only repository permissions
and does not retain checkout credentials, commit, deploy or publish a release.

Playtest: this addition changes CI/CLI orchestration, not gameplay. Ran
`python3 -m unittest discover -s deploy -p test_voxel_street_native.py -v`: three
passed. Tests exercise artifact filtering, missing/failed workflow results,
stale-result removal and inherited report restoration through a synthetic
failed runner. CLI rejection with no assigned display returned exit 2 as
expected. Workflow YAML parsed; exact feature-push scope, read-only permission
and restricted upload path were checked. These are orchestration checks, not
graphical game results. Browser playtesting does not apply to this CI addition;
all saved trading source/media hashes still match the played implementation.

Next controller action: stage and push the assigned feature branch normally.
The native check starts automatically. Return artifact
`voxel-street-native-<exact source SHA>` to the developer for report/capture
inspection. Keep the work out of review until those results pass. This is an
authorized runner/CI handoff; no new owner question is required. Actual native
graphical execution remains pending and is not claimed passed.

### Hosted validation of 2ec9e8edc8fca4a61758b639356a2191ee79365f

Run 39475756266 compiled the exact source and passed its native unit step. The graphical step failed with exit code 1. Its sanitized artifact reports catalog, sandbox and city as unpassed and contains no captures. No graphical pass is claimed. Physical-GPU qualification remains pending.

Added fixed diagnostic codes to the artifact wrapper; raw runtime text is never returned or published. Ran `TMPDIR="$PWD/.tmp" python3 -m unittest discover -s deploy -p test_voxel_street_native.py -q`: 4 passed. `git diff --check` passed. Playtest: hosted graphical attempt failed before producing usable workflow evidence; another exact-source hosted run is required.
