# Cargo vehicles validation

Result: ready for independent review. Media publication is pending the controller. No commit, push, merge or deployment was performed by the developer.

Four voxel cargo bodies are available in the existing offline vehicle slot: container truck, liquid tanker, delivery van and goods lorry. Stand near the parked vehicle and press Shift + the configured vehicle key (default J) to cycle bodies. J enters/exits; WASD and mouse drive/steer; Ctrl boosts. Changes are rejected while driving, while moving above 0.5 blocks/s, or when the new body overlaps world blocks or the nearby player. Each body has its own collision dimensions, cab seat and exit position. The saved fifth field preserves the selected body; old four-field Jeep saves still load.

These are vehicle models and driving integration. There is one offline vehicle slot. Shipment transfer, liquid storage accounting and multiplayer vehicles are outside this change.

## Commands and environment

Executed in the assigned feature worktree on Linux with Java 17-compatible source and LWJGL. All temporary files and the Maven cache were inside target/. The native run used the inherited role DISPLAY and XAUTHORITY with X11, Mesa software rendering, and a 1280 × 720 game window. It used an isolated synthetic offline profile under target/cargo-home and a flat synthetic yard. No real account or save data was used. The harness enabled the existing city camera zoom range for clear inspection captures. Browser playtesting does not apply to this native Java/OpenGL client.

```sh
mvn -q -Dmaven.repo.local=target/m2 -DargLine="-Djava.io.tmpdir=$PWD/target/test-tmp" -Dtest=JeepTest,ControlsTest test > target/cargo-unit-output.txt 2>&1
python3 deploy/run_cargo_smoke.py --display "$DISPLAY"
git diff --check
```

Observed: Maven exit 0. JeepTest: 8 tests; ControlsTest: 5 tests. All 13 passed with zero failures, errors or skips. Native harness exit 0; all assertions passed. `git diff --check` exit 0.

## Playtest: production offline vehicle workflow

The harness ran Main.run, rendered the production game, and sent real X11 key input with xdotool. It used reflection only to build the synthetic yard, read observed state, and position the inspection camera/player between scenarios. Vehicle selection, entry, driving, braking and exit used the production input path.

| Steps | Expected | Observed |
| --- | --- | --- |
| Start with Jeep; use Shift+J while parked to select container truck, tanker, van and lorry | Each distinct body appears; cycle returns to Jeep | Passed for all five bodies; five fresh renderer captures saved |
| For each body, press J, hold W until speed reaches at least 1.5 blocks/s | Driver enters; vehicle moves more than 0.2 blocks | Passed for all five bodies |
| Press Shift+J while driving | Body remains unchanged | Passed for all five bodies |
| Press J while W is still held | Moving exit is rejected | Passed for all five bodies |
| Release W; wait until speed is at most 0.05 blocks/s; press J | Driver exits safely | Passed for all five bodies |
| Press J again from the cab exit, then exit once more | Re-entry works even for the long truck cab | Passed for all five bodies |
| Save and load each body | Body type survives reload | Passed for all five bodies |
| Return to Jeep and put a wall 4 blocks behind its centre; press Shift+J | Larger container body is rejected; Jeep remains selected | Passed |

Unit regression checks also passed: legacy Jeep save loading, normal/boost/reverse driving, steering, glass-wall collision, blocked exits, walking after exit, driver release on respawn, unloaded-boundary collision, and body changes rejecting player overlap.

## Captured evidence

- cargo-container.png: ribbed red shipping container, rear doors, blue cab and three axles.
- cargo-tanker.png: stepped silver tank, top fittings/bands, ladder, blue cab and three axles.
- cargo-van.png: smaller cream delivery body with two axles and rear doors.
- cargo-lorry.png: smaller cream cargo box, blue cab and three axles.
- cargo-jeep.png: original Jeep regression capture.
- cargo-container-drive.mp4: production F10 recording of container entry, driving, braking, exit/re-entry and inspection. 27 frames, 13.51 seconds, 1,046,704 bytes; below the 6 MB limit. The software-rendered test ran at about 2–3 FPS; this is functional evidence, not a performance result.

All five PNGs were visually inspected. The MP4 was decoded with JCodec at 20%, 45%, 80% and 95% of its duration; metadata and sample decoding passed. The 45% sample shows 11 km/h and the rejected-exit notice; the 95% sample shows the parked container truck. The decode command was:

```sh
java -Djava.io.tmpdir="$PWD/target/tmp" --class-path target/m2/org/jcodec/jcodec/0.2.5/jcodec-0.2.5.jar target/CargoClipCheck.java dashboard/evidence/cargo-container-drive.mp4
```

The temporary decoder source is in target/; it uses FrameGrab.seekToSecondPrecise, transforms decoded frames to RGB, and writes inspection PNGs in target/. The sanitized metadata is saved in cargo-video-check.json.

Earlier setup attempts failed on read-only external temp/cache paths, incomplete synthetic chunk columns, and a moving-exit check that released W too early. Those attempts are not counted as passes. The native run was repeated after fixing setup and timing, then repeated with closer camera framing. Final captures come from the successful closer run against the tested implementation.

All evidence files are sanitized synthetic game captures. Their intended location is the assigned feature branch under dashboard/evidence on raw.githubusercontent.com/jaymsDooku/voxel-one. Publication and exact-head review remain the controller/reviewer's work.
