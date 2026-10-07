# Mixed-width road ownership fix

The visible material of a crossing cell no longer decides every street's footprint. Each named road owns explicit cells with intended type, surface and paint order. Mixed-width extensions keep different cell types under one street name. Selection reads those owned cells. Deletion removes that ownership, then restores the highest remaining paint at each affected cell. Narrowing replaces the selected footprint and recomputes its old cells from surviving ownership. This removes stale shoulders and restores the crossing road's lane markings.

Ownership is bounded to 512 streets, 8192 cells per street and 65536 ownership entries. Proposed ownership is built and validated in copied address state before road budget or world mutation. Occupied-cell and zone rejection remain atomic. New snapshots/saves use format 13; save magic is `0x4349543D`. Old format 12 (`0x4349543C`) and earlier saves still load. Legacy ownership is inferred once from route centers and existing cells and marked inferred; old snapshots did not contain the hidden crossing history. New explicit ownership is preserved on save/load and network round trips. Aviation fields and runway/flight IDs 10/11 remain. Road action IDs 12/13 remain. Protocol 24 carries ownership; the reviewed protocol-22 aviation retry remains supported and blocks new road actions.

Environment: Linux, Java 25, Maven 3.9.11, cached dependencies at `/tmp/voxel-m2`, Linux LWJGL natives and Mesa software rendering. Native runs use inherited DISPLAY and XAUTHORITY without reading authentication data. All writes stay in this feature worktree. Profiles are isolated synthetic offline cities. This is a native GLFW game; browser playtesting does not apply.

## Automated checks

Executed:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=MixedRoadOwnershipTest,RoadWorkflowTest,RoadTypesTest,RoadBaseCompatibilityTest,CityAddressesTest,CityToolsTest,CityMultiplayerTest,AviationTest,RegionalPopulationTest,ProtocolCompatibilityTest,MarketEconomyTest,CityTest test > target/road-mixed-tests.txt 2>&1
```

Observed exit 0: 75 tests passed, zero failures/errors/skips. `road-mixed-tests.json` records suite counts. The exact reported four-lane (90,90)..(110,90) and two-lane (100,80)..(100,100) crossing is tested. Wide deletion leaves only the survivor, in both build orders. Tests also cover three/four lanes crossed by dirt/two lanes, restored surface blocks, narrowing to an exact 117-cell union, deletion after narrowing, partial-width extensions, save/network round trips and format-12 mixed-width migration. Existing road, airport, regional, city, market, address and live TLS multiplayer tests pass.

The first run failed two assertions: a removed-cell check used an untouched terrain cell, and an old-save test compared newly added ownership metadata with the old format. The removed-cell assertion now uses an actual road cell; old-save checks compare retained street/address fields and require inferred metadata. That failed run is not counted as passing.

## Playtest: mixed-width roads and road regression

Executed:

```sh
python3 deploy/run_road_mixed_smoke.py --display "$DISPLAY"
```

The first native run exited 0 and passed all listed assertions. Its mixed-road captures were partly hidden by high terrain, so they are not used as final visual proof. The harness now levels only the synthetic crossing site on the game thread before input; production code is unchanged. That full capture rerun ended with exit 143 before reverse-order narrowing finished; it is not counted as passed. No harness assertion failure file was written. The focused rerun exited 0 and passed, with this executed command:

```sh
python3 deploy/run_road_mixed_smoke.py --display "$DISPLAY" --mixed-only
```

The first full run result is preserved in `road-mixed-regression-playtest.json`. The harness runs production Main with real X11 mouse/key input. Fresh profiles: `target/road-mixed-home` and `target/road-mixed-runtime`.

Expected and observed: the first full run passed all original road types, snapping, chaining, selection, widening/narrowing, deletion, upgrading and cancellation checks. A translated crossing at horizontal (130,130)..(150,130), vertical (140,120)..(140,140) avoids earlier test roads. Build wide then narrow, inspect the wide section and delete it: exactly 63 two-lane cells remain, with no shoulders. Clear that crossing, build narrow then wide, edit wide to two lanes: exactly 117 union cells remain. Delete the edited road: exactly the 63-cell crossing survivor remains. The successful focused run captured before/delete/narrow/restore stills and a separate F10 clip for the mixed-width workflow. All four mixed stills were inspected. They show the selected wide footprint, restored narrow crossing with lane markings, narrowed footprint and final survivor. First-run road regression stills remain attached; the focused run does not claim those earlier steps were repeated.

## Playtest: airport regression

Executed after the road window closed:

```sh
python3 deploy/run_road_mixed_airport_smoke.py --display "$DISPLAY"
```

Observed exit 0. The preserved aviation harness passed permits, runway expansion, flight rejection with only one airport, boarding, flight, arrival, overlap rejection, City hall, compact menu and regional UI. Expected and observed: two airports rendered, the second expanded to two runways, the selected adult walked, boarded, flew and arrived at the second terminal. A one-airport flight request and overlap with an exchange were rejected without mutation. City hall still worked. At 1280x640, Book flight remained above the toolbar. Regional settlement of 1000000 and focus of 64 residents passed. Separate synthetic profiles and new artifact names preserve earlier media. Flight and arrival screenshots were inspected.

Road media verification executed:

```sh
python3 deploy/verify_road_mixed_media.py --only road-mixed
```

Observed exit 0. H264, 960x544, 119 frames, 69.312 seconds, 2598998 bytes. Every frame decoded; start/middle/near-end samples decoded. The decoded end frame was inspected and shows the surviving narrow road. The clip is below 6 MB. Airport media verification executed:

```sh
python3 deploy/verify_road_mixed_media.py --only road-mixed-airport
```

Observed exit 0. H264, 960x544, 40 frames, 22.337 seconds, 1581082 bytes. Every frame and start/middle/near-end samples decoded. The decoded near-end frame was inspected. The airport clip is below 6 MB.

Source preservation: SHA-256 checks passed for all production source files captured before testing. Forty-four reviewed-base airport/cargo files and the airport progress entry match base `5e05822fdedde872935214c75dbe7fdd34425201`. `CityFrame.java` is the one expected changed file in the old 45-file list; it appends format-13 ownership and preserves format-12 airport fields. New preservation JSON reports record these facts. `git diff --check` passed; `git ls-files -u` was empty. Final source SHA-256, base airport preservation and diff checks passed after native testing. Old reports and clips are historical and do not prove this revision. Controller publication and independent review remain required. No developer commit, push, merge or deployment occurred.
