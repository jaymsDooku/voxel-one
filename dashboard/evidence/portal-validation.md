# Portal and Cinder Quay validation

Implemented and tested. Awaiting controller synchronization, publication and independent exact-head review. This is a working-source report, not a merge or deployment claim. All links below are pending controller publication on the assigned feature branch.

## Source and scope

Integrated reviewed base: `d6ed16fecd384c175edf4b8e595b77a8f2759ae5` (ground PR 66 and descent PR 63). The complete game-source digest list is [portal-source-sha256.json](portal-source-sha256.json). Generated website game modules match the source bytes. The current published master was also checked: `3760edffdbcddc758f88c834ab03dbd600b4aa8a`; its added files concern Java block raycasting and progress evidence. The controller must integrate it before review. Rerun relevant checks if integration changes game source. Historical dependency/recovery checkpoint reports are superseded by this report. `alternate-earth-contract-tests.txt` and `alternate-earth-recovery-tests.txt` are historical; `alternate-earth-unit-tests.txt` is the current full TAP report.

The same run now goes classic → fighter/descent → alien ground → walk-through portal → Cinder Quay, an original fictional American harbour under Nazi occupation in alternate 1964 after an Axis victory. Original brick warehouses, period sedans, rails, lamps, print counter, radio, newspaper, harbour props, occupation guards, Mara Vale and noncombatant civilians form the district. The player meets the contact, crosses a checkpoint and disables the relay. Completion ends the chapter.

One coordinator retains the source through preparation, fences stale results, validates the destination spawn and guards, latches crossing before commit and copies immutable resources. It preserves health, ammunition, lives, score, weapon, difficulty, settings, orientation and elapsed simulation time. Destination profile/seed and empty terrain edits are independent of the source. Source projectiles, input, attack/reload timers, CPU caches and renderer jobs/buffers are cleared at transfer; the preparer closure also releases its source reference; restart disposes the renderer. The destination has checkpoints. The campaign remains session-only; reload restoration is not supported or claimed.

## Executed checks

Environment: Linux VPS, Node v22.14.0, sandboxed headless Chromium 153.0.8010.12 and WebKit 26.6. Source HTTP server: `python3 -m http.server 8779 --bind 127.0.0.1`. All browser contexts are isolated synthetic profiles. Commands use `TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers`.

- `node --test games/space-invaders/*.test.mjs`: **86 passed, zero failed**. [TAP report](alternate-earth-unit-tests.txt). Includes score/last-kill idempotency, front/back and low-frame sprint crossing, aperture/frame misses, orientation, resources, clock carry, safe arrival, all four built-in source seeds at three locations, preparation timeout/failure/retry, stale jobs, cleanup, pause/restart, three district routes, opaque cover, noncombatants and checkpoints.
- `node deploy/test_space_invaders_portal.cjs`: **four scenarios passed**, desktop 1280×720 and touch 390×844 in both engines. [Report](portal-browser-report-all-0.json).
- `PORTAL_ENGINE=chromium PORTAL_TOUCH=1 PORTAL_LANDSCAPE=1 PORTAL_REDUCED=1 node deploy/test_space_invaders_portal.cjs`: **passed** at 667×375 with reduced graphics and motion, including real touch INTERACT events. [Report](portal-browser-report-chromium-1-landscape.json).
- `node deploy/test_portal_natural_combat.cjs`: **passed** with default health, lives, AI and immunity rules. Two guards defeated, score 100, health 76, lives 3, immunity 0. [Report](portal-natural-combat.json).
- `node deploy/run_portal_regression.cjs test_space_invaders_ground.cjs`: **six scenarios passed**, Chromium/WebKit at 1280×720, 390×844 and 667×375. Native pointer lock was granted in headless Chromium; denied-lock fallback was separately injected. [Report](portal-regression-ground-browser-report.json).
- `GROUND_ORBITAL=1 node deploy/run_portal_regression.cjs test_space_invaders_ground.cjs`: **desktop Chromium passed**, including the opt-in descent and portal continuation. [Report](portal-regression-orbital-ground-orbital-browser-report.json).
- The same regression wrapper ran `test_space_invaders_entry.cjs` (**three WebKit viewports**), `test_space_invaders_fighters.cjs` (**three WebKit viewports**), `test_space_invaders_collisions.cjs` (**20 live hit/miss checks**) and `test_alien_terrain.cjs` (**Chromium/WebKit seed gallery, biome controls and excavation**). All passed. Reports use the `portal-regression-` prefix and preserve historical evidence.
- `node deploy/test_portal_launcher.cjs`: **fresh and cached local generated launches passed**, including real classic movement/fire and imports of new modules. [Report](portal-launcher-report.json).
- `python3 deploy/build_site.py`, generated-source byte comparison, JSON validation and `git diff HEAD --check`: **passed**. No file deletions appear in the proposed diff.

