# Block raycast validation

Environment: Linux, OpenJDK 17.0.20.1; synthetic in-memory chunks, no accounts/server.

Command from assigned worktree:
```sh
mkdir -p target/test-tmp
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH mvn -q -DargLine="-Djava.io.tmpdir=$PWD/target/test-tmp" -Dtest=BlockRaycasterTest,FractionalWorldTest,VoxelModelTest test
```
Result: exit 0. 16 tests, 0 failures, 0 errors, 0 skipped.

Playtest: exercised real BlockRaycaster.cast against loaded synthetic chunks. NaN and both infinities in reach and every origin/direction component, negative reach, zero and signed-zero directions returned null with a null world. This proves rejection before world dereference. Source inspection confirms rejection also precedes coordinate conversion.

Ordinary and scaled rays matched for Float.MIN_VALUE, Float.MIN_NORMAL, 1e-20, 1e20 and Float.MAX_VALUE axis vectors in both directions. Negative-coordinate hits, normals, distance, exact reach boundary and one-float-below misses matched. Unequal-component scaled rays and mixed minimum/maximum components matched ordinary hits. Caller vectors stayed unchanged. Zero reach hit an occupied origin and missed an empty origin. Unloaded cells missed. FractionalWorldTest and VoxelModelTest passed for regression coverage of partial blocks and custom models.

Browser/OpenGL playtesting does not apply to this CPU targeting API; owner requests CPU-only qualification. PNG captures executed test results, not gameplay. No physical-device qualification claimed.

Earlier failed attempts: fixed missing Blocks import and declared World.close checked exceptions in new tests. Regression tests initially failed because /tmp is read-only. Maven -Djava.io.tmpdir alone did not reach the forked JVM; -DargLine fixed this. Failed attempts are not counted as passes.

Diff audit: only input validation/normalization, focused tests, local progress and evidence changed. Two removed implementation lines are replaced by validation and safe normalization. No unrelated deletions. Controller review base has not been supplied; integration and any resulting final-source retest remain controller handoff steps.

Evidence awaits controller commit/push on assigned feature branch. No deployment or Git mutation performed.
