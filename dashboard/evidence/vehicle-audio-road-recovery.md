Vehicle audio review recovery

Resolved dashboard/progress.json conflict contents. All base progress entries and release metadata are retained; item IDs are unique. The index remains unmerged until the controller stages the resolution.

Current base 042677b restores direct diagonal roads. RoadRoute, RoadGeometry, CityAddresses, CityTools, RoadTypesTest, RoadWorkflowTest, RejectedRoadWorkflowSmoke, run_noncardinal_smoke.py and noncardinal historical evidence match that base without content changes. Audio lifecycle, isometric listener height and railway rendering remain present.

Removed generated .lwjgl/3.4.1+2/x64/liblwjgl.so and libopenal.so from working contents. Added /.lwjgl/ to .gitignore. The audio playtest harness now sets org.lwjgl.system.SharedLibraryExtractPath to target/lwjgl-natives. These deletions are intentionally not staged by the developer; the controller must stage them before continuing Git.

Actual checks
- JAVA_HOME=/usr/lib/jvm/jdk-21.0.5-oracle-x64 timeout 180s mvn -o -q -Dmaven.repo.local=target/maven-cache -Djava.io.tmpdir="$PWD/target/tmp" -DargLine="-Djava.io.tmpdir=$PWD/target/tmp -Dorg.lwjgl.system.SharedLibraryExtractPath=$PWD/target/lwjgl-natives" -Dtest=VehicleAudioTest,AviationTest,JeepTest,RailwayTest,IsometricCameraTest,CitySavesTest,RailwaySaveCompatibilityTest,RoadTypesTest,RoadWorkflowTest,RoadBaseCompatibilityTest test: exit 0; 53 tests, 0 failures, 0 errors, 0 skipped.
- /usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/javac -cp target/classes -d target/recovery-checks target/DiagonalRoadRecoveryCheck.java: exit 0.
- /usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java -cp target/classes:target/recovery-checks DiagonalRoadRecoveryCheck: exit 0. RoadRoute.points((150,150),(170,157)) equals the two selected endpoints exactly. No (170,150) road bend is inserted. RoadRoute.railPoints retains the cardinal bend. This CLI check tests noninteractive route integration; browser playtesting does not apply to the CLI.
- git diff --check: exit 0. Resolved progress file has no conflict markers and parses as valid JSON.
- python3 -m py_compile deploy/run_vehicle_audio_playtest.py: exit 0.
- git diff HEAD over the restored road source, tests, smoke and historical noncardinal evidence: empty after checks.

Playtest: no new native graphical run during pending Git recovery. Prior audio playtest and published media are historical submitted-head evidence, not final recovery-head evidence. After controller staging and rebase continuation, repeat the affected native audio workflow and road regression check, capture fresh sanitized media, and inspect it before reporting ready.

Handoff: controller stages progress.json, .gitignore, the .lwjgl file removals, harness cache-path update and this report, then continues the preserved Git operation. No developer staging, commit, push, merge, deploy, reset or rebase abort was performed. No owner answer is needed.
