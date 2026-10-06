# Cargo and aviation rebase recovery

Recovery base: 1ecbf565c449d7b5c96a621310c1a012b80ae1aa. Checks below cover resolved working files before controller continuation, not a final submitted head.

## Correction to the earlier validation report

The earlier cargo-final-validation.md statement that the diff contained no file deletion was scoped to an older base. It did not establish preservation against aviation base 1ecbf565. Head 5c33d5cc removed aviation files against that newer base and downgraded save/protocol compatibility. The blanket preservation claim is retracted. Its tests and media remain historical evidence for that earlier checkout.

cargo-final-validation.md is absent at this replay step. After the controller replays the remaining commit, replace its blanket claim with this correction and record fresh final-head checks.

## Resolved files and preservation

Resolved dashboard/progress.json by retaining base entries and evidence, adding absent cargo entries, and updating the cargo recovery note. The index remains unmerged for the controller to stage and continue. No index mutation, commit, push, merge or deployment was performed.

Compared 734 base blobs under src/, deploy/ and dashboard/evidence/ against local file hashes, excluding intended cargo-modified paths. All 734 matched. No staged deletion exists. Protocol.VERSION remains 22. CitySimulation reads and writes format 12 (0x4349543C). Aviation code, UI, tests, fixtures and evidence remain intact. Main and Controls diffs only add cargo input and labels.

## Executed checks

Environment: Linux, local Maven repository target/m2, isolated test temporary directory target/test-tmp.

Command:
```sh
mvn -q -Dmaven.repo.local=target/m2 -DargLine="-Djava.io.tmpdir=$PWD/target/test-tmp" -Dtest=AviationTest,RegionalPopulationTest,RegionalSaveCompatibilityTest,RegionalMultiplayerTest,JeepTest,ControlsTest test > target/cargo-aviation-recovery-test-output.txt 2>&1
```
Observed: exit 0; 38 tests, zero failures, errors or skips. AviationTest 10; RegionalPopulationTest 10; RegionalSaveCompatibilityTest 4; RegionalMultiplayerTest 1; JeepTest 8; ControlsTest 5.

CLI integration command:
```sh
java --class-path target/classes target/AviationSaveHeaderCheck.java
```
Expected: protocol 22 and a complete format-12 CityFrame save load and roundtrip. Observed: exit 0, `PASS: protocol 22; complete format-12 save loads and roundtrips`. An initial source-launch attempt used the wrong GameConfig import and failed compilation; the corrected import dev.jayms.net.city.GameConfig passed.

`git diff --check` passed. Browser testing does not apply to this native Java application or the save-header CLI check.

Playtest: No new native playtest or media capture was run during this pending rebase. Prior cargo playtests cover earlier heads only. After controller continuation, use inherited DISPLAY/XAUTHORITY, Mesa and isolated synthetic profiles to run cargo and aviation workflows. Select, drive, exit, re-enter and reload cargo bodies; check moving-exit and driving-switch rejection and Jeep regression. Exercise aviation boarding, flight and save restoration. Expected: both features and compatibility work together. Observed for the continued head: pending; no pass claimed.

## Second replay handoff

Resolved checkpoint and progress JSON contents by retaining the aviation recovery state and unioning absent milestone IDs. No duplicate IDs remain. cargo-final-validation.md is now replayed and corrected directly: its ready claim is historical, and its blanket deletion claim is retracted against the aviation base. The native fixture replay only closes the old test world and expands the synthetic yard. Rechecked all 734 aviation-base files: unchanged. Protocol 22 and format-12 reads/writes remain present. JSON parses, conflict markers are absent, and git diff --check passes. The 38-test suite and save CLI results above were executed before this second replay; no new native pass is claimed. Final-head cargo and aviation playtests/media await controller continuation.
