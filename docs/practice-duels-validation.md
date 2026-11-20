# Practice duel validation

Historical validation of the initial `aacdd747` implementation. Its hosted Java 17 checks later passed on all four platforms (802 tests each, no failures/errors, one optional browser skip). Independent review then found two pre-round lifecycle defects. See [the lifecycle follow-up](practice-duels-lifecycle-validation.md) for fixes, new real-TLS regressions and fresh media. Earlier readiness does not qualify the changed source.

Local implementation and workflow checks passed. Controller integration, publication, exact-head hosted CI and independent review remain pending. Physical-GPU performance is unqualified and gates stable rollout. No pending check is a pass.

## Environment and Maven

Linux VPS; OpenJDK 17.0.21; Maven 3.8.7; inherited role display and authentication; llvmpipe (LLVM 15.0.6, 256 bits). Tests use isolated synthetic profiles and in-memory worlds. Scratch output stays under ignored `.tmp/duels/`; no real account or existing terrain save is loaded by the practice playtests.

Final command, exit **0**:

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH timeout --kill-after=5s 360s mvn -B -Dmaven.repo.local=.tmp/m2 -DargLine="-Djava.io.tmpdir=$PWD/.tmp/duels/tmp" -Dsurefire.reportNameSuffix=practice-targeted-final -Dtest=DuelSessionTest,DuelTransportTest,CombatSimulationTest,CombatBoundaryTest,BasaltCourtyardTest,ProtocolCompatibilityTest,SandboxIdentityTest,ControlsTest,PlayerTest,PlayerCameraTest,CityTest,MultiplayerTest verify
```

Actual Maven summary:

```text
[INFO] Tests run: 99, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  02:57 min
```

All 12 suites passed: duel/session/transport 15; foundation combat 28; Basalt 8; protocol 3; save identity 5; controls 5; Player/camera 9; City 11; multiplayer 15. Client, existing server and launcher artifacts built. ZIP inspection confirmed the bundled client contains `DuelServer`, `PracticeApp`, `PracticeView` and both practice shaders. The dedicated practice server runs from that bundled classpath; the existing classified City/Sandbox server is preserved.

An earlier full `verify`, before the final UI/backpressure fixes, used the same Java/cache/temp settings with `timeout --kill-after=5s 900s` and `-Dsurefire.reportNameSuffix=practice-final`, without `-Dtest`. It ended with exit **143** while in `MultiplayerTest`. Completed XML reports cover 99 suites, 564 tests, 0 failures/errors and 1 opt-in `AssetManagerIntegrationTest.browserWorkflow` skip. **The full run did not pass.** The final focused run above subsequently passed `MultiplayerTest` and all affected checks. A full exact-head hosted run is still required.

## Playtest: two native clients

The final application source was also compiled directly with Java 17 into ignored `.tmp/duels/final-classes` while the broad Maven attempt ran. This included all practice production classes, modified Main/catalog/login/foundation classes, and `PracticePlaytest`. The final Maven run then compiled and packaged the same source through the normal build.

Executed command, exit **0**:

```sh
timeout --kill-after=5s 720s /usr/lib/jvm/java-17-openjdk-amd64/bin/java -Duser.home="$PWD/.tmp/duels/profile" -Djava.io.tmpdir="$PWD/.tmp/duels/tmp" -Dorg.lwjgl.system.SharedLibraryExtractPath="$PWD/.tmp/duels/natives" -cp ".tmp/duels/final-classes:target/test-classes:target/classes:$(cat .tmp/duels/classpath)" dev.jayms.combat.duel.PracticePlaytest --display "$DISPLAY" dashboard/evidence .tmp/duels/native-final
```

One real pinned-TLS server, two real clients and two native engine views per case. Inputs use the same client API as the production controls. No pose injection or privileged test packet is used. Each direction delays packets by 0, 40 or 75 ms without a serial sleep per packet. The table reports configured network RTT; fixed-tick queue time is additional.

| Configured RTT | Profile | Identical authoritative ticks compared | Rendered view pairs | Result |
| --- | --- | ---: | ---: | --- |
| Normal / 0 ms added | No Debuff | 1,679 | 3,584 | Complete |
| 80 ms | No Debuff | 1,630 | 3,713 | Complete |
| 150 ms | Debuff | 1,690 | 3,943 | Complete |

Expected and observed: matching ticks have equal full states on both clients, including hit counts, damage, impulse, inventory, effects and result. The run asserted spacing/reach; sprint release/repress and counter-combos; feet/opponent healing; forward splash while kiting; a horizontal missed pot; depleted-slot refill; No Debuff/Debuff supplies; speed/fire drinks before harmful splashes; poison; pearl escape/cooldown; reconnect; forfeit; stale epoch rejection; fresh rematch; input-driven routes around the actual cover; diagonal hit-through-cover rejection; pearl containment at the outer wall; knockout; and blocked item use after death. All cases completed. Separate real-TLS tests verify that a missed splash spends its pot without healing, spectators cannot ready a duel, rooms remain isolated, a full queue does not stop other matches, and the offline target readies/rematches through the real transport. Session tests also cover timeout/draw and disconnect grace.

These representative rows are from matching snapshots observed by **both** clients. Input frame is the caller's view-frame tag; input sequence is the server's command high-water mark. Server tick continues across rounds; combat tick restarts with fresh combat state. Health/hits columns refer to actors A/B, not separate client predictions.

| RTT ms | Action | View frame | Input frame A/B | Input sequence A/B | Server/combat tick | Health A/B | Hits A/B | B velocity (blocks/tick) |
| ---: | --- | ---: | --- | --- | --- | --- | --- | --- |
| 0 | A hit | 482 | 482/482 | 188/140 | 271/188 | 20.000/17.600 | 1/0 | (0.0000,0.3136,0.3185) |
| 0 | B counter | 628 | 628/628 | 270/222 | 354/271 | 17.600/9.000 | 5/1 | (-0.0000,0.1459,0.2638) |
| 80 | A hit | 517 | 511/511 | 181/141 | 259/182 | 20.000/17.600 | 1/0 | (0.0000,0.3136,0.3185) |
| 80 | B counter | 710 | 705/705 | 263/223 | 341/264 | 17.600/9.000 | 5/1 | (-0.0000,0.1459,0.2638) |
| 150 | A hit | 557 | 549/549 | 165/143 | 246/167 | 20.000/17.600 | 1/0 | (-0.0000,0.3136,0.3185) |
| 150 | B counter | 745 | 738/738 | 247/225 | 328/249 | 17.600/9.000 | 5/1 | (-0.0000,0.1459,0.2638) |

The first hit's B horizontal impulse was `0.3185` blocks/tick after air friction in all three cases. Sprint-reset samples reached `0.4563` at server ticks 287 / 275 / 262 respectively. Counter-hits damaged A in each case. Differences between latency cases are expected input timing outcomes; each case's two clients agree on its server result.

A separate 10-sample real-TLS probe measured input acknowledgement including the fixed-tick queue: median **49.800 / 99.850 / 199.809 ms** for configured RTT **0 / 80 / 150 ms**. Min/max were **47.203–52.506 / 98.386–102.324 / 186.305–201.122 ms**. This is local synthetic workflow timing, not an Internet or physical-GPU performance qualification.

## Playtest: production controls and F10

Launched Java 17 `dev.jayms.Main --game practice --offline` with the final compiled source, isolated `.tmp/duels/ui-final-profile` home, the same temp/native paths and inherited display. `xdotool` targeted only the titled practice window on that role display. Executed F10; E; arrows/Enter to swap hotbar Heal/Pearl; R while the kit panel was open; warmup; E during Live; Shift+W for 9.5 seconds; three left presses; downward aim; Pearl; Heal; F refill; Q forfeit; F10 stop.

Expected and observed in the fresh native captures: the panel closes on Live and cannot trap movement; the swapped slots remain; the player reaches the target; one sword hit lands; pearl count changes **4 -> 3** with cooldown; a healing pot is spent and the empty slot refills; the result says **Forfeit: B wins**, **A hits 1**, **longest combo 1**, **pots used 1**, **pots remaining 23**, **A health 20**, **B health 20**. The splash can heal the nearby target as well. The MP4 midpoint and ending, plus the Live and Result PNGs, were visually inspected. Java 25's AWT Robot was used only to crop the already-running Java 17 window for these PNGs; it did not run the game.

An initial failed UI capture was archived in ignored scratch and is excluded from evidence. The final source keeps waiting/disconnect feedback visible and coalesces queued state corrections while preserving welcome/control frames. The fresh final clip displays the actual ready, duel and result flow.

## Media and gates

Both MP4s are fresh engine `ScreenRecorder` output. The controls clip was toggled by actual F10 keys; the two-client duel clip used the same F10 recorder path from the native harness. A JCodec verifier decoded every frame, checked H264, size, frame count and changing first/last frames, and extracted frames for visual review. Both are below 6,000,000 bytes.

| Media | Bytes | Decoded frames | Seconds | SHA-256 |
| --- | ---: | ---: | ---: | --- |
| [practice-real-duel.mp4](../dashboard/evidence/practice-real-duel.mp4) | 506,977 | 18 | 3.245 | `d4821c7333cb0502fd5ee4aa211e801270c644cb2e07fb291db54f8f2c0e2cb0` |
| [practice-controls.mp4](../dashboard/evidence/practice-controls.mp4) | 3,571,508 | 263 | 27.451 | `12fd4d752d3af645598c95ef151e32eff0c4816c66728d0dc219aa6c39bf98c5` |

Approved PNGs: `practice-0ms-a.png`, `practice-0ms-b.png`, result images at 0/80/150 ms, and `practice-controls-live.png` / `practice-controls-result.png`. They show synthetic participants, the original Basalt fixture, HUD and results. Floor-only test captures were archived under ignored scratch. There are no prompts, answers, account data or credentials in the attached media.

All media and this report are **pending controller publication** on the assigned feature branch. No hosted CI, independent review, physical-GPU performance or stable rollout is claimed. The controller must synchronize master, publish the assigned source/media, return an exact-head receipt and run required hosted checks. If synchronization changes game source, rerun affected checks and native playtest/media before review.

## Diff audit

Pre-sync base: `f446ad3f0c8332aa1002885468e1a20fa271018e`. The changes add the isolated practice package, two shaders, tests/harness, docs, media and one progress entry. Existing edits are limited to the game catalog, practice routing/menu option, and trusted projectile-view/slot-swap foundation adapters. Existing City/Sandbox protocol and save implementations are untouched. No files are deleted; there are no intended unrelated deletions. `git diff --check` passed. Final tracked/untracked inspection found only these intended edits; scratch remains ignored and there are no unmerged paths. The synchronized review base has not yet been provided, so the final post-sync audit remains pending.
