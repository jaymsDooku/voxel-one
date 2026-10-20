# Structures checkpoint: hosted validation handoff

Controller published exact head 72e9ff865b55f86ea188afbfa67feb4c56c888e6 in
PR #76 against review base b1d7b8d796b97a87a3d63bb08baf2bb9746b8ca6.
The developer performed no commit, push, merge or deployment. No unmerged paths
or deleted files. The tested 354-file source manifest and 206-file native manifest
match the published checkout. Production source did not change on publication.

## Current implementation

Experimental VSTR packages, capture/anchor/library/import/export, immutable
component namespace, bounded footprint preview, four rotations, occupied-cell
rejection, empty-space preservation, cancellation, atomic save/rollback, guarded
single-operation undo, authoritative multiplayer publications, dependency transfer,
late joining/restart and high/low geometry streaming. See structures.md for
provisional limits and remaining performance restrictions.

Client structure replies now correlate to request IDs. Late cancellation replies
cannot clear a newer placement. Natural-terrain request heights are clamped
separately from high camera positions, keeping all ten base layers available.
Native test commands use render-thread dispatch/completion barriers.

## Executed validation

- 53 focused checks passed after the reply/streaming/compatibility fixes.
- Complete regression run: 753 tests, 0 failures/errors/skips; BUILD SUCCESS,
  13:20 min. Command and environment are in the validation report.
- 32-session integration: passed in 340.9 s with the production 50 ms movement
  cadence. Two login cohorts honor the existing 306-second throttle interval.
  Includes all rotations, 512 dependencies, conflicts, movement state, cancellation,
  late cancellation replies, contested destination, undo, late join and restart.
- An earlier doubled-rate 25 ms run failed on observer movement after 424.1 s.
  Its cause is not established or declared fixed. It is retained in the report.
- Playtest: production Main/GLFW/X11 workflow passed on llvmpipe. Native media and
  decoded H.264 frame inspected. Video is 23.763 s, 15 frames, 270106 bytes.
  Current 206-file native source manifest matches the implementation/harness.
- 18 Python tool tests and 16-member/206-source bundle verification passed.
- git diff HEAD --check passed. No unrelated tracked deletions intended.

The full suite used the earlier movement helper; the only later test-only change
adds connection diagnostics and matches Main's 50 ms cadence. That helper was
compiled separately and passed the 32-session retry. Production code did not
change after the successful native run. Hosted CI must freshly compile/test the
entire submitted source on Java 17.

## Evidence and next action

Sanitized report: dashboard/evidence/structure-experimental-validation.json.
Source hashes: structure-experimental-source.json and structure-native-source.json.
Native images/video and the original validation report are published at the exact
head above. Downloaded bytes match local captures; conflict image and a decoded
video frame were inspected. Supplemental receipt:
`dashboard/evidence/structure-hosted-validation.json` (local, pending publication).
Raw runtime logs, synthetic TLS/accounts and binary benchmark archives stay in
target/; never publish them as dashboard media.

Hosted runs on this exact source:
- Push: https://github.com/jaymsDooku/voxel-one/actions/runs/39479041151
- PR: https://github.com/jaymsDooku/voxel-one/actions/runs/39479051049

Both Linux jobs passed. The push Linux sanitized summary matches exact feature
head 72e9ff8: 753 tests, zero failures/errors/skips. The PR summary names merge
commit 38c732c. Windows and Intel Mac jobs remain running; ARM Mac remains queued.
Source and previously checked media are unchanged. Other hosted platforms remain pending. Resume when either run
concludes or a job fails. Inspect actual results and the sanitized
`maven-test-summary-<platform>` artifacts; do not read full runtime logs. Do not
repeat the unchanged feature_validation handoff or ask the owner to relay results.
If integration changes source, rerun meaningful checks and fresh media. No owner
answer is needed. No delegate tasks remain unresolved.

Reference GPU/shaped-network/distributed 32-client qualification, city/max fixtures
across 20 runs, supported geometry/instance limits, frame/tick/response budgets and
stable rollout remain gated. The current prototype deliberately rejects full
city/max geometry. Synchronous final application/persistence, the existing
2,000,000-edit ceiling and 100 ms fixed-delay server tick schedule are unresolved
qualification/implementation gates. Do not claim final acceptance or stable support.

Latest hosted result: push Windows passed on exact head 72e9ff8: 753 tests,
zero failures/errors/skips. Sanitized summary saved as
`dashboard/evidence/structure-hosted-windows-summary.json` (pending publication).
PR Windows and all Mac jobs remain pending. No production source changed.

PR Windows also passed: 753 tests, zero failures/errors/skips. Its summary
names 38c732c7509659fe8ccd8722b8272b2032989af2 (PR merge commit).
Only Intel/ARM Mac jobs remain pending. Resume on a remaining job completion or
failure; avoid unchanged status turns.

PR Intel Mac passed: 753 tests, zero failures/errors/skips. Summary source
38c732c7509659fe8ccd8722b8272b2032989af2 is the PR merge commit.
Saved sanitized summary locally. Push Intel Mac and both ARM Mac checks remain
pending; qualification and rollout stay gated.

Push Intel Mac passed on exact head 72e9ff8: 753 tests, zero failures/errors/skips.
Sanitized summary saved locally. Only the two ARM Mac jobs remain running.
Qualification and stable rollout remain gated.

PR ARM Mac passed: 753 tests, zero failures/errors/skips, on merge commit
38c732c7509659fe8ccd8722b8272b2032989af2. All four PR build jobs passed;
release publication was skipped as expected. Only exact-head push ARM Mac remains
running. Sanitized summary saved locally; qualification and rollout remain gated.

## Final hosted validation

All eight push/PR build jobs passed. Exact-head ARM Mac summary matches
72e9ff865b55f86ea188afbfa67feb4c56c888e6: 753 tests, zero failures/errors/skips.
All sanitized summaries are saved in dashboard/evidence for controller publication.
Release publication was skipped as expected. No source changed during hosted checks.
Experimental implementation is ready for independent review only. Full city/max
support, reference performance qualification and stable rollout remain gated as
documented above. Earlier pending-CI notes are historical.
