/**
 * Opt-in deterministic HCF / PotPvP foundation, Java 17, with no game or protocol coupling.
 *
 * <h2>Integration contract (1A to arena 1B and integration 2)</h2>
 * Construct one {@link dev.jayms.combat.CombatSimulation} per isolated combat session.
 * Only explicitly registered participants take damage. Arena owns consent, teams, matching,
 * rounds, deathbans, reports and rewards; this package deliberately owns none of those policies.
 * Integration owns Player, movement inputs, inventory UI, sound/particles and network encoding.
 * Do not put those adapters in this package or change public networking to use this foundation.
 * The independent arena package can consume only these public types and immutable snapshots.
 * No native renderer, City, Sandbox or save contract is changed.
 *
 * <h2>Tick and authority</h2>
 * All calls are confined to the server simulation thread. One tick is exactly 50 ms (20 Hz).
 * step(n) runs 0..200 ticks by default; an invalid batch is rejected before any work.
 * There is no wall-clock accumulation or hidden dropped time. Integration retains elapsed-time
 * debt and chooses bounded catch-up batches. Registration order, input order, collision queries,
 * rules and kit are replay inputs. Same inputs on Java 17 produce equal snapshots and events;
 * persistence/rollback serialization and cross-language bitwise replay are not promised.
 * Trusted movement sets feet, grounded state and velocity before a tick, then the simulation
 * applies gravity, collision-resolved velocity and drag. Never trust client position, grounded,
 * damage, hit target or collision assertions. CombatWorld.move must sweep the whole body.
 * movementMultiplier exposes Speed/Slowness for the movement adapter; this service has no
 * locomotion input and does not multiply knockback by potion movement speed.
 *
 * <h2>Intent and replay semantics</h2>
 * An intent names an action, not a victim. Positive strictly increasing per-participant sequence
 * numbers are high-water marks: duplicates and reordered older packets are rejected. All new
 * sequences, including dead/rate-rejected attempts, advance the mark. A rejected packet must
 * never be retried with its old number. No arbitrary client timestamps or future tick queue.
 * Default cap: 8 queued actions per actor per tick, 64 actors, 512 projectiles, 200 projectile
 * ticks. Ordered actions run at the next tick, actor registration order first; dead actors lose
 * queued actions. No sword swing cooldown: every bounded attempt emits SWING, even a miss.
 * An 8192-event ring bounds a stalled consumer; droppedEvents counts discarded oldest events.
 * Drain events each tick and resync presentation from snapshots after any loss. Event IDs are
 * monotonic per simulation and consumers deduplicate by simulation instance plus event ID.
 * Do not infer authoritative damage from presentation events.
 *
 * <h2>Hit rules and initial tuning</h2>
 * Profile names are Voxel One tuning labels, not claims of canonical server emulation.
 * Practice and HCF team initial presets share: 20 health, sword raw 6, reach 3 blocks measured
 * from the 1.62-block eye to a 0.6 x 1.8 axis-aligned body; exact reach included. A nearer solid
 * (including equal-distance ties) blocks a hit. Collinear bodies use nearest hit, registration
 * order for ties. Grounded sprint hits add 0.15 horizontal velocity to base 0.35; vertical
 * launch is max(existingY, 0.4). Horizontal old velocity contributes 50%. Ground drag 0.6,
 * air drag 0.91, gravity 0.08 and vertical drag 0.98 per tick. Falling airborne, non-sprinting
 * sword hits multiply raw damage by 1.5. Air sprint has no bonus. Successful sprint hits consume
 * the arm; holding/repeating sprint cannot rearm. An explicit stop then start is required.
 * A missed or immune attempt does not consume the arm. Guard requires a sword in the selected
 * hand, halves raw incoming windowed damage and stops sprint. Swing, use, selection and cancel
 * enforce one active hand. This initial guard covers all incoming directions.
 *
 * A victim's first windowed melee/arrow hit opens 10 ticks of immunity. Equal/weaker raw hits
 * do nothing. A stronger raw hit applies only the positive raw difference, without extending
 * the window. That increment uses the victim's current guard and armor. Exact expiry tick
 * opens a new full hit. Only positive hits cause knockback/wear/cancel. Intact armor fractions
 * add up to at most 80%; each slot loses ceil(full raw hit / 4), minimum 1, on an accepted hit.
 * Breakage affects subsequent hits, not the breaking hit. Initial kit slot protection is
 * 0.08/0.24/0.20/0.08 with durability 100/200/180/100. Environmental/fire, pearl cost and poison
 * bypass armor and the melee/arrow window. Trusted worldDamage accepts only fire/environment.
 * Bow draw has no cooldown: release after at least 2 ticks, full draw 20 ticks, speed up to
 * 2.5 blocks/tick, damage up to 10 times draw fraction squared. Arrows consume the first
 * ascending arrow slot. Each projectile sweeps its whole step with radius 0.1, checks current
 * bodies after movement, and resolves only the earliest collision. Projectile gravity is 0.03.
 * The owner is excluded from direct collision, but included in splash. Misses fly until a
 * solid/body or 200-tick expiry. Projectiles cannot pass a wall just because endpoints are clear.
 *
 * <h2>Consumables, effects, hunger and pearls</h2>
 * Stable finite 1..36 inventory slots, stack cap 64, single sword/bow, explicit empty slots.
 * No automatic refill, sorting, item pickup or second hand. A thrown item consumes exactly one
 * at launch, including misses, with no refund. Resource-cap rejection consumes nothing.
 * Heal splash speed 0.65, radius 4, heal 8*(1-distance/radius) using body-center distance at
 * impact. It heals all living visible bodies, including self and opponents, never revives,
 * and clamps to maximum health. Solid line of sight blocks splash. Projectiles use swept
 * collisions, not launch-position targeting. Drinks and food require 32 uninterrupted ticks;
 * release/cancel, hand switch, sword swing, guard or positive damage cancels without consuming.
 * Repeated StartUse does not restart a timer. Speed (+20% movement) and Fire Resistance last
 * 600 ticks; fire resistance blocks FIRE damage only. The HCF factory is a team/debuff-enabled
 * initial profile; practice() rejects harmful kits, debuff()/hcf() enable poison/slowness.
 * Harmful splash duration is 600 times distance weight, rounded, minimum one tick. Slowness
 * multiplies movement by 0.85; Speed and Slowness multiply together. Same typed effect extends
 * expiry without stacking strength or delaying poison pulses; latest source owns attribution.
 * Poison pulses each 20 ticks for 1 health, clamps at 1 and never heals an already-lower body.
 * Effects expire before input/pulses at tick >= expiresAt; no last pulse on the expiry tick.
 *
 * Hunger starts at 20, saturation 5. Four exhaustion consumes one saturation, then hunger.
 * Integration reports movement exhaustion; successful sword hits add 0.3. Food adds 6 hunger
 * and 7.2 saturation, capped at hunger/20, only while hungry. Hunger &lt;=6 stops sprint. No
 * passive starvation damage in these initial practice/HCF profiles. Regeneration checks every
 * 80 ticks per life, grants at most 1 health when hunger >=18, adds 6 exhaustion. No instant
 * saturation healing. Meals cannot heal directly or bypass that gate.
 *
 * Pearl speed 1.2, cost 2 unmitigated health at launch, 300-tick cooldown starting at launch;
 * health must exceed cost. A failed unsafe landing still costs the item, health and cooldown.
 * At impact search at most 25 candidates: floor(impact.y), then +1,-1,+2,-2; each checks center,
 * +X,-X,+Z,-Z. CombatWorld.safeLanding must check the entire 0.6 x 1.8 body, support, bounds
 * and hazards. It must reject lethal/unsupported terrain; the service never blindly teleports
 * into a wall or beyond bounds. Success clears velocity and ongoing use. A dead thrower cannot
 * teleport. No collision by expiry means no teleport. World geometry may supply more exact
 * landing candidates in a future version without changing networking.
 *
 * <h2>Death and presentation</h2>
 * Latest positive non-self damage source receives kill credit for 200 ticks, including later
 * environmental/pearl deaths; after that credit is absent. Poison carries its thrower's source.
 * Death cancels use, sprint, guard, velocity, queued intents and effects. Inventory remains
 * inspectable; arena decides losses. Existing projectiles may still hit after their owner dies.
 * Explicit recover(deadId, pose, kit) starts a new life at full health, clears transient state,
 * resets cooldowns, and removes that owner's previous-life projectiles. High-water sequence
 * survives recovery. Dead participants cannot heal, consume or act; no implicit resurrection.
 * Domain events carry tick, source/target IDs, position, amount and safe enum names for audio,
 * hit flash, swing, potion impact, consumption, armor break, teleport, death and recovery.
 * IDs and events remain inside the match; do not publish private runtime identity or chat.
 *
 * <h2>Scope and qualification</h2>
 * These are bounded initial Voxel One parameters, exposed in immutable rules and kit records.
 * Intake references describe configurable HCF kits and gear-safe competitive practice; no
 * canonical 1.7/1.8 timing/knockback is asserted. No third-party art, maps, text or code copied.
 * Foundation is measured by deterministic Java tests and a live synthetic CLI playtest.
 * User-controlled practice feel, audiovisual adapters and native/GPU qualification belong to
 * integration item 2; passing these foundation checks does not qualify a stable game rollout.
 */
package dev.jayms.combat;
