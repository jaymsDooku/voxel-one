# Road selection and paved lanes

Implementation: dirt roads plus paved roads with 2, 3 and 4 lanes. Click **Roads**, choose a road, click the first endpoint, use the direction guide, then click the second endpoint. Selecting another toolbar tool or pressing Escape cancels the draft. Reopen Roads to change the road choice. Paved roads use asphalt and painted dividers. Types survive saves, restart and shared city snapshots. Old city saves load roads as dirt. Network protocol is now 20; city save/frame format is 10.

Environment: Linux, Java 25, Maven 3.9.11, Linux LWJGL natives. Tests use worktree-local temporary files. Native playtesting uses the inherited role X11 display and XAUTHORITY, Mesa software rendering, a fresh synthetic offline city and a worktree-local synthetic home. No real account data is used.

## Automated checks

Executed command:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadTypesTest,CityToolsTest,CityTest,ProtocolCompatibilityTest,BusinessCatalogTest,MarketEconomyTest,CityMultiplayerTest,SurvivalTest,LightingTest test > target/road-final-tests.txt 2>&1
```

Observed: exit 0; 59 tests passed, 0 failures, 0 errors. Expected and observed:

- Road selection consumes menu clicks and sends the selected type into placement.
- Dirt width stays 3 cells. Paved roads have 2, 3 and 4 lanes, with total widths of 3, 5 and 7 cells and 1, 2 and 3 painted dividers. Horizontal and vertical surfaces use the correct divider orientation. Diagonal roads follow the endpoints.
- New cells and dirt upgrades charge $4 per changed cell. Repeating the same road type charges $0. Invalid road types, duplicate endpoints and occupied cells leave road state and the treasury unchanged.
- Save/load and simulation restart preserve road types. Format 9 frames still load as dirt. Historical city saves preserve buildings, ownership and market state during migration to format 10.
- Two clients receive equal paved road metadata and divider edits. Server restart preserves the paved road. Existing zoning, snapping, survival and lighting checks pass.

Earlier runs exposed the read-only default `/tmp`, old save/protocol assertions and concurrent compiler output changes. Temp files now stay in the worktree; assertions reflect the new format. The final focused run above passed. The final full suite below also passed.

## Native Playtest

Command:

```sh
python3 deploy/run_road_placement_smoke.py --display "$DISPLAY"
```

The harness runs `Main`, sends X11 input to the exact GLFW X11 window, and waits for rendered frames before checking results. Camera setup uses the production observer; road choice and placement use real mouse input and GLFW callbacks. Images come from the production framebuffer. Video comes from the engine F10 recorder. The driver and launcher are saved in `deploy/RoadPlacementSmoke.java` and `deploy/run_road_placement_smoke.py`.

Steps and expected results:

1. Open Roads. Expect dirt and paved 2/3/4 lane choices.
2. Select each choice. Click endpoints on clear land. Expect the existing direction guide after the first click, a new road after the second click, distinct widths, and stored type matching the choice.
3. Select paved 2 lanes and place over the dirt section. Expect an asphalt upgrade.
4. Select paved 4 lanes, click only the first endpoint, then press Escape. Expect Inspect mode, unchanged roads and unchanged road spending. Normal city trade can change the treasury independently, so this native check compares road spending; the isolated unit check also verifies treasury rollback.
5. Reopen Roads and press Escape before choosing. Expect the menu to close and Inspect mode to resume.

The observed native results and media publication status are recorded below. Earlier harness attempts corrected window targeting, frame timing, camera framing and the cancellation spending check. No failed attempt is labeled passed.

Artifacts are generated from this implementation. Publication is reserved to the controller. The intended URLs use the assigned feature branch under `https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-30366437373533632d613238632d343963612d383265322d346239646166376638393338/dashboard/evidence/`.

Observed native result: exit 0. All seven workflow checks passed. Road choice opened the direction guide; dirt and paved 2/3/4 lane placements changed the running world with the expected widths; dirt upgraded to asphalt; endpoint cancellation left road count and road spending unchanged; menu cancellation returned to Inspect. The capture shows all paved widths with 1, 2 and 3 dividers. Terrain grading retains the existing dirt-road behavior.

Media validation: `deploy/VerifyRoadVideo.java` was compiled with cached JCodec jars and executed against `dashboard/evidence/road-placement-playtest.mp4`. It decoded the start, midpoint and two seconds before the end. Observed H264, 126 frames, 108.908 seconds, 3,437,776 bytes; decoded buffer size 960 x 544. The decoded final frame and production screenshots were visually inspected. The low-frame-rate Mesa run preserves real capture timing.

- `road-menu.png`: production Roads menu.
- `road-guide.png`: production placement direction guide with paved roads visible.
- `road-surfaces.png`: production paved road widths and lane markings after placement and upgrade.
- `road-placement-playtest.mp4`: continuous production F10 recording of choices, guide, placements, upgrade and cancellation.
- `road-placement-playtest.json`: sanitized native workflow result.
- `road-media-validation.json`: actual decoder metadata and validation result.

All HTTPS media links are **pending controller publication**. No commit, push, PR, merge or deployment was performed by the developer.

## Final full-suite result

Executed after final source compilation:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" surefire:test > target/road-full-final-tests.txt 2>&1
```

Observed: exit 0; 299 tests passed, 0 failures, 0 errors, 0 skipped. `road-full-test-results.json` contains sanitized counts per suite. `git diff --check` also passed.
