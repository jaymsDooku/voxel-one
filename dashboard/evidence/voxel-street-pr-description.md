Voxel Street adds a playable fictional trading day: prepare at the voxel desk, trade or hold cash through a 180-second market, review the outcome, then carry holdings into the next day. The website home now links to the packaged game. Unrelated Music, Sandbox and City features are preserved.

## Acceptance criteria

- [x] Preparation, readable desk focus, optional guidance, order preview, trading, review and next day work without developer tools or a source checkout.
- [x] Three distinct fictional companies have histories and event sensitivities.
- [x] Whole-share market buys fill at ask; sells fill at bid. Cash, holdings, average basis and history update immediately.
- [x] Invalid, unaffordable, oversell and out-of-hours orders are rejected with reasons.
- [x] Midpoint portfolio valuation and daily/realized/unrealized results reconcile, including spread costs.
- [x] Positive quotes and two once-per-session events show expectations, reported results and recorded effects in the timeline.
- [x] Seeded market replay is deterministic and independent of positions.
- [x] Next day preserves account/basis and opens at prior close; restart restores $10,000 and clears positions/events.
- [x] Platform-labelled full-loop MP4 is below 6 MB; playtest note and automated checks are linked below.
- [x] Required Linux, Windows, Mac Intel and Mac ARM builds passed for the tested head.
- [ ] Independent reviewer approval obtained after final checks.

## Verification

Tested source: `c6565c6f6b4f3bb3cc8f75b513048010afb26888`. [Validation notes](https://raw.githubusercontent.com/jaymsDooku/voxel-one/c6565c6f6b4f3bb3cc8f75b513048010afb26888/dashboard/evidence/voxel-street-validation.md), [browser steps and first-use assessment](https://raw.githubusercontent.com/jaymsDooku/voxel-one/c6565c6f6b4f3bb3cc8f75b513048010afb26888/dashboard/evidence/voxel-street-playtest.json), [full Linux Chromium playthrough, 1,171,912 bytes](https://raw.githubusercontent.com/jaymsDooku/voxel-one/c6565c6f6b4f3bb3cc8f75b513048010afb26888/dashboard/evidence/voxel-street-playthrough-linux.mp4), [closing review](https://raw.githubusercontent.com/jaymsDooku/voxel-one/c6565c6f6b4f3bb3cc8f75b513048010afb26888/dashboard/evidence/voxel-street-review.png).

Local checks: 20 Node market/Space Invaders tests and 5 Python package/native-wrapper tests passed. Independent review reports 7 market, 5 Python and 86 regression tests passed. [Exact-head native validation passed](https://github.com/jaymsDooku/voxel-one/actions/runs/39489061897). [Required platform build passed](https://github.com/jaymsDooku/voxel-one/actions/runs/39489071085): Linux, Windows, Mac Intel and Mac ARM all succeeded for the tested head. The publish job was skipped.

Playtest: Linux headless Chromium with an isolated synthetic profile, static Node host serving only the built distribution, no Python server or source-root routing. Full real-time 180-second loop passed: homepage launch, preview, invalid orders, purchase, both announcements, sale, closing review, next day and restart. Existing Space Invaders also launched. The scripted first-use player chose Harbor for steady demand and found actual executions, earnings surprise and facts versus interpretation in the review. This is not an independent human usability study. Playwright recorded the browser slice because it has no native F10 recorder.

Physical-GPU qualification remains pending and gates stable rollout. Website deployment remains runner-owned; no live deployment is claimed. Limit orders, shorting, leverage, derivatives, staff, upgrades, multiplayer, live data and restart persistence remain deferred.

The native validation first attempt failed before captures. One retry passed all three workflows on the same head. Its report and all nine artifact hashes were checked; Sandbox craft and City planning captures were inspected. The original failure cause remains unknown. Saved c6565c6f native evidence awaits controller publication.
