# Structure review fix checkpoint

Current published base: 3f7ab54c500e77b664312a780f39fdf4bed901d9, PR #76.
Review base: b1d7b8d796b97a87a3d63bb08baf2bb9746b8ca6.

Review collision finding is being fixed. Both authorities now call shared
StructureOccupancy before changing models, edits, geometry or saves. This uses
live city frames and current player poses under the authority lock. Offline F12
placement/undo receive a live player supplier from Main. Nonempty resulting cells
reserve the entire cell; empty undo cells may safely clear geometry around bodies.

Four new offline/server authority tests passed, including horses and players
entering after staging, no partial geometry/dependencies/save, retry after leaving,
and guarded restoration versus safe empty undo. Existing structure/fractional/model
focused regressions passed. Exact totals/commands will be in the review-fix report.
Fresh native workflow (including player collision rejection capture) and 32-session
functional regression are running. Raw logs and synthetic profiles stay in target/.
No earlier CI is claimed for these edits. Hosted checks need controller publication
once local tests/media are complete. Full city/max, physical GPU, shaped-network
performance qualification and stable rollout remain gated.

DeepSeek review task 3d9470ff-84f8-4f41-b8bb-74b2c48dd252 failed; explicitly resolved
self. Independent local inspection confirmed validation precedes mutation, server
city advancement shares the authority lock, and undo uses the same commit guard.

## Completed local validation for collision fix

32 focused tests passed (five new collision tests, including production TLS
protocol). Compile/test commands are recorded in structure-collision-validation.json.
32-session functional regression passed in 345.913 s; 50 ms movement cadence,
production authentication throttle preserved. This is single-host functional load,
not reference distributed/network qualification.
Playtest: python3 deploy/run_structure_playtest.py --display "$DISPLAY" passed on
llvmpipe with isolated synthetic profile. Live-player rejection message and placed
world captures inspected. Four rotations, cancellation, undo, repeat placement,
restart, inventory and model editor regression checks passed. The first harness
attempt failed because it mutated a returned position copy; corrected setup rerun
passed. No production failure was concealed.

Fresh sanitized media, report and source manifest are in dashboard/evidence,
pending controller publication. Native source manifest hash:
665d25b1bb96d248b7fdc279db5b8042ce34b049eb27bfbba5df103a8302def9.
No source changed after tests. No deleted files or unmerged paths; diff check passed.
Next: controller feature_validation handoff for revised source CI, then inspect
exact-head hosted summaries and published media before ready. Previous-head CI
remains historical. Full qualification and stable rollout remain gated.

## Published collision fix

Controller published exact head 2c67bc1f754c8644f98d7ce29ddd47db489094bf.
354 collision-source and 207 native-source hashes match; production source did
not change. Downloaded exact-head rejection image/video/report/native manifest
match local bytes. Rejection screenshot and decoded video frame inspected.
Hosted run: https://github.com/jaymsDooku/voxel-one/actions/runs/39485576505
Linux/Windows/Intel Mac running; ARM Mac queued. No revised-head hosted pass
claimed. Resume on run completion or any job failure; do not repeat unchanged
push or idle status turns. No owner answer needed. Qualification stays gated.

Hosted status update: exact head `2c67bc1f754c8644f98d7ce29ddd47db489094bf` now has eight checks across runs `39485576505` and `39485579789`: seven in progress and one ARM Mac check queued. No final results or passes claimed. Source and media are unchanged. Resume on completion or failure; qualification and stable rollout remain gated.

