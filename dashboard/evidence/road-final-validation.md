# Final road and airport validation

Tested production head: `edd8361bde0642d71dfc7bbd4195adb3ce007d3b`, after controller rebase continuation. Publication is pending the controller. No developer commit, push, merge or deployment occurred.

Reviewer defects fixed: the airport implementation, tests, media and progress entry are preserved; 45 recorded base file hashes still match. City saves keep format 12 and magic `0x4349543C`, including airport data. RUNWAY=10 and FLIGHT=11 retain the reviewed base packet layouts. DELETE_ROAD=12 and EDIT_ROAD=13 use protocol 23. The client can reconnect to the reviewed protocol-22 aviation base, preserves aviation packets, and blocks new road actions on that server. The current server accepts only protocol 23.

Environment: Linux, Java 25, Maven 3.9.11, cached dependencies at `/tmp/voxel-m2`, Linux LWJGL natives, Mesa software rendering, inherited role DISPLAY and XAUTHORITY. All test writes are inside the assigned feature worktree. Native profiles are fresh synthetic offline cities. No real account data is used. This is a native GLFW application; browser playtesting does not apply.

## Automated checks

Executed:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadBaseCompatibilityTest,RoadWorkflowTest,RoadTypesTest,AviationTest,RegionalPopulationTest,ProtocolCompatibilityTest,CityToolsTest,CityAddressesTest,CityTest,CityMultiplayerTest,MarketEconomyTest test > target/road-final-tests.txt 2>&1
```

Observed exit 0: 70 tests passed, zero failures/errors/skips. Sanitized counts are in `road-final-tests.json`. Tests include base format-12 airport save/load/resave, literal protocol-22 runway and flight packets, new IDs, live TLS protocol-22 retry with road actions blocked, live current server road edit/delete on two clients, late join and restart. Road tests cover snapping at every width and direction, chaining and duplicate clicks, resize/delete, occupied-cell rejection and crossing-route preservation. Airport tests cover permits, runway expansion, protected cells, passenger flights and saved-flight state. Regional, city, address, tool and market regression tests pass.

The earlier broad-suite attempt was interrupted and is historical, not a final-head passing result. This final run covers the affected integration and regression suites.

## Playtest: roads

Executed:

```sh
python3 deploy/run_road_final_smoke.py --display "$DISPLAY"
```

Observed exit 0. All expected road steps passed. The launcher runs the unchanged `RoadWorkflowSmoke` harness in production Main. Real X11 clicks exercise all road types, chained straight and turning sections, Inspect selection, yellow section highlighting, widening to four lanes, narrowing to two lanes, deletion while preserving the adjacent road, and a road guide aimed through a valid residential zone. The road must stop before its footprint covers the zone. Regression checks upgrade dirt and cancel both an endpoint and the menu without changing road cell count or road spending. Fresh F10 video and screenshots use `road-final-*` names. Profiles are `target/road-final-home` and `target/road-final-runtime`.

## Playtest: airport regression

Executed after the road window closed:

```sh
python3 deploy/run_road_airport_final_smoke.py --display "$DISPLAY"
```

Observed exit 0. All expected airport steps passed. The launcher runs the preserved `AviationSmoke` harness in production Main. It exercises airport permits, runway expansion, single-airport flight rejection, passenger boarding, flight and arrival, exchange-overlap rejection, compact airport menus and regional UI regression. It uses a separate fresh synthetic flat city under `target/road-airport-final-home` and `target/road-airport-final-runtime`. Captures use `road-final-airport-*` and `road-airport-final-*` names, preserving all base airport artifacts.

Fresh media validation and final source/preservation checks passed. Old road media remains historical evidence and is not used as proof for this head.

Road video validation: `VerifyRoadVideo` compiled with cached JCodec jars and decoded the fresh clip at start, midpoint and two seconds before its end. Observed exit 0: H264, 130 frames, 110.374 seconds, 960 x 544 decoded pixels, 3,424,419 bytes. The selection/snap screenshots and decoded end frame were visually inspected. They show the selected cells and actions, the snapped guide at the zone edge, and the built road outside that zone.

Airport video: H264, 40 frames, 34.39 seconds, 960 x 544 decoded pixels, 1,543,489 bytes. `VerifyAviationMedia` decoded every frame, checked frame count against metadata, and confirmed first/last decoded images differ. `VerifyRoadVideo` also decoded start, midpoint and two seconds before the end. Both executions exited 0. Fresh airport expansion, flight, arrival and district screenshots and the decoded video end frame were visually inspected. The clip shows flight; the stills show arrival and the district regression. All new PNG/MP4 artifacts are below 6 MB each.

Media checks used these executed Java invocations through Python, with cached JCodec jars in the assembled classpath:

```python
from pathlib import Path
import subprocess, os
root = Path.cwd()
jars = [str(p) for p in Path('/tmp/voxel-m2').rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root/'target/classes'), str(root/'target/road-final-smoke'), *jars])
subprocess.run(['javac', '-cp', cp, '-d', 'target/road-final-smoke', 'deploy/VerifyRoadVideo.java'], check=True)
subprocess.run(['java', '-Xmx512m', '-Djava.io.tmpdir='+str(root/'target/tmp'), '-cp', cp, 'VerifyRoadVideo',
    'dashboard/evidence/road-final-playtest.mp4', 'target/road-final-video-end.png',
    'dashboard/evidence/road-final-video-validation.json'], check=True)
subprocess.run(['javac', '-cp', cp, '-d', 'target/road-final-smoke', 'deploy/VerifyAviationMedia.java'], check=True)
subprocess.run(['java', '-Xmx512m', '-Djava.io.tmpdir='+str(root/'target/tmp'), '-cp', cp, 'VerifyAviationMedia',
    'dashboard/evidence/road-airport-final-playtest.mp4',
    'dashboard/evidence/road-final-airport-video-validation.json'], check=True)
subprocess.run(['java', '-Xmx512m', '-Djava.io.tmpdir='+str(root/'target/tmp'), '-cp', cp, 'VerifyRoadVideo',
    'dashboard/evidence/road-airport-final-playtest.mp4', 'target/road-final-airport-video-end.png',
    'dashboard/evidence/road-final-airport-video-samples.json'], check=True)
```

Final checks: `git diff --check` passed. No unmerged index remains. All production source hashes match those recorded before execution. All 45 recorded base airport/source/media hashes still match, and the airport progress entry matches the reviewed base. `road-final-source-preservation.json` records the source hashes. `road-final-media-manifest.json` records fresh artifact sizes and hashes. Production code was not changed during this validation turn; only new launchers, progress and evidence were written.

All fresh evidence URLs are pending controller publication under the assigned feature branch. Ready for independent review after controller publication. No owner answer is needed.
