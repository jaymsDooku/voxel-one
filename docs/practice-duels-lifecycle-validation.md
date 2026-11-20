# Practice duel lifecycle fixes

The two pre-round departure defects are fixed. This report covers revised working-tree source after `aacdd74724f5c8fd89fe5e27f9e633366d4d5e81`. The review base remains `f446ad3f0c8332aa1002885468e1a20fa271018e`. New exact-head CI and independent review, including the independent 150 ms check, remain required. Physical-GPU qualification remains pending and gates stable rollout.

## Changes

- Matchmaking and queue-capacity checks use one predicate: a QUEUE room needs a live side-A peer and an empty side B. A reconnect lease without a live waiter cannot absorb newcomers.
- N can leave READY immediately. The server and session use the same phase guard; WARMUP and LIVE still require forfeit instead of requeue.
- READY now uses the existing 200-tick / ten-second disconnect grace. Expiry cancels the pairing and opens the result flow without awarding a combat win. A returning player must ready again. Established-round disconnect/win/draw rules are unchanged.

## Maven and regression checks

Linux; OpenJDK 17.0.21; Maven 3.8.7; isolated synthetic TLS identities and profiles. All transient output remains in ignored `.tmp/duels/lifecycle-fix/`.

Before the fix, these real-TLS regressions reproduced the defects:

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH timeout --kill-after=5s 120s mvn -B -q -Dmaven.repo.local=.tmp/m2 -DargLine="-Djava.io.tmpdir=$PWD/.tmp/duels/lifecycle-fix/tmp" '-Dtest=DuelLifecycleTest#newcomersMatchAfterFirstWaiterLeaves+readySurvivorCanRequeue' -Dsurefire.reportNameSuffix=lifecycle-red test
```

Exit **1**: three failures, no errors/skips. A newcomer joined the abandoned room. Both parameterized READY-survivor seats failed to requeue. The revised code then passed all **21** focused tests in `DuelLifecycleTest`, `DuelSessionTest` and `DuelTransportTest`.

Final application-source check, exit **0**:

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH timeout --kill-after=5s 540s mvn -B -Dmaven.repo.local=.tmp/m2 -DargLine="-Djava.io.tmpdir=$PWD/.tmp/duels/lifecycle-fix/tmp" -Dtest=DuelLifecycleTest,DuelSessionTest,DuelTransportTest,CombatSimulationTest,CombatBoundaryTest,BasaltCourtyardTest,ProtocolCompatibilityTest,SandboxIdentityTest,ControlsTest,PlayerTest,PlayerCameraTest,CityTest,MultiplayerTest -Dsurefire.reportNameSuffix=lifecycle-final verify
```

Actual Maven output:

```text
[INFO] Tests run: 105, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  03:19 min
```

All **105 tests in 13 suites** passed, with no failures/errors/skips. Client, existing server and launcher packaging passed. Coverage includes five real-TLS lifecycle cases: abandoned waiter followed by two usable newcomers; READY requeue from either seat; ten-second expiry followed by a usable new pairing; and reconnect within grace preserving the room and requiring returning-player consent. A deterministic session regression covers both READY players leaving without a fake combat win. Existing combat, arena, protocol, save, controls, Player, City and multiplayer checks passed.

The keyboard fixture was then hardened to wait for mapped UI and both clients' READY snapshots. Its updated source was compiled and executed with Java 17. Final `mvn -B -q -Dmaven.repo.local=.tmp/m2 -DskipTests test-compile` also passed. This compile-only check is not counted as another test run.

## Playtest: production R/N/R recovery

Executed `DuelLifecyclePlaytest --display "$DISPLAY" .tmp/duels/lifecycle-fix/ui-4` using Java 17, the compiled fixture class plus `target/classes` and the Maven dependency classpath. The ignored `run-ui-4.py` wrapper starts the fixture, drives the production window, captures only that window, stops F10 and checks the process result. Home/temp/native paths are isolated under the assigned worktree. DISPLAY/XAUTHORITY are inherited; no other display or account is used.

The fixture first disconnects a real queued client while a spectator retains the abandoned room. Two new real TLS clients must share a fresh pairing, with the newcomer in side A. It then runs the production `PracticeApp` and closes the companion after the player readies. Actual 150 ms held keyboard presses execute **F10, R, N, R, Shift+W, F10, Esc**. A replacement companion readies through the same transport.

Expected and observed: the initial abandoned room is skipped; READY shows A ready and B reconnecting; N leaves READY **before** reconnect grace expires; the replacement joins the live waiter and readies; R starts a fresh duel with both players at **20 health** and reset inventory/pose; movement works after recovery. The fixture exits **0** with its assertions passed. The three fresh PNGs show departed READY, replacement READY and recovered LIVE. Java 25 AWT Robot only crops the Java 17 window; it does not run the game.

An initial static clip and two UI-startup attempts were rejected and retained only in ignored scratch. The successful retake waits for a visible window and includes movement. No media check was weakened.

## Playtest: revised two-client duel regression

Executed, exit **0**:

