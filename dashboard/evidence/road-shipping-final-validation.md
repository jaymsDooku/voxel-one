# Road and shipping final validation

Rebased source starts at 9bbad6b07b362fb797c03a759ba250590eb2c4eb. Road snapping, chained placement, highlighted selection and edit/delete remain. Each road owns its cells/type/surface/paint order. Terrain generator 3, coastal ports type 24, ocean generation and shipping rendering remain. Protocol 24 carries snapshot 13; old format-12 magic 0x4349543C loads; new saves use 0x4349543D. Port gate is <23; road actions gate is <24. Runway/flight IDs 10/11 and road IDs 12/13 remain distinct. Older saves lack hidden crossing history; their ownership is inferred and marked inferred. New ownership survives exact save/network roundtrips.

Linux, Java 25, Maven 3.9.11, Linux natives, /tmp/voxel-m2 cached dependencies. Native tests use production Main, inherited role DISPLAY/XAUTHORITY, Mesa and fresh isolated synthetic profiles under target/road-shipping-final-*. No authentication data is read. This is a native GLFW game; browser playtesting does not apply.

## Verification

Executed full command:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn --batch-mode -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" verify > target/road-shipping-final-verify.txt 2>&1
```

The first run found two stale BusinessCatalogTest resave magic assertions. RegionalSaveCompatibilityTest had the same stale magic/reader-version assertions. They now expect new format 13 while old input fixtures remain unchanged. The run was stopped with Ctrl-C, exit 130; it is not counted as passed. The same full command was run again with output redirected to target/road-shipping-final-verify-corrected.txt. The corrected broad command ended with exit 143 after 42 complete suites (248 tests) passed with no failures/errors. It is incomplete and not a passing full verify. All 21 remaining test classes are now executed in three bounded completion groups; actual commands and suite results are recorded per group. Packaging is checked separately. Platform CI will rerun after controller publication; no new remote CI success is claimed.

## Compatibility CLI

Executed:

```sh
javac -cp target/classes -d target deploy/RoadShippingCompatibilityProbe.java
java -Djava.io.tmpdir="$PWD/target/tmp" -cp target/classes:target RoadShippingCompatibilityProbe target/road-shipping-final-probe
```

Expected and observed: generator-3 world save reopens; literal format-12 snapshot with port type 24 and mixed-width road cells loads unchanged; format-13 exact road ownership and port roundtrip passes; old12 resave uses magic3D and retains port. Exit 0. The initial fixture lacked a required property address and failed with Missing property address; that failed probe is not counted. The corrected synthetic fixture includes its address.

## Playtest: roads, mixed widths, shipping and airport

Native commands use this wrapper, with one scenario at a time on the assigned display:

```sh
python3 deploy/run_road_shipping_final_smoke.py --display "$DISPLAY" --scenario road
```

Road run exited 0. Expected and observed original workflow: dirt and paved 2/3/4-lane placement, direction guide, chain endpoints, highlighted selection, widen/narrow, delete preserving neighbor, zone-edge snap and overlap rejection, upgrade and cancellation without spending.

Mixed run executed:

```sh
python3 deploy/run_road_shipping_final_smoke.py --display "$DISPLAY" --scenario mixed
```

Expected: 147-cell wide section; delete wide leaving exactly 63 narrow crossing cells; reverse build order, narrow wide leaving exactly 117 union cells; delete edited road leaving the 63-cell narrow survivor. Site is leveled only in the synthetic fixture so shoulders and surfaces are visible. Observed exit 0: all exact cell counts passed, with narrow survivor type intact. Fresh deletion screenshot inspected.

Shipping run executed:

```sh
python3 deploy/run_road_shipping_final_smoke.py --display "$DISPLAY" --scenario shipping
```

Expected: inland port rejects without mutation, valid coastal site shows terminal, docked carrier and cargo; reverse-angle rendering and existing college permit work. Observed exit 0: inland rejection was atomic, coastal port and carrier rendered, reverse view and college permit passed. Port/carrier screenshot inspected.

Airport run executed:

```sh
python3 deploy/run_road_shipping_final_smoke.py --display "$DISPLAY" --scenario airport
```

Expected: airport permit/expansion, single-airport flight rejection without mutation, adult boarding/flight/arrival, exchange-overlap rejection, City hall, compact menu and regional UI regressions. Observed exit 0 after adapting the test driver to the preserved 13-row shipping menu. All requested stages and UI regression assertions passed. Base media stays intact; prior clips are historical, not evidence of this source. Controller stages/publishes artifacts. No developer commit, push, merge or deployment occurred.

Media command executed for road and mixed scenarios:

```sh
python3 deploy/verify_road_shipping_final_media.py --scenario road
python3 deploy/verify_road_shipping_final_media.py --scenario mixed
```

Road decode exited 0: H264 960x544, 129 frames, 116.659 seconds, 3439003 bytes. Every frame and start/middle/near-end samples decoded; near-end frame and snap screenshot inspected. Mixed decode exited 0: H264 960x544, 118 frames, 93.414 seconds, 2579749 bytes. All frames and start/middle/near-end samples decoded. The near-end frame shows the narrow survivor and was inspected. Each published clip must stay below 6 MB.

The first airport run failed Airport menu action before placement. The preserved pre-port aviation driver used 12 menu rows; production shipping adds a thirteenth. The final wrapper now applies the exact test-driver row adaptation already used by run_shipping_aviation_smoke.py, leaving production and the historical aviation harness unchanged. The corrected airport run exited 0, including permit/expansion, single-airport rejection, exchange overlap rejection, adult boarding/flight/arrival, compact menu, City hall and regional UI.

Shipping video verification:

```sh
python3 deploy/verify_road_shipping_final_media.py --scenario shipping
```

Exit 0: H264 960x544, 30 frames, 24.188 seconds, 916896 bytes; every frame and start/middle/near-end samples decoded.

Bounded test completion commands:

```sh
python3 deploy/run_road_shipping_final_test_group.py --group 0
python3 deploy/run_road_shipping_final_test_group.py --group 1
python3 deploy/run_road_shipping_final_test_group.py --group 2
```

Group 0 exited 0: 15 multiplayer tests passed. Group 1 exited 0: 35 tests passed. Group 2 Maven exited 0: 58 tests passed. Its wrapper initially failed while collecting CityStockExchangeTest because it assumed package dev.jayms. The collector now resolves the unique suite name; existing fresh results were collected without claiming a rerun. No unexecuted check is counted as passed.

Airport video verification executed:

```sh
python3 deploy/verify_road_shipping_final_media.py --scenario airport
```

Airport decode exited 0: H264 960x544, 40 frames, 27.8 seconds, 1610075 bytes. All frames and start/middle/near-end samples decoded. The near-end frame and flight screenshot were inspected. All four new videos are below 6 MB and show this implementation. Original road, mixed-width deletion, port/carrier and airport flight media were visually inspected.

Source SHA-256 checks pass: production source stayed unchanged throughout native runs; corrected source/tests stayed unchanged during bounded completion. Base shipping progress remains intact. Final packaging and aggregate checks passed.

All 63 test classes are covered exactly once in the aggregate: 356 tests passed, zero failures/errors/skips. The broad process exit143 remains explicitly incomplete, not passed; the missing classes were completed in bounded Maven test runs. Full coverage and commands are recorded in road-shipping-final-tests.json. Packaging verify exited 0 separately; it compiled and packaged artifacts with tests skipped because all 63 classes were already exercised:

```sh
MAVEN_OPTS="-Djava.io.tmpdir=$PWD/target/tmp" /tmp/apache-maven-3.9.11/bin/mvn --batch-mode -q -Dmaven.repo.local=/tmp/voxel-m2 -Dlwjgl.natives=natives-linux -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -DskipTests verify > target/road-shipping-final-packaging.txt 2>&1
```

Final checks: corrected source/test SHA-256 remained unchanged; 59 shipping/terrain base files match, with one intended current-protocol handshake-test update. Shipping and airport progress entries are preserved. JSON/test coverage, clip sizes and git diff --check pass. No unmerged index remains. New artifacts are pending controller publication and independent exact-head review. Remote platform CI has not yet run on this local submission.
