# PotPvP practice and the faction arena

Choose **PotPvP Practice** from the Voxel One launch window to spar offline. Basalt Courtyard is an original practice map with a flat central lane, mirrored cover, stepped flank platforms, and a spectator gallery.

## Run a match

Use the packaged client JAR for the desktop player. The matching server JAR can host a lobby without graphics.

```text
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --game practice --offline --kit no-debuff
java -jar target/voxel-one-1.0-SNAPSHOT-server.jar --practice-server --kit debuff --tls ./practice-tls
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --game practice --server 127.0.0.1 --name Aster --fingerprint PRINTED_SHA256
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --game practice --server 127.0.0.1 --name Rowan --fingerprint PRINTED_SHA256
```

The lobby listens on port 25567 on loopback by default. Use `--bind` on the server and `--port` on both ends for a different address or port. Check the certificate fingerprint against the one printed by the host. No Debuff and Debuff lobbies use temporary guest names, and each server hosts one arena.

| Default control | Action |
| --- | --- |
| Enter | Toggle ready or return from results to the lobby |
| W/A/S/D, Shift, Space | Move, sprint, and jump |
| Left click | Swing once |
| Right click | Throw the selected splash potion or pearl. Hold to drink, eat, block, or draw a bow |
| 1–9 or scroll | Select a hotbar slot |
| E | Open your kit or match inventory |
| Click two slots | Swap their stacks |
| Shift-click a stack | Move it between your hotbar and reserve. Compatible stacks fill first |
| Hover a slot and press a bound hotbar key | Swap the hovered stack with that hotbar slot |
| F10 | Start or stop an MP4 recording |
| Escape | Close the inventory or leave |

No Debuff starts players with a sword, armor, healing, speed, fire resistance, food, and pearls. Debuff adds poison and slowness. The server simulates combat at 20 ticks per second with a 3.05-block melee reach, a 10-tick damage immunity window, and a 16-second pearl cooldown. Splash healing fades with distance and can heal your opponent. Break forward movement or sprint to rearm sprint knockback. Player state and supplies reset for the next duel. The results show each fighter’s health, hits, longest combo, potions used, and potions remaining.

## Run the HCF kit map

```text
java -jar target/voxel-one-1.0-SNAPSHOT-server.jar --practice-server --factions ./faction-arena.dat --tls ./faction-tls
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --game practice --factions --server 127.0.0.1 --name Aster --fingerprint PRINTED_SHA256
```

Create a faction account in the sign-in prompt on your first visit. The server stores faction progress and salted account hashes in separate files next to the requested save. The kit map uses two factions, Ember and Slate, with up to two fighters per side. It supports eliminations and King of the Hill.

- Press **Z** or **X** to select a team in the lobby. **F1–F5** select the Fighter, Bard, Archer, Rogue, or Miner armor loadout. Armor and role effects follow the selected loadout after its warmup. **R** triggers the active class ability. Ember wears orange and Slate wears blue. A visible fighter's label shows their class.
- Each faction has a protected home and supply stash. **C** takes supplies from your home stash. When a knockout leaves the other faction at zero deaths until raidable (DTR), the survivors have 20 seconds to enter the opposing home and press **V** to raid its finite stash.
- Enemy damage and harmful splash start combat tags. A tagged player cannot retreat into a protected home. Press **L** to check safe logout. Disconnecting while tagged leaves a vulnerable body, and reconnecting with the same account can recover it.
- A defeat records a five-minute deathban and reduces team DTR. The default DTR freeze lasts 90 seconds before recovery begins. Only an uncontested team progresses the central hill, and a takeover resets the opposing progress.

When the server is stopped, add `--season end` at startup to preserve standings and disable new rounds, or `--season begin` to start a fresh opening. A fresh season clears round standings, raids, and deathbans. The separate account logins survive the reset.

## Design references

The combat benchmark measures hit feedback latency, frame-to-frame movement and Paper trajectory error in Basalt Courtyard. [Combat smoothness measurements](combat-smoothness.md)

- Shotbow’s published class loadouts and `gg` arena announcement informed the class roles and the separation between practice and faction stakes. [Shotbow class list](https://shotbow.net/forum/threads/hcf-classes.937/) · [Shotbow practice announcement](https://www.shotbow.net/forum/portal/?page=173)
- HCTeams Map 12 documented a Protection II, Sharpness II, Power IV profile. Kohi Practice 2 documented ranked No Debuff duels. [HCTeams Map 12](https://www.youtube.com/watch?v=6hmShHIljFw) · [Kohi No Debuff](https://www.youtube.com/watch?v=YWJeDdMLtcE)
- HCFRevival described restoring sprint resets, fast hit registration, and throwable timing on a newer engine. Minemen’s match report informed the arena stats. [HCFRevival developer account](https://www.reddit.com/r/mcservers/comments/141l73e) · [Minemen No Debuff results](https://minemen.club/match/f7a41388-b901-31f6-bcb1-65e340076aea)
