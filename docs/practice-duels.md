# Practice duels

## Implementation plan

1. Integrate the merged combat foundation and Basalt Courtyard in an opt-in practice world. Keep existing City, Sandbox, handshakes and saves unchanged.
2. Add a bounded versioned transport. The server accepts input, never client positions, targets, damage, hits or effects. Isolate each two-player match, spectators and reconnect leases.
3. Add No Debuff and Debuff kits, ready/warmup, results, rematch, inventory editing and an offline drill. Use the engine renderer, movement keys, mouse attack/use, number keys and wheel.
4. Test the real fixture and transport, then two rendered clients at normal, approximately 80 ms and 150 ms RTT. Record tick/input/frame observations and F10-path media under 6 MB.
5. Run Java 17 Maven checks. Controller synchronizes master and publishes source/media; rerun affected checks after integration. Hosted CI and independent review remain required. Physical GPU qualification remains pending and gates stable rollout.

Steps 1–4 are implemented and locally exercised. The [initial report](practice-duels-validation.md) records the original checks. The [lifecycle follow-up](practice-duels-lifecycle-validation.md) records the reviewed queue/READY fixes, 105 passing tests and fresh native latency and keyboard media. The [input/kit follow-up](practice-duels-input-kit-validation.md) records deferred sprint activation, a visible next-round kit with frozen results, 108 passing tests, and fresh normal/80/150 ms and production-keyboard playtests. Controller publication, new hosted CI and independent re-review remain pending.

## Bounded defaults

Practice uses a separate ephemeral world identity and port, never a City or Sandbox save. Initial movement and combat values follow the foundation and are tuned on the server. Match warmup is 60 ticks; duration is 6000 ticks; reconnect grace is 200 ticks at 20 Hz. No ranked service or third-party game content is used. Each round creates fresh combat state. Kit editing only moves existing supplies.

Publication is reserved to the controller. Local milestones and sanitized media are pending publication until an exact-head receipt is returned. No pending check counts as passed.

## Launch

Use Java 17 and `mvn verify`. Select **Voxel Practice Duels** in the normal game picker, then **Open practice**. The next screen chooses No Debuff or Debuff, an offline target, or a pinned online server. No City/Sandbox account is used by practice. The offline target is stationary; it can receive hits, effects and splashes and takes part in the same ready/results/rematch flow.

```sh
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --game practice --offline
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --game practice --offline --profile debuff
java -cp target/voxel-one-1.0-SNAPSHOT-client.jar dev.jayms.combat.duel.DuelServer --bind 0.0.0.0 --port 25567 --tls-directory runtime/practice/tls
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --game practice --server HOST --port 25567 --fingerprint SHA256 --profile no-debuff
```

The dedicated service uses the bundled client artifact as its classpath; it needs no display. The existing classified City/Sandbox server artifact and handshake remain unchanged. Compare the printed server certificate fingerprint with the server owner's trusted value before connecting. Practice never reads existing account stores. Reconnect capabilities stay in process memory. An optional `--spectate MATCH` connection watches the detached gallery and cannot affect combat. Match IDs are available through `DuelClient.match()` for an arranged session; no ranked backend is required.

Each match owns a fresh resident Basalt fixture, with stable world identity `practice-basalt-v1`. It never loads or writes a terrain save. Local practice TLS identity and recordings live under the separate `practice/` subdirectory of the normal preferences directory. Round state and kit ordering last for the current match; quitting discards them. `--world` is rejected by the practice entry point so existing saves cannot be repurposed by accident.

## Controls and round flow

| Input | Action |
| --- | --- |
| WASD / mouse | Move / aim; movement is resolved by the server |
| Space / Left Shift | Jump / sprint; release and press sprint again to rearm after a sprint hit |
| Left mouse | Sword swing on each press |
| Right mouse | Use selected item; hold drinks/food until complete; release cancels. Sword guards while held |
| 1–9 / wheel | Select hotbar slot |
| E | Kit editor between rounds. Arrows choose a slot; Enter marks the first slot, then swaps it with the second |
| F | Refill an empty selected hotbar slot from the first nonempty reserve slot; no supplies are created |
| R | Ready / accept rematch |
| Q / N | Forfeit / enter a new queue from READY or after the result |
| C / Esc | Reconnect in the same process / leave |
| F10 | Start / stop the engine MP4 recorder |

Both participants ready in separate galleries. A three-second warmup precedes placement at the arena's facing starts. Editing, flight and terrain changes have no practice transport commands. A kill, timeout, forfeit or expired reconnect grace ends the match. Results show both players' hits, longest uninterrupted combo, splash pots used, splash pots remaining and health. Rematch requires both participants and creates fresh inventory, armor, effects, cooldown, projectile and pose state. A new queue leaves the old room and seeks a fresh opponent. A disconnected body remains in the round during the ten-second grace; an expired grace awards the connected opponent a win. Two expired disconnects draw.

