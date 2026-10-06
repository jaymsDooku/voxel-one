# Roads and jeep validation after rebase

Roads offers dirt roads and paved roads with 2, 3 and 4 lanes. Choosing a road starts endpoint placement with the direction guide. Paved roads use asphalt and painted lane dividers. Dirt roads can be upgraded. Road types survive saves and multiplayer restart; old saves load as dirt.

Tested production source: `3b6fd21d2011bb6b4fdd4f524d02632ecf33e814`, rebased onto `34bcb3197904b27aa12bad31b5aedf659bbc991f`. The complete source diff is confined to roads. Jeep source, model, tests, Main input/rendering/persistence integration, shader transparency, original playtest tools, docs and original media match the base byte for byte, including after the native runs. Every base progress entry is preserved, including the jeep entry. The road entry is also present. IDs are unique. No unresolved index, deletions or conflict debris remain.

The new `deploy/run_road_jeep_regression.py` launcher uses the unchanged jeep harness and cached dependencies. It writes fresh jeep regression media under `road-jeep-*` names, preserving the base jeep artifacts.

## Build and automated checks

Environment: Linux, Java 25.0.3, Maven 3.9.11, Linux LWJGL natives, cached dependencies under `/tmp/voxel-m2`, and worktree-local temporary files. Native runs use the inherited role DISPLAY and XAUTHORITY, Mesa software rendering and separate fresh synthetic offline profiles. No real account data is used. This is the native GLFW game; browser playtesting does not apply.

Executed incremental compilation, exit 0:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux test-compile > target/road-rebased-compile.txt 2>&1
```

Executed the full prebuilt suite without overlapping compilation:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" surefire:test > target/road-rebased-full-tests.txt 2>&1
```

Observed full-suite retry: exit 0; 305 tests passed, 0 failures, 0 errors, 0 skipped. Sanitized suite counts are in `road-full-test-results.json`. Expected and observed: road choice, widths, markings, upgrading, atomic rejection, save migration, multiplayer restart and prior city behavior pass; jeep controls, collision, entry/exit, respawn and persistence pass.

One initial full-suite process and the first jeep process ended with exit 143 without a completed result. They are not counted as passing. The jeep retry passed. The full-suite retry ran after the native game exited and passed.

## Playtest: roads

Executed:

```sh
python3 deploy/run_road_placement_smoke.py --display "$DISPLAY"
```

Observed exit 0. The production application received real X11 mouse and key input through GLFW callbacks. The harness targeted its exact GLFW window and waited for rendered frames before checking results. Its isolated city and home were recreated under `target/road-runtime` and `target/road-home`.

Steps, expected and observed results:

1. Open Roads: dirt and paved 2/3/4 lane choices appear.
2. Choose each road and click the first endpoint: the direction guide appears. Click the second endpoint: the road is built in the running world with the chosen type. Dirt width remains 3 cells; paved widths are 3, 5 and 7 cells, with 1, 2 and 3 dividers.
3. Choose paved 2 lanes and place over dirt: the selected section becomes asphalt.
4. Choose paved 4 lanes, click one endpoint and press Escape: Inspect resumes, road count and road spending remain unchanged. City trade can change the treasury independently.
5. Reopen Roads and press Escape before choosing: the menu closes and Inspect resumes.

All workflow assertions passed. Screenshots show the menu, guide and built paved widths. Terrain grading keeps the existing dirt-road behavior.

## Playtest: jeep regression

Executed after the road process exited:

```sh
python3 deploy/run_road_jeep_regression.py --display "$DISPLAY"
```

Observed exit 0 on retry. The unchanged `deploy/JeepPlaytest.java` harness ran in Main with a separate fresh synthetic profile and flat test world under `target/road-jeep-playtest` and `target/road-jeep-home`. Real X11 input exercised driver entry, first-person windshield view, third-person view, W driving, Ctrl boost, S reverse, A/D steering, mouse steering, rejection of exit while moving, a full-body wall stop at boost speed, stopped exit and walking after exit. All expected results were observed. Screenshots show driver seating, transparent glass and the wall stop; the decoded video end shows walking after exit.

## Fresh media

Both clips come from the production F10 recorder. `deploy/VerifyRoadVideo.java` was compiled against cached JCodec jars and run against each clip, using `target/road-smoke` as its class directory. It decoded the start, midpoint and two seconds before the end. Decoded end frames and production screenshots were visually inspected.

- Road video: H264, 125 frames, 116.2 seconds, 3342226 bytes.
- Jeep regression video: H264, 107 frames, 75.962 seconds, 3106613 bytes.
- Decoded buffers: 960 x 544. Low-frame-rate Mesa captures retain real elapsed timing.
- All nine images/videos are below the 6 MB limit. `road-media-manifest.json` records fresh sizes and SHA-256 hashes.
- Native results: `road-placement-playtest.json` and `road-jeep-playtest.json`.
- Decoder results: `road-media-validation.json` and `road-jeep-media-validation.json`.

All new media links are pending controller publication under the assigned feature branch. The developer did not stage, commit, push, merge or deploy. The controller must publish the artifacts before review.

Final preservation recheck passed after all workflows and tests. `road-source-preservation.json` records base equality and file hashes. `git diff --check` passed. Production source remained unchanged during validation; only road-owned harness, progress, reports and fresh media were written. Both native workflows, all 305 tests and media validation passed. Ready for independent review and controller publication.
