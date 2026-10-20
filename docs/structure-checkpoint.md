# Structures checkpoint: hosted validation handoff

All edits and evidence remain in the assigned feature worktree. No developer
commit, push, merge or deployment. Current integrated HEAD is
b1d7b8d796b97a87a3d63bb08baf2bb9746b8ca6; controller review base/synchronization
receipt has not been supplied. No unmerged paths or tracked file deletions.

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
Native images/video and report are local, pending controller publication. Raw
runtime logs, synthetic TLS/accounts and binary benchmark archives stay in target/;
do not publish them as dashboard media. The allowlisted execution bundle remains
local in target/structure-handoff.

Request trusted feature_validation handoff: controller stages/commits, integrates
as needed, pushes only the assigned feature branch and returns an exact-head
receipt plus hosted results. This is not a review submission or completion claim.
.github/workflows/build.yml runs Java 17 mvn verify on Linux, Windows, mac-intel
and mac-arm after the feature push. Inspect those results on resumption. If source
changes during integration, rerun meaningful tests and fresh media. Audit against
the controller-supplied review base when available. Do not commit/push manually.
No delegate tasks remain unresolved.

Reference GPU/shaped-network/distributed 32-client qualification, city/max fixtures
across 20 runs, supported geometry/instance limits, frame/tick/response budgets and
stable rollout remain gated. The current prototype deliberately rejects full
city/max geometry. Synchronous final application/persistence, the existing
2,000,000-edit ceiling and 100 ms fixed-delay server tick schedule are unresolved
qualification/implementation gates. Do not claim final acceptance or stable support.