Before a round starts, N can leave READY immediately and seek another opponent. A READY disconnect keeps the existing ten-second reconnect grace; expiry cancels the pairing without awarding a combat win. A returning player must ready again. New queue entrants join only a room with a live waiting peer. An abandoned room can retain its reconnect lease, but cannot absorb unrelated newcomers.

The table shows default movement, attack/use, inventory, hotbar and recording keys. Practice reads the existing `controls.properties` bindings and mouse sensitivity for those actions. It adds no flight or block-edit action. The HUD's compact hints show defaults.

## Tuning and authority

`DuelSession.kit` defines finite slot-ordered kits: sword, 60% additive starting armor, four pearls, two speed drinks, one fire resistance drink, eight food and 24 healing splashes in No Debuff. Debuff replaces two healing splashes with one poison and one slowness splash. Reserve healing splashes are separate single-item slots. `CombatRules` defines the shared damage, armor cap, item timing, impulses, hunger and effect values. Both participants use the same profile. `DuelSession` defines warmup, round/grace duration and normalized walking/sprint speeds. These are initial bounded practice values; no universal competitive formula is claimed.

`DuelWire` has separate magic, version 2, explicit world identity, 64 KiB frame limit, bounded lists/strings and typed intent opcodes. No existing protocol number changes. The server ticks at 20 Hz and accepts at most four commands per actor per tick from a 32-command connection queue. Sequence high-water marks include rejected attempts. Epochs fence old-round actions. Input acknowledgements include the last input frame and sequence. Server snapshots correct all pose, velocity, inventory, gear, health, effects and projectile state. Version 2 also carries a separate immutable 36-slot next-round kit. The RESULT editor shows that kit immediately; the finished inventory and all result stats stay frozen. Practice clients and servers must use the same version; City and Sandbox handshakes and saves remain unchanged. Held movement expires after five ticks without fresh input. Client values never choose a hit target, collision result, grounded state, item effect, cooldown or damage amount.

Rooms are capped at eight, total connections at 32 and spectators at eight per room. Each room has exactly two combat actors. TLS isolates reconnect capabilities in transit. Spectator commands are discarded. Arena ray/sphere sweeps use current engine voxel boxes, including partial blocks. Movement resolves swept whole-body collision and play bounds; pearls require supported full-body landings inside play bounds. The view uses actual Basalt engine meshes with a small diffuse shader for combat readability and bounded rendering cost; it does not modify City/Sandbox's atmosphere pipeline.

Each connection retains at most four output frames. Backpressure coalesces queued state frames into the latest correction while preserving handshake/control frames. A delayed client may miss presentation events; full snapshots and result counters remain authoritative. Initial waiting and disconnect states remain visible instead of leaving an empty scene.

## Validation plan

Holding Shift before W starts sprint when forward movement begins. A successful sprint hit still requires releasing and pressing Shift again.

Run `DuelSessionTest` for sprint activation/reset, RESULT kit editing with preserved stats, real geometry, movement bounds, stale epochs, finite inventory, profiles, splash, pearl, round reset, disconnect and timeout. Run `DuelTransportTest` for bounded framing, two real pinned-TLS clients, exact-tick agreement, spectator/room isolation, reconnect, rematch and queue. `DuelLifecycleTest` covers abandoned waiters, READY departure/requeue from both seats, grace expiry and reconnect consent over real TLS. `DuelLifecyclePlaytest` drives the production R/N/R recovery flow with real companion clients and the native keyboard. `DuelInputKitPlaytest` observes production Shift-before-W, right use, RESULT kit swaps and the edited rematch over real TLS. Preserve existing combat, protocol, identity, Player and City regressions with Maven `verify`.

`PracticePlaytest` drives two real TLS clients and two native `PracticeView` windows against one server per latency case. It adds 0, 40 or 75 ms in each direction without serial per-packet sleeps, for normal, approximately 80 ms and 150 ms simulated RTT. Inputs use the same public client API as the native controls. It compares both clients' snapshots for matching authoritative ticks and records input frame, view frame, tick, health, hits, feet and impulses. Its scenarios cover spacing, sprint release/repress, counter-combos, self/opponent/forward/missed splashes, refill, profile effects, pearl escape/cooldown/wall containment, cover rejection, reconnect, forfeit, stale action and rematch reset. F10-path recording and screenshots come from the tested native views. Software rendering is workflow evidence, not physical-GPU performance qualification.
