# Voxel One

A Java/LWJGL voxel playground with seeded procedural biomes, a survival inventory, walking, jumping, flight, editable controls, and an authenticated multiplayer server. Requires Java 17+ and an OpenGL 3.3 desktop. Use a JDK on the server for first-run certificate generation.

## Build and download

Run `mvn verify` with a JDK and Maven. The build selects native libraries for Windows, Linux x86-64, Intel macOS, or Apple Silicon macOS. To build a Windows client from Linux, use `mvn verify -Dlwjgl.natives=natives-windows`.

GitHub Actions builds all four desktop variants. Download and extract your desktop's artifact from **Actions → Build and test**:

- `voxel-one-1.0-SNAPSHOT-client.jar`: desktop client, dependencies and shaders included.
- `voxel-one-1.0-SNAPSHOT-server.jar`: headless server without graphics dependencies.

## Connect and sign in

```sh
java -jar voxel-one-1.0-SNAPSHOT-client.jar --server SERVER_IP --port 25565
```

The launcher offers **Sign in**, **Create account**, and **Play offline**. Usernames contain 3–16 letters, numbers or underscores and are case insensitive. Passwords contain 10–128 characters. Accounts are specific to the server; one active session is allowed per account. Your username appears as a cyan holographic label above your avatar.

On first connection, compare the certificate fingerprint displayed in the launcher with the fingerprint printed by the server, then trust it. The client remembers this certificate and refuses a changed certificate. You can provide the owner's SHA-256 fingerprint explicitly:

```sh
java -jar voxel-one-1.0-SNAPSHOT-client.jar --server SERVER_IP --fingerprint SHA256_FINGERPRINT
```

The deployed VPS address is `198.100.154.156`. The `jayms` test account's password is supplied separately, not stored in this repository. Download the updated client: protocol 3 includes inventory, health, and procedural world synchronization, so clients from the initial multiplayer version cannot connect.

On macOS, add `-XstartOnFirstThread` immediately after `java`. To skip the launcher and play offline, use `--offline`. Launching without arguments opens the launcher.

## Controls menu

Press **Escape** or **F1** to open the controls menu. It shows every action and its current binding. Click a row (or select with Up/Down and Enter), then press a keyboard key or mouse button. Assigning an occupied input swaps the two bindings. Escape cancels a binding or resumes play. Scroll the mouse wheel or use the arrow keys to view additional controls. The menu also adjusts mouse sensitivity, restores defaults, and quits.

Bindings and sensitivity persist in `~/.voxel-one/controls.properties` (on Windows, `%USERPROFILE%\.voxel-one\controls.properties`). Server fingerprints and your last username live in `connections.properties`; passwords are never saved by the client. Multiplayer continues while the menu is open.

| Default input | Action |
| --- | --- |
| W/A/S/D | Walk or fly horizontally |
| Left Shift | Sprint / faster flight |
| Space | Jump, or ascend while flying |
| Left Ctrl | Descend while flying |
| F | Toggle flight |
| Mouse | Look |
| Left click | Break block |
| Right click | Place the selected hotbar block |
| 1–9 / mouse wheel | Select a hotbar slot |
| E | Open/close inventory |
| F5 | First/third-person camera |
| Tab | Release/capture mouse |
| Escape / F1 | Controls menu |

Flight keeps voxel collision enabled. Walking accelerates and decelerates smoothly; diagonal movement is normalized. Limbs swing according to distance walked, settle at rest, and stop swinging during flight. Other players render with 100 ms of interpolation, including yaw and animation, to smooth the server's 20 Hz updates.

Pending placements become solid immediately on the placing client, preventing movement into an unconfirmed block. Every edit carries the position at the moment of the click; the server checks bounds, reach, occupancy, and player overlap and sends an acceptance/rejection result. Rejected edits roll back. If a delayed edit from another player overlaps you, collision recovery moves you to the nearest free block face instead of leaving you trapped.

## Inventory, health, and generated worlds

The bottom HUD has nine hotbar slots, with health immediately above them. **E** opens a 36-slot inventory: 27 backpack slots and the same nine hotbar slots. Click a stack, then another slot to move, swap, or merge it. Stacks hold up to 64 items. Number keys 1–9 and the mouse wheel select the active hotbar slot; keyboard bindings, including inventory and every hotbar slot, can be edited in the controls menu.

