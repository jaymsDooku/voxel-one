# Right-angle and long-road recovery

Resolved working conflicts in CitySimulation.java and dashboard/progress.json. Retained RoadRoute normalization and shared geometry, long-road edit-only cap 8192, placement cap 768 and global city cap 8192. All non-road stage-2 progress entries retained exactly. JSON and git diff --check passed.

Command: `MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=MixedRoadOwnershipTest,RoadTypesTest,CityToolsTest test > target/road-angle-long-recovery-tests.txt 2>&1`. Linux, Maven 3.9.11, cached dependencies, Linux natives, target/tmp inside assigned worktree. Expected long-chain resize/save/delete, cap/occupancy rejection, crossing survivor, right-angle routing and UI checks pass. Observed exit 0; 24 tests passed, zero failures/errors/skips.

Reviewer requirements still apply after acknowledgement commit replays: only CITY_RESULT consumes city callbacks; add CRAFT_RESULT/ROAD interleaving regression; acknowledge matching ROAD in RoadWorkflowTest before expecting next chained command. Not claimed fixed at this intermediate step.

Playtest: final-head native checks and fresh media await rebase completion. Historical media is not final-head proof. Unmerged index awaits controller staging/continuation. No developer stage, commit, push, abort/reset or rebase continuation.
