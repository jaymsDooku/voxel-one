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

## Review correction: preservation and installer checks

The review at6a9187b found missing portal/alternate-Earth source, website assets
and historical evidence. Current integration retains the current-master versions;
all210 affected historical evidence files were verified byte-for-byte. The scoped
Hub workflow now explicitly runs portal/alternate-Earth/ground regressions and
checks their website/source module copies, preventing silent loss of these entry
points during another Hub change. A source browser playtest covers the final shot,
ready portal, pause/resume, walking to Earth and retained resources. It uses
synthetic setup and software WebKit; it is not physical GPU qualification.

The old Windows shortcut count mixed a synthetic USERPROFILE/LOCALAPPDATA with
Windows Installer's OS folder resolution. Installer and shortcut lookup now use
the real ephemeral OS context; only the launched app uses synthetic Java home
and cache. The check inspects the two expected own Desktop/Start-menu links and
requires both to target the installed executable. It does not enumerate other
shortcuts or alter execution policy. The corrected Windows check needs the next
exact-source hosted run; Linux success does not establish Windows MSI success.

### Engine/client compatibility

Native catalog entries require `client-compatibility=source-set-v1` and
`client-sources`, a list of at most eight full source revisions. The entry's
published `source` must be included. Each revision identifies a tested shared
engine/client release for that experience; this is an explicit compatibility
set, not a numeric range inferred from commit IDs. The publisher emits only the
native receipt's tested, published source. Adding older compatible sources
requires testing and a reviewed catalog change. Browser receipts do not change
this native contract. Artifact identity remains `voxel-client`, so experiences
with the same source still share one verified download.

The updater checks the allowed source set under its installation lock before
transferring or changing installed metadata. An incompatible global latest
release can fall back only to a verified compatible cached release. Both the
Hub and `--experience` CLI check the returned revision again before launching.
Turning off update checks does not bypass compatibility. Missing or unknown
contracts invalidate native catalog snapshots; the last valid snapshot or
bundled catalog supplies recovery. A mismatch gives a refresh/install message
and does not launch an incompatible client. The legacy updater and
`--update-only` without experience selection retain their existing behavior.

These corrections do not qualify stable rollout. Windows installation/MSI,
physical GPU/DPI, and upgrades across two published shell generations must
retain their release gates until actual platform evidence passes.

### Controller publication handoff

The Hub catalog and stable bootstrap use separate files on the fixed
`experience-catalog` branch: `catalog.properties` and `shell.properties`.
The shell manifest names an immutable GitHub release asset. It no longer depends
on the normal engine release's `latest` manifest; those releases do not publish
a Hub shell. The legacy native `update.properties` path is unchanged. Existing
bootstraps still require the documented one-time new-launcher download.

The read-only `Prepare Hub preview publication` workflow accepts the exact
reviewed feature source, a new `hub-preview-N` tag and the current destination
head (or `absent`). It builds and tests the shell, then uploads a controller
bundle. It has no write permission and does not create a release or branch.
The bundle includes `publication-plan.json`, both metadata files and the shell
JAR. Local dirty-source bundles are diagnostic only and must be regenerated
from committed reviewed source. Never publish their base SHA as exact source.

Controller sequence:

1. Verify independent review, qualification scope, feature SHA and destination
   branch head. Stop if either head differs from the plan. Preserve existing
   branch files. The expected-head field is a controller compare-and-swap
   precondition; this read-only tool does not perform the update itself.
2. Publish the immutable preview shell asset first. Never clobber an existing
   generation. Do not mark it latest or promote the Hub to stable.
3. Verify asset SHA-256 and size, then atomically promote the two metadata files
   through the fenced controller. No Java rebuild is needed for later catalog
   changes using already reviewed native/web receipts.
4. Run the read-only verifier shown below. All three fixed public URLs must
   deliver matching bytes. A 404 or mismatched artifact is a failed integration check. The tool
   prints no response bodies or signed redirect addresses.
5. Run actual packaged bootstrap sessions against public metadata for generation
   one, then a second reviewed published generation. Retain both immutable
   assets and old installation data. Fixture-generation checks are separate
   and do not satisfy this published-upgrade requirement.

Physical GPU/DPI, current-head Windows/macOS gameplay, actual public catalog
integration and two published shell generations remain release gates. A
prepared bundle, synthetic HTTP test or preview label does not complete them.

```sh
python3 tools/hub/publication_bundle.py verify --bundle BUNDLE_DIR --report REPORT_JSON
```

For a later web-only catalog update, the controller can run only the existing
assembler and promote `catalog.properties` with its branch-head fence. It must
retain the last verified `shell.properties` and shell assets. No launcher/native
JAR rebuild or second shell release is required for that metadata-only change.

```sh
python3 tools/hub/publish_catalog.py --native-receipt NATIVE_RECEIPT --web-receipt WEB_RECEIPT --output CATALOG_CANDIDATE
```

A published shell-upgrade demonstration must use two distinct reviewed shell
artifacts. Renaming an unchanged artifact does not prove an upgrade. Capture the
active immutable generation, next-start activation and retained synthetic saves.

The preparation workflow also builds clean portable packages for Windows, Linux
and both Mac architectures, plus a Windows MSI. Those artifacts use the source
JAR, not the two-generation test fixture. Each has a source/hash/scope receipt
and remains unqualified for stable rollout. The legacy download button opens
the releases list so a Hub preview remains discoverable without changing the
normal engine release's `latest` pointer. Published package links and actual
platform qualification still require the controller handoff.

Catalog native installs resolve `build=playtest-N` to the fixed repository's
`releases/download/playtest-N/update.properties`. They do not follow the global
latest release. A fresh machine can therefore install approved A after an
incompatible B becomes latest. The manifest must still match the catalog's
client source allowlist, and downloaded bytes must pass SHA-256, size and JAR
checks. City, Sandbox and editor retain their shared verified cache. Native
catalog tags outside the bounded `playtest-N` contract are rejected. Legacy
launches without an experience selection keep their existing latest behavior.