New players start with an empty inventory. Break a block with left click, then walk within two blocks of its floating, rotating item to pick it up. Right click places one item from the selected slot. Full inventories leave uncollected items in the world. The server owns item counts, pickups, and inventory moves, and saves inventories per account. Two players cannot collect the same item, and rejected placements do not consume items.

Health starts at 20. Falls longer than three blocks cause damage; flight is safe. At zero health you respawn with full health and keep your inventory. This release does not add combat, crafting, hunger, or tools with durability.

Every world uses a saved seed. Climate noise selects **plains, forest, desert, and snowy mountain** biomes; layered terrain noise creates hills and mountains, with underground caves and trees in forests. Terrain and tree features agree across chunk boundaries and are generated from the same seed on the server and client. Chunks load as you explore and distant GPU meshes unload. Horizontal coordinates support -1,000,000 through 1,000,000; generated blocks span Y -32 through 95, with flight space above. The HUD displays your biome and seed.

Use `--seed NUMBER` when creating a server world, or `--offline --world FILE --seed NUMBER` for a new offline world. An existing world keeps its saved seed regardless of the command-line seed. Offline play saves inventory, drops, health, and edits to `~/.voxel-one/offline-world.dat` by default. Saves occur every minute and on normal exit; use the controls menu's Quit button to save before exiting.

Older server saves are upgraded on the next save: their block edits remain at their original coordinates, over the new default seeded terrain. Make a backup before upgrading if you want to retain the previous terrain with an older server binary. The deployed VPS keeps a pre-upgrade backup.

## Run a server

```sh
java -jar voxel-one-1.0-SNAPSHOT-server.jar --bind 0.0.0.0 --port 25565 --world world.dat --accounts accounts.db --tls-dir tls
```

Allow inbound **TCP 25565** in the server's host and provider firewalls. For clients outside a home LAN, forward the port to the server computer. For local tests, bind to `127.0.0.1` and connect there. Launch additional clients with different accounts to test multiplayer.

First launch creates a self-signed TLS certificate and prints its SHA-256 fingerprint. Keep the `tls` directory across updates so clients retain trust. Account creation is available in the launcher. To provision an account as the server owner (run while the server is stopped):

```sh
java -jar voxel-one-1.0-SNAPSHOT-server.jar --accounts accounts.db --create-account jayms
```

This generates a random password and prints it once. It refuses to overwrite an existing account. Do not run this command against the live server's account file: the running process holds its account store in memory.

Accounts persist as salted PBKDF2-HMAC-SHA256 hashes with 600,000 iterations. Login, registration, world updates, and movement use pinned TLS 1.2/1.3. Login rate limits, limited concurrent password hashing, bounded connection counts, and bounded network queues limit resource use. TLS keys and account files use owner-only permissions on Linux. Back up `world.dat`, `accounts.db`, and the `tls` directory together; do not publish them.

World edits save every minute and on Ctrl+C or service stop. Keep the same world file across restarts. World seeds, per-account inventories, health, and uncollected drops save alongside block edits. Player positions reset to a free spawn area on reconnect. The server supports up to 32 authenticated players. Movement remains client simulated; this is a cooperative playtest server without authoritative movement or anti-cheat.

## VPS service

`deploy/voxel-one.service` is a systemd user unit for this VPS checkout. Install it under `~/.config/systemd/user/`, put the server JAR at `runtime/server.jar`, then run:

```sh
systemctl --user daemon-reload
systemctl --user enable --now voxel-one
sudo loginctl enable-linger "$USER"
```

Adjust paths if installing elsewhere. The runtime directory contains the server JAR, world, accounts, and TLS identity and is excluded from Git.

Use `systemctl --user status voxel-one` to check the process, `journalctl --user -u voxel-one -f` for logs, and `systemctl --user stop voxel-one` to stop and save. To update: stop, replace the JAR, start.

## Verification

`mvn verify` runs tests for gravity, jumping, collision, late block recovery, flight, acceleration and walking animation state, world edges, normalized movement, persisted bindings, remote interpolation, authenticated TLS sessions, wrong credentials, duplicate logins, registration, certificate pin rejection, exact-pose block placement, rollback, shared edits, late joins, disconnects, salted password storage, seeded biome generation, trees and caves, chunk boundaries, 36-slot stack capacity, inventory moves, exclusive item pickups, placement supply checks, health and respawn, and save/reload.
