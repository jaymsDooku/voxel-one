# Voxel One

A Java/LWJGL voxel playground with seeded procedural biomes, a survival inventory, walking, jumping, flight, editable controls, and an authenticated multiplayer server. Requires Java 17+ and an OpenGL 3.3 desktop. Use a JDK on the server for first-run certificate generation.

## Build and download

For Windows, download [Voxel-One-windows.zip](https://github.com/jaymsDooku/voxel-one/releases/latest/download/Voxel-One-windows.zip), extract it, and double click **Play Voxel One.cmd**. Java 17+ must be installed and available on PATH. The included launcher checks for updates on every start, downloads the latest tested Windows client automatically, verifies its SHA-256 checksum, and opens the sign-in screen for the VPS at `198.100.154.156`. Keep using this launcher for future updates; you no longer need to copy a new game JAR manually. Close and reopen the game to receive a published update.

The [latest release](https://github.com/jaymsDooku/voxel-one/releases/latest) also includes Linux and Mac launcher ZIPs, platform-specific client JARs, and the headless server. The small `voxel-one-launcher.jar` works on all four supported platforms. Run `java -jar voxel-one-launcher.jar --offline` to use it for offline play, or pass `--server HOST` for another server. `--update-only` downloads/checks the client without starting it.

The launcher installs immutable versions in `%LOCALAPPDATA%\VoxelOne` on Windows and `~/.voxel-one/client` on Linux/Mac. It downloads into a temporary file and replaces the installed-version record only after integrity checks succeed. If checking/downloading an update fails, it can start a previously verified client. A corrupted installed JAR is rejected. First use requires an internet connection. Logs live in `game.log` under the installation cache. Your existing game saves, controls, remembered username, and trusted server certificate remain in the usual `.voxel-one` directory.

Every successful build of the current `master` commit publishes an immutable GitHub release and then marks it latest, after Windows, Linux, and both Mac builds pass. Release files and the update manifest become public together. Updates are checked on startup; an already running game continues its current version.

Run `mvn verify` with a JDK and Maven. The build selects native libraries for Windows, Linux x86-64, Intel macOS, or Apple Silicon macOS. To build a Windows client from Linux, use `mvn verify -Dlwjgl.natives=natives-windows`.

GitHub Actions builds all four desktop variants. Download and extract your desktop's artifact from **Actions → Build and test**:

- `voxel-one-1.0-SNAPSHOT-client.jar`: desktop client, dependencies and shaders included.
- `voxel-one-1.0-SNAPSHOT-server.jar`: headless server without graphics dependencies.
- `voxel-one-1.0-SNAPSHOT-launcher.jar`: small automatic updater without graphics dependencies.

## Connect and sign in

```sh
java -jar voxel-one-1.0-SNAPSHOT-client.jar --server SERVER_IP --port 25565
```

The launcher offers **Sign in**, **Create account**, and **Play offline**. Usernames contain 3–16 letters, numbers or underscores and are case insensitive. Passwords contain 10–128 characters. Accounts are specific to the server; one active session is allowed per account. Your username appears as a cyan holographic label above your avatar.

On first connection, compare the certificate fingerprint displayed in the launcher with the fingerprint printed by the server, then trust it. The client remembers this certificate and refuses a changed certificate. You can provide the owner's SHA-256 fingerprint explicitly:

```sh
java -jar voxel-one-1.0-SNAPSHOT-client.jar --server SERVER_IP --fingerprint SHA256_FINGERPRINT
```

The deployed VPS address is `198.100.154.156`. The `jayms` test account's password is supplied separately, not stored in this repository. Restart through **Play Voxel One** to install the updated client: protocol 8 adds coloured lights and synchronised held-light colours, so older clients cannot connect.

On macOS, add `-XstartOnFirstThread` immediately after `java`. To skip the launcher and play offline, use `--offline`. Launching without arguments opens the launcher.

## Fractional world blocks and crafting

The playable world uses sparse material octrees with 16 subdivisions per block axis, including ordinary terrain chunks. Place **1/2, 1/4, 1/8 and 1/16 cubes** within the same world cell. Their actual surfaces control picking and collision; uniform regions collapse back to a single node, and exposed faces merge into larger quads. Models retain their existing separate 8/16/32 voxel definitions.

Open **E → Recipes** to browse **95 recipes**. Scroll, select a recipe, check the required quantities, then click **Craft**. Materials can come from any of the 36 inventory slots. Outputs stack into available space; excess items drop at your feet. Multiplayer validates the recipe and consumes inputs on the server. Recipes include cutting each building material into eight smaller cubes, joining eight pieces into a larger cube, planks, bricks, glass, grass, compost, a flower pot, and an LED light. Selecting a fractional item in the hotbar determines its placement size. Breaking a piece drops the matching size, preserving material volume.

Existing worlds, models, accounts and inventories migrate when loaded; saves retain fractional edits and ground drops. The client receives an authoritative cell snapshot after each edit, so a conflicting or rejected edit restores all pieces in that cell.

## Controls menu

Press **Escape** or **F1** to open the controls menu. It shows every action and its current binding. Click a row (or select with Up/Down and Enter), then press a keyboard key or mouse button. Assigning an occupied input swaps the two bindings. Escape cancels a binding or resumes play. Scroll the mouse wheel or use the arrow keys to view additional controls. The menu also adjusts mouse sensitivity, restores defaults, and quits.

Bindings and sensitivity persist in `~/.voxel-one/controls.properties` (on Windows, `%USERPROFILE%\.voxel-one\controls.properties`). Server fingerprints and your last username live in `connections.properties`; passwords are never saved by the client. Multiplayer continues while the menu is open.

| Default input | Action |
| --- | --- |
| W/A/S/D | Walk or fly horizontally |
| Left Shift | Sprint / faster flight |
| Space | Jump repeatedly while held, or ascend while flying |
| Left Ctrl | Descend while flying |
| F | Toggle flight |
| Mouse | Look |
| Left click | Break block |
| Right click | Place the selected hotbar block |
| 1–9 / mouse wheel | Select a hotbar slot |
| E | Open/close inventory |
| F5 | Cycle first-person → third-person behind → front view → first-person |
| F6 | Toggle isometric sky overview |
| F7 | Open/close voxel model editor |
| F8 | Choose LED placement colour |
| Mouse wheel / = / - (sky view) | Zoom in/out |
| Home (sky view) | Fit the full visible world |
| Tab | Release/capture mouse |
| Escape / F1 | Controls menu |

Flight keeps voxel collision enabled. Walking accelerates and decelerates smoothly; diagonal movement is normalized. The avatar uses classic block proportions: an 8x8x8 head, an 8x12x4 torso, and 4x12x4 limbs, scaled at 16 pixels per block. Opposing arms and legs use a distance-based walk cycle with smooth acceleration and settling at rest; flight stops the walking cycle. The geometry and gait reference [Mojang's humanoid model](https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/models/mobs.json) and [player animations](https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/animations/player.animation.json); Voxel One uses its own blue-shirt skin and face. Left click swings the right arm even when aiming at empty space. First-person shows the right arm and the selected hotbar item, including custom tiny-voxel models. External cameras and other players show the held item and swing too. Attack animations last 0.3 seconds; rapid clicks preserve the first half of a swing before restarting it. Other players render with 100 ms of interpolation, including yaw and animation, to smooth the server's 20 Hz updates.

Attack swings start at shoulder height and strike downward; successful placements use a gentler motion. In the inventory, hover over an occupied slot to see its item or custom model name. Hair renders as an inflated outer skin layer.

Pending placements become solid immediately on the placing client, preventing movement into an unconfirmed block. Every edit carries the position at the moment of the click; the server checks bounds, reach, occupancy, and player overlap and sends an acceptance/rejection result. Rejected edits roll back. If a delayed edit from another player overlaps you, collision recovery moves you to the nearest free block face instead of leaving you trapped.

Press **F5** once for third-person behind your player, twice for a front view looking back at your player, and a third time to return to first-person. Both external views move closer when terrain blocks the camera. Movement, player facing, and block interaction stay tied to the player's aim throughout the cycle.

Terrain remains visible approximately **2,048 blocks in every direction**, fading into the sky at the horizon. Nearby chunks retain full voxel geometry, collision, models, and edits. Beyond them, surface meshes use progressively coarser cells; fine background tiles also include trees and known block edits. Distant caves, custom models, and individual constructions beyond the fine-detail region are approximated by the landscape. These background meshes are generated on a worker thread with bounded uploads per frame and cached only for the current visible region. Coarse parent tiles remain visible until their refinements are ready; detailed chunk columns take over together to avoid holes and overlapping surfaces. Interaction and gameplay chunk loading keep their existing range.

Press **F6** to view the entire visible world from a fixed isometric angle high in the sky. This uses an orthographic projection, so blocks keep the same size with distance. The view centers on your current region and fits a **4,096 × 4,096 block** footprint, including distant terrain. Scroll or press **= / -** to zoom, up to 64× for a close view around your player; **Home** fits the whole visible world again. Press F6 to return to your previous player camera, or F5 to return and switch player camera. Sky view releases the mouse and stops movement input while physics and multiplayer continue. Its four keyboard actions can be rebound in the controls menu.

## Rendering and coloured lights

A visible sun lights the world, with a daylight sky, soft filtered sun shadows on nearby geometry, and coloured indirect lighting. Glass and other surfaces reflect the sky environment according to their roughness. The renderer uses linear HDR lighting, filmic tone mapping and a subtle bloom around bright emitters. Procedural material textures use mipmaps, trilinear filtering and up to 8× anisotropic filtering where supported. Edges use up to **4× MSAA**; software renderers and hardware without multisampling use FXAA. The HUD remains sharp because it renders after the scene resolve.

To make a light, open **E → Recipes**, select **LED light**, and craft **one glass + one stone → one LED light**. Put it in the hotbar. Press **F8** to choose any **24-bit RGB colour** with the sliders, presets, or six-digit hex entry. Ctrl+A clears the hex field; Enter or Apply saves it; Escape cancels. Right click places the light with that colour. Each placed light retains its own colour in saves and multiplayer, including late joins and rejected-edit corrections. F8 changes future placements and the held light, leaving existing lights unchanged. Black emits no light. LED cubes can be cut and joined just like other materials, down to 1/16 size. Picked-up light items use the generic LED stack; choose their colour before placing them again.

Global illumination is a bounded voxel approximation: skylight and one diffuse sunlight bounce propagate through air alongside coloured LED light. Walls occlude propagation; lights mix and fade over roughly fifteen blocks. A single background worker rebuilds a **96 × 128 × 96** lighting volume around the player after edits or movement between chunk regions, then uploads it for smooth sampling. Fractional cells and tiny models use approximate coverage for indirect lighting; direct sun shadows use their actual geometry. The sun uses a 2,048² shadow map (1,024² on software drivers) and covers the nearby 160-block footprint. Distant terrain uses sky lighting and environment reflections beyond the detailed lighting volume. Reflections currently show the sky environment, including the sun, rather than nearby objects. Glass is reflective and remains solid/opaque. Lighting may take a moment to settle after an edit.

## Inventory, health, and generated worlds

The bottom HUD has nine hotbar slots, with health immediately above them. **E** opens a 36-slot inventory: 27 backpack slots and the same nine hotbar slots. Click a stack, then another slot to move, swap, or merge it. Stacks hold up to 64 items. Number keys 1–9 and the mouse wheel select the active hotbar slot; keyboard bindings, including inventory and every hotbar slot, can be edited in the controls menu.

New players start with an empty inventory. Break a block with left click, then walk within two blocks of its floating, rotating item to pick it up. Right click places one item from the selected slot. Full inventories leave uncollected items in the world. The server owns item counts, pickups, and inventory moves, and saves inventories per account. Two players cannot collect the same item, and rejected placements do not consume items.

Health starts at 20. Falls longer than three blocks cause damage; flight is safe. At zero health you respawn with full health and keep your inventory. Combat, hunger, and tools with durability are not implemented yet.

Every world uses a saved seed. Climate noise selects **plains, forest, desert, and snowy mountain** biomes; layered terrain noise creates hills and mountains, with underground caves and trees in forests. Terrain and tree features agree across chunk boundaries and are generated from the same seed on the server and client. Chunks load as you explore and distant GPU meshes unload. Horizontal coordinates support -1,000,000 through 1,000,000; generated blocks span Y -32 through 95, with flight space above. The HUD displays your biome and seed.

Use `--seed NUMBER` when creating a server world, or `--offline --world FILE --seed NUMBER` for a new offline world. An existing world keeps its saved seed regardless of the command-line seed. Offline play saves inventory, drops, health, and edits to `~/.voxel-one/offline-world.dat` by default. Saves occur every minute and on normal exit; use the controls menu's Quit button to save before exiting.

Older server saves are upgraded on the next save: their block edits remain at their original coordinates, over the new default seeded terrain. Make a backup before upgrading if you want to retain the previous terrain with an older server binary. The deployed VPS keeps a pre-upgrade backup.

## Tiny voxel models and the editor

Press **F7** to open the model editor. It starts with a procedurally generated **flower pot**: a hollow terracotta pot, soil, stem, leaves, and a pink flower made of tiny colored voxels. Click **Create item**, close the editor with F7, select the item in your hotbar, then right click a nearby surface to place it. Creation grants one item; repeat to obtain more copies. Break a placed model and walk over its floating miniature to pick it up again. Model items use the existing 36-slot inventory and stack up to 64.

The editor has a live 3D workspace and a layer painting grid. In the 3D workspace, select **Add, Paint, Erase, or Pick** and left click or drag directly on the model. **Right drag** orbits and scrolling zooms. An empty model starts on the checkerboard floor. Use a 1, 2, 4, or 8 voxel brush, the palette or any six-digit **RGB hex colour**; keys 1–4 select tools. A wireframe previews the target. Paint recolours existing voxels; Add preserves occupied voxels. In the layer grid, left click paints and right click erases. Select a color below the grid. Click **X/Y/Z** or press those keys to choose the slice axis; Up/Down, the +/- buttons, or scrolling over the grid changes the layer. Click the name to rename your model. **Ctrl+Z / Ctrl+Y** or the Undo/Redo buttons restore edits. **Clear** starts an empty model; the Flower pot button restores the example. **Save draft / Load draft** use `~/.voxel-one/models/draft.vxm` (on Windows, `%USERPROFILE%\.voxel-one\models\draft.vxm`). Copy this file to share an editable draft with another player.

Opening the editor while aiming at a placed model loads an editable copy. Publishing creates an immutable model type: subsequent editing leaves placed copies intact. Multiplayer sends definitions to everyone, including late joiners, before blocks and inventory reference them. Models, placed copies, and items persist across server restarts and offline saves. Creation is free during this cooperative playtest; no crafting recipe is required. The server allows 16 distinct custom models per account and 128 per world, with a two-second creation cooldown. Identical names and voxel data reuse an existing type.

Models occupy one world cell with **8³, 16³, or 32³** colored voxels; the player editor uses 32³. Empty regions and uniform colored regions collapse in a sparse voxel octree, and undo snapshots share immutable tree nodes. Picking and collision traverse the octree, so the empty space around the pot is passable. Rendering removes internal faces, greedily merges adjacent faces of the same color, caches one mesh per model type, and uses instanced draws for placed copies. The flower pot has 1,476 occupied voxels and 770 merged surface quads. Each model is limited to 8,192 quads and a 200 KB serialized definition; unusually detailed models receive an editor error instead of exhausting the renderer.

Programmatic generators use the same model format as the editor. For example:

```java
import dev.jayms.net.model.*;

SparseVoxelOctree voxels = new SparseVoxelOctree(32);
voxels.fill(10, 0, 10, 22, 3, 22, 0xffbc5939); // opaque ARGB; upper bounds exclusive
voxels.set(16, 3, 16, 0xff50b552);
ModelDefinition model = new ModelDefinition("Tiny planter", voxels);
// model.write(DataOutputStream) exports a .vxm draft;
// ModelDefinition.read(DataInputStream) imports it.
```

`ModelGenerators.flowerPot()` demonstrates a more detailed procedural model. The editor submits definitions through `MultiplayerClient.createModel(...)` or `LocalGame.createModel(...)`; the server validates and assigns stable world item IDs. Colors must be opaque ARGB; zero erases a voxel. Previous inventory/world saves upgrade automatically without changing their seed or existing blocks. The VPS retains a backup from before this upgrade.

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

`mvn verify` runs tests for gravity, jumping, collision, late block recovery, flight, acceleration and walking animation state, world edges, normalized movement, persisted bindings, remote interpolation, authenticated TLS sessions, wrong credentials, duplicate logins, registration, certificate pin rejection, exact-pose block placement, rollback, shared edits, late joins, disconnects, salted password storage, seeded biome generation, trees and caves, chunk boundaries, 36-slot stack capacity, inventory moves, exclusive item pickups, placement supply checks, health and respawn, three-view camera cycling, front-view aim and movement, camera obstruction, orthographic isometric framing, aspect ratios, zoom limits, and save/reload. Model checks cover octree compression and round trips, immutable snapshots, greedy surface merging and winding, precise picking/collision, custom item creation, multiplayer definition ordering, late joins, pickups, and model persistence across restarts. Animation checks cover classic dimensions, opposing limbs, downward attack and gentler placement trajectories, swing timing, inflated outer hair geometry, held-item/swing network round trips, and remote swing resets. Inventory hover tests cover built-in and custom names, moved stacks, empty slots, gaps, and resized windows. Fractional-world and crafting checks cover octree compression, persistence, fine picking and collision, greedy geometry, material volume, recipes, missing inputs, full-inventory overflow, legacy save migration, server validation, conflicting edits, authoritative corrections and late joins. Direct 3D editor tests cover starting on the floor, adding to faces, painting, picking, erasing and misses. Distant-terrain tests cover seeded surfaces and edits, tree geometry, face winding, bounded tile residency, negative coordinates, exact coverage, non-overlapping refinement, parent fallbacks, and isometric depth/viewport fitting across the expanded footprint. Lighting tests cover RGB material persistence and mesh emission, sealed-room occlusion, light falloff and mixing, diffuse surface bounce, colour validation, protocol round trips, legacy v5 migration, crafting, rejected-edit restoration and multiplayer late joins/restarts. Updater tests exercise real HTTP downloads, caching, checksum rejection, atomic replacement, offline fallback, invalid manifests, corrupted installations, platform detection, and launch arguments.

GPU rendering checks can be reproduced on Linux with a built Linux client:

```sh
xvfb-run -a env LIBGL_ALWAYS_SOFTWARE=1 java -cp target/voxel-one-1.0-SNAPSHOT-client.jar deploy/RenderingSmoke.java /tmp/voxel-render-evidence
xvfb-run -a env LIBGL_ALWAYS_SOFTWARE=1 java -Dvoxel.msaa=4 -cp target/voxel-one-1.0-SNAPSHOT-client.jar deploy/RenderingSmoke.java /tmp/voxel-render-evidence
```

These check the rendered sun, uploaded irradiance, visible shadow/reflection differences, texture filters, framebuffer resizing, and OpenGL errors, writing screenshots and compact JSON evidence. The second command forces the 4× MSAA path on the software driver.
