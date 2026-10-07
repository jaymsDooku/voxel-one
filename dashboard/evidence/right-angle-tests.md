Right-angle road placement validation

Environment: Linux X11, inherited assigned role DISPLAY and XAUTHORITY, Mesa software rendering, JDK 25, Maven. Native checks use target/road-home and target/road-runtime/synthetic-world.dat. No real player profile was used.

Implementation: all road types follow the selected cells along X, then Z. Off-axis endpoints form one joined right-angle route. Preview uses the same floored route. Existing validation checks the full footprint before charging or placing either leg.

Commands and results:
- `mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -Dtest=RoadTypesTest,CityAddressesTest,BuildingGuideTest test`: failed before tests because the shared Maven cache is read-only. Dependencies were copied into target/m2.
- First worktree-cache test runs failed because JUnit used read-only /tmp. Passing java.io.tmpdir as a Maven property did not change the forked JVM temp directory.
- `mvn -q -Dmaven.repo.local=target/m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/test-tmp" -Dtest=RoadTypesTest,CityAddressesTest,CityTest,RoadSpacingTest test`: PASS, 23 tests, zero failures/errors. Tests cover all four road types, both bend directions, unequal legs, negative-coordinate flooring, same-cell rejection, second-leg collision atomicity, widths, divider surfaces, costs, upgrades, persistence, addresses and spacing.

Playtest: `python3 deploy/run_road_placement_smoke.py --display "$DISPLAY"`. Production Main, real X11 mouse and keyboard events, isolated synthetic offline city. Choose each road type; click straight endpoints and inspect width/type. Choose paved 3 lanes; click (100,70), hover (120,90), then click it. Expected: preview and placement follow X to (120,70), then Z to (120,90), with no diagonal shortcut. Observed: both legs have the chosen type; (110,80) remains unbuilt. Choose paved 2 lanes; click (160,90), then (140,70). Expected/observed: reverse-direction X and Z legs. Regression: upgrade dirt through the menu, cancel after the first endpoint, cancel the road menu. Expected/observed: upgrade succeeds; cancellation leaves roads and spending unchanged. First run PASS. F10 H264 recording captured from this implementation. Second run PASS with a 15-second mesh-settle pause. The steeper-camera harness first had an ambiguous Math import compile error; java.lang.Math fixed it. Final run PASS with a -75 degree camera pitch for the completed-road screenshot. The screenshot was visually inspected and shows both joined legs.

Browser playtesting does not apply: this is the native game road tool.

Artifacts are pending controller publication. No Git push or deployment was performed.

Media check: `javac -cp target/m2/org/jcodec/jcodec/0.2.5/jcodec-0.2.5.jar -d target/road-smoke deploy/VerifyRoadVideo.java`, then `java -Djava.io.tmpdir="$PWD/target/tmp" -cp target/road-smoke:target/m2/org/jcodec/jcodec/0.2.5/jcodec-0.2.5.jar VerifyRoadVideo dashboard/evidence/right-angle-playtest.mp4 target/right-angle-decoded.png dashboard/evidence/right-angle-media.json`: PASS. H264, 960 x 544, 199 frames, 128.511 seconds, 5,277,188 bytes. Start, middle and end decode successfully. `ffmpeg` was unavailable; JCodec performed the actual decode check.

Full build and regression suite: `mvn -q -Dmaven.repo.local=target/m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/test-tmp" verify`: PASS, exit 0, 367 tests across 65 classes, zero failures/errors. Sanitized suite counts are in right-angle-unit-results.json. `git diff --check`: PASS.
