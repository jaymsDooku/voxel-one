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
