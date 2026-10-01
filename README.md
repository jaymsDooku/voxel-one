# Voxel One

A Java/LWJGL voxel playground with walking, jumping, flight, editable controls, and an authenticated multiplayer server. Requires Java 17+ and an OpenGL 3.3 desktop. Use a JDK on the server for first-run certificate generation.

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

The deployed VPS address is `198.100.154.156`. The `jayms` test account's password is supplied separately, not stored in this repository. Download the updated client: protocol 2 requires TLS and account login, so clients from the initial multiplayer version cannot connect.

On macOS, add `-XstartOnFirstThread` immediately after `java`. To skip the launcher and play offline, use `--offline`. Launching without arguments opens the launcher.

## Controls menu

Press **Escape** or **F1** to open the controls menu. It shows every action and its current binding. Click a row (or select with Up/Down and Enter), then press a keyboard key or mouse button. Assigning an occupied input swaps the two bindings. Escape cancels a binding or resumes play. The menu also adjusts mouse sensitivity, restores defaults, and quits.

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
| Right click | Place stone |
| F5 | First/third-person camera |
| Tab | Release/capture mouse |
| Escape / F1 | Controls menu |

Flight keeps voxel collision enabled. Walking accelerates and decelerates smoothly; diagonal movement is normalized. Limbs swing according to distance walked, settle at rest, and stop swinging during flight. Other players render with 100 ms of interpolation, including yaw and animation, to smooth the server's 20 Hz updates.

Pending placements become solid immediately on the placing client, preventing movement into an unconfirmed block. Every edit carries the position at the moment of the click; the server checks bounds, reach, occupancy, and player overlap and sends an acceptance/rejection result. Rejected edits roll back. If a delayed edit from another player overlaps you, collision recovery moves you to the nearest free block face instead of leaving you trapped.

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

World edits save every minute and on Ctrl+C or service stop. Keep the same world file across restarts. Player positions reset to a free spawn area on reconnect. The world covers X/Z -64 through 79 and Y -32 through 47 and supports up to 32 authenticated players. Movement remains client simulated; this is a cooperative playtest server without authoritative movement or anti-cheat.

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

`mvn verify` runs tests for gravity, jumping, collision, late block recovery, flight, acceleration and walking animation state, world edges, normalized movement, persisted bindings, remote interpolation, authenticated TLS sessions, wrong credentials, duplicate logins, registration, certificate pin rejection, exact-pose block placement, rollback, shared edits, late joins, disconnects, salted password storage, and save/reload.
