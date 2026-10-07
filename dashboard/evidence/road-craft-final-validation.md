# Final road validation after right-angle rebase

Reviewed actual implementation against base bb65741. No base source/docs files removed; 188 unchanged files match exactly. RoadRoute, terrain, industrial progression, energy economy and cheat docs are unchanged. Every base progress milestone matched exactly before the road milestone update. RoadGeometry retains X-then-Z routes for placement, snapping and edits. Long existing-road edits retain cap 8192; new placement cap remains 768. Protocol 24, road IDs 12/13 and format-13 ownership remain; old saves and airport/port data remain supported.

CRAFT_RESULT updates only notice. CITY_RESULT alone consumes city result callbacks; disconnect drains unconfirmed requests. RoadWorkflowTest supplies the exact matching acknowledgement before the next chained command. Real-server regression sends craft(-1, spawn) immediately before equal-endpoint ROAD and checks the ROAD endpoint rejection then successful retry. No response-format or protocol change was needed.

Environment: Linux, inherited assigned X11 DISPLAY and XAUTHORITY, Mesa software OpenGL, isolated fresh synthetic profiles under target/. No Xvfb or other display. Maven 3.9.11, cached /tmp/voxel-m2, Linux natives, worktree-local target/tmp. Browser playtesting does not apply to the native GLFW game.

Commands, expected and observed:

- `MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadWorkflowTest,MixedRoadOwnershipTest,RoadBaseCompatibilityTest,CityToolsTest,CityMultiplayerTest,ProtocolCompatibilityTest,RoadTypesTest,OperatingEnergyTest,IndustrialProgressionTest,TerrainGenerationTest,ShippingAviationCompatibilityTest,AviationSaveCompatibilityTest test > target/road-craft-final-tests.txt 2>&1`: exit 0. All 64 tests across 12 suites passed; zero failures/errors/skips. Covers callback interleaving/retry, matching and canceled acknowledgements, right-angle routes/zone snapping, mixed-width/long road ownership and caps, multiplayer edit/delete, protocol compatibility, energy/industrial progression, terrain and reviewed base saves/aviation.
- `MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DskipTests verify > target/road-craft-final-package.txt 2>&1`: exit 0. Packaging verification passed; this packaging command skips tests.
- `python3 deploy/run_road_craft_final_smoke.py --display "$DISPLAY" --scenario road > target/road-craft-final-native.txt 2>&1`: exit 0. Playtest: running production Main with X11 mouse/key input places dirt and paved 2/3/4-lane roads, chains endpoints, selects/highlights, widens/narrows, deletes while retaining neighbor, snaps to zone edge without overlap, upgrades dirt and cancels without changing roads/spending. All expected assertions passed. Fresh snap/selection images inspected.
- `python3 deploy/run_road_craft_retry_smoke.py --display "$DISPLAY" --scenario road > target/road-craft-retry-native.txt 2>&1`: exit 0. Playtest: synthetic budget set to 0 rejects (40,160) to (60,160); fixture funds 100000 then retry to (80,160) retains start (40,160), leaving no gap; next (100,160) advances after success. Selection/edit/delete passes. Diagonal endpoint choice (40,180) to (60,200) creates X-then-Z legs at (50,180) and (60,190), with no road at diagonal shortcut (50,190). Fresh right-angle image inspected. Native receipt confirms all checks passed.
- `python3 deploy/verify_road_craft_final_media.py --scenario road > target/road-craft-final-media.txt 2>&1` and `python3 deploy/verify_road_craft_retry_media.py --scenario road > target/road-craft-retry-media.txt 2>&1`: exit 0 each. Samples at start/middle/near-end and every frame decoded. Both fresh F10 clips below 6 MB. Decoded end frames inspected.
- `git diff --check`: passed. No unmerged paths. No developer Git stage, commit, push, merge or deployment. Remote platform CI and artifact publication remain controller work; independent review required.

Only the checks above are final-head results. Older reports/media remain historical.

Validated source head: f871a7bcc523b231ce75b9e866908ca979f633c1

Fresh video counts: [["road-craft-final-road", 129, 3419691], ["road-craft-retry-road", 74, 1927336]]
