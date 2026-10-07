# Road workflow validation

Implementation adds width-aware zone boundary snapping, chained road clicks, road route highlights, and edit/delete actions. Editing chooses dirt or paved 2/3/4 lanes and resizes the footprint. Shared crossing cells remain when deleting a route. Collinear touching extensions keep one named route; that route is the selectable section. Deleted road surfaces become dirt. Editing retains the route name. Multiplayer road actions require protocol 22.

Environment: Linux, Java 25.0.3, Maven 3.9.11, cached dependencies in `/tmp/voxel-m2`, Linux LWJGL natives. All writes stay in this feature worktree. Native checks use inherited DISPLAY and XAUTHORITY, Mesa software rendering, and a fresh synthetic offline profile under `target/road-workflow-home` and `target/road-workflow-runtime`. No real account data is used. This is a native GLFW application; browser playtesting does not apply.

Focused checks:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadWorkflowTest,RoadTypesTest,CityToolsTest,CityAddressesTest test > target/road-chain-tests.txt 2>&1
```

Observed exit 0: 21 tests, zero failures/errors. Tests exercise chained endpoints, duplicate clicks, zone intersections from four directions at all widths, clear routes, section sizing, save/load, edit/delete command encoding, occupied-cell rejection, and preservation of crossing roads. Existing road width, markings, upgrade, building-address and zone-tool checks also pass. An early run lacked the forked test JVM temporary directory setting and failed to create JUnit temp directories. It is not counted as passing. Two old assertions were updated for the requested chain/selection behavior. The dirt diagonal test respects the existing Manhattan road path.

Full suite command:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" surefire:test > target/road-workflow-full-tests.txt 2>&1
```

The broad suite ended with exit 143 before completion. It is not counted as passing. Completed reports had one obsolete `Protocol.VERSION == 21` assertion. That assertion now expects 22 for the new road section command protocol. The targeted city/road/market/protocol integration run below covers the affected behavior. See `road-workflow-full-suite-attempt.json` for the incomplete attempt.

## Playtest: road workflow

```sh
python3 deploy/run_road_workflow_smoke.py --display "$DISPLAY"
```

The harness runs Main and sends real X11 mouse/key input to its GLFW window. It waits for rendered frames before checking state. Observed final native run: exit 0. All steps below passed. Fresh media and result JSON were saved in `dashboard/evidence/`. The first game run stopped at the viewport assertion for the first chained click: world `100.0,94.0`, screen `998,536`, below the play area. The test now moves the camera before that click; production source did not change. This failed run is not counted as passing. A second run passed chain/select/widen/narrow/delete checks but failed its zone-creation assertion. The zone was enlarged from 10 x 8 to 12 x 12 blocks; the third run reported `Zones cannot overlap` because the fixture intersected the founding farm. The zone was moved to x=74..86, z=66..78 beside the deleted road. Production source stayed unchanged through these fixture corrections. Only a completed run is counted as passing. One initial harness compilation failed because a checked exception appeared inside a stream lambda. That launcher did not run the game. The lambda now uses an already-read frame.

Steps and expected results:

1. Choose dirt and each paved lane count; place two endpoints. Direction guide appears and the chosen road width/material appears in the world.
2. Continue the last four-lane road with two more clicks, including a turn. Each click builds the next section from the prior endpoint.
3. Press Escape, click the two-lane road in Inspect, and capture the highlighted section and edit/delete options.
4. Edit to four lanes, then back to two. World cells widen to 7 and narrow to 3. Capture the edited section.
5. Delete that route. The adjacent dirt road remains.
6. Create a residential zone with the game tools. Start a road outside it and aim through it. Capture the guide; click to build up to the zone boundary. No road cell covers any zone.
7. Upgrade the remaining dirt road; cancel after a first point and cancel the road menu. Road cell count and road spending remain unchanged during cancellation.
8. Stop the production F10 recorder and inspect fresh captures.

Publication is reserved to the controller. Media URLs will use the assigned feature branch and are pending controller publication. No commit, push, merge or deployment was performed.

## Affected integration checks

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadWorkflowTest,RoadTypesTest,CityToolsTest,CityAddressesTest,ProtocolCompatibilityTest,CityTest,MarketEconomyTest,CityMultiplayerTest test > target/road-workflow-integration-tests.txt 2>&1
```

First integration run: 47 tests, 46 passed, one timeout before the new road appeared. The test sent commands inside the existing 500 ms server city-command limit. It now waits 550 ms before successive commands from the same client; server limits are unchanged. Final integration run: exit 0, 47 tests passed, zero failures/errors. The protocol 22 assertion passes. The real server/client test builds a separate road, edits it from two to four lanes, then deletes it from the other client. Both clients must receive matching authoritative road state. Existing late join and restart checks still run.

## Fresh media

The F10 clip is H264, 129 frames, 119.256 seconds, 960 x 544 decoded pixels, and 3,401,917 bytes. `VerifyRoadVideo` decoded start, midpoint and two seconds before the end. The decoded end frame and selection/edit/snap screenshots were visually inspected. All images and video are below the 6 MB artifact limit. Source hashes match across native validation. The final media manifest records sizes and SHA-256 hashes.

Decoder execution used this Python orchestration, with cached JCodec dependencies in the Java classpath:

```python
from pathlib import Path
import subprocess, os
root = Path.cwd()
jars = [str(p) for p in Path('/tmp/voxel-m2').rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root/'target/classes'), str(root/'target/road-workflow-smoke'), *jars])
subprocess.run(['javac', '-cp', cp, '-d', 'target/road-workflow-smoke', 'deploy/VerifyRoadVideo.java'], check=True)
subprocess.run(['java', '-Djava.io.tmpdir='+str(root/'target/tmp'), '-cp', cp, 'VerifyRoadVideo',
    'dashboard/evidence/road-workflow-playtest.mp4', 'target/road-workflow-video-end.png',
    'dashboard/evidence/road-workflow-video-validation.json'], check=True)
```

Observed exit 0.

Final `git diff --check` passed. No unmerged index exists. Production sources matched their recorded hashes after all checks. Ready for controller publication and independent review; the broad interrupted suite is not a passing result.
