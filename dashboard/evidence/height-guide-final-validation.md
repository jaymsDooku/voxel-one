# Road guide reviewer fixes and final validation

Starting source HEAD: `812b8120cc8c30875e7d67a099f280c32b839a9c`. This run includes the local harness fixes described below. Controller commit/publication is pending.

## Changes

Synthetic World edits, surface samples, city snapshots, camera changes, CityTools cursor queries and click-state checks run on the game thread. The driver queues FutureTasks; `FrameObserver.afterFrame` executes them. Same-thread calls run inline. Tasks time out after 30 seconds, cancel pending work and report the underlying exception.

The MAX_Y and existing flat-road cases now assert that a real click stored exactly one first endpoint within one block of the expected map coordinates before Escape. Escape is also sent after the built road to finish its chained section before starting the MAX_Y case. A click that selects nothing now fails the test.

## Diff audit

The synchronized integration base is `3c91629` (the carrier-routes commit immediately before this task's two rebased commits). Audited the full proposed tracked diff against this base, including local edits. No files are deleted. Main adds only the surface sampler. CityTools replaces flat-plane guide projection/picking with surface-aware methods; those removed lines are intentional. Other changes are the road test, native harness, reports/media and this task's progress entry. Vehicle audio, its Main integration, `focusY()`, native-cache ignore rule, carrier routes and all prior evidence remain intact. Assertions confirmed every base dashboard item remains verbatim. The local `master` ref is stale and was not used as the synchronized base.

## Checks

Environment: Linux X11 on the inherited assigned role display, Mesa software rendering. Native runs use fresh synthetic offline profiles under `target/road-home` and `target/road-runtime`. No live profile or account data is used.

```sh
mvn -q -o -Dmaven.repo.local=target/m2 -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadGuideHeightTest,CityToolsTest,RoadWorkflowTest,RoadTypesTest,VehicleAudioTest package
python3 deploy/run_road_height_smoke.py --display "$DISPLAY"
git diff --check
```

Package passed. Focused tests: 26 passed, zero failures/errors/skips (RoadGuideHeightTest 1, CityToolsTest 10, RoadWorkflowTest 5, RoadTypesTest 9, VehicleAudioTest 1).

Playtest: Production `Main.run` with real X11 clicks and keys. Choose paved road, click first endpoint, hover/click an endpoint on a synthetic raised patch. Expected and observed: preview coordinates matched within one block, and resulting road cells were present. The guide follows visible column tops across mixed terrain.

Playtest edge case: Add a synthetic surface at `Terrain.MAX_Y`, finish the previous chained road, select a new first endpoint at (55, 90). Expected and observed: exactly one endpoint near (55, 90) exists before Escape; cancel preserves road count and spending.

Playtest regression: Select (65, 80) on an existing flat road. Expected and observed: exactly one endpoint near (65, 80) exists before Escape; Escape returns to inspection.

The first corrected native run passed. A second run checks the final timeout/error-reporting helper and refreshes the media. Its final result is recorded below. Prior reports/media from before the harness fixes are historical and are not used as proof of the stronger assertions. Retained audio integration runs during this playtest; no new audible vehicle-sound test is claimed.

Final repeat Playtest: exit 0. All three workflow checks passed on the final harness. Fresh screenshots and the F10 MP4 were copied from this synthetic run. No runtime crash was observed in either corrected run.

Media verification: compiled `deploy/VerifyVehicleAudioMedia.java` against JCodec jars in `target/m2/org/jcodec`, then ran `VerifyVehicleAudioMedia dashboard/evidence/height-guide-playtest.mp4 target/height-guide-video-frames` with `target/road-smoke` and those jars on the classpath. Full decode passed: H264, 960 x 540, 79 frames, 78 changed frames, 59.145 seconds, 1,865,096 bytes. The screenshot and decoded middle frame were inspected. The clip is below 6 MB. Software-rendered capture shows low frame rate; no performance gain is claimed.

Final `git diff --check` passed. Starting HEAD remained unchanged. Local changes are only the fixed native harness, this task progress and refreshed evidence. No commits, pushes, merges or deployment were performed.
