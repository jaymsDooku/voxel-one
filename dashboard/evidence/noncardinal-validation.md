# Straight roads at non-cardinal headings

Implementation: road preview, cell raster, placement and saved street routes now connect grid-snapped endpoints directly. Dirt and all paved lane choices use the same Bresenham center line. Existing widths, cost, footprint collision checks, placement cap 768 and edit/city cap 8192 remain. Rail preview and placement retain their cardinal bends. Existing saved explicit bends stay explicit.

Environment: Linux, Java 25.0.3, Maven 3.9.11, cached dependencies at `/tmp/voxel-m2`, Linux LWJGL natives. Native checks use inherited role DISPLAY and XAUTHORITY, Mesa software rendering and fresh synthetic profiles inside this feature worktree. No real account data is used. Voxel One is a native GLFW app; browser playtesting does not apply.

Executed commands:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadTypesTest,RoadWorkflowTest,MixedRoadOwnershipTest,CityToolsTest,RailwayTest,CityMultiplayerTest,CityAddressesTest test > target/noncardinal-tests.txt 2>&1
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadTypesTest test > target/noncardinal-travel-tests.txt 2>&1
python3 deploy/run_noncardinal_smoke.py --display "$DISPLAY" --scenario road
```

Automated expected results: shallow, steep, reverse and negative grid routes follow a direct line; all four types support travel between endpoints; diagonal dirt saves and reloads with direct address data, then widens without a bend. Zone-edge snapping keeps every generated footprint cell outside the zone. Same-cell and collision rejection are atomic. Cardinal widths, ownership/crossing preservation, long-chain edits, rail bends and multiplayer road commands retain behavior.

Playtest: production Main with real X11 input. Build a cardinal road with zero synthetic budget: expect rejection with anchor retained. Fund the fixture and retry: expect no gap and successful chaining. Select, resize and delete that cardinal road. Build from (40,180) to (60,200): expect a direct diagonal center at (50,190) with neither cardinal leg. Select it, widen to four lanes, then delete it: expect heading retained and exact section removed. Build all four types from (40,base) to (60,base+7), with base 210, 220, 230, 240: expect shallow direct routes with no cardinal leg at (55,base). Capture fresh PNGs and the production F10 MP4. Profiles: `target/noncardinal-road-home` and `target/noncardinal-road-runtime`.

Earlier runs exposed old bend assertions and a dirt address bend; these were corrected. A native assertion sampled a valid four-lane shoulder and was corrected to check farther along the route. These earlier runs are not passing final evidence.

Publication is reserved to the controller. All new media URLs are pending controller publication on the assigned feature branch. No developer commit, push, merge or deployment occurred.

Observed final results: the seven affected suites passed, 41 tests total, zero failures, errors or skips. The final RoadTypesTest run includes the travel-path assertions and passed all 9 tests. The first travel-only launch overlapped a Maven cleanup and could not open its Surefire boot JAR; after the first Maven process ended, the same command was rerun alone and exited 0. No failed launch is claimed as passed.

Playtest observed: `python3 deploy/run_noncardinal_smoke.py --display "$DISPLAY" --scenario road` exited 0. Every listed UI assertion passed. Fresh diagonal, edited-diagonal and shallow-road images were visually inspected. They show direct grid routes and the new straight-section tool hint. No cardinal bend is inserted. The native capture runs at about 1 FPS under software rendering; F10 records real frames.

Media command: `python3 deploy/verify_noncardinal_media.py --scenario road` exited 0. Both Java decoders passed: H264, 960 x 544, 173 frames, 126.093 seconds, 4,245,368 bytes. Every frame decoded; sampled start, middle and near-end frames decoded. The clip is below the 6 MB limit.

Final `git diff --check` passed. `git ls-files -u` was empty. All production source hashes match those recorded before the final native run finished; the same production classes were used for that run. Sanitized counts, source hashes and media hashes are saved with this report. Ready for independent review after controller publication.
