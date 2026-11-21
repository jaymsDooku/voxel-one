# Measuring arena combat smoothness

Measure the attacker’s feedback, the defender’s movement on both screens, and the server trajectory separately. A duel supplies hit events for sampling.

## Reference and scenarios

The reference is PaperMC/Paper-1.7 at `ab9e48b010f9658c96fdca68b711714bedb42e82`. `EntityLiving.a(Entity,float,double,double)` applies the base impulse. `EntityHuman.attack` adds sprint knockback. `EntityLiving.e(float,float)` moves the body before gravity and drag, and `ba()/bb()` control the arm swing and half-cycle restart. The trajectory checks use Voxel One’s configured 0.36 base vertical lift and cap. Stock Paper uses 0.40. [Paper 1.7 source](https://github.com/PaperMC/Paper-1.7/tree/ab9e48b010f9658c96fdca68b711714bedb42e82)

1. Hit a stationary defender in the center of Basalt Courtyard, then compare the feet against an independent implementation of the Paper travel equations at each tick. Repeat with a sprinting attacker and a defender moving on the hit tick. Both traces run through landing.
2. Present the stationary victim’s actual arena snapshots at 60 and 144 Hz, first at steady 20 Hz delivery, then with eight offsets of the same 0–34 ms jitter pattern and one dropped snapshot every nine ticks. Observe the victim as local camera and remote model. Measure onset, holds during movement, largest frame step and error relative to a fixed one-tick-delayed reference. Checking delay and frame steps alongside holds prevents trading a freeze for extra buffering or a large jump.
3. Run the native renderer and two loopback TLS clients in a No Debuff duel. Record the exact render clock, server positions and impulses, presented positions, red tint, arm phase, and scripted attack sequence. Only attribute a click when the server drained one matching attack and confirmed its result. Count missed visual cues in the denominator. Run multiple rounds.
4. Replay each recorded stream with the other presentation build on **the same snapshot and frame timestamps**. Compare matched hit ticks. Repeat live to expose interactions that replay cannot cover. Use `--trace-only` for timing measurements and a separate recorded run to inspect the motion visually.

## Reach and contested knockback

Practice measures sword reach along a three-block ray from the rendered eye to the first visible surface of the selectable hitbox. The server checks the claimed opponent against its own recorded eye and target poses. A target's center can therefore be farther than three blocks away. While the camera previews incoming knockback, a swing at the edge also needs to be in range from the confirmed eye.

The horizontal speeds below are Voxel One arena settings. They use Paper's velocity retention, travel order, ground and air drag, and the arena's 0.36 lift cap. For calibration, a settled opponent on the flat center of Basalt Courtyard takes one hit and supplies no movement input. Measure horizontal travel until the opponent stops.

| Sword hit | Launch speed (blocks/tick) | Previous travel | Tuned travel straight ahead | Tuned travel at 45° |
|---|---:|---:|---:|---:|
| Normal | 0.211457 | 1.901 blocks | 1.000 blocks | 1.000 blocks |
| Sprint hit, including a re-armed W-tap | 0.562670 | 4.799 blocks | 3.000 blocks | 2.995 blocks |

Diagonal variation comes from the per-axis velocity cutoff. A moving defender retains momentum, can steer in the air and can collide with terrain, so their actual travel can differ.

Melee now resolves in packet order before the movement step. On contact the victim spends that step moving under knockback, so a defender holding W has less opportunity to run through a hit. The first movement snapshot includes the actual displacement and the remaining velocity for the next step. Reach, hurt protection and the horizontal launch settings above still apply.

The Basalt Courtyard cadence test covers all nine pairings of 8, 9 and 10 CPS, four click-clock offsets and both packet orders. Both players aim and click from spawn, including while out of reach. The defender holds W throughout. At 9 versus 9 CPS with the clocks half a click apart, both players dealt damage before the W-tapper landed four unanswered full sprint hits on fight ticks 56, 67, 78 and 92. A damage-eligible counter missed outside three blocks between each pair. Both spawn assignments and packet orders reproduced the sequence. The same controls on `0333e83c` produced two consecutive full hits. With identical approaches and synchronized clicks the opening exchanges still traded. In a separate native two-client round on Windows, the W-tapper recorded a three-hit combo.

Each player can press **G** to toggle the 9 CPS auto-clicker in Practice or Factions. The fight HUD shows the binding and its state. It starts off and sends ordinary crosshair attacks while enabled. Inventory, item use and loss of focus pause it. The binding is available in Controls as **Toggle arena auto-click (9 CPS)**. The local sparring partner also attempts nine swings per second between healing attempts.

Reproduce with `mvn "-Dtest=ArenaAutoClickerTest,ContestedPracticeTest,CombatEngineTest,MeleeTargetingTest,PracticeNetworkTest" test`. The network test measures one full block of normal knockback on two clients at 0, 80 and 150 ms of added round-trip delay.

## Measurements and acceptance

| Measurement | How to read it |
|---|---|
| Click → confirmation → red | Report the median, p95 and number observed for each interval. Confirmation is the first client sample carrying the hit. Red is the first rendered tint greater than 0.5. Times end at the render callback before swap and display scanout. |
| Visible knockback onset | For a confirmed grounded sprint hit, measure when the rendered feet rise by 0.03 blocks. Require the displayed feet to have been within 0.025 blocks of launch height on the preceding frame. This excludes a previous arc already visible on screen. Report eligible and observed counts. |
| Paused motion | In the deterministic trace, count frame intervals moving less than 0.00001 blocks during reference motion. In the native trace, consecutive server snapshots must show over 0.03 blocks of travel. Count frame intervals moving under 0.001 blocks and report their total duration and longest streak. Skip missing-tick or epoch boundaries. The impact window covers ticks 0–4 after a hit. |
| Frame pacing and sword motion | Report frame-time median and p95 separately. Count distinct non-idle arm phases within each round, then test their order, duration, restart and expiry. Distinct phases alone cannot establish that the animation is correct. |
| Trajectory fidelity | Both 33-tick fixtures must stay within 0.00001 blocks of the independently calculated path. The repeatable test also limits clean motion onset, pose error, single-frame jumps, and the stalled fraction across all eight jitter offsets. |

## Basalt Courtyard results

These historical presentation measurements used the previous horizontal speeds. Three recorded baseline duels, three recorded updated duels, and three additional updated duels with media capture disabled ran on Windows. The comparison below replays the previous and final presentation code on the same three capture-disabled traces. Times cover the loopback client and renderer up to the pre-swap callback. The physics reference comes from the Paper server source.

| Matched input stream | Previous presentation | Updated presentation |
|---|---:|---:|
| Hit confirmation to red, median / p95 | 58.1 / 75.3 ms (25 of 28) | 6.8 / 15.7 ms (28 of 28) |
| Time paused by the local camera in the impact window | 85.5 / 1,467.5 ms | 0 / 1,467.5 ms |
| Time paused by the remote model in the impact window | 33.4 / 6,140.1 ms | 33.1 / 6,140.1 ms |
| Distinct intermediate arm phases per round | 1, 1, 1 | 49, 48, 49 |

For the 25 hits that displayed red in both versions, the median per-hit saving was **50.9 ms**. Eleven of 18 grounded launches qualified for the rise comparison. The median per-hit saving for visible rise was **0 ms**. The 1,532 measured fight frames had a median interval of 16.7 ms and p95 of 18.6 ms. Across the separate recorded duels, newly rendered red also arrived sooner, while the p95 for visible rise varied from 194 to 305 ms with media capture active. Both results matter when judging how a hit feels.

| Jittered stationary-hit fixture | Previous presentation | Updated presentation |
|---|---:|---:|
| Remote paused intervals at 60 Hz | 24 / 328 | 12 / 328 |
| Remote paused intervals at 144 Hz | 99 / 808 | 44 / 808 |
| Local camera paused intervals at 60 Hz | 45 / 328 | 39 / 328 |
| Local camera paused intervals at 144 Hz | 155 / 808 | 136 / 808 |
| Largest remote step at 144 Hz | 0.413 blocks | 0.103 blocks |

The maximum server trajectory error was 0.000001466 blocks for the stationary victim and 0.000001142 blocks for the sprint hit. The largest jittered remote hold at 144 Hz was still 83.3 ms. A missing snapshot can withhold the next position needed to draw a confirmed movement path.

For a human comparison, alternate attacker and defender on the same machine in a Paper 1.7.10 duel and a Voxel One duel with matched frame cap, network delay and kit. Include a single grounded hit, a strafing trade, and a W-tap. Review unlabeled clips in shuffled order and mark the frame of any delayed reaction, freeze or camera reversal before revealing the build. This pass covers automated trajectories and native rendering. Human ratings and a recording from a Minecraft client are still outstanding.

## Two-way sword trades

Paper's `EntityLiving.damageEntity` starts a ten-tick hurt reaction and broadcasts entity status 2 on a fresh hit. During the protected part of the damage window, a stronger hit can deal the difference without starting another reaction. Voxel One uses that distinction for the opponent’s red tint and the local injury cue. Its four-tick outgoing hit marker has its own clock, so the player can see both sides of a trade on the same frame. On a replay of the same Basalt round, the opponent was still visibly red on 44 of 44 rendered frames six to eight ticks after injury, compared with zero on the previous build. [PaperMC 1.7 source revision](https://github.com/PaperMC/Paper-1.7/tree/ab9e48b010f9658c96fdca68b711714bedb42e82)

On receiving a full hit, the first-person world briefly tilts around the crosshair. Voxel One caps the tilt at six degrees about 80 ms into the reaction. Paper supplies the hurt timing, while the angle and curve are local presentation choices. In a recorded two-client 9 CPS duel, the renderer showed incoming and outgoing cues together on 37 frames and reached a peak roll of six degrees. Both clients agreed on the knockout.

When the fighters’ horizontal centers are less than 0.01 blocks apart, Paper samples a direction for each full hit. At and beyond that threshold, the direction follows the fighters’ actual separation. The sprint bonus still follows the attacker’s aim. In a seeded stationary trade with identical starting positions and facing, the old engine pushed both fighters 0.211 blocks along the same axis. The corrected first step separated their X positions by 0.368 blocks while preserving each impact’s horizontal magnitude.

The mirrored Basalt test sends both fighters forward from spawn, swaps which fighter submits first, and samples separation and time back in reach. At 8, 9 and 10 CPS both fighters registered the opening three exchanges on the same server tick as their opponent. They returned to sword reach 10, 10 and 12 ticks after the first impact, respectively. On the opening exchange the first sender moved 0.419 blocks outward and the second moved 0.194. This asymmetry follows the order of Paper’s velocity updates: an incoming hit retains half the prior motion and a successful sprint attack multiplies the attacker’s horizontal motion by 0.6. The arena retains its calibrated three-block eye reach and one- and three-block settled horizontal travel.

## Targeted sprint clicks at 8–10 CPS

In the vanilla 1.7.10 client, selecting a remote player and clicking while sprinting runs the attack locally as well as sending the attack packet. The remote-player damage handler reports success locally, so the client retains 60% of its horizontal motion and drops sprint even when the server's hurt protection rejects the damage. The visual swing into empty air follows a different path. Paper's server-side attack dampens the attacker only after accepting a hit with a knockback bonus. [Mojang 1.7.10 client jar](https://launcher.mojang.com/v1/objects/e80d9b3bf5085002218d4be59e668bac718abbc6/client.jar) · [PaperMC 1.7 source revision](https://github.com/PaperMC/Paper-1.7/tree/ab9e48b010f9658c96fdca68b711714bedb42e82)

Voxel One validates a hostile player target against the same melee ray, reach and arena checks as a damaging swing. A valid sprint click consumes the current sprint and multiplies the attacker's carried X and Z velocity by 0.6. The damage check still controls defender knockback, armor wear and hit credit. A subsequent sprint bonus requires a forward or sprint release.

The local view can preview the horizontal slowdown for up to one 50 ms tick while it waits for confirmed movement. It eases out any remaining visual offset as confirmed positions arrive. If a new incoming impulse arrives during that preview, the new motion plays at full speed while the earlier offset is reconciled.

In Basalt Courtyard, paired runs at 8, 9 and 10 CPS covered both spawn orientations and receipt orders. When a protected targeted click followed an incoming hit on the same tick, the horizontal contribution from that hit fell from 0.211457 to 0.126874 blocks for a normal hit and from 0.562670 to 0.337602 blocks for a sprint hit. These are first-step contributions with steering removed. Vertical displacement, health and hit counts matched the controls. An incoming hit processed afterward carried its full new impulse. An air swing matched the no-click control.

## Knockback Lab

Use **Knockback Lab** on the desktop Play screen to spar offline, or pass `--game practice --offline --kit knockback-lab`. For two players, start a dedicated server with `--practice-server --kit knockback-lab` and connect with up-to-date practice clients using the server's usual host, port and fingerprint.

Both fighters start with a sword and empty reserve slots. They can trade indefinitely, with no health loss, armor wear, hunger drain or round timer. Hit confirmations, the hurt protection window, base and sprint knockback, and targeted sprint-click slowdown follow the standard practice rules. The HUD shows hits, best combo, and the last tick's horizontal and vertical movement in blocks per tick. Press Enter to ready up, and use F12 for the practice hitbox overlay.

In offline PotPvP or Knockback Lab, press **F3** to toggle the sparring partner's stats panel. It shows landed hits, best combo, horizontal and vertical movement per tick, and the held item. Timed PotPvP also shows health and healing potions used and remaining. The key is rebindable in Controls. If bound to Enter, use **Ctrl+Enter** to toggle the panel.

## Reproduce

These PowerShell commands use the current build’s desktop dependencies. Set up a graphics-capable desktop for the native run. The deterministic tests run headless.

```powershell
mvn "-Dtest=ArenaCombatTraceTest,ArenaMotionTimelineTest,ArenaSwingTimelineTest,PracticeWireTest" test
mvn "-Dtest=PracticeTradeResponseTest,ContestedPracticeTest,CombatEngineTest" test
mvn "-Dtest=PracticeClickSlowdownTest,ArenaMotionTimelineTest,SparringPartnerTest" test
java -cp "target/test-classes;target/classes" dev.jayms.combat.client.ArenaCombatTrace --samples > target/arena-trajectory.jsonl
mvn -DskipTests package
java -cp "target/test-classes;target/voxel-one-1.0-SNAPSHOT-client.jar" dev.jayms.combat.client.PracticeNativePlaytest target/arena-round --trace-only
java -cp "target/test-classes;target/voxel-one-1.0-SNAPSHOT-client.jar" dev.jayms.combat.client.PracticeNativePlaytest target/trade-round --trade
python deploy/arena_frame_metrics.py target/arena-round/native-frames.csv
```

Run the following with the presentation build being evaluated. The replay writes into the same directory as the original `acceptedClicks.csv`. The analyzer accepts multiple traces and pools events across rounds without joining the end of one fight to the start of another.

```powershell
java -cp "target/test-classes;target/classes" dev.jayms.combat.client.ArenaFrameReplay target/arena-round/native-frames.csv target/arena-round/replayed.csv
python deploy/arena_frame_metrics.py target/arena-round/replayed.csv --paired native-frames.csv
```
