# Sprint activation and RESULT kit follow-up

Source starts at `c1961fe3d6bc44acc2f4e4eb305fd3e14fa0da01`, against review base `f446ad3f0c8332aa1002885468e1a20fa271018e`. This report covers the pending follow-up edits. The controller records the published exact head. Earlier CI and media remain historical evidence for their named revisions.

## Changes

Holding Shift while stationary now defers sprint activation until forward movement begins. The latch records activation, rather than merely a held key. A successful sprint hit still stops sprint; continuing to hold Shift cannot restart it. Releasing and pressing Shift again restores activation when forward movement begins.

The server sends a separate immutable 36-slot next-round kit. The RESULT inventory overlay labels it `NEXT ROUND KIT` and shows swaps immediately. The finished body, inventory, health and all result stats remain unchanged. Rematch uses the displayed next-round layout and resets round stats. The isolated practice wire is version **2**; practice client and server versions must match. City/Sandbox protocol numbers, handshakes and save schemas are unchanged.

## Automated checks

Linux, OpenJDK 17; isolated synthetic TLS fixtures and an ignored local Maven cache. Executed:

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH timeout --kill-after=5s 180s mvn -B -Dmaven.repo.local=.tmp/m2 -DargLine="-Djava.io.tmpdir=$PWD/.tmp/duels/input-kit-fix/tmp" -Dtest=DuelSessionTest,DuelTransportTest test
```

Exit **0**, **19 tests**, **0 failures/errors/skips**. Three new session regressions cover deferred activation, release/repress after a sprint hit, and RESULT edits with frozen body/stats. The two-client real-TLS regression also checks both clients receive the edited kit before rematch and that the new round applies it. Unknown practice versions remain rejected.

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH timeout --kill-after=5s 540s mvn -B -Dmaven.repo.local=.tmp/m2 -DargLine="-Djava.io.tmpdir=$PWD/.tmp/duels/input-kit-fix/tmp" -Dtest=DuelLifecycleTest,DuelSessionTest,DuelTransportTest,CombatSimulationTest,CombatBoundaryTest,BasaltCourtyardTest,ProtocolCompatibilityTest,SandboxIdentityTest,ControlsTest,PlayerTest,PlayerCameraTest,CityTest,MultiplayerTest -Dsurefire.reportNameSuffix=input-kit-fix verify
```

Exit **0**, **108 tests**, **0 failures/errors/skips**, with desktop/server/launcher packaging successful in **03:17 min**. Actual Maven output follows. Full four-platform CI must run on the newly published source; `c1961fe3` CI does not qualify these edits.

Actual Maven summary:

```text
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.897 s -- in dev.jayms.SandboxIdentityTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.323 s -- in dev.jayms.PlayerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.081 s -- in dev.jayms.PlayerCameraTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 22.44 s -- in dev.jayms.CityTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.736 s -- in dev.jayms.ProtocolCompatibilityTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.720 s -- in dev.jayms.combat.arena.BasaltCourtyardTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.071 s -- in dev.jayms.combat.CombatBoundaryTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 28.35 s -- in dev.jayms.combat.duel.DuelTransportTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 33.58 s -- in dev.jayms.combat.duel.DuelLifecycleTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.245 s -- in dev.jayms.combat.duel.DuelSessionTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.061 s -- in dev.jayms.combat.CombatSimulationTest
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 95.97 s -- in dev.jayms.MultiplayerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.027 s -- in dev.jayms.ControlsTest
[INFO] Tests run: 108, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  03:17 min
```

A separate ignored compile fixture restores the old movement transition while keeping the new snapshot API. The new Shift-before-W regression fails on that behavior, as expected. The working source was never replaced. The focused and broader runs above pass on the fix.

## Playtest: production keyboard and F10

Executed `timeout --kill-after=5s 120s python3 .tmp/duels/input-kit-fix/run-keyboard.py`, exit **0**. The script launches:

```sh
/usr/lib/jvm/java-17-openjdk-amd64/bin/java -Duser.home="$PWD/.tmp/duels/input-kit-fix/keyboard/profile" -Djava.io.tmpdir="$PWD/.tmp/duels/input-kit-fix/tmp" -Dorg.lwjgl.system.SharedLibraryExtractPath="$PWD/.tmp/duels/natives" -cp "target/test-classes:target/classes:$(cat .tmp/duels/classpath)" dev.jayms.combat.duel.DuelInputKitPlaytest --display "$DISPLAY" .tmp/duels/input-kit-fix/keyboard
```

The inherited DISPLAY/XAUTHORITY are used without reading authentication cookies. The Java 17 production `PracticeApp` runs against its real pinned-TLS local drill server and actual Basalt Courtyard. The observer only reads public client snapshots; it injects no inputs, pose, combat state or kit state. `xdotool` sends production keys held for 150 ms, or the explicit longer movement hold. Java 25 AWT Robot only crops the Java 17 game window for PNGs. Browser playtesting does not apply to this native OpenGL client.

Steps, expected and observed:

