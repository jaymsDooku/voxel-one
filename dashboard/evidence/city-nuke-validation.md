Nuke effects validation — Linux native City Builder

Implementation: 96 bounded smoke puffs expand and rise, with an outward dust skirt and hot flash. Round translucent puffs render back to front after opaque city geometry. Blend state and depth writes are restored. Smoke fades during its last 2 seconds and clears after 8 seconds. Impact voxels retain their materials and fly as rotating physical chunks, with half-size increased from 0.22 to 0.42. The existing 192-body limit, radius-6 crater and persistent destruction remain.

Playtest: `python3 deploy/run_missile_smoke.py --display "$DISPLAY"` passed on the final source. Production Main, Linux X11, inherited DISPLAY/XAUTHORITY, Mesa software OpenGL, 960x720 window, isolated synthetic city/profile, no accounts or network. Browser playtesting does not apply to this native LWJGL client.

Workflow and expected/observed results:
- Delete without cheats rejected; F4 enabled cheats and Insert budget cheat worked.
- F10 recorded a falling missile; a repeated Delete while falling was rejected.
- One impact removed 148 voxels and spawned 148 chunks. The hit building was removed; the distant building stayed intact.
- Assertions confirmed 96 smoke puffs and 1..192 physical shards, plus smoke rise and shard displacement greater than 1 world unit at age 1.2 seconds. At cleanup, peak smoke rise was 20.290257 world units and peak shard displacement was 100.357834 world units. Screenshots show the flash, translucent rising smoke and colored chunks displaced outward.
- Smoke and all shards cleared after 8 seconds; zero shards at completion is the expected cleanup result.
- Save/reload preserved demolition and AIR edits. F4 disabled cheats; another Delete was rejected.
- No OpenGL errors were observed.

Media: fresh F10 H264 MP4, 14 decoded frames, 720x544, 24.861 seconds, 420643 bytes. All frames decoded; count matched metadata; first and last frames differed. Inspected blast/debris screenshots plus decoded middle and last video frames; the last frame shows risen translucent smoke and outward displaced voxels. Low frame count reflects the software-rendered test environment; no hardware performance claim is made. Artifacts await controller publication on the assigned feature branch.

Build: `mvn -q -Dmaven.repo.local=target/maven-cache -Dlwjgl.natives=natives-linux -DskipTests compile` passed.

Historical pre-rebase broad regression attempts: the first full suite encountered read-only `/tmp`. A second full suite used worktree-local Java temp storage and ended with exit 143 before completion. Neither full-suite attempt is reported as passed. Focused final test results follow below.

Diff audit: removed only the old 24-cube expanding ring, replacing it with smoke and flash. Moved missile rendering after opaque city actors for correct smoke blending. Updated the smoke harness and new artifact names; preserved historical missile media. No unrelated source deletions. Audited the proposed diff against review base 8d6fcb1958608bc90fcf3054d7fd9276918b262e. No unrelated deletions. During rebase recovery, preserved the existing planet milestone and every other progress entry, then added the nuke milestone. Controller completed the rebase. Reran the 37 focused tests and native workflow on the resulting source; replaced all nuke media with this run. No game source changes were needed after integration.

Focused final checks: `mvn -q -Dmaven.repo.local=target/maven-cache -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=CityMissilesTest,PhysicsTest,CitySavesTest,RenderingAlgorithmsTest test` passed: {'tests': 37, 'errors': 0, 'failures': 0, 'skipped': 0}. Smoke spawn, growth, rise, cleanup and outward impulses are asserted. `git diff --check` passed.

Post-rebase validation: incoming HEAD `f66e9c4c6dc4c1d5897495c7151c58e9fd089ae3`. The native harness first hit a stale budget-cheat observation. Fixed its frame signal to publish after observed state and capture finish, and added explicit motion assertions and peak counts. The full native rerun passed. Only harness, progress and evidence changed after the controller recovery.

Validated source SHA-256 (controller can compare these with the submitted head):

- `src/main/java/dev/jayms/physics/CityMissiles.java`: `c4d0943f3a8bc67fca333ba1877cb9af93ba3bf26c4f82ef0c4b2cdfe67dc7e2`
- `src/main/java/dev/jayms/Main.java`: `a1fa5ce73d9fe5dc434627e694bb52712d3d6a8baac982bcbf2fafe3f08d0276`
- `src/test/java/dev/jayms/CityMissilesTest.java`: `10b6adb37d9fea24da0fa4242f215a056ab1d6a547912e411915d7e4893e1f0b`
- `deploy/MissileSmoke.java`: `c24a35162cfa86c210bdb6e4cbcdd06f38f151456cb8749c2c12dc153ca60ca2`
- `deploy/run_missile_smoke.py`: `97d75cfe9b9fb8de53aced4ed1e8a1e5a4864183c6bb6ca79a4bfebd3f661ece`
- `deploy/VerifyMissileMedia.java`: `64df82641aaaa8d7b61e347b266c7bf77e402ceb88b589bc47ea04c971cc28cc`
