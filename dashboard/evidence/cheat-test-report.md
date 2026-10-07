# Cheat mode validation

Final native run completed successfully (exit 0).

Implementation: feature/queue-32393565343262322d373731352d343339342d396432342d653663393930356237303038.
Media publication is pending the controller's commit and push. All media comes
from this worktree, using an isolated synthetic city and profile.

## Automated checks

Command:

```sh
mvn -q -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=JeepTest,ControlsTest,CityEconomyTest,CityTest,AviationTest,ShippingTest,ProtocolCompatibilityTest test
```

Observed: exit 0. 48 tests passed; 0 failures, 0 errors, 0 skipped.
The suites exercise driving/collision, cargo body save compatibility, control
migration, economy persistence, city commands, aviation, shipping and protocol
compatibility. Counts are recorded in `cheat-unit-tests.json`.

An initial broad test run failed because `/tmp` is read-only in this sandbox.
A second broad run used a worktree temp directory and was stopped to focus on
the affected suites. Neither broad run is claimed as passing.

`git diff --check`: passed.

## Playtest: native game controls

Command:

```sh
python3 deploy/run_cheat_playtest.py --display "$DISPLAY"
```

Environment: Linux, inherited assigned X11 display and XAUTHORITY, software
OpenGL (`LIBGL_ALWAYS_SOFTWARE=1`), installed Java 25.0.3. The harness sets
`user.home` and `java.io.tmpdir` to worktree directories. It makes a fresh
synthetic profile on each run. It sends real X11 keys through production GLFW
callbacks. Test setup and state assertions use a frame observer. Runtime logs
stay in target and are excluded from evidence. Browser playtesting does not
apply to this native Java/OpenGL application.

Expected and observed in the completed run:

- Insert outside cheat mode: no money added; prompt to enter mode.
- F4: mode enabled and flight enabled. Space rises more than 2 blocks; Ctrl
  descends more than 1 block. `cheat-flight.png` captures this state.
- Insert in city cheat mode: budget rises by exactly $10,000. Saving and reading
  the city save retains the grant. `cheat-budget.png` shows the mayor dashboard.
- Page Down: spawns Jeep, Container truck, Liquid tanker, Delivery van and Goods
  lorry in turn. Each type matches the selected kind. The five
  `cheat-vehicle-*.png` images capture these bodies.
- Repeating a spawn in the same place: rejects overlap and keeps the vehicle.
- J: enters the spawned lorry. Spawning or leaving cheat mode while driving is
  rejected. J exits through the normal safe exit path.
- Page Up: selects Passenger jet and Cargo carrier, then wraps to Jeep.
  Page Down spawns both static assets. `cheat-jet.png` and `cheat-carrier.png`
  show them. These retain the normal game's static vehicle behavior.
- A blocked spawn: asks for clear space and preserves the current road vehicle.
- F4 exit: mode and flight disabled. Insert then leaves the budget unchanged.
- Existing F flight toggles, F6 sky camera, F9 mayor dashboard: still work.
- Budget above $990,000,000: Insert refuses another grant.
- 64 successful spawns recorded: Page Down refuses a 65th spawn.

F10 records the production renderer while these inputs run. The finished
`cheat-mode.mp4` is below the 6 MB artifact limit. `cheat-playtest.json` records
successful completion. Screenshots and video have been inspected for the
synthetic scene and visible cheat/vehicle state.

## Behavior limits

Cheat actions are checked locally and are unavailable in a network session.
The network protocol and server command path have no new cheat operation.
Jets and carriers are static assets. Treasury grants, carrier blocks and the
current road vehicle use existing saves. Extra parked road vehicles and jets
last for the session. CHEATS.md documents controls and save behavior.

Video verification: `VerifyRoadVideo` decoded the start, middle and two seconds
before the end. H264, 960 x 544, 100 frames, 60.488 seconds, 2,951,585 bytes.
The decoded frame was inspected. Details are in `cheat-video.json`.
