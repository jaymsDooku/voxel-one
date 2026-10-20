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

Validation: 20 automated tests passed, including existing game regressions.
The Linux Chromium playtest completed the full real-time loop with a purchase,
announcement, partial sale, both events, review, next day and seed restart.
The 187.25-second MP4 is 868,279 bytes. See
`dashboard/evidence/voxel-street-validation.md`, `voxel-street-playtest.json`,
`voxel-street-unit-tests.txt`, and `voxel-street-media.json` for commands,
observations, limits and source hashes. Screenshots and MP4 are local until the
controller publishes the assigned feature branch.

Independent final-head review, feature-push CI and synchronization with the
review base remain required. The developer does not approve this work. Physical
GPU performance is unqualified; no stable rollout claim is made. No commit,
push, merge or deployment was performed by the developer.

Recovery note: independent review found unrelated music visualizer deletions and
sandbox smoke-test reversions in the earlier submitted head. The preserved
rebase tree retains those merged features and fixes. The progress conflict now
retains both entries. Controller rebase continuation and fresh final-head checks
are pending; the earlier validation is historical, not approval of this recovery.