```sh
timeout --kill-after=5s 720s /usr/lib/jvm/java-17-openjdk-amd64/bin/java -Duser.home="$PWD/.tmp/duels/lifecycle-fix/native/profile" -Djava.io.tmpdir="$PWD/.tmp/duels/lifecycle-fix/tmp" -Dorg.lwjgl.system.SharedLibraryExtractPath="$PWD/.tmp/duels/natives" -cp "target/test-classes:target/classes:$(cat .tmp/duels/classpath)" dev.jayms.combat.duel.PracticePlaytest --display "$DISPLAY" .tmp/duels/lifecycle-fix/native/media .tmp/duels/lifecycle-fix/native
```

Two real pinned-TLS clients, two native engine views and the actual Basalt Courtyard per case; llvmpipe (LLVM 15.0.6, 256 bits). No privileged pose or hit injection. The initial report describes the input scenarios and collision expectations. The full workflow passed again after the lifecycle fixes: spacing, sprint resets/counters, cover rejection, feet/forward/opponent/missed splashes, finite refill, profiles/buffs/debuffs, pearl escape/cooldown/wall containment, forfeit/death/stale actions, reconnect, rematch and fresh-state isolation.

| Added RTT | Profile | Matching authoritative ticks | Rendered view pairs | Outcome |
| --- | --- | ---: | ---: | --- |
| Normal / 0 ms | No Debuff | 1,696 | 3,874 | Complete |
| 80 ms | No Debuff | 1,654 | 4,027 | Complete |
| 150 ms | Debuff | 1,663 | 4,087 | Complete |

Each comparison checks both clients' entire state at the same server tick, including hit/damage/impulse, inventory, effects and results. Selected matching snapshots follow. Health/hits are actors A/B. Velocity is B in blocks/tick. Input frame tags and sequences are recorded separately from server/combat ticks.

| RTT ms | Action | View frame | Input frame A/B | Input sequence A/B | Server/combat tick | Health A/B | Hits A/B | B velocity |
| ---: | --- | ---: | --- | --- | --- | --- | --- | --- |
| 0 | A hit | 512 | 512/512 | 189/140 | 278/190 | 20.000/17.600 | 1/0 | (-0.0000,0.3136,0.3185) |
| 0 | B counter | 522 | 520/520 | 195/146 | 284/196 | 16.400/17.600 | 1/1 | (-0.0000,-0.1697,0.1809) |
| 80 | A hit | 524 | 519/519 | 181/141 | 257/182 | 20.000/17.600 | 1/0 | (0.0000,0.3136,0.3185) |
| 80 | B counter | 708 | 703/703 | 263/223 | 339/264 | 17.600/9.000 | 5/1 | (-0.0000,0.1459,0.2638) |
| 150 | A hit | 590 | 584/584 | 166/144 | 241/167 | 20.000/17.600 | 1/0 | (-0.0000,0.3136,0.3185) |
| 150 | B counter | 785 | 776/776 | 248/226 | 324/250 | 17.600/9.000 | 5/1 | (-0.0000,0.1459,0.2638) |

This is developer validation. It does not clear the independent reviewer's pending 150 ms qualification or physical-GPU performance gate.

## Fresh media and handoff

Both clips are current implementation output, H264, fully decoded by the unchanged JCodec verifier, with changing frames and sizes below **6,000,000 bytes**. Extracted video frames and all four new PNGs were visually inspected. The recovery clip uses actual F10 keys; the real-duel clip uses the same F10 recorder path in the native harness.

| File | Bytes | Frames | Seconds | SHA-256 |
| --- | ---: | ---: | ---: | --- |
| [practice-lifecycle-duel.mp4](../dashboard/evidence/practice-lifecycle-duel.mp4) | 370,946 | 11 | 3.007 | `22a43ea6b7a0b9ba8b597e0b64cee3d586f5ab383aeb9843faeecb2a1d93a14f` |
| [practice-lifecycle-recovery.mp4](../dashboard/evidence/practice-lifecycle-recovery.mp4) | 1,063,437 | 88 | 10.789 | `60c1a8414e44a398e69214af2911ba2b6bedf28254e2afbee98cb7260cfd5a2a` |

New PNGs: `practice-lifecycle-ready-disconnect.png`, `practice-lifecycle-replacement-ready.png`, `practice-lifecycle-recovered-live.png`, `practice-lifecycle-150ms-result.png`. All new media is pending controller publication on the assigned feature branch. It contains only synthetic game state. No prompts, answers, credentials, private accounts or full runtime logs are included.

The earlier 802-test four-platform CI applies to **aacdd747 only**. The controller must publish this changed feature source, integrate master, return the exact-head receipt and run new hosted checks. Rerun affected checks/media if integration changes source. Independent review and the independent 150 ms repeat remain required. No merge, deployment or stable rollout is claimed.

The entire proposed change was audited against the recorded base. No files or unrelated merged features are deleted. New changes are confined to queue/session recovery, regression/keyboard fixtures, relevant docs/progress and sanitized media. Scratch remains ignored; git whitespace/conflict/status checks are recorded in the controller result.
