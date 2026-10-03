# Integrated terrain review remediation

Validated the working tree integrated onto base `1ed24587886a1c21f8e57a5552acf9bc472dea15`. The controller must stage the three resolved files and continue the pending rebase; no commit, staging, publication or rebase continuation was performed here.

Preserved city save formats 1–7 (including `0x43495437`) and current civic-building decoding. Added two BusinessCatalogTest regressions for format 7 catalog/all civic types/network frame round trips and format 6 civic migration to format 7. LocalGame retains the configured catalog while selecting the stored terrain version. Protocol is now 17, retaining the base city commands and business catalog serialization.

Concurrent configurable businesses, civic buildings, demolition, building guides, metric trends, camera rotation and their tests/configuration remain integrated. All 39 base progress entries and 272 base configuration/history files were compared byte-for-byte and preserved. CityFrame is unchanged from the reviewed base. CitySimulation differs only in passing terrain to agriculture.

## Verification

`MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" verify -q`

Exit 0: 216 tests, zero failures, errors or skips; packages generated. Includes the six server migration/client generator version tests with loopback sockets. The first attempt had one incomplete synthetic city-address fixture; that fixture was corrected before the successful rerun. Sanitized counters: terrain-integrated-verify.json. Earlier 187-test reports describe previous code and are historical.

`EGL_PLATFORM=surfaceless LIBGL_ALWAYS_SOFTWARE=1 MESA_SHADER_CACHE_DIR="$PWD/target/tmp/mesa" java -Djava.io.tmpdir="$PWD/target/tmp" -Dorg.lwjgl.system.SharedLibraryExtractPath="$PWD/target/tmp/lwjgl" -cp target/voxel-one-1.0-SNAPSHOT-client.jar deploy/TerrainRenderingSmoke.java target/terrain-integrated-render --headless`

Exit 0: actual headless OpenGL rendering with engine shaders and 468 terrain tiles; both camera views, four planning orientations, and generated settlement walking passed (player x=16.174974, grounded). Both PNGs were visually inspected. This is automated Mesa software OpenGL evaluation, not a manual desktop play session. The engine recorder used by F10 recorded the representative run.

`java -cp target/voxel-one-1.0-SNAPSHOT-client.jar deploy/TerrainRecordingCheck.java target/terrain-integrated-render/terrain-opengl-camera-walk.mp4 dashboard/evidence/terrain-integrated-recording-check.json`

Exit 0: H264, 120 frames, 53.471 seconds, 864x540, 1,693,196 bytes; seekable and first/end frames differ. New artifacts have the terrain-integrated prefix; earlier evidence is retained.

`git diff --check`: passed. The conflict index remains unmerged intentionally until controller staging, while working files have resolved content.
