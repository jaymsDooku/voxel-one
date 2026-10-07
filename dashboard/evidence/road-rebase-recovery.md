# Road and airport rebase recovery

This is a rebase handoff checkpoint, not a ready-for-review receipt.

Resolved content conflicts in `dashboard/progress.json`, `Protocol.java` and `CityCommand.java`. The airport feature, tests, media and progress entry from the current rebase base remain. `road-rebase-base-preservation.json` records byte equality for 45 airport/source/media files and preservation of the airport progress entry.

The reviewed base keeps RUNWAY=10 and FLIGHT=11. DELETE_ROAD=12 and EDIT_ROAD=13 are new IDs. Protocol is 23. The client blocks new road commands on protocol 22 and retries the reviewed aviation base protocol 22 after a handshake mismatch. The server accepts only its current protocol during handshake. Save format remains 12, with magic `0x4349543C`; airport state is read and written with that format.

Recovery checks run on Linux, Java 25, Maven 3.9.11, cached dependencies at `/tmp/voxel-m2`, Linux LWJGL natives, and worktree-local temporary synthetic data:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadBaseCompatibilityTest,RoadWorkflowTest,RoadTypesTest,AviationTest,RegionalPopulationTest,ProtocolCompatibilityTest test > target/road-rebase-compat-tests.txt 2>&1
```

Observed final run: exit 0; 34 tests passed, zero failures/errors. `git diff --check` passed and source conflict markers are gone. The index remains unmerged until the controller stages the resolved contents. The checks include literal protocol-22 aviation packets, format-12 airport save/load/resave, a synthetic protocol-22 TLS peer that receives runway/flight commands but no new road actions, road geometry and actions, airport behavior, and regional population behavior.

Playtest: native final-head checks and fresh media are pending controller rebase continuation. This recovery stage does not claim a new native pass. Prior road recordings are retained as historical evidence from the earlier implementation; they do not validate the resolved head. After the controller stages and continues the rebase, rerun `python3 deploy/run_road_workflow_smoke.py --display "$DISPLAY"` and the preserved `deploy/run_aviation_smoke.py` workflow with the inherited role display and isolated synthetic profiles. Save fresh media before returning ready.

The developer did not stage, continue/abort the rebase, reset, commit, push, merge or deploy. The controller owns staging and rebase continuation. Resolved file contents remain in the assigned worktree.

Earlier compatibility runs: the first live-peer check exposed missing protocol-22 retry support. After adding that retry, the fixture closed before queued commands reached the peer and raised EOFException. The fixture now waits for a peer acknowledgement. Neither failed run is counted as passing.
