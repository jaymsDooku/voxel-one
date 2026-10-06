# Jeep verification

Status: implementation and developer validation finished; separate review and controller publication pending.

Implemented: an offline open-top voxel 4x4 jeep with four wheels, two seats, a transparent glass windshield, driver entry/exit, WASD and mouse steering, Ctrl acceleration, full-body collision, clear one-block climbs, and parked position/heading persistence. Existing glass blocks remain available. Respawn releases the seat and stops the vehicle. Multiplayer vehicle authority/synchronization is not included.

## Executed automated checks

Environment: Linux sandbox; JDK 21.0.5; Maven 3.9.9 and dependency cache inside this worktree. Java temporary files are inside `target/tmp`.

```sh
JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$PWD/target/tmp" JAVA_HOME=/usr/lib/jvm/jdk-21.0.5-oracle-x64 ./target/apache-maven-3.9.9/bin/mvn -q -Dmaven.repo.local=target/maven-cache -Dtest=JeepTest,PlayerCameraTest,PlayerTest,ControlsTest test
```

Observed: exit 0; 20 tests, 0 failures, 0 errors, 0 skipped. JeepTest: 6; PlayerCameraTest: 3; PlayerTest: 6; ControlsTest: 5.

Expected and observed: boosted forward travel exceeds normal throttle; reverse moves backward; releasing throttle brakes to zero; A/D turns rather than strafing; mouse changes heading; glass walls stop the full body at boost speed; moving exit is rejected; stopped exit succeeds; blocked sides keep the driver seated; distant entry fails; walking resumes after exit; unloaded boundaries stop the vehicle; reload restores a parked pose with no driver or speed; respawn releases the driver and stops the vehicle. All assertions passed. Existing player physics, camera views and control persistence tests also passed.

`python3 -m py_compile deploy/run_jeep_playtest.py` and `git diff --check` exited 0. The graphical launcher compiled its Java harness successfully.

## Playtest: running native application

Actual final command:

```sh
python3 deploy/run_jeep_playtest.py --display "$DISPLAY"
```

Environment: inherited assigned authenticated X11 display; 1280 x 720 framebuffer; Linux/Mesa software OpenGL; isolated synthetic profile in `target/jeep-home`; isolated world save in `target/jeep-playtest`. XAUTHORITY was passed through without reading or printing its cookie. No new X server was started. Runtime logs stay private in `target/`. Browser playtesting does not apply to this native Java/GLFW application.

Observed: exit 0. The harness sent real X11 key/mouse events to this application's window and waited for rendered frames/observable state. Production GLFW callbacks and the game loop processed the inputs.

| Step | Expected | Observed |
| --- | --- | --- |
| Stand beside jeep, J | Attach player to driver seat | Passed; driver seat state and pose captured |
| F5 through front view to first person | Remain seated; view through glass | Passed; first-person windshield image captured |
| F5 back to third person | Restore chase view | Passed |
| Hold W | Forward travel | Passed; speed at least 6 blocks/s and travel over 2 blocks |
| Hold Ctrl with W | Speed rises above normal throttle | Passed; speed exceeded the prior value by more than 2 blocks/s |
| Hold A then D | Turn left then right | Passed; each heading changed more than 10 degrees |
| Move captured mouse horizontally | Steer jeep | Passed; heading changed more than 1 degree |
| J while moving | Keep driver seated | Passed |
| Release W | Brake to rest | Passed |
| Hold S, then release | Reverse, then brake | Passed; negative speed and reverse travel over 0.5 block |
| Reset synthetic test vehicle for a straight boosted run toward brick wall | Full body stops before crossing wall | Passed; body remained clear and speed fell below 0.5 block/s |
| Stop and J | Safe side exit | Passed |
| Hold W on foot | Normal walking resumes | Passed; player moved over 0.5 block |
| F10 during workflow | Capture gameplay MP4 | Passed; native F10 recorder produced the attached video |

