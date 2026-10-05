# City protocol compatibility verification

The protocol 15 client now reconnects using protocol 14 when a pinned TLS server
rejects version 15 and advertises version 14. Protocol 14 uses the same snapshots
and existing city command encodings; the version 15 change adds SPECIAL commands
and permits special-building snapshot types. SPECIAL commands are blocked locally
on version 14 connections. Other incompatible versions remain rejected, with
client/server version numbers and update guidance.

## Actual local checks (Linux, Java 25)

Maven was not on PATH. Used `/tmp/apache-maven-3.9.11/bin/mvn` with a copied,
workspace-local dependency cache, offline mode, and workspace-local temporary files.

Passed:

```
/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local=.build-cache \
  -Djava.io.tmpdir="$PWD/target/tmp" \
  "-DargLine=-Djava.io.tmpdir=$PWD/target/tmp" --batch-mode \
  -Dtest=CityTest,SpecialBuildingsTest,CityToolsTest verify
```

20 tests, zero failures/errors. BUILD SUCCESS; client, server and launcher JARs
packaged. Both production and all test sources compiled.

Blocked by environment:

```
/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local=.build-cache \
  -Djava.io.tmpdir="$PWD/target/tmp" \
  "-DargLine=-Djava.io.tmpdir=$PWD/target/tmp" --batch-mode \
  -Dtest=ProtocolCompatibilityTest,CityMultiplayerTest,MultiplayerTest test
```

12 tests: zero assertion failures, 11 errors, one passed. The sandbox denies
listening sockets (`java.net.SocketException: Operation not permitted`). Earlier
attempts also hit the read-only default `/tmp`; moving temporary files into the
worktree resolved that filesystem issue without relaxing sandbox controls.

`git diff --check` passed.

## Required controller/reviewer follow-up

Run `mvn --batch-mode verify` in a test environment permitting loopback sockets.
New TLS regression tests cover protocol 14 login and registration, snapshot
loading, bidirectional command delivery, SPECIAL suppression, and rejection of
protocols 13 and 16. Existing multiplayer tests cover same-version authentication
and city synchronization. These network assertions have compiled but have not
executed successfully here.

Live City server protocol and owner login were not verified. No deployment,
commit, push, or release was performed. This fix covers a protocol 15 client
connecting to protocol 14; genuinely incompatible server versions still need a
matching release. Changes remain in the assigned feature worktree for review.

## Recovery against current base (supersedes protocol-15 scope above)

Resolved working-file conflicts using current base protocol 16. Retained all base
progress entries, building direction guide, its tests and evidence, and business
catalog support. The controller must stage these resolutions and continue the
rebase; no staging, rebase continuation, commit, or push was performed here.

The protocol 16 client now explicitly retries only protocols 14 and 15. Both
initial and streamed city frames use format 6 for those connections; protocol 16
continues to use format 7 with saved business catalogs. SPECIAL is disabled only
below protocol 15. TLS pinning is unchanged. Regression tests now exercise login
and registration against both legacy versions and reject protocols 13 and 17.

Actual recovery commands (Linux, Java 25, offline workspace-local Maven cache):

```
/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local="$PWD/target/recovery-m2" \
  -Djansi.tmpdir="$PWD/target/tmp" -Djava.io.tmpdir="$PWD/target/tmp" \
  "-DargLine=-Djava.io.tmpdir=$PWD/target/tmp" --batch-mode \
  -Dtest=CityTest,SpecialBuildingsTest,CityToolsTest,BusinessCatalogTest verify
```

PASS: 33 tests, zero failures/errors; BUILD SUCCESS, all test sources compiled,
client/server/launcher packaged. CityToolsTest includes the base direction guide.

The same Maven invocation with
`-Dtest=ProtocolCompatibilityTest,CityMultiplayerTest,MultiplayerTest,CityTest,SpecialBuildingsTest,CityToolsTest,BusinessCatalogTest test`
finished with 45 tests, zero assertion failures, 11 environment errors. Network
checks remain blocked by `SocketException: Operation not permitted` when opening
loopback listeners. This run preceded expanding the legacy regression to include
protocol 15; the final expanded test was also rerun separately.

