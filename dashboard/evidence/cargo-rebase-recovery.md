# Cargo vehicle rebase recovery

Result: conflict file contents resolved; controller Git continuation required. This is a recovery checkpoint, not final-head approval.

The only unmerged path was dashboard/progress.json. The resolved JSON retains both regional-scaling and cargo-vehicle task entries, all prior base items and their evidence, and the newer existing root timestamp. JSON parsing and unique item-ID checks passed. No conflict markers remain in the inspected source, cargo harness or progress file.

Checked 713 existing files under src/, dashboard/evidence/ and deploy/ against the current rebase base using git ls-tree and git hash-object. Every file matched, excluding the six expected cargo-modified paths: progress JSON, Main.java, Jeep.java, JeepModel.java, Controls.java and JeepTest.java. No staged deletion exists. The Main/Controls diff contains only vehicle input and labels; regional/district behavior remains intact.

Protocol.VERSION is 21. CitySimulation accepts format-11 magic 0x4349543B and writes it. Regional tests, format-10 fixtures and prior evidence are retained.

## Executed regression checks

```sh
mvn -q -Dmaven.repo.local=target/m2 -DargLine="-Djava.io.tmpdir=$PWD/target/test-tmp" -Dtest=RegionalPopulationTest,RegionalSaveCompatibilityTest,RegionalMultiplayerTest,JeepTest,ControlsTest test > target/cargo-rebase-test-output.txt 2>&1
git diff --check
```

Linux, assigned feature worktree, isolated test temporary directories under target/test-tmp. Maven exit 0. Diff check exit 0.

| Suite | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| RegionalPopulationTest | 9 | 0 | 0 | 0 |
| RegionalSaveCompatibilityTest | 4 | 0 | 0 | 0 |
| RegionalMultiplayerTest | 1 | 0 | 0 | 0 |
| JeepTest | 8 | 0 | 0 | 0 |
| ControlsTest | 5 | 0 | 0 | 0 |
| Total | 27 | 0 | 0 | 0 |

The save integration tests loaded exact base format-10 fixtures, upgraded and reloaded format-11 saves, retained paved lane types and one million regional residents, and rejected truncated saves. The multiplayer integration test and regional population tests passed. Vehicle/control regressions passed.

Playtest: no new native/browser run was claimed during this conflict-recovery handoff. Existing truck media and reports are preserved as pre-rebase evidence. The controller must stage resolutions and continue the rebase, then resume final-head native workflow checks and fresh captures. Browser playtesting does not apply to the native client or file/protocol integrations.

No index mutation, rebase continuation/abort/reset, commit, push, merge or deployment was performed by the developer. This report's publication is pending the controller.
