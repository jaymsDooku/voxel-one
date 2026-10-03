> Historical initial-submission report. Current full verification and actual OpenGL evidence are recorded in [terrain-final-validation.md](terrain-final-validation.md).

# Terrain generation v2 — developer verification

Implemented bounded river-valley milestone for new worlds, with broader layered landforms continuing outside the watershed. Default seed: 748291. This is ready for independent review, not merged or deployed.

## Features to inspect

- Settlement terrace: x=-16..32, z=0..48, ground height 26. Tree anchors stay off the terrace; farmland fertility is 0.85. A smooth apron blends into the floodplain without blocking channels.
- Mountain ridges: near x=±210; the pass at z=160 is lower than the surrounding ridgeline. Fine mountain displacement follows the slope of the underlying landform, leaving plains smooth.
- Connected trunk and four tributaries: immutable coarse graph, z=-384..448, descending into a coastal bay. Channels have shaped cross-sections and floodplains. This pilot uses static water blocks, not a fluid simulation.
- Quarry: exposed stone near (132,-72). Geology also controls deep mineral veins and stone extraction suitability. Temperature, moisture and fertility control biome cover and actual crop growth. Existing worlds keep their previous crop growth rate.
- Cross-chunk trees use global anchor coordinates. Absolute-coordinate noise, immutable drainage and bounded thread-safe caches keep generation independent of chunk order.
- Saved generator version protects existing worlds: unversioned saves use generator 1; new worlds use generator 2. Offline save format 7 and server save marker -7 store it. Wire protocol 15 transfers it to clients. Main terrain, distant terrain, city simulation and lighting fallback all use the stored version. Client/server must use matching protocol versions.
- Water is non-solid for walking. Water and mineral blocks have names, colors, valid wire IDs and safe selection behavior when the user selects a subdivision they do not support.

## Actual checks

Maven is installed at /tmp/apache-maven-3.9.11/bin/mvn. Cached dependencies were copied into target/maven-cache within this worktree. All test temp files stayed in target/tmp. No production account data or credentials were used for evidence.

1. Offline regression suite, exit 0, 162 tests passed before the final natural-material selection guard and concurrent sampling check:

   `/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" '-Dtest=*,!MultiplayerTest,!CityMultiplayerTest,!UpdaterTest' test -q`

2. Final integration rerun after the last source edits, exit 0, 35 tests passed:

   `/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=TerrainGenerationTest,FractionalWorldTest,CraftingTest,VoxelModelTest,PlayerTest test -q`

   Includes ten terrain regressions: flat build site and landmarks; wet graph connectivity across four seeds including negative and maximum-long seeds; chunk-order and negative-boundary consistency; trees spanning chunk borders and cache eviction; climate effects and broad coastal water; actual player settlement/water/bank collision; simultaneous sampling; natural-material wire/selection safety; original generator fingerprint; offline save migration. The legacy fingerprint was captured by compiling the branch-base generator in target/legacy, rather than using the modified implementation to define its expected result.

3. Final package command and harness compilation are recorded in terrain-generation-checkpoint.md.

4. `git diff --check` passed.

## Camera evaluation

`java -Djava.io.tmpdir="$PWD/target/tmp" -cp target/classes:target/maven-cache/org/joml/joml/1.10.9/joml-1.10.9.jar deploy/TerrainViewEvidence.java dashboard/evidence`

Exit 0. Three 960x600 CPU rasters use engine DistantTerrainMesher geometry, IsometricCamera projection and Camera view matrices. They were visually inspected. These are software geometry evaluations; they are not screenshots of an interactive OpenGL session.

- terrain-cpu-city-planning.png: 206352 visible terrain pixels. Shows joined tributaries, river mouth/coastal bay, broad flat settlement land, ridges, quarry exposures and the low pass.
- terrain-cpu-ground-level.png: 326149 visible terrain pixels. Standing view from the settlement shows open buildable ground, trees on the nearby hills, and visible quarry stone.
- terrain-cpu-river-bank.png: 489515 visible terrain pixels. Ground view at the shallow river margin shows water, banks and wooded slopes along the channel.

The movement regression separately uses the actual Player collision and jump code on generated chunks: settlement standing, descent through water to the river bed, and escape from the channel over the banks all passed.

## Environment limits and review follow-up

A full-suite attempt failed when local HTTP/TLS test servers opened sockets (`Operation not permitted`). MultiplayerTest, CityMultiplayerTest and UpdaterTest were excluded from the successful offline run. Their network checks remain unverified here; review/CI should run them in a socket-capable environment, especially generator-version transfer and old server-save migration.

Xvfb capture timed out before drawing (exit 124), with no usable display available in this sandbox. deploy/TerrainRenderingSmoke.java is retained as a reproducible actual OpenGL harness. OpenGL capture and interactive/F10 recording remain unverified here. No GPU result or video is claimed.
