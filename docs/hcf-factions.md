# HCF factions in Basalt Courtyard

HCF is a separate, opt-in game. Select **Voxel HCF Factions** in the normal game picker, then **Open HCF**, or launch `dev.jayms.Main --game hcf`. Sign in to a separate HCF account, choose Blue or Gold on first registration, equip armor in protected spawn, and press R to deploy. Existing members retain their team on every sign-in. There are two teams, eight members per team, and sixteen members per season. Existing City, Sandbox and practice accounts, worlds, protocols and saves are unchanged. No third-party content is used.

## Plan and bounded defaults

1. Reuse merged item 2 (`30a06872`): deterministic combat, movement, original courtyard, real native renderer and F10 recorder.
2. Add a trusted combat policy for all damage, harmful effects, motion and pearls. Default policy preserves practice behavior. Keep HCF roles and faction state in a separate package and service.
3. Add a bounded kit-map supply/claim/KOTH loop and a versioned atomic season store. Fail closed on corrupt state, unsupported versions or save failure.
4. Run Java 17 deterministic, real-TLS and regression checks. Exercise the normal Main entry with real native clients and actual keyboard/F10. Publish sanitized media through the controller.
5. Controller integrates current master, publishes the assigned branch, runs hosted checks, and submits the exact tested head for independent review. Rerun affected checks/media after source integration changes. Physical GPU qualification still gates stable rollout.

Steps 1–4 are implemented and locally exercised. Final-source Maven verification passed 128 tests, the five-client native scenario passed, and the F10 clip was decoded and inspected. Validation results and remaining gates are recorded in `hcf-factions-validation.md`. This document describes initial tuning, not an established competitive formula. Economy, rankings, large territory maps and seasonal automation remain out of scope.

## Launch and controls

Use Java 17 and Maven. The bundled client jar includes the dedicated headless HCF service.

```sh
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --game hcf --offline
java -cp target/voxel-one-1.0-SNAPSHOT-client.jar dev.jayms.combat.hcf.HcfServer --bind 0.0.0.0 --port 25568 --directory runtime/hcf --phase active
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --game hcf --server HOST --port 25568 --fingerprint SHA256
```

Online and local modes show the HCF sign-in screen. Passwords are entered there, never on the command line. Compare an online server's certificate fingerprint with its operator's trusted value. The local server starts in ACTIVE phase and persists under the profile's separate `hcf/server` directory. Uncheck **Create HCF account** on later visits. The client retains the password in memory for C reconnect, wipes it on close, and never saves it.

| Control | Action |
| --- | --- |
| WASD, Space, Shift | Move, jump, sprint; existing movement bindings/sensitivity apply |
| Left / right mouse | Sword attack / guard, use, bow draw/release |
| 1–9 / wheel | Hotbar selection; existing slot bindings apply |
| E | Cycle armor in spawn; failed class choices can be skipped with another press |
| R | Explicitly deploy into combat |
| B | Spend one team stash credit for the selected role's full kit and fresh armor in spawn |
| J | Bard aura or Miner supply work; other roles are passive |
| H | Return to own spawn when untagged |
| L | Safe logout in own protected spawn while untagged |
| G | Claim the team's one fixed site from spawn |
| T | Raid one supply credit from a raidable enemy claim while inside it and untagged |
| U | Collect nearby kill loot into empty inventory slots |
| F | Refill the selected empty hotbar slot from finite inventory reserves |
| C | Sign in again after disconnect; deathban survives reconnect |
| F10 | Toggle engine MP4 recording; existing recording binding applies |
| Esc | Leave; leaving combat while tagged is defeat |

The HUD shows the season, roster, armor/active class, warmup, health, food, pearl cooldown, combat tag, spawn protection, ability cooldown, energy, mark and aura expiry, deathban, DTR/freeze/regen, stash, objective score and KOTH state. Role selection changes armor only. B supplies that role's inventory. There is no terrain editing or flight command.

## Classes, supplies and combat

Every slot of armor must be intact and match the role's original protection signature. Changing armor, breaking armor, death, home or disconnect clears class activity and source-owned auras/marks. The warmup is 100 ticks (5 seconds). Source/target armor is checked again at damage time, so a same-tick armor break cannot retain a class bonus. Broken armor requires spawn resupply. Armor-only changes preserve health, finite inventory and cooldowns.

| Role | Total armor protection | Team limit | Ability |
| --- | --- | --- | --- |
| Fighter | 60% | 8 | Frontline armor; ordinary shared combat |
| Bard | 32% | 1 | J spends 30 of 100 energy once; allies within 8 blocks receive 15% movement and damage for 100 ticks (5s). The effect requires the living, connected Bard to remain in range. Energy recovers 1 per second; J cooldown 100 ticks. Enemies never receive it. |
| Archer | 28% | 1 | Successful arrow damage marks the enemy for the Archer's team for 120 ticks (6s). That team's damage gains 25%; marks refresh duration, never stack. |
| Rogue | 36% | 1 | Sword damage doubles in the victim's rear cone: horizontal dot <= -0.65, distance 0.1–3 blocks, vertical gap <= 1.5. Only a successful hit spends the 200-tick (10s) cooldown. Victim facing comes from server-accepted look input. |
| Miner | 44% | 2 | 10% faster movement in own assigned claim site. J recovers one team supply credit there while untagged, with 1200-tick (60s) cooldown. No economy or terrain mutation. |

