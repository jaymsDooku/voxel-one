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
