> Historical recovery checkpoint: the six terrain migration/handshake checks now pass in the loopback test runtime. Current validation is recorded in terrain-loopback-tests.json and terrain-final-validation.md.

# Terrain review remediation — durable checkpoint

The preserved rebase has not been aborted, reset, staged, continued, committed or pushed by this developer. The controller should stage the working-file resolutions and continue it. `dashboard/progress.json` is valid JSON with no conflict markers; its index remains unmerged until the controller stages it.

## Preserved accepted camera feature and history

IsometricCamera, Controls, IsometricCameraTest, ControlsTest, CityToolsTest and dashboard/evidence/isometric-rotation-tests.json are byte-identical to current-master HEAD in this rebase. Main differs only in terrain-version construction. All 34 existing master progress items were preserved unchanged; the terrain entry was added separately. The camera entry and its evidence were not replaced or edited.

## Checks completed in this worktree

- Targeted camera/control/picking plus terrain and unsupported-save check: passed. IsometricCameraTest 7, ControlsTest 5 and CityToolsTest 3 all pass.
- Full offline suite: **169 tests passed, zero failures/errors**, exit 0:

  `/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" '-Dtest=*,!MultiplayerTest,!CityMultiplayerTest,!UpdaterTest' test -q`

- Package build, exit 0:

  `/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local=target/maven-cache -DskipTests package -q`

- `git diff --check`: passed. Validation only; no index changes.

## Actual OpenGL and playable camera evidence

The display-based GLFW attempt failed initialization. A null-platform, surfaceless EGL context then succeeded entirely inside the sandbox, without changing sandbox or approval controls. Renderer: Mesa llvmpipe (LLVM 15.0.6), OpenGL 4.5 core, Mesa 22.3.6.

Actual engine terrain meshes and RenderPipeline were rendered in both IsometricCamera and ground Camera views. The harness then rotated through four orientations and stepped the actual Player on generated settlement chunks. The player remained grounded and moved from x=8.5 to x=16.174974. GL error checks passed. Both PNG captures were inspected.

`EGL_PLATFORM=surfaceless LIBGL_ALWAYS_SOFTWARE=1 MESA_SHADER_CACHE_DIR="$PWD/target/tmp/mesa" java -Djava.io.tmpdir="$PWD/target/tmp" -Dorg.lwjgl.system.SharedLibraryExtractPath="$PWD/target/tmp/lwjgl" -cp target/voxel-one-1.0-SNAPSHOT-client.jar deploy/TerrainRenderingSmoke.java dashboard/evidence --headless`

Exit 0. The harness uses ScreenRecorder.toggle/capture, the same recorder behind the F10 control. This is automated headless OpenGL evaluation and simulated player input, not a manual GUI/keyboard session. The scene is 468 engine terrain tiles with the actual shaders; software rendering speed is not presented as a performance benchmark.

Artifacts:
- terrain-city-planning.png — actual OpenGL planning view, including connected rivers, coast, settlement site and ranges/pass.
- terrain-ground-level.png — actual OpenGL standing view, showing buildable ground, wooded hills and quarry exposure.
- terrain-opengl-camera-walk.mp4 — four planning orientations followed by walking on generated terrain. H.264, 120 frames, 42.867 seconds, 864x540, 1,693,188 bytes (below 6 MB).
- terrain-opengl-recording-check.json — decoded first/final frames, checked seekability and differing camera images.

Recording verification command, exit 0:

`java -cp target/voxel-one-1.0-SNAPSHOT-client.jar deploy/TerrainRecordingCheck.java dashboard/evidence/terrain-opengl-camera-walk.mp4 dashboard/evidence/terrain-opengl-recording-check.json`

Earlier CPU images remain honestly labelled geometry evidence. They are supplemented by the actual OpenGL artifacts above.

## New server migration and multiplayer version regressions

Six targeted methods were added to MultiplayerTest:

1. legacyServerSaveFormatsMigrateAndKeepGeneratorOnClientAndRestart — save markers -3/-4/-5/-6, old terrain selection, transferred seed/version, edits and terrain consistency, migration to -7 and another restart.
2. seedlessServerSaveMigratesToVersionedLegacyTerrain — oldest edit-only save, requested seed preserved into a versioned legacy save and restart.
3. brandNewServerChoosesVersionTwoAndPersistsIt — fresh world defaults to v2; handshake and persisted header agree.
4. versionTwoServerSaveRoundTripsToClientsAcrossRestart — v2 saved-world/client round trip, overrides and restart persistence.
5. unsupportedSavedGeneratorFailsBeforeOpeningListenerAndPreservesSave — versions 0/99 are rejected before network startup and original bytes remain unchanged.
6. clientRejectsUnsupportedGeneratorFromAuthenticatedHandshake — synthetic TLS handshake advertising unsupported version 99 is rejected by the actual client.

All six compile. The actual execution attempt produced **1 pass, 5 socket-permission errors, zero assertion failures**. Method 5 passed. Methods 1/2/3/4/6 fail while creating a socket with `java.net.SocketException: Operation not permitted`; their functional results remain unverified. A separate default-sandbox loopback capability probe also failed with EPERM. No controls were disabled or escalated.

Reproduction command for the remaining required validation:

`/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" '-Dtest=MultiplayerTest#legacyServerSaveFormatsMigrateAndKeepGeneratorOnClientAndRestart+seedlessServerSaveMigratesToVersionedLegacyTerrain+brandNewServerChoosesVersionTwoAndPersistsIt+versionTwoServerSaveRoundTripsToClientsAcrossRestart+unsupportedSavedGeneratorFailsBeforeOpeningListenerAndPreservesSave+clientRejectsUnsupportedGeneratorFromAuthenticatedHandshake' test -q`

In CI with Maven on PATH and a normal writable dependency cache, the same test selector can be used with `mvn test`. Test fixtures are generated in temporary directories; only synthetic identities/accounts are used. Do not publish fixture files or full logs.

## Remaining blocker / continuation

The independent review explicitly requires running migration/handshake checks in a socket-capable environment. Provide permitted loopback execution or run these tests in socket-capable CI; do not weaken the sandbox. Do not request merge before these checks and independent review pass. No implementation or evidence has been discarded. The terrain progress item is blocked for this remaining validation, while camera history remains intact.

Local synthetic diagnostics are retained under target/terrain-review-*.txt, target/terrain-network-review-tests.txt and target/terrain-recovery-offline-tests.txt. Publish the concise evidence artifacts, not full runtime logs, account fixtures or credentials.
