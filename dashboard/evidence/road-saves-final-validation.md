Final road and City saves validation

Source head: f85918ec0b0b7cc03f9bb69de64b9b0196be7840. Required review base: 6d1ec251. Production source was not changed during this validation. A new RoadCitySavesTest checks copied ownership isolation. New capture wrappers preserve the base smoke drivers and use fresh synthetic profiles.

Environment: Linux X11, inherited assigned DISPLAY and XAUTHORITY. Authentication cookie was not read. llvmpipe (LLVM 15.0.6, 256 bits); OpenGL 4.5 Core Profile Mesa 22.3.6. Cached Maven dependencies at /tmp/voxel-m2; temporary files inside target/tmp. No personal profiles used. Browser playtesting does not apply to this native GLFW game.

Preservation: Python compared required-base src/, deploy/, dashboard/evidence/, README.md and CHEATS.md files against `git show 6d1ec251:<path>`. No paths missing; 1013 files unchanged. All 18 City saves source/test/harness/media files matched byte for byte. All unrelated base progress entries matched exactly by ID. Main's only difference from required base is road command result delivery. CitySaves, SavesMenu and Controls menu integration remain present. Details: road-saves-final-preservation.json.

Automated commands:

`MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=CitySavesTest,RoadWorkflowTest,MixedRoadOwnershipTest,RoadBaseCompatibilityTest,CityToolsTest,CityMultiplayerTest,ProtocolCompatibilityTest,RoadTypesTest,OperatingEnergyTest,IndustrialProgressionTest,TerrainGenerationTest,ShippingAviationCompatibilityTest,AviationSaveCompatibilityTest test`

Expected: save isolation, road workflow/ownership, protocol/save compatibility and base regressions pass. Observed: exit 0; 66 tests in 13 suites; no failures, errors or skips. Includes real-server craft(-1) interleaved immediately before rejected ROAD, correct road acknowledgement and successful retry. CRAFT_RESULT does not consume city callbacks. Chain test waits for matching acknowledgement.

`MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadCitySavesTest test`

Expected: copy preserves mixed-width road ownership; editing/deleting copy keeps genuine narrow crossing, leaves original unchanged, rejects duplicate name without changing copy; new slot stays separate. Observed: exit 0; one test passed. Total 67 tests in 14 suites, zero failures/errors/skips. Counts: road-saves-final-tests.json.

`MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DskipTests verify`

Observed: exit 0, packaging passed. This command skipped tests; tests are counted from the executed runs above.

Playtest: `python3 deploy/run_road_saves_menu_smoke.py --display "$DISPLAY"`

Production Main with real X11 mouse/key input. Expected: Controls > City saves saves Original, copies Harbour with roads/buildings/clock, rejects duplicate without switching, creates fresh Hills in city mode despite sandbox launch defaults, lists three saves, loads Original, restores its state and isolates clocks, resumes play and F6 toggles view. Observed: exit 0; all driver assertions passed. Fresh menu and duplicate PNGs inspected. Files road-saves-final-city-saves-menu.png and road-saves-final-city-saves-duplicate.png. The preserved base driver does not record video; its captured images are fresh from this implementation.

Playtest: `python3 deploy/run_road_saves_final_smoke.py --display "$DISPLAY" --scenario road`

Production Main with isolated synthetic offline city and X11 input. Expected: dirt/paved 2/3/4 lanes, guide, chained sections, highlight, widen/narrow, delete preserves neighbor, zone-edge snapping and overlap rejection, distinct widths, dirt upgrade, cancellation changes neither world nor spending, Escape returns to inspect. Observed: exit 0; all assertions passed. Fresh selection, edited road and snap PNGs inspected.

`python3 deploy/verify_road_saves_final_media.py --scenario road`

Observed: exit 0. Fresh F10 H264 video decoded at start/middle/near end and every frame. 129 frames, 3436186 bytes, below 6 MB. Decoded end frame inspected. Details: road-saves-final-road-video-samples.json and road-saves-final-road-video-all-frames.json.

Playtest: `python3 deploy/run_road_saves_retry_smoke.py --display "$DISPLAY" --scenario road`

Expected: budget rejection preserves original start; funded retry has no gap; success advances chain; selection/edit/delete and X-then-Z right-angle route still work. Observed: exit 0; all assertions passed in production Main with real X11 input and a fresh isolated synthetic profile. Connected retry image inspected.

`python3 deploy/verify_road_saves_retry_media.py --scenario road`

Observed: exit 0. Fresh F10 H264 clip decoded at start/middle/near end and every frame. 75 frames, 1928944 bytes, below 6 MB. Decoded end frame inspected.

`git diff --check` passed. No unmerged index paths. Production source hashes still match road-saves-final-source.json. Final evidence is ready for controller publication and independent review. Older road-craft and city-saves artifacts are preserved historical evidence; they are not fresh validation for this head. Publication and remote CI remain reserved to the controller.
