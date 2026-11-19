# HCF / PotPvP combat foundation (1A)

The new `dev.jayms.combat` package is a Java 17 service for an explicitly opted-in session. It does not change Player, Main, City, Sandbox, save formats or public networking. The public contract, all initial values, tick ordering and edge-case semantics are in [`package-info.java`](../src/main/java/dev/jayms/combat/package-info.java).

## Implementation plan and handoff

1. Add immutable rules, finite kits, typed intents/effects/events and a trusted world collision seam. Done.
2. Implement server-owned participants, fixed stepping, replay/rate bounds, melee, armor, knockback, bows, consumables, hunger, pearls and death/recovery. Done.
3. Exercise deterministic integration scenarios, resource limits and a live synthetic CLI playtest. Done; repository-wide validation passed, as recorded below.
4. Controller integrates the latest master, publishes the tested feature source and evidence, runs ordinary PR CI and submits the exact head for independent review. Pending. Rerun affected checks and regenerate media if integration changes source.

Arena item 1B may depend on `CombatRules`, `KitProfile`, `CombatWorld`, `CombatSimulation`, immutable snapshots and `CombatEvent`. It owns matching, consent, teams, round results, reporting and stakes. Item 2 owns Player/input/inventory/network/rendering adapters. The service accepts trusted pose updates and movement exhaustion; adapters must not trust client hit, grounded or collision assertions. `CombatWorld.move` must provide swept body collision. `safeLanding` must enforce full-body clearance, support, bounds and hazards. No collision provider is silently replaced by an always-clear production default. `TestCombatWorld` is a synthetic test-only floor/wall fixture outside the arena package.

Tick order is explicit: for each actor in registration order, expire effects and process its queued actions; then move all living bodies, resolve projectiles in launch order, and sustain each actor (use completion, poison pulse, exhaustion, regeneration). A use completion and poison pulse on the same tick complete the use first. Collision queries must not reenter or mutate the simulation.

The package depends only on the JDK. It can be compiled separately and placed on a server classpath. The existing classified server artifact is not changed here; its adapter/packaging integration remains with item 2. No production game starts a combat session implicitly.

## Decisions and initial measurements

The presets are Voxel One choices, not a claim to reproduce a universal legacy server formula. The intake's [Shotbow class discussion](https://shotbow.net/forum/threads/hcf-classes.937/), [HCFR Arena notes](https://hcfr.gg/blog/patch-notes-10-11-26) and [Minemen match report](https://minemen.club/match/f7a41388-b901-31f6-bcb1-65e340076aea) provide context for configurable competitive practice. Their pages could not be retrieved by the research tool during implementation; no unverified source timing was adopted. No third-party game art, map, text or code was copied.

| Choice | Initial value / observed model result |
| --- | --- |
| Clock | Fixed 20 Hz; 50 ms per tick; no wall clock or hidden skipped time |
| Resource caps | 64 participants, 8 actions per participant per tick, 512 projectiles, 200 ticks per step and projectile lifetime, 8192 retained presentation events |
| Sword / hurt window | Raw 6; 3-block ray reach; 10-tick victim window; two same-tick swings produced one 6-health hit |
| Compound hit | Raw 6 followed by falling critical raw 9 inside the window produced total 9 damage, not 15 |
| Sprint hit | Initial horizontal impulse 0.50; first tick target feet moved from `(2,0,0)` to `(2.5,0.32,0)`; explicit stop/start required to rearm |
| Armor / guard | Additive intact protection capped at 80%; guard halves damage; per-slot wear and breakage affect later hits |
| Bow | Full draw 20 ticks, max speed 2.5 blocks/tick, max damage 10; swept collision rejects walls and consumes one ordered arrow slot |
| Heal splash | Heal 8, radius 4, distance falloff and solid visibility; CLI self health `12 -> 18.40`, opponent `14 -> 17.69` |
| Effects | Speed 1.2x, Slowness 0.85x, Fire Resistance blocks FIRE; base duration 600 ticks; expiry precedes pulses |
| Poison | 1 health per 20 ticks, never lethal or healing; CLI opponent reached 1 and stayed alive; forbidden in No Debuff practice |
| Food / regeneration | Use 32 ticks; +6 hunger/+7.2 saturation; regeneration 1 health per 80 ticks only at hunger >=18 with exhaustion cost |
| Pearl | Launch cost 2 health, 300-tick cooldown; at most 25 supported/bounded full-body candidates; unsafe impact keeps the player in place and spends the throw |
| Replay / recovery | Strict sequence high-water marks survive death and recovery; rejected attempts cannot later spend an item; old-life projectiles are removed on recovery |

The HCF initial factory enables team/debuff supplies. No Debuff practice rejects harmful kits. Rules and kit records expose tuning without changing the protocol. Event loss is bounded and counted; consumers must drain events each tick and resync from snapshots after a gap. Kill credit uses `tick - lastAttackAt <= attributionTicks`, including the exact boundary. A matching replay uses the same registration order, ordered intents, trusted poses and collision results. There is no persistence or rollback serializer in this handoff.

## Validation

