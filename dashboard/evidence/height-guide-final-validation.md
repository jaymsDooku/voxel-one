# Recovered road guide validation

Source checked: `b789ec952601655edd3b8c91fc06203944601e8f`. Controller publication of the refreshed report and media is pending.

Environment: assigned feature worktree on Linux; inherited role X11 display and authentication, Mesa software rendering. Native capture uses a fresh synthetic offline city in `target/road-home` and `target/road-runtime`.

Executed build and tests:

```sh
mvn -q -o -Dmaven.repo.local=target/m2 -DskipTests package
mvn -q -o -Dmaven.repo.local=target/m2 -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadGuideHeightTest,CityToolsTest,RoadWorkflowTest,RoadTypesTest,VehicleAudioTest test
```

Both passed. Deleted only `target/classes`, `target/test-classes` and `target/maven-status`, then ran a fresh compilation and package check:

```sh
mvn -q -o -Dmaven.repo.local=target/m2 -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadGuideHeightTest,CityToolsTest,RoadWorkflowTest,RoadTypesTest,VehicleAudioTest package
```

Passed: 26 tests, zero failures/errors/skips. Counts: RoadGuideHeightTest 1, CityToolsTest 10, RoadWorkflowTest 5, RoadTypesTest 9, VehicleAudioTest 1.

Recovery checks: audio source, `focusY()` accessor, audio tests/harnesses and native-cache ignore rule have no difference from the current base. Main keeps audio initialization, listener/update and shutdown, plus the road surface sampler. Vehicle audio evidence remains in place. Every base dashboard item is retained verbatim, including Vehicle sounds. Dashboard IDs are unique. `git diff --check` passed. No native-cache files are tracked.

Playtest command:

```sh
python3 deploy/run_road_height_smoke.py --display "$DISPLAY"
```

Playtest: production `Main.run`, real X11 clicks and keys. Choose paved road, click a first endpoint, hover and click a second endpoint on a raised synthetic patch. Expected: the hover and click select the same map coordinates despite different heights; a road is built. The test also checks a surface at `Terrain.MAX_Y`, cancellation without changes to road count or spending, and selection on an existing flat road followed by Escape. Native results and media are recorded below after execution.

The retained audio integration runs during this road playtest. This report does not claim a new audible vehicle-sound playtest. The unchanged audio implementation has its preserved prior evidence and its focused test was rerun here.

Final Playtest result: exit 0. Unequal-height endpoint preview matched within one block and the road was built. `Terrain.MAX_Y` selection/cancel left road count and spending unchanged. Existing flat-road selection and Escape passed. Fresh screenshot inspection shows both rings, spokes and sampled route over the mixed-height map. Main ran with the retained audio integration.

Final media: `height-guide-preview.png`, `height-guide-built.png` and `height-guide-playtest.mp4` were refreshed from this run. The F10 clip is 1,521,664 bytes. `ffprobe` was unavailable; the existing JCodec `VerifyVehicleAudioMedia` verifier decoded every frame instead: H264, 960 x 540, 55 frames, 54 changed frames, 42.885 seconds. Its middle frame was visually inspected. Verifier result: `height-guide-final-video-check.json`. Software-rendered capture shows low frame rate; no performance improvement is claimed.

Verifier command: compile `deploy/VerifyVehicleAudioMedia.java` with JCodec jars from `target/m2/org/jcodec`, then run `VerifyVehicleAudioMedia dashboard/evidence/height-guide-playtest.mp4 target/height-guide-video-frames` with `target/road-smoke` and those jars on the Java classpath. It passed.

Final source HEAD remained `b789ec952601655edd3b8c91fc06203944601e8f`. Source code was unchanged during validation. Only fresh road media, reports and this task progress were updated. No Git commit, push, merge or deployment was performed.
