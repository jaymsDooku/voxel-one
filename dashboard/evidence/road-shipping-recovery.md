# Road/shipping rebase recovery

This is an intermediate rebase resolution. It is not a final-head submission.

Resolved Protocol.java by retaining protocol 23 and describing both coastal ports and road section actions. Resolved MultiplayerClient.java by retaining both independent serverProtocol < 23 gates. Retained all base progress entries and added only the road item. No staging, commit, rebase continuation, push, merge or deployment was done by the developer.

Base 60628241773b1985572b5f1b723a09ab12f41672 preservation: 60 shipping/terrain source, test, harness and media files match byte for byte. The shipping progress entry matches. Terrain.CURRENT_VERSION remains 3. SpecialBuildings.PORT remains 24. Shipping placement and rendering remain in the base source. Road IDs remain 12/13; aviation IDs remain 10/11.

Executed on Linux with Java 25, Maven 3.9.11, cached /tmp/voxel-m2 dependencies and Linux natives:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn --batch-mode -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" verify > target/road-shipping-recovery-verify.txt 2>&1
```

The full verification attempt found a stale protocol test: it classified protocol 22 as incompatible despite the supported aviation reconnect. Removed 22 from that rejection-only list; dedicated protocol-22 retry tests remain. The first verify attempt is not a passing run. The broad run was stopped with Ctrl-C (exit 130) after the stale assertion was identified; it is incomplete and not passed. Corrected focused verification is running with this command:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn --batch-mode -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=ProtocolCompatibilityTest,ShippingTest,ShippingProtocolCompatibilityTest,ShippingAviationCompatibilityTest,RoadBaseCompatibilityTest,RoadWorkflowTest,RoadTypesTest,TerrainTest,CityToolsTest verify > target/road-shipping-recovery-focused.txt 2>&1
```

Focused verify exited 0: 34 tests passed, zero failures/errors/skips, and Maven packaging completed. The TerrainTest pattern matched no class; no terrain test is claimed from that pattern. The actual terrain migration method is checked separately below.

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn --batch-mode -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" "-Dtest=TerrainGenerationTest#saveVersionAndLegacyMigration" test > target/road-shipping-recovery-terrain.txt 2>&1
```

Terrain migration test exited 0: one test passed. A new generator-3 synthetic save reopened successfully; generator-1 migration stayed unchanged; generator 99 was rejected. Total: 35 tests passed across the focused verify and separate terrain test. Conflict marker scan and git diff --check passed. Unmerged index entries intentionally remain for the controller to stage.

Playtest: not run on this intermediate rebase state. The controller must continue the preserved rebase and resume the developer for final-head road, shipping and airport native workflow checks and fresh media. Historical clips are preserved as base artifacts and are not new validation proof.

Later replayed ownership changes must retain format 13 and protocol 24, plus terrain generator 3 and coastal ports. The base ShippingAviationCompatibilityTest initial handshake assertion currently expects protocol 23; update it to the final protocol version when the ownership commit is replayed. Final-head mvn verify and platform CI must pass before review resubmission.

## Second recovery: media/progress commit

Only dashboard/progress.json conflicted. Retained all master top-level fields and all non-road milestones exactly. Replaced only the road milestone from the replayed commit, with status in_progress and a note that final-head testing is pending. The earlier media links are historical. Python JSON parsing, unique-ID checks and exact non-road/top-level equality passed. All 60 preserved shipping/terrain file hashes still match. git diff --name-only HEAD -- src/main/java src/test/java was empty; no source or test changed during this recovery step. The prior 35 executed checks are unchanged-source evidence, not a fresh rerun. git diff --check passed.

Playtest: deferred until controller Git continuation completes. No new native run or fresh media is claimed for this intermediate metadata-only step. The controller must stage the resolved JSON and continue the preserved rebase. No developer staging, commit, push, merge or deployment occurred.
