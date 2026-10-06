# Cargo validation on aviation base

Tested source: 94e3268308bb8839c46d08ccd73a20817cdb7e94. Aviation base: 1ecbf565c449d7b5c96a621310c1a012b80ae1aa. No game source was changed during validation. Publication and independent review belong to the controller.

The container truck, liquid tanker, delivery van and goods lorry use Shift+J near the parked offline vehicle. J enters/exits. WASD and mouse drive/steer. Each body has distinct collision bounds, cab and saved type. Existing Jeep behavior remains available.

## Compatibility and preservation

38 tests passed, zero failures/errors/skips: AviationTest 10, RegionalPopulationTest 10, RegionalSaveCompatibilityTest 4, RegionalMultiplayerTest 1, JeepTest 8, ControlsTest 5. Protocol 22 assertion and complete format-12 save roundtrip passed. All 734 checked aviation-base source/test/deploy/evidence blobs outside intended cargo paths match unchanged. No deleted paths appear in the diff against that base. This comparison was executed against the aviation base, unlike the corrected historical cargo-final-validation.md claim. Earlier media and reports remain historical.

## Actual commands

```sh
mvn -q -Dmaven.repo.local=target/m2 -DargLine="-Djava.io.tmpdir=$PWD/target/test-tmp" -Dtest=AviationTest,RegionalPopulationTest,RegionalSaveCompatibilityTest,RegionalMultiplayerTest,JeepTest,ControlsTest test > target/cargo-aviation-final-tests.txt 2>&1
java --class-path target/classes target/AviationSaveHeaderCheck.java
python3 target/run_cargo_aviation_final.py --display "$DISPLAY"
python3 target/run_aviation_cargo_final.py --display "$DISPLAY"
java -Djava.io.tmpdir="$PWD/target/tmp" --class-path target/m2/org/jcodec/jcodec/0.2.5/jcodec-0.2.5.jar target/CargoAviationClipCheck.java dashboard/evidence/cargo-aviation-final-container-drive.mp4
git diff --check
```

Temporary native wrappers copy deploy/run_cargo_smoke.py and deploy/run_aviation_smoke.py. Only artifact destinations change: cargo-placement-playtest.json becomes cargo-aviation-final-playtest.json; cargo-* media becomes cargo-aviation-final-*; airport-* media/results becomes cargo-aviation-airport-*. Aviation dependency cache changes from target/maven-cache to target/m2. The Java harnesses and production input paths are unchanged. Decoder copies CargoFinalClipCheck.java with its class, output report and temporary sample filenames renamed to CargoAviationClipCheck/cargo-aviation-final-*.

Playtest: Linux X11, inherited role DISPLAY/XAUTHORITY, Mesa software rendering, isolated synthetic offline profiles target/cargo-home and target/aviation-home. No real account or save data. Browser testing does not apply to this native Java/OpenGL application or its save CLI integration.

Cargo steps: select all five bodies with real Shift+J; enter with J; hold W until speed >=1.5 blocks/s and displacement >0.2 blocks; reject Shift+J while driving and J while moving; brake to <=0.05 blocks/s; exit, re-enter from cab exit and exit again; save/reload each type; cycle to Jeep; reject the larger container body beside a wall. Expected: body identity persists, movement works, unsafe changes fail, stopped entry/exit works. Observed: exit 0; all assertions passed. Five fresh images inspected. Production F10 clip decoded and four samples inspected: 26 frames, 29.622 seconds, 1,004,394 bytes, below 6 MB. Software-rendered capture is slow (1–4 FPS visible); no performance pass is claimed.

Aviation first attempt: exit 143 before results.json; no failure assertion file was produced. Not counted as a pass. Retry exited 0. Playtest: same inherited Linux X11 display, Mesa, isolated synthetic city. Place two airports and expand runway; reject single-airport booking and overlapping exchange without mutation; book adult citizen via menu; observe walking, boarding, flight and arrival; use compact menu; settle 1,000,000 residents and focus 64 via district commands. Expected: flights reach the destination and permits/regional commands remain distinct. Observed: every production-input assertion passed. Fresh flight image inspected; aviation clip decoded separately. Format-12 booked-flight persistence and midflight restore also passed in AviationTest.

Source/media SHA-256 values are in cargo-aviation-final-verification.json. Artifact publication is pending the controller. Prior CI results belong to the earlier head and do not establish CI success for this source. No full-suite pass is claimed.

Aviation video: 40 frames, 30.165 seconds, 1,636,573 bytes. Four samples decoded; one sample visually inspected. Fresh media contain synthetic game state only. git diff --check passed after record updates.