Live City protocol has not been established, and owner login has not been
successfully demonstrated. Approval still requires external loopback-capable
validation and a sanitized live protocol check. Do not infer the live protocol
from source or release version. Preserve the rebase state and all local edits.

Final expanded `-Dtest=ProtocolCompatibilityTest test` result: 2 tests, zero
assertion failures, 2 socket-permission errors; BUILD FAILURE. Final working files
contain no conflict markers and `git diff --check` passes. The index deliberately
still records unresolved paths until the controller stages the resolutions.

## Controller handoff checkpoint (2026-10-04)

Rechecked preserved worktree: `git diff --check` passed; no conflict markers
remain under `src/` or `dashboard/`. Index conflicts remain intentionally pending
controller staging. All implementation and evidence edits are preserved.

Next steps for the controller:

1. Stage resolved files and continue the existing rebase without reset or abort.
2. Run `mvn --batch-mode verify` in an environment permitting loopback listeners,
   including ProtocolCompatibilityTest, CityMultiplayerTest and MultiplayerTest.
3. Obtain a sanitized, read-only check of the affected City endpoint's actual
   advertised protocol. Do not access accounts, credentials or full runtime logs.
4. Return test results and the protocol diagnostic for assessment before review.

No new network tests or live checks were performed in this checkpoint turn.
Successful live login and passing network checks remain unverified.


## Authorized VPS validation (2026-10-04; supersedes prior environment blocker)

The controller staged all preserved resolutions and completed the existing rebase
onto `1ed2458`. Every pre-existing base progress item is retained without a field
change. The implementation remains on its feature branch; no production deployment
or merge was performed.

Linux VPS, Java 25.0.3, Maven 3.9.11, offline worktree-local dependency cache:

```sh
/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local=target/recovery-m2 \
  -Djansi.tmpdir=target/tmp -Djava.io.tmpdir=target/tmp \
  '-DargLine=-Djava.io.tmpdir=target/tmp' --batch-mode \
  -Dtest=ProtocolCompatibilityTest,CityMultiplayerTest,MultiplayerTest,CityTest,SpecialBuildingsTest,CityToolsTest,BusinessCatalogTest verify
```

PASS at 16:22:56 UTC: **45 tests, zero failures/errors/skips; BUILD SUCCESS**.
The client, server and launcher JARs packaged successfully. After extending the
legacy fixture to send a format-6 `CITY_STATE` before the road acknowledgement,
`-Dtest=ProtocolCompatibilityTest test` also passed at 16:24:28 UTC: **2 tests,
zero failures/errors/skips; BUILD SUCCESS**. The final source contains this extra
stream-alignment assertion path. The additional run recompiled all test sources.

The successful legacy fixture exercises login and registration for both server
protocols 14 and 15, reconnect with the same TLS pin, initial and streamed city
frame format 6, command alignment, SPECIAL suppression only on protocol 14, and
rejection of protocols 13 and 17. Current-version multiplayer tests exercise
actual temporary-server account authentication, city synchronization and existing
protocol behavior. These are local fixture authentications, not owner login to
the live City server.

Sanitized live diagnostic at the same VPS runtime:

- `voxel-city-one.service` is an active **user** service bound to port 25566.
- A TLS inspection followed by a connection pinned to that inspected certificate
  sent only the protocol magic and requested protocol 16, with no auth operation,
  username or password. The reply had valid magic, advertised **protocol 14**,
  and rejected the mismatched handshake.
- A separate connection with a deliberately wrong pin was rejected.
- This observed the current peer certificate; it does not assert independent
  owner confirmation of that certificate or successful live account login.
- No account database, TLS private key, service credentials or runtime logs were
  read or attached. No production source, save, service or release was changed.

The advertised protocol confirms that the explicit protocol-14 reconnect path is
relevant to this endpoint. **Live owner login remains untested.** Independent review
of the revised exact PR head and platform CI is still required before merge.

## Recovery onto protocol 18 base (2026-10-04)

A subsequent controller-preserved rebase is onto base `a3c1513`, protocol 18.
Resolved Protocol.java and progress.json working-file conflicts, retaining all
current base milestones and features. Index resolutions are left for controller
staging and rebase continuation.