Playtest: A fixture marks eleven source pilots defeated, then real F input kills the last required pilot. Expected one reward and one reachable portal while control remains available; observed score 1000→1075 once and active movement/fire. Shooting the portal did not transfer. Keyboard walking and simultaneous synthetic touch move/look crossed the opening. Arrival required a new Take control click, with no automatic lock request. Score 1075, lives 2, health 63 and loaded ammunition matched the exact sampled pre-crossing value (without a refill). Camera steering was scripted; movement, firing and interactions used controls. The player walked to Mara, read the dated underground newspaper, encountered actual occupation projectiles, killed a guard, crossed cover-lined routes and disabled the relay. The chapter-complete screen appeared. Failed preparation retained the source mission and coordinates; Cancel, E/INTERACT reactivation and successful Retry worked. Pause, rotation/context recovery, loading restart, destination death and campaign restart passed. The long route fixture grants temporary immunity to isolate objective/input tests; the separate natural-combat run uses **no immunity, health, lives, damage or enemy overrides** and took actual guard damage.

## Cost and limits

Each preparation keeps one bounded CPU destination and the intact source. There is no second GPU world or live portal camera. The preview is a fixed original voxel street. Destination has nine actors and a bounded prop list; source CPU voxel caching is capped at 250,000 entries, ground GPU meshes at 16 MiB, projectiles at 32, trails at 12 and pickups at 16. Preparation timeout is eight active-simulation seconds and freezes with pause. The source is retained until commit.

Measured destination samples (software rendering with other QA processes active; not controlled hardware benchmarks):

| Engine | Viewport | Prepare call ms | Frame p50 / p95 ms | GPU mesh bytes |
|---|---|---:|---:|---:|
| chromium 153.0.8010.12 | 1280×720 | 2.10 | 50.00 / 66.70 | 1558944 |
| chromium 153.0.8010.12 | 390×844 | 1.50 | 16.70 / 33.40 | 1558944 |
| webkit 26.6 | 1280×720 | 1.00 | 61.00 / 71.00 | 1203264 |
| webkit 26.6 | 390×844 | 3.00 | 40.00 / 51.00 | 1406016 |

These measurements do not establish 60 FPS desktop or 30 FPS phone performance. Physical iPhone/Safari, named GPU, real touch capture and audio-output checks remain follow-up. Browser recordings are silent; audio cues are implemented but not audibly proved by these clips. Production Sites/Hub/CDN publication and feature CI belong to the controller/reviewer after review; they were not executed here.

## Media

Normal-speed browser recordings, re-encoded without speed changes to H.264 MP4. Each finished clip is below 6 MB; exact bytes/durations are in [portal-media-metadata.json](portal-media-metadata.json). Media were visually inspected. All profiles and failure messages are synthetic.

- [Last pilot → portal → arrival → guard encounter → relay, plus lifecycle checks](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-36303761643261652d393034372d346565312d626666332d663431303333623661393130/dashboard/evidence/portal-journey.mp4).
- [Default-rules occupation fight, with natural damage](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-36303761643261652d393034372d346565312d626666332d663431303333623661393130/dashboard/evidence/portal-natural-combat.mp4).
- [Source portal](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-36303761643261652d393034372d346565312d626666332d663431303333623661393130/dashboard/evidence/portal-source-chromium-desktop.png).
- [1964 arrival](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-36303761643261652d393034372d346565312d626666332d663431303333623661393130/dashboard/evidence/portal-arrival-chromium-desktop.png).
- [Occupation projectile](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-36303761643261652d393034372d346565312d626666332d663431303333623661393130/dashboard/evidence/portal-guard-fire-chromium-desktop.png).
- [Resistance/newspaper](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-36303761643261652d393034372d346565312d626666332d663431303333623661393130/dashboard/evidence/portal-contact-chromium-desktop.png).
- [Intact source after preparation failure](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-36303761643261652d393034372d346565312d626666332d663431303333623661393130/dashboard/evidence/portal-failure-webkit-desktop.png).
- [Successful retry](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-36303761643261652d393034372d346565312d626666332d663431303333623661393130/dashboard/evidence/portal-retry-webkit-desktop.png).
- [Reduced landscape arrival](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-36303761643261652d393034372d346565312d626666332d663431303333623661393130/dashboard/evidence/portal-arrival-chromium-touch-landscape.png).
- [Chapter completion](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-36303761643261652d393034372d346565312d626666332d663431303333623661393130/dashboard/evidence/portal-complete-chromium-desktop.png).

## Diff audit and handoff

Audited the proposed changes against the integrated base above, including new source/assets and generated website copies. Intentional replacements remove the old Continue-to-level-four-cockpit handoff, wave-parity renderer selection and assertions/documentation for that former progression. The terrain lab regression uses an isolated survey fixture rather than entering the campaign ground mission. No unrelated feature or file was deleted. Every existing base progress entry remains unchanged; the only added item is this queue task.

No commit, push, merge, deployment, master mutation or other-worktree edit was performed. The controller owns synchronization and publication. A separate reviewer must test the exact submitted head, inspect the published media and check hashes before approval.
