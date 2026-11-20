# HCF factions validation

Implementation is local to the assigned HCF feature branch. The prerequisite practice item is merged at base `30a0687254a6afd731686402823952599d798a69`. Controller publication, synchronization against the current review base, exact-head hosted checks and independent review remain pending. Physical GPU qualification remains unavailable and gates stable rollout. No missing qualification is counted as passed.

## Environment and commands

Linux VPS, OpenJDK 17.0.21, Maven 3.8.7, inherited role DISPLAY/XAUTHORITY. Actual renderer queried with `glxinfo -display "$DISPLAY" -B`: llvmpipe (LLVM 15.0.6, 256 bits), Mesa 22.3.6, OpenGL core 4.5. This is software-rendered workflow evidence, not a physical-GPU performance result.

`git check-ignore .tmp/hcf` confirmed the existing ignored scratch path before use. All synthetic profiles, TLS/account stores, downloaded headful runtime components, transient reports and full output stay there. No existing user profile is loaded. The system Java 17 runtime lacked `libawt_xawt.so`; the same Debian OpenJDK **17.0.21+9** headful JRE package was downloaded/extracted into scratch and overlaid on an isolated runtime copy. No system installation, sandbox changes or other display was used.

Maven uses the system Java 17 compiler module and a worktree-local dependency cache. The normal-entry native test uses the isolated headful Java 17 runtime. The final native source was compiled separately into `.tmp/hcf/final-classes`. The recorded run overlays those final-source classes on an isolated client-jar copy, so Maven cannot replace a class while the native process loads it.

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH mvn -B -q -Dmaven.repo.local=.tmp/m2 -DargLine="-Djava.io.tmpdir=$PWD/.tmp/hcf" -Dtest=HcfSessionTest,HcfStoreTest,HcfTransportTest,CombatSimulationTest,CombatBoundaryTest test