Linux exact-head CI job `118844989091` passed. Downloaded sanitized test summary and verified sourceHead `2c67bc1f754c8644f98d7ce29ddd47db489094bf`; totals: {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. Six other checks remain running and one ARM Mac check queued. Reports await controller publication. Qualification and rollout stay gated.

Windows exact-head CI job `118844989452` passed. Downloaded sanitized summary matches `2c67bc1f754c8644f98d7ce29ddd47db489094bf`: {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. Linux also passed; five checks running and one ARM Mac check queued. Supplemental reports await controller publication. Qualification and stable rollout stay gated.

Additional Linux job `118846441790` passed; downloaded report sourceHead `c9c2ff3781a3ab1b76b1347d6d87905b8892fc9d`, totals {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. Four checks remain running and one ARM Mac queued. Qualification and rollout remain gated.

ARM Mac exact-head CI job `118844989423` passed. Downloaded sanitized summary matches `2c67bc1f754c8644f98d7ce29ddd47db489094bf`: {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. Push Intel Mac and PR Windows/Intel Mac still running; PR ARM Mac queued. Qualification and stable rollout stay gated.

Intel Mac exact-head CI job `118844989427` passed. Downloaded summary matches `2c67bc1f754c8644f98d7ce29ddd47db489094bf`: {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. All four feature-head builds now passed; feature publish skipped. PR Windows and Intel Mac still running; PR ARM Mac queued. Await remaining PR checks before readiness. Qualification and stable rollout remain gated.

PR Intel Mac job `118846442089` passed. Downloaded report matches merge head `c9c2ff3781a3ab1b76b1347d6d87905b8892fc9d`: {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. PR Windows remains running; PR ARM Mac queued. All four feature-head builds passed. Qualification and stable rollout remain gated.

PR Windows job `118846442118` passed. Downloaded report matches merge head `c9c2ff3781a3ab1b76b1347d6d87905b8892fc9d`: {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. Only PR ARM Mac remains queued. All four feature-head builds passed. Qualification and stable rollout remain gated.

PR ARM Mac job `118846442471` completed with conclusion `cancelled`, not passed. All four feature-head builds and three PR merge-head builds passed. Controller must recover the cancelled PR ARM Mac check against the unchanged submitted source before readiness. No source changes needed. Qualification and stable rollout remain gated.

Paused at controller request for shared quota or service control. All unpublished report and checkpoint edits are preserved. No implementation or tests started during this pause. Source head remains `2c67bc1f754c8644f98d7ce29ddd47db489094bf`. All four feature-head builds passed with 758 tests each and zero failures/errors/skips; three PR merge-head builds passed. Last observed PR run `39485579789` remains attempt 1 with ARM Mac job `118846442471` cancelled; controller retry is pending. Resume only after authoritative quota/service control permits work, then inspect actual retry results. Qualification and stable rollout remain gated.


## Paused validation after master integration

Source head: `e351d91bf4de6f17657976e4cd467aeb3f44c15b`. Controller advanced Git recovery; no Git mutation or publication performed by developer. Java production source and native harness match the previous collision-fix source. Diff check passed; no deleted files against recorded review base or unmerged paths were found.

Focused check: `mvn -q -DargLine="-Djava.io.tmpdir=$PWD/target/structure-audit-tmp" -Dtest=StructureOccupancyTest,StructureTest,FractionalWorldTest,VoxelModelTest test` completed with 32 tests, zero failures/errors/skips. Initial attempt failed because `/tmp` was read-only; retry used the worktree temp directory.

Playtest: fresh `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` and 32-client `StructureMultiplayerTest` with suffix `rebase32` started, then interrupted for the requested pause. Neither run is claimed passed. Initial native attempt started before Maven compilation finished and failed to compile the harness; sequential retry remains incomplete. The native harness removes `dashboard/evidence/structure-native-result.json` at startup; its current absence is an incomplete-run artifact, not an intended feature deletion. Rerun the native harness to restore the result and produce fresh inspected media before publication/review. Existing media and hosted reports remain historical.

Resume: verify stopped process state, rerun incomplete native and 32-client checks, inspect fresh media, write sanitized integration validation evidence, and request controller feature validation publication. Hosted checks for this head, city/max fixtures, physical GPU, shaped-network and 20-run performance qualification remain pending. Readiness and stable rollout remain gated. All unpublished edits preserved.


## Resumed integration validation complete

Source `e351d91bf4de6f17657976e4cd467aeb3f44c15b`: 32 focused tests passed; fresh native Main/GLFW/X11 workflow passed; 32-client loopback functional check passed in 352.882 s with zero failures/errors/skips. Fresh player-conflict and placed-world screenshots inspected. F10 H264 clip decoded: 17 frames, 39.679 s, 364434 bytes. Source manifest verified unchanged. Sanitized commands, results, prior failed/interrupted attempts and media hashes: `dashboard/evidence/structure-rebase-validation.json`. No deleted files or unmerged paths; master features retained. Controller feature validation publication required before hosted checks can finish. Earlier hosted reports are historical. City/max, physical GPU, shaped-network, 20-run performance and stable rollout remain gated.