Each resupply supplies one sword, seventeen single healing splashes, four pearls, two speed drinks, one fire-resistance drink, eight ordinary food, one bow, one poison splash, one slowness splash, and eight arrows (32 for Archer). Inventory is 36 slots. There are **no gaps or golden food** in this initial profile. Stash starts at sixteen credits, replenishes one every 200 active ticks (10s), and caps at 32. A kit costs one credit; KOTH awards four credits. This is an economy-free kit-map supply entry, not currency or a crafting economy. Fresh registration and post-death recovery receive the base role kit. Resupply does not heal, clear a tag or reset a pearl cooldown.

Combat runs at 20 Hz with sixteen actors, 128 projectiles, eight combat intents per actor per tick, and four wire commands per actor per tick. Transport queues cap at 32 inputs. All hit tests, armor, damage, consumption, projectile collision and pearl landings use the shared server simulation. The policy blocks friendly melee, arrows, poison application/pulses and slowness, including self debuffs; blocked hits spend no class cooldown and apply no knockback or tag. Healing splashes retain the shared area behavior and may heal enemies. Opposing successful damage or harmful-effect application refreshes both players' 300-tick (15s) combat tag. Source-owned marks clear on role/life cleanup. No client can send a position, target, damage amount, effect, team change, DTR value or administrative action.

## Spawn, claims, DTR and logout

Blue spawn is the north strip (`z <= -25.7`); Gold spawn is the south strip (`z >= 25.7`). Player half-width is included. Spawn blocks all outgoing and incoming combat. Five seconds of spawn protection also block outgoing attacks after deployment, preventing invulnerable offense. Enemy spawn is always closed. Tagged players cannot enter protected spawn through walking, knockback or pearls, use H, or safe logout. Destination checks and exact swept claim intersection apply to motion and teleport paths.

G claims one fixed site per team: Blue `x -30..-20`, Gold `x 20..30`, both `z -12..12`. Claims span the full playable height; body margins apply. Friendly players enter their claim. Enemies enter only when the owner has DTR **at or below zero**, and the entering enemy is untagged. Tagged attackers cannot enter even a raidable enemy claim. T transfers one stash credit per accepted command, bounded by available enemy supplies and the attacker's stash cap. No block destruction or sprawling territory system is added. Once DTR recovers above zero, further enemy entry and raids stop; existing intruders can retreat toward an edge rather than becoming trapped.

DTR starts/caps at 3, loses 1 per defeat, and floors at -3. **A death taking DTR from 1 to exactly 0 intentionally makes that team's claim raidable. Negative DTR stays raidable.** Each death freezes recovery for 120,000 ms (120s). The first +0.25 recovery occurs 60,000 ms after that freeze; later increments occur every 60,000 ms up to 3. Recovery catches up at most one increment per active server update, never an unbounded offline loop. A transition from 0 to +0.25 closes raid access. Timers and transitions persist atomically with the deathban and defeat record.

Default deathban is 60,000 ms (60s), configurable with `--deathban-seconds` from 1 to 86,400 seconds. It belongs to the authenticated HCF account and persists across reconnect and restart. Death drops finite inventory at the corpse for 60,000 ms, capped at sixteen drops. U collects within two blocks into empty slots; leftovers retain the same expiry. Loot, ban, DTR and team supplies share one atomic save.

Combat logout uses **immediate persisted defeat**, rather than a simulated reconnect body. The normal last-attacker attribution remains available on the death event. Untagged disconnect returns the living player to own spawn without extra supplies. Deployment is persisted before the server moves a player out of spawn. **Every deployed player left in a save at server restart is defeated once**, even if the last combat tag had expired. This conservative crash rule intentionally prevents a restart from erasing a body; it also applies to operator restarts with deployed players. Return home or safe logout first. Restart defeats do not create invented loot or kill credit. The deployed flag clears in the same transaction as DTR loss and deathban, so another restart cannot charge the same defeat twice. Spawn members restart with the selected base kit and fresh class warmup; transient look/input, projectiles, effects, marks, auras, partial captures and the objective's 30s lockout reset. Deployed players receive the longer 60s deathban, so a restart does not give those participants a faster capture.

## KOTH, phases and season reset

KOTH is the marked central `x/z -4..4` square, feet below height 4. Only living, connected, deployed and unprotected actors in ACTIVE phase count. An uncontested team captures for 200 ticks (10s), then earns one team point and four supply credits. The objective pauses for 600 ticks (30s) after scoring. Opposing teams contesting **reset** capture to zero. Emptying the point resets it too. A different sole team starts a fresh full capture; progress never transfers to the takeover team. Scores persist, cap at 1,000,000, and do not claim a ranking service.

New dedicated servers start in OPENING: equip, claim and prepare, with no deployment, damage, scoring, restock or DTR regen. Start a stopped server with `--phase active` to open combat, or `--phase ended` to freeze gameplay. Phase is persisted. There is no seasonal scheduler or client admin opcode. Use `--reset-season` while starting a stopped server to explicitly increment the season, clear roster/deathbans/loot, reset claims/DTR/stash/scores, and return to OPENING. HCF account password hashes stay in the separate account store; practice and City/Sandbox state remain untouched. An old-season command is rejected. A filesystem lock rejects a second process or administrative start using the same HCF directory.

`season.hcf` has explicit magic/version 1, bounded lists and values, a 16 KiB limit and no Java object deserialization. The server batches all mutations in one tick and commits before sending that tick's authoritative state. Admission outside the tick commits immediately. Password derivation never holds the simulation lock. Save writes use a private sibling temporary file, file fsync, required atomic rename and directory fsync where supported. Corruption, unsupported versions and failed persistence stop admission/gameplay; they never silently reset a season. Operators must not reuse a City/Sandbox/practice directory for HCF. TLS/account files and full runtime output are private and never validation artifacts.