Preserved protocol 18 while adapting legacy reads: protocols 14/15 omit the
terrain generator field and now use Terrain.LEGACY_VERSION; their city snapshots
use format 6. Current protocol 18 uses format 8. Capital, exchange and exchange
building commands are blocked on legacy connections; existing protocol-15 SPECIAL
support is retained. The streamed fixture from controller head 98ded1 is retained.
Tests now also assert legacy terrain and suppression of newer commands.

Actual Linux sandbox, Java 25, Maven 3.9.11 commands:

```
/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local="$PWD/target/recovery-m2" \
  -Djansi.tmpdir="$PWD/target/tmp" -Djava.io.tmpdir="$PWD/target/tmp" \
  "-DargLine=-Djava.io.tmpdir=$PWD/target/tmp" --batch-mode \
  -Dtest=CityTest,SpecialBuildingsTest,CityToolsTest,BusinessCatalogTest verify
```

PASS: 36 tests, zero failures/errors; BUILD SUCCESS and packaged artifacts.
The final additional legacy assertions were added afterward and compiled during
the following actual command:

```
/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local="$PWD/target/recovery-m2" \
  -Djansi.tmpdir="$PWD/target/tmp" -Djava.io.tmpdir="$PWD/target/tmp" \
  "-DargLine=-Djava.io.tmpdir=$PWD/target/tmp" --batch-mode \
  -Dtest=ProtocolCompatibilityTest,CityMultiplayerTest test
```

BLOCKED: 3 tests, zero assertion failures, 3 environment errors; listening sockets
raise `SocketException: Operation not permitted`. No sandbox controls changed.
`git diff --check` passed, and no conflict markers remain in src/dashboard.

Playtest: affected workflow is the desktop application's TLS login, legacy city
snapshot loading and subsequent command synchronization. Browser playtesting does
not exercise this desktop application. Attempted the TLS integration fixtures with
temporary synthetic test accounts (no owner accounts). Expected: legacy login and
registration complete, initial and streamed frames decode, allowed road commands
arrive, unsupported commands stay local. Observed: fixture listeners cannot bind;
workflow execution stops before authentication. No successful desktop playtest,
edge-case playtest, regression playtest or screenshot/video was captured from
this revised implementation. These checks are NOT passed.

Controller's earlier 45/45 and 2/2 passing runs and pinned live protocol-14 probe
remain evidence for head 98ded1, not proof of this newer protocol-18 recovery.
Required next steps: controller stages resolutions and continues the rebase, runs
network checks on the resulting head, exercises desktop login/legacy snapshots,
unsupported-command edge case and current-protocol regression with synthetic
accounts, and captures sanitized implementation media. Save media under
`dashboard/evidence/` and publish using the existing authorized progress artifact
path. Attach its actual durable HTTPS URL. No media URL exists for this task yet.
Independent review must exercise the final head and inspect that media.

## Reviewer base-preservation recovery

Rebase worktree HEAD is required base 431ff142666f791c4323882075834f3390685153.
Working changes are limited to MultiplayerClient.java, Protocol.java, the legacy
ProtocolCompatibilityTest, this report and the City progress entry. Market
pricing/supplier/meal/labour/wage code, market tests/fixtures/evidence and website
queue sources/tests match that base. This supersedes previous recovery scope.

Resolved progress JSON while retaining all base entries. The base itself contains
committed Updated upstream/Stashed changes markers. Within progress.json chose
the newer queue record (2026-10-05T16:07:43.701252+00:00), then added the City item.
Other marker-bearing queue evidence and generated website JSON remain identical
to base; no unrelated implementation changes were made.

Executed git diff --check: passed. Progress JSON parses. No source conflict
markers remain. The index still records progress.json as unmerged for controller
staging. No tests or media were rerun in this recovery handoff; previous results
do not validate the resulting head. Controller must stage and continue rebase
without publishing, then resume final-source workflow checks and fresh media.
## Renewed recovery checkpoint (2026-10-05)

Resolved the remaining evidence and progress working-file conflicts. Kept all
current-base milestones and both validation histories. No index staging, commit,
rebase continuation, push, deployment or merge was performed.

