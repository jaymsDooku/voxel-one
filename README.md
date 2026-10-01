# Voxel One

A Java/LWJGL voxel playground with a walking player and a headless TCP multiplayer server. The client supports offline play or a shared world with up to 32 connections. Java 17 or newer is required.

## Build and download

Install a JDK and Maven, then run `mvn verify` from this directory. Builds select native libraries for the build machine (Windows, Linux x86-64, Intel macOS, or Apple Silicon macOS). To build a Windows client from Linux, use `mvn verify -Dlwjgl.natives=natives-windows`.

GitHub Actions builds all four desktop variants on every push. Open the latest successful run under **Actions → Build and test**, download the artifact for your desktop, and extract it. It includes:

- `voxel-one-1.0-SNAPSHOT-client.jar`: runnable desktop client with dependencies and shaders.
- `voxel-one-1.0-SNAPSHOT-server.jar`: runnable server, no Maven, graphics, or native libraries needed. The same server JAR runs on any OS with Java 17+.

## Playtest on one desktop

Open two terminals in the folder containing the JARs. Start the server in the first:

```sh
java -jar voxel-one-1.0-SNAPSHOT-server.jar --bind 127.0.0.1
```

Connect in the second:

```sh
java -jar voxel-one-1.0-SNAPSHOT-client.jar --server 127.0.0.1
```

Launch another client with the same command to see a second player and test shared block edits. On macOS add `-XstartOnFirstThread` immediately after `java` when launching the client. To play offline, omit `--server`.

## Playtest across computers

On the server computer:

```sh
java -jar voxel-one-1.0-SNAPSHOT-server.jar --bind 0.0.0.0 --port 25565 --world world.dat
```

On your desktop (replace `SERVER_IP` with the server computer's LAN address or public hostname):

```sh
java -jar voxel-one-1.0-SNAPSHOT-client.jar --server SERVER_IP --port 25565
```

Allow inbound **TCP 25565** in the server computer's firewall. For a server behind a home router and clients outside your LAN, forward TCP 25565 to that computer. A remote host also needs this port allowed in its network firewall. Keep the server terminal running; stop it with Ctrl+C to save block edits to `world.dat`. Restart with the same world file to restore edits. Use a different `--world` file for a fresh world.

## Controls

| Input | Action |
| --- | --- |
| W/A/S/D | Walk |
| Left Shift | Sprint |
| Space | Jump (release before jumping again) |
| Mouse | Look |
| Left click | Break a block |
| Right click | Place stone |
| F5 | Toggle first/third-person camera |
| Tab | Release/capture mouse |
| Esc | Quit |

A crosshair marks the interaction direction; reach is six blocks from the player's eyes. Gravity, voxel collision, normalized diagonal movement, and placement checks prevent walking through terrain or placing blocks inside players. Unloaded edges are solid. The title bar shows connection state and player count.

## Current multiplayer scope

The server orders and broadcasts block changes, checks edit bounds/reach/player overlap, sends world edits and existing players to late joiners, removes disconnected players, and saves edits on graceful shutdown. Clients generate identical base terrain locally and apply server edits on the rendering thread. Networking runs on separate threads with bounded queues so a slow connection does not block rendering or other clients.

Movement is client simulated and relayed after finite-number and world-bound checks; this is a cooperative playtest server, with no accounts, encryption, or authoritative movement/anti-cheat. Player positions reset at reconnect. The world covers X/Z -64 through 79 and Y -32 through 47. Edits save on graceful shutdown, so a forced kill can lose edits since launch. Remote avatars currently use the existing block model and do not interpolate or animate.

`mvn verify` runs headless tests for gravity, landing, jumping, collision, world edges, diagonal speed, negative coordinates, two-client movement/edit replication, late joins, disconnects, input validation, and save/reload.

## VPS service

This checkout includes `deploy/voxel-one.service`, a systemd user service configured for this VPS checkout. Copy the server JAR to `runtime/server.jar`, install the unit under `~/.config/systemd/user/`, then run `systemctl --user daemon-reload` and `systemctl --user enable --now voxel-one`. Adjust paths in the unit if installing elsewhere. Enable lingering with `sudo loginctl enable-linger "$USER"` to start it at boot and keep it running without an interactive login.

Use `systemctl --user status voxel-one` to check the process, `journalctl --user -u voxel-one -f` for logs, and `systemctl --user stop voxel-one` to stop and save. To update, stop the service, replace `runtime/server.jar`, then start it again. The world file lives in `runtime/world.dat` and is excluded from Git.
