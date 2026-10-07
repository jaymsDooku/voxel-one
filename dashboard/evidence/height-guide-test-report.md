# Terrain-aware road guide validation

Environment: Linux X11 on the inherited assigned role display, Mesa software rendering. The native harness uses a fresh synthetic offline city under `target/road-home` and `target/road-runtime`. No live profile or account data is used.

Commands:

```sh
mvn -q -Dmaven.repo.local=target/m2 -DskipTests package
mvn -q -o -Dmaven.repo.local=target/m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=RoadGuideHeightTest,CityToolsTest,RoadWorkflowTest,RoadTypesTest test
python3 deploy/run_road_height_smoke.py --display "$DISPLAY"
git diff --check
```

Playtest: Production `Main.run` with real X11 mouse clicks and keys. Open Roads, choose paved road, click the first endpoint, hover and click a second endpoint on a synthetic raised patch. Expected: preview and click select the same map position despite unequal heights; the road is built. Observed: endpoint coordinates matched within one block and the resulting road cells were present. The rings, spokes, target markers and sampled preview follow column tops, including trees and edited blocks; the overlay remains visible over the rendered map.

Edge case: Click a synthetic surface at `Terrain.MAX_Y` and press Escape. Expected and observed: selection succeeds; cancellation changes neither road count nor road spending.

Regression: Click an existing flat road to start a section, then press Escape. Expected and observed: selection succeeds and the tool returns to inspection.

Media: `height-guide-preview.png`, `height-guide-built.png` and the production F10 recording `height-guide-playtest.mp4` come from this worktree's running implementation. Publication is pending the controller's commit and push. The clip is below 6 MB. Captures show low frame rates on this software-rendered host; no performance improvement is claimed.

The first attempt to build using `/tmp/voxel-m2` failed because that cache could not create its Maven lock file. The build then passed using the worktree-local `target/m2` cache. A test import error was corrected before the passing test run. No failed check is counted as passed.

Automated result: 25 tests passed: RoadGuideHeightTest (1), CityToolsTest (10), RoadWorkflowTest (5), RoadTypesTest (9). The new test covers heights -32, 32, 60 and 95, plus cache invalidation between heights. An earlier regression run could not create JUnit temp directories in read-only `/tmp`; it passed with the worktree-local temp directory. One wider-capture attempt failed during startup with `FarmModels.close()` on a null field; no media or successful playtest is attributed to that attempt.

Final native result: exit 0 after the wider-view retry. Both concentric rings are visible over mixed terrain in the preview capture. The final F10 MP4 is 1,464,644 bytes. `git diff --check` passed. The implementation and media await controller publication and independent review.
