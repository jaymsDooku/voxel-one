# Long chained road edit validation

Existing road edits now use the bounded 8192-cell ownership/city capacity. New placements still use the 768-cell cap. Global road capacity, occupancy, zone/building checks and budget checks stay intact. No save or protocol change. Only CitySimulation's edit-specific cap changed in production.

Environment: Linux, inherited assigned DISPLAY and XAUTHORITY, Mesa software OpenGL, isolated synthetic profiles in target/. Maven 3.9.11, /tmp/voxel-m2 cached dependencies, Linux natives and target/tmp. Browser playtesting does not apply to this native GLFW game.

Commands:

- `MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn --batch-mode -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=MixedRoadOwnershipTest,RoadWorkflowTest,RoadBaseCompatibilityTest,CityMultiplayerTest test > target/road-long-tests.txt 2>&1`: initial fixture run exit 1. Widening exhausted synthetic funds; not counted passed.
- Same command redirected to `target/road-long-tests-rerun.txt`: exit 0 after fixture budget 100000 isolates geometry from spending rejection.
- Same Maven options with `-Dtest=MixedRoadOwnershipTest test > target/road-long-capacity-tests.txt 2>&1`: exit 0 after adding full-city capacity edge test. Across affected suites: 16 tests passed, zero failures, errors or skips.
- Expected/observed: eight 20-block four-lane placements merge to 1127 cells; edit to three lanes gives 805 cells; widening restores 1127; save/reload exact; deletion succeeds. Oversized direct placement rejects, occupied-player long edit rejects, and full 8192-cell city widening rejects. Rejections retain frame and world edits. Existing mixed-width survivor and multiplayer checks pass.
- `python3 deploy/run_road_long_smoke.py --display "$DISPLAY" --scenario road > target/road-long-native.txt 2>&1`: old low-budget capture stopped with exit 130; not passed.
- `python3 deploy/run_road_long_smoke.py --display "$DISPLAY" --scenario road > target/road-long-native-rerun.txt 2>&1`: production Main exit 0, all assertions passed. Wrapper exit 1 solely because raw F10 clip was 6712702 bytes, above 6 MB.
- Playtest: preserved production Main mouse workflow covers snapping, chaining, highlight, resize, delete, upgrade and cancellation. Added actual eight-section chain, long-road selection, narrowing, widening and deletion. Synthetic treasury is seeded to 100000 before the long-chain case. Observed: all running-game assertions passed, including exactly 1127 to 805 to 1127 cells and deletion. Fresh highlight image inspected.
- `python3 -m py_compile deploy/run_road_long_smoke.py deploy/verify_road_long_media.py`: passed.
- `git diff --check`: passed. No Git publication or commit performed.

Media: compiled `deploy/CompressLongRoadVideo.java` using cached /tmp/voxel-m2 jars; executed `java -Xmx512m -cp <cached classpath> CompressLongRoadVideo target/road-long-final-road-home/.voxel-one/recordings/Voxel-One_2026-10-07_02-32-41_6382434730952816154.mp4 dashboard/evidence/road-long-final-road-playtest.mp4`. Exit 0; resized to 720 x 408 without changing frame timestamps or scene order. All 220 frames retained. `python3 deploy/verify_road_long_media.py --scenario road > target/road-long-media.txt 2>&1`: exit 0, sampled start/middle/near-end and decoded every frame. Fresh decoded end frame and highlight image inspected. Artifacts pending controller publication.