Visual inspection: opened the parked, seated-driver, first-person windshield and wall-stop PNGs. The car has an open top and two seats. The seated avatar aligns with the cushion. Terrain and the wall remain visible through the tinted glass. Inspected decoded driving footage with the driver HUD and tested vehicle visible. The final F10 video is 38.91 seconds, H.264, 960 x 540, 3,103,193 bytes; no speed change or re-encoding was applied. Software-rendered frame rate was about 2–3 fps; this evidence does not establish hardware performance.

Earlier validation found harness timing/focus and first-mouse initialization issues; corrected before the passing final run. Visual inspection found the driver anchor too high; lowered it, reran all 20 targeted tests and reran the full app workflow. Earlier missing-display checkpoints are superseded by this executed run.

## Evidence

All artifacts below are from this implementation and use only the synthetic game profile. Publication is reserved to the controller and remains pending.

- [jeep-parked.png](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-31313831626165362d386635342d346361642d613135352d633534333763656632646561/dashboard/evidence/jeep-parked.png) — pending controller publication; 709699 bytes; SHA-256 `41ba6d18c2c0b880429cd45ad6800404d48429bd8d66018b9972c9b9723e114c`.
- [jeep-driver-seat.png](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-31313831626165362d386635342d346361642d613135352d633534333763656632646561/dashboard/evidence/jeep-driver-seat.png) — pending controller publication; 654399 bytes; SHA-256 `10164e818c66dccef77d7eab8fad15cf58f550d7ff39770053f5f99781202860`.
- [jeep-windshield.png](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-31313831626165362d386635342d346361642d613135352d633534333763656632646561/dashboard/evidence/jeep-windshield.png) — pending controller publication; 388568 bytes; SHA-256 `eb313e79da0e1d9f9856b55a7068291a05b27ada820dc01b617c7cc5a7e0511d`.
- [jeep-wall.png](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-31313831626165362d386635342d346361642d613135352d633534333763656632646561/dashboard/evidence/jeep-wall.png) — pending controller publication; 672700 bytes; SHA-256 `6ea23b8ca3db14258cf79e6374f143e6bf74d0e24a65634d500946883b3a5ae7`.
- [jeep-driving.mp4](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-31313831626165362d386635342d346361642d613135352d633534333763656632646561/dashboard/evidence/jeep-driving.mp4) — pending controller publication; 3103193 bytes; SHA-256 `f0d6c389415d142666d6311f8494ae0ac354ba559c99a564b70bff67f6d9bcd9`.
- [jeep-playtest.json](https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-31313831626165362d386635342d346361642d613135352d633534333763656632646561/dashboard/evidence/jeep-playtest.json) — pending controller publication; 382 bytes; SHA-256 `6dce5ac79b3aaa1964f7e9710c9119e7a7832a4425262d63a721493f39c138c4`.

## Tested source snapshot

These hashes identify the implementation used for the final app run before controller Git publication. The reviewer must still verify the exact submitted head and independently exercise the workflow.

- `src/main/java/dev/jayms/Main.java`: `7ec4978592d8cccf015a2fe76017dfa01c3f65688e350aa1b74ef0bfd7377871`
- `src/main/java/dev/jayms/player/Jeep.java`: `fee90881847413cfdf28669bb43c2f658f336543ca74f4ca4cd94fb569717645`
- `src/main/java/dev/jayms/player/JeepModel.java`: `22c0d10ff0dc9552a912ba07cb8939f9343acd157f31ab3597977b002b33b08c`
- `src/main/java/dev/jayms/player/Player.java`: `c37b83e829f008693ce324a45b149226f5c36aa4a19bca3d876a094fe6d11618`
- `src/main/java/dev/jayms/ui/Controls.java`: `1f82764d31e522a6ad7fc31324adc82ef53862133bf9a0be6fbf5923d76a66cf`
- `src/main/resources/shaders/voxel.frag`: `3305a32170e0d5a671c6039b2acf31d7ae74e0ab527eb9dc60cbe9f55aa71eb3`

No commit, push, PR, merge or deployment was performed by the developer.
