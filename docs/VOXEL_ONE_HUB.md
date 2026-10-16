# Voxel One Hub

The packaged preview opens the Swing hub. With a direct launcher JAR, use
`--hub` or `-Dvoxel.hub.preview=true`; ordinary legacy invocation keeps its
existing updater/login flow. Home, Games,
details, Engine & Tools, Downloads and Settings / Help use the same public
catalog. City and Sandbox retain the verified shared client updater and existing
save, control, username and certificate-trust locations. Explicit game selection
locks the legacy login selector so it cannot silently join a different game.

Native automation remains supported:

```sh
java -jar voxel-one-launcher.jar --update-only
java -jar voxel-one-launcher.jar --experience city --offline
java -jar voxel-one-launcher.jar --experience sandbox --server localhost --port 25565
java -jar voxel-one-launcher.jar --experience engine-editor --offline
```

The CLI accepts native experiences only. Web games open from the hub in the default
browser. Passwords are never added to arguments. Sign-in and certificate trust
remain in the native client; website sign-in remains in the browser.

## Catalog contract

`src/main/resources/dev/jayms/launcher/catalog.properties` is the offline library.
The public promotion destination is the `experience-catalog` branch's root
`catalog.properties`. The hub bounds it to 65,536 bytes and 64 entries and checks
its format, channel, IDs, platform names, text lengths, source IDs, URLs and launch
targets before replacing its cache atomically. Duplicate IDs, malformed metadata
and unsafe web targets are rejected. Unknown adapters and native targets are
shown as unsupported and cannot launch. An empty published catalog keeps saves
and installed clients. Original cover art is drawn locally, with a generic cover
for future games. Artwork is bundled or generated locally from the cached catalog style ID.
The current catalog does not fetch remote artwork.

Native adapter targets are `city`, `sandbox` and `editor`; all use artifact
`voxel-client`. Browser/docs URLs use HTTPS and the fixed project website or
repository host/path allowlist. Arbitrary commands, passwords and shell text
have no catalog fields. New entries using these adapters need no UI code.

`tools/hub/publish_catalog.py` writes a promotion candidate from separate native
and web JSON receipts. Each receipt requires `tested: true`, `published: true`,
`source` (40-character commit), `build` and public one-line `notes`. The web
receipt also requires the supported published `url`. The publication controller
must verify receipts against actual checks and deployment before promoting the
candidate. A website-only build updates only its own metadata. This tool does
not deploy, push, assert publication from a merge, or scrape development tickets.
Browser Play stays disabled until a promoted catalog records its published source.
The bundled catalog marks source provenance as `unpublished`; it does not assert
that this feature branch has shipped. The public catalog branch is not created
by this implementation.

## Packages and shell updates

Run `mvn package`, then on each target platform:

```sh
python3 tools/hub/package.py --jdk /path/to/jdk --output target/hub-package
python3 tools/hub/package.py --jdk /path/to/jdk --output target/hub-installer --installer
```

Use a full supported JDK (17 or newer), with `jmods`. App images include native
launchers, Java desktop/HTTP/crypto/networking and the modules needed by the game.
The runtime retains `bin/java` for the child client JVM. Windows portable ZIPs
need no separate Java installation. Windows MSI packaging requires WiX and adds
desktop and Start-menu shortcuts. macOS and Linux packages must be built on their
own platform. The runtime legal directory and `NOTICE.txt` remain in the package.

The stable `Bootstrap` opens the bundled or last verified shell first. A background
stage uses a separate `hub-shell` cache and `shell.properties`. It verifies size,
SHA-256, JAR entry point, trusted release URL and bounded download before an atomic
state update. Immutable paths include both source revision and artifact SHA-256. Legacy
verified flat cache files are reused and copied into the new layout without
download or deletion; offline lookup still accepts the old layout.
New versions take effect on the next start; the active immutable
JAR is never deleted or replaced. Failed staging retains the old version. The
client and shell have separate installed metadata and locks.
`tools/hub/shell_manifest.py` assembles the separate shell release entry. Existing
`update.properties` remains compatible. Existing users need a one-time download
of the native hub package; the old bootstrap cannot replace itself. No final
new-package download link exists until the controller's tested release succeeds.

## Current validation limits

This is an opt-in feature preview, not a qualified desktop release. Normal
release CI does not automatically promote the Hub. Linux checks use the assigned
authenticated X11 desktop and isolated synthetic profiles. Current Linux captures
include 1280x800 and small logical desktops; missing extents are recorded rather
than captured clipped. JVM scaling does not establish physical Windows DPI.

Hosted Windows/macOS updater, shell staging and synthetic TLS login have run.
Windows shortcut validation and hosted native GLFW creation failed in the last
published run; fixes/diagnostics need another exact-head dispatch. Mac Intel Hub
captures were unobstructed, while Windows/Mac ARM could not fit all target sizes.
The inspected production software WebKit report covers move/fire/pause/resume/
reload. Physical GPU sessions, actual monitor scaling, MSI shortcut confirmation,
full platform extents, two published shell generations and final publication
integration remain pending. No smooth-performance or release-readiness claim
follows from the sparse four-frame Linux render clips.

Native detail pages bundle real offline gameplay frames captured
from this candidate. The legacy login has a link to published desktop downloads. Install paths
and per-experience server ports are configurable. An async manifest probe exposes
available engine revisions, Update available and Offline cached states.

FlatLaf was evaluated from its official documentation: it supports Java 8+,
HiDPI, light/dark themes and an Apache 2.0 license. This candidate currently uses
standard Swing; a Windows accessibility/DPI prototype must validate the final
theme before a dependency is locked. References:
https://www.formdev.com/flatlaf/
https://docs.oracle.com/en/java/javase/17/jpackage/packaging-overview.html

## Fresh web assets

`tools/hub/fingerprint_web.py --source games/space-invaders --output target/web-build`
creates a separate static build. It fingerprints CSS and ESM filenames with a
whole-experience digest and rewrites references, including the terrain worker.
The build excludes test modules and documentation. The controller deploys this
build, verifies it in the real website, and records its source/build provenance.
Use a receipt URL ending in `?build=<20-character build digest>` so a previously
played game's HTML also has a fresh address. The catalog promotion tool preserves
that URL. `tools/hub/web-freshness-playtest.cjs` exercises two builds in the same
browser context against a localhost host with deliberately immutable caching;
this is a cache integration check, not a production deployment claim.
