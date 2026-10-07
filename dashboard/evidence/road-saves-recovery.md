Recovery checks

Resolved only the progress timestamp conflict. JSON parses; unrelated progress entries match index stage 2. CitySaves, SavesMenu, Main integration, CitySavesTest, save smoke harnesses and historical media remain present. CRAFT_RESULT does not consume city callbacks; chain test supplies the matching acknowledgement.

Command: `MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadWorkflowTest,CityMultiplayerTest,CitySavesTest,SavesMenuTest test`

Exit 0. Executed suites: [{"name": "dev.jayms.RoadWorkflowTest", "tests": "5", "failures": "0", "errors": "0", "skipped": "0"}, {"name": "dev.jayms.CityMultiplayerTest", "tests": "1", "failures": "0", "errors": "0", "skipped": "0"}, {"name": "dev.jayms.CitySavesTest", "tests": "2", "failures": "0", "errors": "0", "skipped": "0"}]. No SavesMenuTest class exists; no such suite is claimed. Linux; synthetic temporary profiles. `git diff --check` passed.

Playtest: deferred until controller stages and continues the rebase. Current checks are intermediate recovery checks, not final-head validation. Save/copy/new/load and road native workflows need fresh final-head execution and media.