Executed the focused seven-class Maven verify command above using Linux, Java
25.0.3, Maven 3.9.11 and target/recovery-m2. BusinessCatalogTest (11), CityToolsTest
(9), CityTest (10) and SpecialBuildingsTest (6) passed: 36 tests.
ProtocolCompatibilityTest had 2 socket-permission errors; CityMultiplayerTest had
1 socket-permission error, all before authentication. Zero assertion failures in
these completed classes. MultiplayerTest completed with 15 tests, 13 errors and zero assertion failures.
Final total: 54 tests, zero assertion failures, 16 errors; BUILD FAILURE (exit 1).
No successful full verify or packaging is claimed for this run.

Playtest: invoked synthetic TLS fixtures for legacy login/registration and initial
and streamed snapshots, incompatible-version edge cases and current-version City
synchronization regression. Expected: authentication and command/frame assertions
complete. Observed: listening sockets denied with Operation not permitted. Desktop
workflow and implementation image/video remain unexecuted and unavailable. Browser
playtesting does not apply to this desktop TLS workflow. Controller execution and
media capture are already authorized; no live owner account login is required.

Delegation service became unavailable during evidence reconciliation; reconciliation
was inspected locally. No delegate output is used as test evidence.

## Network-enabled recovery validation (2026-10-05)

Linux, Java 25.0.3, Maven 3.9.11; worktree HEAD 762fd97b with preserved
protocol-18 recovery files. No rebase continuation or index staging performed.
A Python socket.bind/listen probe on 127.0.0.1 succeeded.

Executed:

```sh
/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local="$PWD/target/recovery-m2" -Djansi.tmpdir="$PWD/target/tmp" -Djava.io.tmpdir="$PWD/target/tmp" "-DargLine=-Djava.io.tmpdir=$PWD/target/tmp" --batch-mode -Dtest=ProtocolCompatibilityTest,CityMultiplayerTest,MultiplayerTest,CityTest,SpecialBuildingsTest,CityToolsTest,BusinessCatalogTest verify
```

Playtest: ran the production MultiplayerClient against temporary synthetic TLS
servers. Requested behavior: protocol-18 client retries pinned protocol 14/15
login and registration, decodes initial and streamed format-6 city snapshots,
uses legacy terrain and receives road acknowledgements. Edge case: incompatible
protocols rejected and newer commands suppressed locally. Regression: current
City server authentication and synchronization. Expected: fixture assertions pass.
Observed: ProtocolCompatibilityTest 2/2 and CityMultiplayerTest 1/1 passed, zero
failures/errors. Non-network classes also passed 36 tests. Browser playtesting
does not apply to this desktop network integration. Desktop UI playtest and
sanitized implementation media still await the authorized controller capture.
No live owner account login was attempted. No private runtime logs attached.

Final result: 54 tests, zero failures/errors/skips; BUILD SUCCESS (exit 0).
MultiplayerTest passed 15/15. Client, server and launcher packaging completed.
These results cover the preserved recovery working files, not a completed rebase
head. Controller must stage and continue the rebase and supply desktop media.

## Controller Git recovery handoff

Working files have no conflict markers; progress JSON parses and git diff --check
passes. The index still records the two evidence/progress paths as unmerged.
Controller must stage resolved contents and continue the preserved rebase without
publication, then resume development for resulting-head tests and media. Prior
54-test results remain scoped to pre-rebase working files. This is an authorized
Git recovery handoff, not an owner blocker or final readiness claim.

## Second reviewer recovery handoff

Merged both report histories. All prior tests remain historical and do not
validate this ongoing rebase. Kept the reviewer-required base-preservation
requirements and every non-City progress entry from the current rebase side.
Only evidence/progress paths required resolution at this step. Controller must
stage and continue without publication, then resume resulting-source validation
including market regressions, website checks, City login and fresh media.
No workflow checks or media were executed during this handoff.
## Recovered-head verification (2026-10-05)