1. R starts the drill. F10 starts the engine recorder. Hold Shift for 500 ms while still, then press W without releasing Shift. Stationary Shift leaves sprint off; W activates sprint. The player moves **21.840000000000032 blocks**, including the capture interval while the keys stay held, from server tick **138** / input frame **381** to tick **217** / input frame **645**. Releasing stops sprint.
2. Select slot 2 and press right mouse for 200 ms. Exactly one healing splash is spent. Q produces `Forfeit: B wins`. The finished hotbar shows that slot empty; result A shows **0 hits, 0 combo, 1 pot used, 23 left, 20.0 health**.
3. E opens the kit editor. Right, Enter, Right, Enter swaps the next-round heal and pearl slots. The panel immediately shows slot 2 Pearl x4 and slot 3 Heal x1. The finished hotbar stays empty in slot 2. The observer confirms the entire finished body and Score are unchanged.
4. E closes the editor. The same finished results remain visible. R starts rematch. The live hotbar now matches the edited next-round kit exactly, with Pearl x4 in slot 2 and Heal x1 in slot 3. Round pot stats reset to zero. Walk again, F10 stops recording, Escape exits. The fixture and server remain healthy.

Fresh `practice-input-kit-keyboard.mp4`: **1,453,480 bytes**, H264, **848×544**, **106 frames**, **20.363 seconds**. JCodec decoded every frame; first and last differ. The editor, frozen-result and rematch PNGs were visually inspected. Artifacts contain only the synthetic practice application. Publication is reserved to the controller.

Media verification executed, exit **0**:

```sh
/usr/lib/jvm/java-17-openjdk-amd64/bin/java -cp ".tmp/duels/media-classes:target/classes:$(cat .tmp/duels/classpath)" VerifyPracticeMedia dashboard/evidence/practice-input-kit-keyboard.mp4 .tmp/duels/input-kit-fix/keyboard-media.json .tmp/duels/input-kit-fix/keyboard-end.png
```

## Playtest: two native TLS clients

Executed command:

```sh
timeout --kill-after=5s 720s /usr/lib/jvm/java-17-openjdk-amd64/bin/java -Duser.home="$PWD/.tmp/duels/input-kit-fix/native/profile" -Djava.io.tmpdir="$PWD/.tmp/duels/input-kit-fix/tmp" -Dorg.lwjgl.system.SharedLibraryExtractPath="$PWD/.tmp/duels/natives" -cp "target/test-classes:target/classes:$(cat .tmp/duels/classpath)" dev.jayms.combat.duel.PracticePlaytest --display "$DISPLAY" .tmp/duels/input-kit-fix/native/media .tmp/duels/input-kit-fix/native
```

Exit **0**. Renderer: **llvmpipe (LLVM 15.0.6, 256 bits)**. Two real pinned-TLS clients and two native engine views share one actual Basalt Courtyard server per case. Every matched tick compares the complete immutable state, including the new next-round kit, pose, velocity, hit/damage results, inventory and effects.

| Added RTT | Profile | Matching authoritative ticks | Rendered view pairs | Result |
| --- | --- | ---: | ---: | --- |
| Normal / 0 ms | No Debuff | 1,699 | 3,814 | Complete |
| 80 ms | No Debuff | 1,645 | 4,013 | Complete |
| 150 ms | Debuff | 1,687 | 4,228 | Complete |

Expected and observed in every case: spacing reaches sword range; both actors hit and counter; sprint resets work; cover rejects the obstructed hit; feet/opponent splashes heal; forward-kiting and missed splashes spend finite supplies; an empty slot refills from reserve; speed/fire resistance complete; Debuff poison applies; pearls escape but respect cooldown and walls; reconnect preserves the match; forfeit/rematch resets health, effects and position; stale actions cannot end the new round; knockout ends the match; dead/result actions cannot consume items. Automated protocol/lifecycle tests separately preserve spectator, world and queue isolation.

Selected melee/counter snapshots from the sanitized observation table follow. Both clients compared equal at each listed server tick; health and velocity are authoritative, rather than peer-reported hit results.

| RTT ms | View frame | Input frame A/B | Input sequence A/B | Server/combat tick | Health A/B | Hits A/B | B velocity (blocks/tick) |
| ---: | ---: | --- | --- | --- | --- | --- | --- |
| 0 | 619 | 618/618 | 253/204 | 338/253 | 11.400/16.200 | 2/4 | (0.0000, 0.3136, 0.3185) |
| 80 | 708 | 704/704 | 238/207 | 313/238 | 10.400/15.200 | 2/4 | (0.0000, 0.3136, 0.3185) |
| 150 | 790 | 783/783 | 248/226 | 325/249 | 17.600/9.000 | 5/1 | (-0.0000, 0.1459, 0.2638) |

Fresh `practice-input-kit-duel.mp4`: **442,019 bytes**, H264, **800×528**, **14 frames**, **3.120 seconds**. All frames decoded; first and last differ. The sampled frame was visually inspected and shows the real opponent, combat health and actual courtyard. The new 0/80/150 ms result PNGs record the completed workflows. This real-duel clip and the longer production-keyboard F10 clip are both below 6 MB.

The same `VerifyPracticeMedia` command above ran on `practice-input-kit-duel.mp4` with `duel-media.json` / `duel-end.png` output paths, exit **0**. Private runtime output and TLS material stay in ignored scratch; only sanitized observations and media are eligible for publication.

## Scope and gates

Audited the complete proposed diff against `f446ad3f0c8332aa1002885468e1a20fa271018e`: no file deletions or unrelated feature removals were found. `git diff --check` passed. There is no unmerged index or Git recovery in progress. The only removals in existing shared source replace the game enum tail, add practice selection, and preserve the existing launcher paths. Foundation combat changes remain additive presentation and trusted inventory APIs. Review media and historical reports are preserved.

New hosted CI, exact-head independent re-review and controller publication remain pending. Physical-GPU qualification was not run and still gates stable rollout. Software-rendered playtests do not satisfy that gate. No owner access question is needed.
