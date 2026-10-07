# Final replay conflict recovery

Resolved only dashboard/progress.json conflicts. Retained HEAD top-level metadata, road note and offline cheat milestone. Every non-road stage-2 entry matches exactly. Incoming historical readiness claim was not retained. No deleted files. JSON validation and git diff --check passed. Working contents resolved; controller must stage and continue.

Command: `MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn --batch-mode -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=BusinessCatalogTest,RegionalSaveCompatibilityTest,IndustrialProgressionTest,OperatingEnergyTest,MixedRoadOwnershipTest test > target/road-final-replay-recovery-tests.txt 2>&1`

Environment: Linux, Maven 3.9.11, cached dependencies, Linux natives and worktree-local temporary directory. Expected: business catalog, regional save compatibility, industrial progression, energy costs and mixed-width road ownership pass. Observed: exit 0; 34 tests passed; zero failures, errors or skips.

Playtest: native final-head workflow checks and new media await completed rebase. Earlier artifacts remain historical. No Git staging, commit, push or continuation performed.