Commands run in the assigned Linux feature worktree with OpenJDK 17.0.21, Maven 3.8.7, a worktree-local dependency cache and synthetic participants only. Transient compiler/test output stays in ignored `.tmp/combat/`. Runtime account data and full logs are not evidence.

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH mvn --batch-mode -q -Dmaven.repo.local=target/maven-cache -Dtest=CombatSimulationTest,CombatBoundaryTest test
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH timeout --kill-after=5s 900s mvn --batch-mode -q -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dsurefire.reportNameSuffix=combat1a-complete verify
/usr/lib/jvm/java-17-openjdk-amd64/bin/java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -d .tmp/combat/standalone src/main/java/dev/jayms/combat/*.java
/usr/lib/jvm/java-17-openjdk-amd64/bin/java -m jdk.javadoc/jdk.javadoc.internal.tool.Main -quiet -Xdoclint:all -d .tmp/combat/javadoc src/main/java/dev/jayms/combat/*.java
/usr/lib/jvm/java-17-openjdk-amd64/bin/java -Djava.awt.headless=true -cp target/test-classes:target/classes dev.jayms.combat.CombatFoundationPlaytest dashboard/evidence/hcf-combat-1a-playtest.png
```

- Final focused combat tests: 28 passed, 0 failures/errors/skips. The final command used `-DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dsurefire.reportNameSuffix=combat1a-focused-final` with the same two combat suites. Includes re-click, sprint rearm, damage-window boundaries, obstruction/ties, armor, guard, food/cancellation/regen, finite ordered slots, self/opponent/moving/missed splash, poison below 1, expiry, pearl collision/unsafe destinations/cooldown, bow collision/draw, replay, death/recovery, resource caps and event loss accounting.
- Direct regression Maven verify: passed, exit 0, 67 tests with 0 failures/errors/skips, including combat (28), City (11), Sandbox identity (5), controls (5), protocol compatibility (3) and multiplayer (15). Built the client/server/launcher packages. Command: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH timeout 120s mvn --batch-mode -q -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dsurefire.reportNameSuffix=combat1a-regression -Dtest=CombatSimulationTest,CombatBoundaryTest,SandboxIdentityTest,ProtocolCompatibilityTest,ControlsTest,CityTest,MultiplayerTest verify`. Multiplayer took 89.909 seconds; a long suite alone is not evidence of an execution gap.
- Standalone Java 17 compile: passed with no engine or third-party classpath. The final media was regenerated by separately compiling `TestCombatWorld.java` and `CombatFoundationPlaytest.java` against `.tmp/combat/standalone`, then running the same playtest main with that classpath.
- Java 17 API documentation HTML validation: passed. Missing individual member documentation produces warnings; the package contract is complete.
- Full Maven verify: **passed**, exit 0, 147 suites, 779 tests, 0 failures/errors, 1 skipped (`AssetManagerIntegrationTest.browserWorkflow`, requires the opt-in `asset.browser` flag). Built client/server/launcher packages. Final command above used a 900-second bound and worktree-local dependency/temporary directories. Earlier cache/temp-path failures and interrupted runs are not counted as passes. A separate 90-second bounded `AssetManagerIntegrationTest` probe also passed (2 passed, 1 opt-in browser skip). Ordinary hosted CI remains pending against the controller-published exact head; it must finish before review readiness.
- Playtest: executed the CLI above against the live service. Requested workflow: sprint melee then finite self/opponent healing. Edge case: wall blocks the hit. Regression: poison stays nonlethal; pearl chooses a supported landing; duplicate and cooldown attempts preserve remaining inventory. Expected and observed values match the table. Exit 0. Six frames are drawn from actual snapshots, not stock media.
- Capacity probe: 64 synthetic participants each queued 8 upward pots (512 live projectiles), 40 warm-up ticks then 40 measured ticks. Java 17 measured median 0.607 ms, p95 1.034 ms, max 3.434 ms per tick; all projectiles were removed by tick 200. Ran `/usr/lib/jvm/java-17-openjdk-amd64/bin/java -cp .tmp/combat/standalone dev.jayms.combat.CapacityProbe` after compiling the disposable `.tmp/combat/CapacityProbe.java` against the standalone package/test fixture. This measures synthetic collision CPU cost only; it does not qualify real terrain, networking, GPU or stable game rollout. The initial simulation budget is 50 ms per tick; measured synthetic cost stayed below that budget.
- Media: [`hcf-combat-1a-playtest.png`](../dashboard/evidence/hcf-combat-1a-playtest.png), visually inspected, 78,847 bytes, SHA-256 `c78f90b3018387397573f57fb10673058605a94c15ad6c982e0640566120e2f3`. Intended feature-branch raw URL is recorded in the controller result; publication remains pending.

Browser/native practice playtesting does not apply to this noninteractive foundation: it has no game entry, Player adapter or UI yet. The executable CLI exercises the affected integration surface. User-controlled feel, audiovisual integration and physical GPU qualification remain pending for item 2 and gate game rollout claims. No existing game, hosted check or physical qualification is claimed from this foundation playtest.

## Diff audit

The proposed change adds only the isolated combat production/test package, this contract/plan, its sanitized playtest PNG and the relevant progress entry. There are no intended deletions and no existing game or network source edits. Pre-sync tracked changes were checked against worktree base `8a53afce`; new source/media were audited separately. The controller must provide the synchronized review base for a final diff audit after integrating master. Preserve other workers' merged features and retain independent review.