/usr/lib/jvm/java-17-openjdk-amd64/bin/java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -cp "target/classes:target/test-classes:$(cat .tmp/hcf/classpath)" -d .tmp/hcf/final-classes src/main/java/dev/jayms/combat/hcf/*.java src/main/java/dev/jayms/combat/CombatSimulation.java src/main/java/dev/jayms/combat/duel/PracticeView.java src/main/java/dev/jayms/GameIdentity.java src/main/java/dev/jayms/Main.java src/main/java/dev/jayms/ui/LoginDialog.java src/test/java/dev/jayms/combat/hcf/*.java

# The harness-only exit/restock adjustment was recompiled with:
/usr/lib/jvm/java-17-openjdk-amd64/bin/java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -cp ".tmp/hcf/final-classes:target/classes:target/test-classes:$(cat .tmp/hcf/classpath)" -d .tmp/hcf/final-classes src/test/java/dev/jayms/combat/hcf/HcfPlaytest.java

# After the tick-batching change:
/usr/lib/jvm/java-17-openjdk-amd64/bin/java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -cp ".tmp/hcf/final-classes:target/classes:target/test-classes:$(cat .tmp/hcf/classpath)" -d .tmp/hcf/final-classes src/main/java/dev/jayms/combat/hcf/*.java src/test/java/dev/jayms/combat/hcf/*.java .tmp/hcf/HcfCapacityProbe.java
cp target/voxel-one-1.0-SNAPSHOT-client.jar .tmp/hcf/native-base-client.jar
timeout --kill-after=5s 240s .tmp/hcf/jdk17/bin/java -Xmx2g -Duser.home="$PWD/.tmp/hcf/native7/profile" -Djava.io.tmpdir="$PWD/.tmp/hcf/native7/tmp" -Dorg.lwjgl.system.SharedLibraryExtractPath="$PWD/.tmp/hcf/natives" -cp ".tmp/hcf/final-classes:.tmp/hcf/native-base-client.jar:$(cat .tmp/hcf/classpath)" dev.jayms.combat.hcf.HcfPlaytest --display "$DISPLAY" dashboard/evidence .tmp/hcf/native7

/usr/lib/jvm/java-17-openjdk-amd64/bin/java -cp ".tmp/hcf/final-classes:.tmp/hcf/native-base-client.jar:$(cat .tmp/hcf/classpath)" dev.jayms.combat.hcf.HcfMediaCheck dashboard/evidence/hcf-team-playtest.mp4 .tmp/hcf/native7/media.json .tmp/hcf/native7/first.png .tmp/hcf/native7/last.png
```

Dependency classpath preparation used Maven `test-compile dependency:build-classpath -Dmdep.outputFile=.tmp/hcf/classpath` with the same Java 17/cache settings. No command contains a password or private capability. The native test creates random synthetic secrets in memory and fills the ordinary sign-in dialog without publishing the secret. Screenshots capture only the game window after sign-in.

## Deterministic and transport checks

The initial 43-test focused run passed. The expanded 45-test run passed with zero failures/errors/skips after the restart transaction fix; the first full regression verification passed **123 tests in 16 suites**, zero failures/errors/skips, and built the client/server/launcher packages. Those runs precede the last HUD timer/focus presentation, codec and server tick transaction changes. Final-source results are recorded below; earlier results do not qualify later changes by themselves.

The HCF tests cover friendly melee/arrows/poison/slowness, spawn shields on offense and defense, marks and team damage, mark expiry, ally-only Bard spend/replay/cooldown, Rogue victim-facing geometry and cooldown, armor signatures/warmup/limits/break cleanup, caps, combat logout, account deathban, restart defeat once, surviving roster/loot, DTR `1 -> 0`, negative DTR, raid transfer, freeze/regen closing access, knockback and pearl entry rejection, kill loot collected once, supply/restock/Miner utility, practice isolation, season opening/end/reset, old-season rejection, corruption/version/list bounds, deferred tick commit / no partial save, real pinned TLS login and wrong-password rejection, reconnect and restart, exclusive directory locking, and actual administrative CLI phase/reset startup.

The full Java 17 Maven verification completed with **154 suites, 828 tests, zero failures/errors and one opt-in browser skip**, exit 0, including all client/server/launcher packaging. Command:

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH timeout --kill-after=5s 900s mvn -B -q -Dmaven.repo.local=.tmp/m2 -DargLine="-Djava.io.tmpdir=$PWD/.tmp/hcf" -Dsurefire.reportNameSuffix=hcf-final verify
```

That full run began before the final HUD timer/mark codec, server tick transaction changes and last edge tests. The final-source regression below supersedes it for affected code. The optional asset browser workflow skip is not a pass. The actual HCF administrative CLI was separately executed through the production process three times (ACTIVE, ENDED, reset to OPENING), with persisted phases/season and released process locks verified; that integration is also included in the final Maven test set.

## Final-source verification

**Passed, exit 0: 16 suites, 128 tests, zero failures/errors/skips**, including 20 HCF tests and 108 existing regressions. This run includes the final HUD/codec, swept-region policy, restart transaction, administrative CLI and tick-batching changes. It rebuilt the client/server/launcher artifacts. ZIP inspection confirmed the client contains HCF App/Server/Session/Store, the shared policy and both practice shaders.

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH timeout --kill-after=5s 600s mvn -B -q -Dmaven.repo.local=.tmp/m2 -DargLine="-Djava.io.tmpdir=$PWD/.tmp/hcf" -Dsurefire.reportNameSuffix=hcf-final-batched -Dtest=HcfSessionTest,HcfStoreTest,HcfTransportTest,CombatSimulationTest,CombatBoundaryTest,DuelSessionTest,DuelLifecycleTest,DuelTransportTest,BasaltCourtyardTest,ProtocolCompatibilityTest,SandboxIdentityTest,ControlsTest,PlayerTest,PlayerCameraTest,CityTest,MultiplayerTest verify
```

The final native run below was repeated after batching changed the server. No production source changed after that qualifying run.

## Playtest: normal Main, five real TLS clients, two teams

**Passed, exit 0.** The executable command above invokes the normal `Main.main --game hcf --server ...` entry, fills its separate HCF sign-in dialog, and runs the production native client. It uses five authenticated synthetic accounts: Blue Bard/Fighter and Gold Archer/Fighter/Rogue. All actor movement and attacks use the real public TLS client input path or production keyboard. No fixture pose, health, damage, DTR, tag or score is injected into the native scenario. Opposing clients enter together so the contested objective is tested before an uncontested capture can finish.

| Steps / expected behavior | Observed result |
| --- | --- |
| E armor, G claim, B kit, wait warmup, R deploy; real W movement into the original courtyard | Bard activates after warmup; players explicitly deploy; finite role kit and team stash are visible |
| Both teams enter KOTH | Contested flag true; capture resets to zero |
| Actual F10, then J near Blue Fighter and Gold Archer | Bard spends 30 energy once; ally aura is present; enemy aura remains absent |
| Gold Archer draws/releases a real bow; Gold Fighter follows with a sword hit | Blue Fighter receives a Gold mark; Gold focus melee deals **3.0** health damage |
| Archer hits the production player, then actual H | Player remains deployed; HUD shows combat tag and protected-entry/travel rejection |
| Tagged Gold Fighter disconnects and signs in again with the same account | Persisted defeat, deathban and DTR `3 -> 2`; account cannot deploy |
| Archer combat logout; Rogue deploys, reaches victim's rear and hits, then combat logout | Rogue rear hit spends its cooldown and amplifies damage; Gold DTR reaches **exactly 0** and its claim becomes raidable |
| Gold leaves KOTH; Blue completes the full new capture | Blue earns one score and the objective starts its 30s lockout |
| Wait for the production player's tag to expire; W/A into the Gold site; T | Actual zero-DTR enemy entry succeeds; one supply credit transfers to Blue |
| H, B, L, C | Untagged home succeeds; resupply spends one credit (scheduled restock accounted for); safe logout/reconnect causes no deathban |
| Esc | Production window closes and the harness exits cleanly |

This is also a regression check of the shared courtyard, movement, real combat/projectiles and F10 recorder. The separate Maven practice tests cover unchanged arena-only ready/warmup/duels and save/protocol isolation. Restart and administrative phase/reset checks run through deterministic state tests and actual server/CLI integration; a browser playtest does not apply to that headless server surface.

Earlier attempts are not counted as clean passes: one stopped at missing Java 17 headful support; the first arrival script let KOTH score before contest; another arrow was intercepted by a friendly body; one completed every gameplay assertion but its scripted key release raced the window closed by Esc. A further operational attempt raced Maven replacing its class directory. The final run isolates the base jar to remove that race. The final native command above completed with exit 0 and is the qualifying run. Preliminary output stays in ignored scratch.

## Bounded capacity and persistence budget

The initial budget is 50 ms per authoritative tick at 20 Hz. A Java 17 headless probe used the actual resident Basalt Courtyard, sixteen synthetic actors and 128 upward finite-kit splashes. Fixture placement was trusted setup for this CPU probe, not part of the native client evidence. Forty measured ticks had median **1.132 ms**, p95 **2.430 ms**, maximum **2.758 ms**; every projectile was gone by tick 200. These timings exclude rendering/networking and admission/password derivation.

The probe also submitted the permitted full burst of **64 supply operations** (four per participant), then stepped and committed. The initial implementation made 64 separate atomic saves and took **145.496 ms**, exceeding the 50 ms budget. The final server batches the entire tick and commits before sending authoritative state; the same burst accepted all 64 operations in **7.019 ms** including the durable commit. A deterministic regression verifies that a tick's supply/deployment changes stay out of the save until the single commit and then become durable together. Registration password derivation was also moved outside the simulation lock. No durability check or file fsync was removed.

Executed the disposable `.tmp/hcf/HcfCapacityProbe.java`, compiled with the Java 17 compiler module into `.tmp/hcf/final-classes`, then:

```sh
/usr/lib/jvm/java-17-openjdk-amd64/bin/java -cp ".tmp/hcf/final-classes:target/classes:$(cat .tmp/hcf/classpath)" dev.jayms.combat.hcf.HcfCapacityProbe .tmp/hcf/capacity-batched
```

This local CPU/file-system result stayed within the initial budget. It does not qualify physical GPU performance, Internet latency or stable rollout.

## Media

The final actual-F10 clip is **1,355,946 bytes**, H264, **848×540**, **98 decoded frames**, **11.219 seconds**. Every frame decoded and first/last frames differ. All six PNGs and extracted first/last video frames were visually inspected. They show the actual implementation and only synthetic game state. No prompts, answers, credentials, private accounts or full runtime output are evidence.

| Artifact | Purpose |
| --- | --- |
| `hcf-team-playtest.mp4` | Actual F10: contested KOTH, Bard aura, combat and tagged home rejection / logout result |
| `hcf-team-aura-koth.png` | Contested reset, active Bard and ally-only energy spend |
| `hcf-tagged-home-rejected.png` | Live combat tag and H rejection |
| `hcf-logout-deathban.png` | Reconnected defeated fighter; Gold DTR 2 |
| `hcf-rogue-backstab.png` | Rogue rear attack and reduced target health |
| `hcf-koth-takeover.png` | Blue score 1, objective cooldown, Gold DTR 0 |
| `hcf-raid-zero-dtr.png` | Gold RAIDABLE at zero DTR; actual raid transfer notice |

Clip SHA-256: `0ef30ab1fa87190ef9941dc16a1b7152067e876ad0fb47c5e13805eaba862ff3`. Aura PNG: `fd187d548b16e8405140136027cebcfe91b4a9b6378d483dcda277df50b60e1a`. Raid PNG: `bda6c4295ea3a6f5feeb1e7f44bfa10b89afd3403e6046cd3f64a80ce7dad90c`.

All new media and this report are **pending controller publication** on `feature/queue-64303464323562632d646332662d343761382d393633662d653438333938373436663433`. The controller must publish them before review admission; no upload is claimed here.

## Diff audit and remaining gates

Audited tracked changes against `30a0687254a6afd731686402823952599d798a69` and separately inspected the added HCF source/tests/docs/media, including the final transaction batching change. No files are deleted. Existing removals only replace enum/menu tails and constructor/landing lines with additive HCF routing and a default-preserving policy seam. Original City, Sandbox, practice protocols and storage implementations are preserved. `git diff --check` passed. There is no unmerged index or pending Git recovery. Final tracked/untracked status contains only intended HCF edits/additions; scratch remains ignored.

The synchronized review base has not been provided. The controller must integrate master, supply that base, and request affected checks/media again if source changes. Hosted feature-push checks and independent exact-head review remain required. Software rendering does not qualify physical GPU performance or stable rollout.