Source inspected at recovered head `979ee5c1c0c3de6218e80c8de82a26fce12562be`.
Protocol 18 and current base features are retained. CityToolsTest includes the
eight-target direction guide and road-endpoint regression checks. Legacy 14/15
reads use terrain legacy version and city frame format 6; newer capital/exchange
commands are suppressed. TLS certificate pinning remains in the connection path.

Linux, Java 25.0.3, Maven 3.9.11, offline worktree-local cache. Executed:

```sh
/tmp/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local="$PWD/target/recovery-m2" -Djansi.tmpdir="$PWD/target/tmp" -Djava.io.tmpdir="$PWD/target/tmp" "-DargLine=-Djava.io.tmpdir=$PWD/target/tmp" --batch-mode -Dtest=ProtocolCompatibilityTest,CityMultiplayerTest,MultiplayerTest,CityTest,SpecialBuildingsTest,CityToolsTest,BusinessCatalogTest verify
```

Observed: **54 tests, zero failures/errors/skips; BUILD SUCCESS (exit 0)**. Client,
server and launcher JARs packaged. This is a fresh run on recovered production
source, not reuse of the pre-rebase 54-test run.

Playtest: production MultiplayerClient against synthetic pinned TLS fixtures.
Requested behavior: protocol-18 client logs in and registers against protocols
14 and 15, loads initial and streamed legacy snapshots and receives road results.
Edge cases: unsupported protocol rejection, capital/exchange guards and SPECIAL
blocked only below 15. Regression: current-protocol authentication and City
synchronization plus existing multiplayer and direction guide tests. Expected
and observed: all assertions pass (legacy 2/2, City multiplayer 1/1, multiplayer
15/15 and non-network 36/36). Browser playtesting does not apply to this desktop
application. The earlier sanitized controller diagnostic established that the
affected live endpoint advertises 14; no owner account login is claimed.

Desktop media uses the actual LoginDialog and Main with a synthetic pinned TLS
server, fresh checkout-local settings, and Java Robot screen capture. Password
fields are cleared before capture. No private account files or live credentials
are used. The fixture source is CityProtocolMedia.java. Reproduction command:

```sh
python3 deploy/capture_city_protocol.py
```

The script compiles the fixture and starts a checkout-local Xvfb using software
OpenGL at 1280x900. Because /tmp is read-only, its copied binary redirects /tmp
strings to ./tm and uses a copied keyboard directory, all under target/. No
sandbox controls are changed. The fixture signs in through the actual dialog.
Protocol 14 requires a pinned reconnect and READY after its city snapshot.
Protocol 16 is deliberately incompatible and remains on the login error screen.
Media artifacts are new captures from this recovered implementation and await
controller publication on the assigned feature branch.

Capture command completed with exit 0. Observed protocol-14 fixture READY and
actual rendered City world/HUD after closing the first-run controls menu. The
protocol-16 dialog shows client 18/server 16 with server update guidance and an
empty password field. Both images were visually inspected.

- Actual protocol-14 login reaching City world: 763730 bytes. Pending controller publication: https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-33356462633030392d326535322d343064332d396463632d366438373831373162393833/dashboard/evidence/city-protocol-legacy-login.png
- Actual incompatible-protocol login error: 11290 bytes. Pending controller publication: https://raw.githubusercontent.com/jaymsDooku/voxel-one/feature/queue-33356462633030392d326535322d343064332d396463632d366438373831373162393833/dashboard/evidence/city-protocol-incompatible.png

No production deployment or owner account login was performed. Final readiness
is for independent review, not developer approval or merge.

## Third reviewer recovery handoff

Preserved both report sides and capture source/media. The recovered-head section
above describes historical 979ee5c validation, which reviewer feedback found
insufficient to establish unrelated base-feature preservation. It is not evidence
for the current required-base rebase. All non-City progress entries are retained.
Final resulting-source checks must include market and website regressions and
fresh protocol-login media. No workflow checks or captures ran in this handoff.
Controller must stage resolved files and continue without publication.

## Required-base validation complete

See city-protocol-final-validation.md for fresh source validation at 735e96b,
77 passing Java tests plus packaging, 10 website checks and new desktop images.
This supersedes all historical recovery/test sections above. Independent review
and controller publication remain required; no developer approval is claimed.
