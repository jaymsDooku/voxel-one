# Road and industrial rebase recovery

Intermediate resolution only. CityMultiplayerTest retains the replayed road build/edit/delete server checks and the current industrial base's 35-firm expectations. The old 17-firm expectation was removed. Progress retains all master entries and top-level fields; only the road item was added from the replayed commit, with in_progress and an intermediate/historical-media note.

Preservation checks passed: 90 industrial/energy/cheat/docs files match the current rebase base byte for byte. No files are deleted. Main, industrial progression/logistics, energy costs, cheat controls, tests, documentation and progress records remain. All base progress entries are retained. Road ownership/protocol24 commits still need replaying; this state currently uses protocol23.

Executed on Linux with Java25, Maven3.9.11, cached /tmp/voxel-m2 dependencies and Linux natives:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn --batch-mode -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=CityMultiplayerTest,IndustrialProgressionTest,OperatingEnergyTest,RoadWorkflowTest,RoadBaseCompatibilityTest,ProtocolCompatibilityTest test > target/road-industrial-recovery-tests.txt 2>&1
```

Observed exit0: 25 tests passed, zero failures/errors/skips. Real synthetic TLS multiplayer commands verify road build, resize and deletion reach both clients and the 35-firm base survives. Industrial progression, operating energy, road workflow and legacy airport/protocol compatibility tests pass. Reports contain only sanitized suite counts. JSON parses; conflict markers were removed; git diff --check passed. The unmerged index intentionally remains for controller staging.

Playtest: native UI and fresh media are deferred until the controller finishes the preserved rebase. No new native run or media is claimed for this intermediate state. Historical clips are retained. After Git continuation, verify the final source retains industrial, energy, cheat, shipping and airport features, then run final-head integration/native checks and capture new evidence. No developer staging, rebase continuation, commit, push, merge or deployment occurred.
