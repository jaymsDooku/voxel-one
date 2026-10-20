# Add the first playable Voxel Street trading day

A player can use the voxel desk to research three fictional businesses, preview
risk, trade through a three-minute session, inspect a reconciled closing review,
and carry positions into the next day. The simulation is deterministic and
independent of player holdings. No profit or guidance-completion gate is used.

Acceptance covered by this change:

- Preparation, market, review and next-day flow through visible controls.
- Three distinct businesses, price histories and event sensitivities.
- Whole-share market buys at ask and sells at bid; clear invalid-quantity,
  insufficient-cash, oversell and closed-market rejection.
- Immediate cash, basis, holdings and timestamped execution accounting.
- Midpoint equity, spread costs, daily dollar/percentage reconciliation,
  realized/unrealized results and company contributions.
- Positive valid quotes, one company announcement and one broader event per
  session, expectations/results and recorded event impulses.
- Matching seeds/actions replay the same market and account.
- Next day carries account/basis and opens at prior closes; restart clears it.
- Readable focused trading view entered from the voxel desk.

Validation on recovered source `2285f5c61383ea1a5b5595d7e19142d86f977585`:
20 Node tests and 16 Java tests passed. The full proposed diff retains the music
visualizer and sandbox smoke fixes. All 37 protected files match the integration
base byte-for-byte; no paths are deleted against the recorded base. The earlier
submitted-head deletion-audit claim was incomplete and has been corrected.

Fresh browser playtest and media results are recorded in
`dashboard/evidence/voxel-street-validation.md`, `voxel-street-playtest.json`,
`voxel-street-unit-tests.txt`, and `voxel-street-media.json`. Source and artifact
hashes identify the tested implementation. Media awaits controller publication.

The native graphical harness refused preflight because this turn has no inherited
DISPLAY or XAUTHORITY. Native graphical launch/return, Sandbox and City checks
remain pending; unit tests are not claimed as graphical workflow validation.
Physical GPU performance is also unqualified. No stable rollout is claimed.

Independent final-head review and feature-push CI remain required. The developer
does not approve this work. No commit, push, merge or deployment was performed
by the developer. The controller alone completed the prior rebase.

The remaining native test now has a scoped feature-push GitHub Actions job:
`.github/workflows/voxel-street-native.yml`. It returns an exact-source report
and allowlisted synthetic captures from a disposable hosted software-rendered
display. Three local orchestration checks pass. Actual hosted graphical results
remain pending; the controller must return the artifact for inspection before
review. This adds validation infrastructure only; native game sources remain
unchanged relative to the integration base.
