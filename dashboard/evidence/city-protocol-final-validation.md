# City protocol compatibility: required-base validation

Production source: recovered head 735e96bb410e8d7801c44cf500ccd72c388dcc82,
rebased onto reviewer-required 431ff142666f791c4323882075834f3390685153.
All market code, tests, fixtures and evidence, and website queue code/tests are
byte-identical to that base. Every unrelated progress entry is preserved after
resolving the base's committed marker text to its newer queue record. Changed
paths are restricted to login compatibility and its tests/media/progress.

Environment: Linux, Java 25.0.3, Maven 3.9.11, checkout-local offline cache and
temporary files. Executed:

```sh
/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local="$PWD/target/recovery-m2" -Djansi.tmpdir="$PWD/target/tmp" -Djava.io.tmpdir="$PWD/target/tmp" "-DargLine=-Djava.io.tmpdir=$PWD/target/tmp" --batch-mode -Dtest=ProtocolCompatibilityTest,CityMultiplayerTest,MultiplayerTest,CityTest,SpecialBuildingsTest,CityToolsTest,BusinessCatalogTest,MarketEconomyTest,ExchangeLabourMarketTest,ShopRestockMarketTest verify
node --test website/test/*.test.mjs
```

Website result: 10 tests passed, zero failures, exit 0. These noninteractive
queue/package integrations exercise preserved website code; browser playtesting
is not relevant to the desktop login fix.

Base-preservation checks compare the changed-file list against the City-only
allowlist and compare all non-City progress records with normalized base JSON.
Both checks passed. The base's unrelated evidence and generated website JSON
with pre-existing markers remain byte-identical; those files are not changed
by this login fix.

Java result: **77 tests, zero failures/errors/skips; BUILD SUCCESS, exit 0**.
Client, server and launcher JARs packaged. MarketEconomyTest 13/13,
ExchangeLabourMarketTest 6/6 and ShopRestockMarketTest 4/4 pass. These include
market pricing, affordable supplier fills, meal nutrition/seller accounting,
labour offers/payroll, restock depletion/budget cases and NaN wage rejection.
CityToolsTest 9/9 retains direction-guide coverage.

Playtest: production MultiplayerClient connects to synthetic pinned TLS peers.
Requested behavior: protocol-18 login and registration retry 14/15, initial and
streamed format-6 city snapshots decode, legacy terrain loads and road responses
remain aligned. Edge cases: protocols 13/17 reject; newer capital/exchange
commands stay local and SPECIAL is allowed only from 15. Regression: current
protocol City authentication/synchronization plus multiplayer. Expected: all
fixture assertions pass. Observed: ProtocolCompatibilityTest 2/2,
CityMultiplayerTest 1/1 and MultiplayerTest 15/15 pass.

Desktop Playtest command:

```sh
python3 deploy/capture_city_protocol.py
```

The fixture uses actual LoginDialog and Main, a synthetic loopback TLS peer,
and a fresh checkout-local user.home per run. No private accounts/settings
are read. Java Robot captures a 1280x900 Xvfb desktop with software OpenGL.
The peer accepts protocol 14 only after the pinned reconnect and initial city
snapshot READY. The separate protocol-16 peer produces the real mismatch
message for the incompatible-version edge case. Password fields are cleared
before capture. Browser playtesting does not apply to this desktop application.
No live owner login or production deployment is claimed. The earlier sanitized
controller probe established that the affected endpoint advertises protocol 14.

Desktop capture completed with exit 0. Observed: pinned protocol-14 login
reaches the rendered City world/HUD after Resume; protocol 16 remains on the
actual mismatch screen (client 18/server 16), with server update guidance and
an empty password field. Both fresh images were visually inspected.

- city-protocol-legacy-login.png: 763510 bytes; SHA-256 ce4b9ac01cf25c2faeb7be9255d03d0104e0f6354d7e797931336c9be032ba2a. Pending controller publication: https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-33356462633030392d326535322d343064332d396463632d366438373831373162393833/dashboard/evidence/city-protocol-legacy-login.png
- city-protocol-incompatible.png: 11997 bytes; SHA-256 c5519ea0bd363cc80561a7384a64ba2d86a3d82dc74de8cc58fba96989e80929. Pending controller publication: https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-33356462633030392d326535322d343064332d396463632d366438373831373162393833/dashboard/evidence/city-protocol-incompatible.png

Capture helper now creates a fresh synthetic settings directory each run.
Compilation through the capture script passed. git diff --check and Python
compilation passed. These results supersede historical pre-rebase validation.
No staging, commit, push, deployment or merge was performed by the developer.
