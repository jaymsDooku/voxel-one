Nuke effects validation — Linux native City Builder

Implementation: 96 bounded smoke puffs expand and rise, with an outward dust skirt and hot flash. Round translucent puffs render back to front after opaque city geometry. Blend state and depth writes are restored. Smoke fades during its last 2 seconds and clears after 8 seconds. Impact voxels retain their materials and fly as rotating physical chunks, with half-size increased from 0.22 to 0.42. The existing 192-body limit, radius-6 crater and persistent destruction remain.

Playtest: `python3 deploy/run_missile_smoke.py --display "$DISPLAY"` passed on the final source. Production Main, Linux X11, inherited DISPLAY/XAUTHORITY, Mesa software OpenGL, 960x720 window, isolated synthetic city/profile, no accounts or network. Browser playtesting does not apply to this native LWJGL client.

Workflow and expected/observed results:
- Delete without cheats rejected; F4 enabled cheats and Insert budget cheat worked.
- F10 recorded a falling missile; a repeated Delete while falling was rejected.
- One impact removed 148 voxels and the hit building; the distant building stayed intact.
- Assertions confirmed 96 smoke puffs and 1..192 physical shards. Screenshots show the flash, translucent rising smoke and colored chunks displaced outward.
- Smoke and all shards cleared after 8 seconds; zero shards at completion is the expected cleanup result.
- Save/reload preserved demolition and AIR edits. F4 disabled cheats; another Delete was rejected.
- No OpenGL errors were observed.

Media: fresh F10 H264 MP4, 14 decoded frames, 720x544, 30.299 seconds, 421276 bytes. All frames decoded; count matched metadata; first and last frames differed. Inspected blast/debris screenshots plus decoded middle and last video frames; the last frame shows risen translucent smoke and outward displaced voxels. Low frame count reflects the software-rendered test environment; no hardware performance claim is made. Artifacts await controller publication on the assigned feature branch.

Build: `mvn -q -Dmaven.repo.local=target/maven-cache -Dlwjgl.natives=natives-linux -DskipTests compile` passed.

Broad regression attempt: the first full suite encountered read-only `/tmp`. A second full suite used worktree-local Java temp storage and ended with exit 143 before completion. Neither full-suite attempt is reported as passed. Focused final test results follow below.

Diff audit: removed only the old 24-cube expanding ring, replacing it with smoke and flash. Moved missile rendering after opaque city actors for correct smoke blending. Updated the smoke harness and new artifact names; preserved historical missile media. No unrelated source deletions. Audited the proposed diff against review base 8d6fcb1958608bc90fcf3054d7fd9276918b262e. No unrelated deletions. During rebase recovery, preserved the existing planet milestone and every other progress entry, then added the nuke milestone. Controller must stage and continue the rebase; final-head tests and fresh media remain pending.

Focused final checks: `mvn -q -Dmaven.repo.local=target/maven-cache -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=CityMissilesTest,PhysicsTest,CitySavesTest,RenderingAlgorithmsTest test` passed: {'tests': 37, 'errors': 0, 'failures': 0, 'skipped': 0}. Smoke spawn, growth, rise, cleanup and outward impulses are asserted. `git diff --check` passed.
