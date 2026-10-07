# Intermediate progress recovery

Resolved only dashboard/progress.json conflict contents. Kept newer HEAD metadata and pending road note. Every non-road stage-2 milestone matches exactly, including right-angle routes. JSON valid; git diff --check passed; no deleted files. RoadRoute normalization remains in simulation and shared geometry. Unmerged index awaits controller staging/continuation.

Command: `MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadTypesTest,CityToolsTest test > target/road-angle-progress-recovery-tests.txt 2>&1`. Linux, Maven 3.9.11, cached dependencies, Linux natives, worktree-local target/tmp. Expected right-angle routing/UI regressions pass. Observed exit 0; 16 tests passed; zero failures/errors/skips.

Acknowledgement commit still must replay. Reviewer requirements remain: consume city callbacks only on CITY_RESULT, add craft/ROAD interleaving regression, and deliver the matching acknowledgement in RoadWorkflowTest before expecting another chained command. Not claimed fixed at this intermediate head.

Playtest: final-head native checks and fresh media await rebase completion. Historical media is not final-head proof. No developer Git stage, commit, push or continuation performed.
