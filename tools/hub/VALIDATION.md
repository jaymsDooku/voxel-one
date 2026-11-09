# Scoped Hub validation

Run local Java regressions, packaging and publisher tests with a supported JDK
on `PATH` (`JAVA_HOME` may select it):

```sh
python3 tools/hub/check_local.py
```

This command uses `target/maven-cache` and `target/hub-tmp` inside this checkout.
Its Java selection matches the scoped workflow, including game identity,
save separation and the pinned TLS registration/login regression.
It preserves existing artifacts, stops on failure and allows Maven to fetch
missing dependencies. It does not publish or run platform gameplay checks.
Passing these local checks does not satisfy GPU, Windows DPI/performance,
published shell upgrades or browser cached-reload acceptance.

Hosted Windows validation uses `--software-rendering`. This downloads only
Mesa-dist-win 26.2.5 MinGW from its distributor's immutable release URL, verifies
59,285,313 bytes and SHA-256
`2471864068672b82032d8f8cccfb8da8e196b1b06b316b99838b47c35d738a17`,
then uses the runner's existing 7z to extract x64 DLLs into the disposable work
directory. DLLs are copied only beside that check's bundled `java.exe`, after the
portable-launch/MSI checks. No installer, distributor script, registry change,
system DLL replacement or driver registration runs. The archive and its notices
remain in `target`; Mesa is not added to shipped Hub packages. Provenance:
https://github.com/pal1000/mesa-dist-win/releases/tag/26.2.5

This follows Mesa's documented application-local Windows OpenGL implementation:
https://docs.mesa3d.org/drivers/llvmpipe.html
The report records the archive and each DLL digest. Native checks use
`GALLIUM_DRIVER=llvmpipe`, `LIBGL_ALWAYS_SOFTWARE=1` and `LP_NUM_THREADS=2`.
The real City, Sandbox and editor renderer must report `llvmpipe`, render four
frames and produce nonuniform framebuffer content. Context creation alone does
not pass rendering. Linux can use the same flag with existing system Mesa and
the inherited display; no dependency or display server is installed by the check.
macOS software rendering is not configured and remains separately pending.

Software results are explicitly distinct from physical GPU, actual Windows
monitor DPI and performance qualification. `fullQualificationPassed` remains
false. Missing DLLs, hash failure, wrong renderer or render failure cannot become
a pass. Inspect screenshots and decode the recordings after the exact-source run.

Mac format diagnosis: the upstream NSGL implementation examined at GLFW 3.4 returns `0x10009` when
`NSOpenGLPixelFormat` cannot select a format. It always includes
`NSOpenGLPFAAccelerated`; relaxing color/depth bits does not remove that condition.
The application's Mac request now uses supported core 4.1 instead of 4.3 and
enables `GLFW_COCOA_GRAPHICS_SWITCHING`, which permits eligible offline GPUs.
The existing core 3.3 fallback and minimum engine requirements remain intact.
This is a supported GPU-selection path, not a CPU-renderer fallback or a bypass
of screen permissions. Source:
https://raw.githubusercontent.com/glfw/glfw/3.4/src/nsgl_context.m
https://www.glfw.org/docs/3.3.9/window_guide.html
The packaged Linux native library reports GLFW 3.5.0 with LWJGL 3.4.1;
the 3.4 source reference is not a claim of identical bundled native source.
The changed exact-source Mac run must confirm the actual bundled behavior.

On macOS the four bounded probe variants are: 4.1 with graphics switching; 3.3
with switching; 4.1 without switching; and 4.1 with switching, Retina disabled,
relaxed color/depth bits, zero samples/stencil and double buffering retained.
Non-Mac variants are unchanged. A successful probe still requires actual
production rendering. Four format failures remain pending native qualification;
they must never be turned into a passing package result.

`HubPerformanceProbe` records provisional software-session measurements from the
actual bundled runtime and Hub: JVM start to visible Swing UI/native queue idle,
frame construction time, Java heap after navigation, and 30 page rebuild/layout
round trips on the EDT. Native queue-drain time is recorded separately; it is not
silently included in EDT response time. Heap values are not process RSS, native
memory or peak memory. The report's Java-visible processor count describes the
virtual session, not a physical reference machine.

Initial regression budgets are 10,000 ms to visible UI idle, 256 MiB Java heap,
and 1,000 ms p95 EDT navigation. These bounded defaults detect gross stalls;
passing them does not satisfy owner-required physical performance qualification.
Reports include the observed values and a 900x600 library capture. A budget
failure fails this provisional check and remains recorded. No production
accounts, environment dump or private runtime logs are collected.

The coordinator publishes only the assigned feature branch. Its push starts
`.github/workflows/hub-validation.yml`. The workflow has read-only repository
access. It does not publish releases, deploy sites or use account credentials.

After the workflow is registered, the coordinator can repeat an exact-head run:

```sh
gh workflow run hub-validation.yml --ref feature/queue-62383835386364312d346130642d346262652d383736632d393534373062343337356161 -f source_sha=EXACT_40_HEX_FEATURE_HEAD
```

Replace the placeholder with the published branch head. The workflow rejects a
different head. Collect `hub-windows-<SHA>`, `hub-mac-intel-<SHA>` and
`hub-mac-arm-<SHA>` artifacts. Check each report's source SHA and source file
fingerprints. Inspect the screenshots and decode the videos before review.
Artifacts expire after 14 days. Copy sanitized reports and selected media into
`dashboard/evidence/` for permanent feature-branch evidence.

The checker builds two app images with bundled runtimes. The two fixture shells
contain the same reviewed code with different generation markers. They test
verified shell replacement and retention, not two published production releases.
It launches native executables with an empty PATH, checks the active cached shell
process, and exercises corrupt/partial downloads, cancellation, competing
launchers and legacy installation migration. A busy competing launcher must retry
after the lock owner exits; both must reuse one download. Windows checks hold an
actual file handle while updating, then install/uninstall the MSI and check its
desktop/start-menu shortcuts. Synthetic profiles and save sentinels stay outside
uploaded evidence. Only compact reports, screenshots and bounded videos upload.

The same bundled runtime also opens the production login dialog against isolated
City and Sandbox loopback TLS servers. It checks the fixed game choice, explicit
server port, invalid-port error and successful pinned sign-in. Captures occur
before filling the password. This validates the login handoff; it does not claim
an online rendered-world session or a production account login.

Desktop checks require a real accessible runner desktop. No display, macOS screen
permission or graphics restrictions are bypassed. Failure remains a failed or
pending platform check and requires a coordinator-provided scoped desktop runner.
The checker rejects clipped 1280x800 captures. JVM scales 1/1.5/2 exercise Swing
scaling; they do not establish actual Windows monitor DPI changes. Actual Windows
100/150/200% monitor scaling, GPU gameplay and two published shell releases remain
separate acceptance checks. Do not infer these passed from an environment check.

Local partial artifact checks can use `check_packages.py --artifacts-only`.
This option does not satisfy packaged GUI/platform acceptance. Local source SHA
labels identify the base commit; working-tree fingerprints identify local edits.
Remote checks run the exact published source SHA.

The initial production probe returned anonymous403 and supported private-access404
for both `/games/space-invaders/` and `/games/space-invaders/index.html`.
Those results are historical after the coordinator's route restoration.
Do not change site privacy or promote a merged source commit as a deployed build.

Update after the scoped run at `dd38d8c1e15311cd820c08cb05998869d5ae5249`:
the coordinator restored the private game route. Its sanitized production report
records HTTP200, movement/fire, pause/resume and cached reload. The bundled web
card now uses that published build and reviewed source. The earlier404 report is
historical. Root's independent future catalog publication still uses tested and
published receipts; the last native receipt can be reused for web-only updates.

The first Mac run had a system local-network prompt over its captures. Passing
assertions do not accept those images. The loopback server now skips hostname
discovery. Do not grant permissions automatically or suppress the platform prompt.
Inspect the next exact-source screenshots for unobstructed Hub content. Desktop
setup selects only advertised display modes inside the fenced ephemeral Actions
job; unsupported modes stay recorded and full-size checks remain required.
Windows cached-shell process checks can use a Boolean query for the launched
child when ProcessHandle omits arguments. They never upload a command line.

Linux F10 checks must intersect the native process PID and game window title
(`xdotool search --all`). An OR search can select the Hub window and falsely send
F10 there. Hold one start/stop key event; wait for the new recording to encode.
Sparse software-rendered frames prove recording/launch only, not smooth gameplay.

The scoped checker also runs `NativeRenderProbe` against the verified platform
client using bundled Java by absolute path. Native rendering retains normal OS
driver/utility PATH entries; the launcher/MSI tests still use an empty PATH.
No external Java is invoked. This harness change is not proof that hosted GLFW
failures are repaired; inspect the new actual renderer results. It uses the existing frame observer,
an isolated LOW-profile game and four actual framebuffer frames for City,
Sandbox and editor. This is renderer/module smoke, not interactive gameplay,
launcher CLI coverage or GPU performance acceptance. Clips need decoding and
human inspection. Linux software rendering produced only four sparse frames.

The runtime module list includes `jdk.management` for the native allocation
recorder and `jdk.httpserver`; these were identified by `jdeps`. Catalog
receipts in `dashboard/evidence/hub-native-publication.json` and
`hub-web-publication.json` describe independently published native/web builds.
`hub-prepared-catalog.properties` is prepared metadata; it has not been promoted
by this developer. Controller/Root retain publication control.

## Provisioned desktop dispatch

Hosted run38621773293 could not create GLFW windows. Windows/Mac ARM also
provided no advertised desktop mode large enough for required captures. Those
checks remain failed. A manual `runner_pool=desktop` dispatch selects only
controller-provisioned runners bearing `self-hosted`, `voxel-hub-desktop`,
and the platform labels `Windows`/`X64`, `macOS`/`X64`, or `macOS`/`ARM64`.
These labels are a provisioning contract, not a claim those runners exist.
Use isolated disposable test machines with an active authenticated desktop,
OpenGL3.3 or later, and sufficient advertised display modes. Preserve platform
privacy/approval controls. No unrelated accounts or private data may be present.

The controller dispatches the same command above with `-f runner_pool=desktop`
after publishing this feature source. Push runs retain hosted diagnostics.
No arbitrary runner label input is accepted. Exact-head checks, read-only
permissions, synthetic profiles, bounded media and independent review remain
required. The Windows MSI shortcut-count diagnostic must execute on that runner;
it has only been syntax checked locally. Physical monitor DPI and real gameplay
checks remain distinct from JVM-scale and four-frame renderer smoke.

## Hosted preview scope

The controller can dispatch the ordinary hosted pool now; self-hosted runner
provisioning is optional and does not block hosted diagnostics. The Hub first
window and minimum size use the usable desktop bounds. Navigation scrolls on
small desktops. The probe always captures the fitted window, and records
900x600/1280x800 in unavailable-extents.txt when the desktop cannot hold them.
That is missing qualification, not a passing full-size capture. The report's
fullQualificationPassed remains false. GLFW failures remain failed checks.

The packaged executable opts into `voxel.hub.preview=true`; direct legacy
launcher invocation keeps its prior flow unless `--hub` or that property is
set. The stable bootstrap forwards this choice to a cached shell. Automatic
Hub packaging/shell promotion was removed from the ordinary release workflow
until qualification. The feature workflow builds preview packages only.

The Windows shortcut probe now invokes its fixed synthetic check with
PowerShell -Command, not a temporary .ps1 file. It changes no execution policy
and prints only the matching shortcut count on failure. The prior generic
powershell.exe exit1 did not prove missing shortcuts; the new exact-head run
must determine the result. Windows physical DPI, real GPU play, full-size
captures on small hosted desktops and two published shell generations remain
unqualified. No unqualified rollout is authorized by a passing preview check.

Native window failures retain the fixed hexadecimal GLFW error code in the
exception before cleanup. Raw callback descriptions and native stacks remain
excluded from package reports. `WindowFailureProbe` exercises a real invalid
width and expects `0x10004` (`GLFW_INVALID_VALUE`). This diagnoses a failure; it
does not demonstrate that hosted Windows/macOS support the required renderer.
The graphics context request and existing fallback are unchanged. Interpret
codes using https://www.glfw.org/docs/latest/group__errors.html.

Play now refreshes the catalog before launch when update checks are enabled.
The launch resolves the selected stable ID from that validated snapshot instead
of using the entry held by an old card or detail button. Refresh failure uses
the last valid cached/bundled catalog. A removed or unsupported entry cannot run
through a stale button. With update checks off, Play uses the current catalog
without a catalog request. Native compatibility and immutable release checks
still apply after selection.

`PrePlayCatalogPlaytest` runs actual Swing actions against a loopback publisher.
It promotes native source A to B and a web URL while old detail buttons remain
open, checks responsiveness during a blocked fetch, duplicate-click suppression,
malformed metadata fallback, removal and updates-off behavior. By default the helper starts the real native client. An optional third argument
selects a synthetic command-receiver JAR; it verifies exact `--game city
--offline` arguments from the actual Hub launch. The browser dispatcher captures
the selected URL. `check_packages.py` compiles this command fixture and uses it
with the bundled runtime so catalog selection is independent of GPU creation.
The separate mandatory City/Sandbox/editor rendering checks still use the real
client and remain failed when native graphics is unavailable. Neither synthetic
command dispatch nor browser dispatch qualifies gameplay or public promotion.

Shell activation now requires an explicit acknowledgement. The child sends it
only after the Hub is visible or the legacy updater has initialized. The stable
bootstrap waits up to 20 seconds, rejects early nonzero exits even if they wrote
a ready marker, and stops unacknowledged candidates. `acknowledged.properties`
keeps the last working downloaded shell independently of the staged
`installed.properties` pointer. A failed candidate restores that downloaded
shell. Rollback holds the updater lock and does not overwrite newer concurrent
staging. Candidate identity includes the content checksum, not just source SHA.
A rejected artifact is not restaged until its bytes/version change. Immutable
JARs and saves remain intact. If a ready shell cannot persist its acknowledgement,
it stays open but does not stage another shell.

`HubShellActivationTest` executes real healthy, failing and unacknowledged JARs
through Bootstrap. It covers early nonzero exit after a ready marker, timeout,
working downloaded fallback, later successful upgrade and concurrent staging.
The packaged checker also stages a real executable that exits 7, then verifies
that the previously acknowledged downloaded shell process is running and the
pointer was restored. Its `shell-rollback/native-executable.png` captures that
actual recovered Hub. These are synthetic package tests, not published upgrades.


Promotion diagnostics emit only a fixed stage, exception type and Boolean status
indicators through `HUB_CHECK_FAILURE:`. They never expose driver logs or account
state. `promotedNativeInstalled=true` confirms the verified B revision was cached
before a later native-start failure. The optional real-client mode remains useful
for diagnosing that distinction. Linux Mesa 2.1 fault injection demonstrates the
real-client promotion check failing at native-start after B installation while
the command-only promotion check passes; the mandatory real rendering probe still
fails. This is a negative-control test, not a reduced graphics requirement.


The package process observer now waits up to25 seconds for the exact expected
JAR among the launched native process's descendants. This aligns the observer
with Bootstrap's20-second startup allowance instead of assuming three seconds
is enough. It still fails for the wrong JAR or an exited parent. Failure details
contain only parent liveness, descendant count and argument-list availability;
raw process arguments are never emitted. Linux real-shell tests cover a five-
second delayed child, immediate startup and a wrong-JAR negative case. This is
a test timing fix, not proof of the prior Intel failure's cause. Intel and other
platform package/activation checks must run against the resulting exact source.


Startup screen evidence captures the whole primary desktop of the assigned
synthetic display. It no longer invents a centered window rectangle with40px
insets. This preserves full visible window borders at1280x800 and900x600;
unused desktop may be black. This is not exact OS-window-bound capture or
multi-monitor/physical-DPI qualification. Use only the owned synthetic display.

## Own-child process query repair

`PackageUiProbe` closes the query process input stream immediately. The prior
probe left redirected PowerShell stdin open, so a query could wait for EOF.
The probe drains stdout through a daemon reader and retains at most 17 bytes,
never a process command line. It accepts `MATCH` only on exit code 0, a finished
reader and at most 16 output bytes. A 17th byte rejects oversized output. The
pipe replaces the temporary output file that remained locked in hosted Windows
cleanup. No capture file is created or deleted.
Each CIM call has a 2-second operation limit; each helper process wait has at most 3 seconds
within the original 25-second overall deadline. Timeout remains a non-match.
Reader completion and forced shutdown each have a 1-second cleanup bound.
The fallback queries only descendants whose argument list is unavailable and
requires the exact escaped `-jar` target. Hosted Windows validation must confirm
the actual PowerShell behavior; Linux controls alone do not prove that fix.

`ProcessQueryProbeTest` executes real child controls for EOF, timeout, wrong
output, oversized output, exit code 7 and stderr backpressure. The packaged check runs these controls
with its bundled Java runtime before the updater and UI checks.

Publication inputs must be real tested deployment/release receipts. Files under
`target/hub-receipts` are synthetic test fixtures and cannot authorize promotion.
`target/hub-repair-preview-publication/controller-publication-data.json` records
prepared assets and the remaining controller sequence as data. It is not an
executable handoff and makes no deployment, physical GPU or stable release claim.

## Independent native promotion while web is unavailable

An explicit web receipt with `available=false`, `tested=false`, `published=false`,
`source=unpublished` and `build=unpublished` retains the Space Invaders card with
its existing unavailable label and disabled browser action. Native receipts
still require a tested, published immutable release. Ambiguous availability
values or publication claims on a withheld web entry are rejected before the
last generated catalog is replaced. A later tested and published web receipt
enables the browser entry without changing native identity or downloading its
shared client again.

The preview preparation workflow uses `hub-preview-native-receipt.json` and
`hub-preview-web-receipt.json` from `dashboard/evidence/`. The initial native
receipt names verified published `playtest-632`; web is explicitly withheld.
Older publication evidence remains historical. The controller must validate
current receipt provenance, review the exact committed publisher source and
regenerate the bundle before any promotion. No unavailable receipt qualifies
the required final browser session or full release acceptance.

The scoped package check also runs `NativeContextProbe`: four hidden 64x64 context attempts (4.3 core, 3.3 core, 3.3 without Retina framebuffer, and 3.3 with unconstrained color/depth/stencil bits and single buffering). It records only fixed variant IDs, creation booleans and GLFW codes. This diagnoses hosted API/pixel-format failures; diagnostic completion does not qualify native rendering, gameplay, physical GPU or DPI. Production context requirements are unchanged.

When all four context attempts report creation failure with nonzero fixed codes, redundant City/Sandbox/editor launches are recorded as `pending`. The package run still exits nonzero because required checks did not pass. Missing, malformed or partly successful context output still attempts real rendering. Hosted diagnostic completion never qualifies a provisioned desktop or physical GPU.

After controller publication, compile `tools/hub/PublicCatalogProbe.java` against the downloaded verified public shell JAR, then run `PublicCatalogProbe <isolated-cache>` with the packaged runtime. It exercises the production HTTPS parser and persists the accepted catalog, then constructs a fresh store and requires the same catalog version from disk. It does not claim a second published generation or deployed web-game freshness. Keep the isolated cache inside the assigned worktree.

For a later catalog promotion, copy the previous accepted catalog to a separate baseline file before refreshing, then pass that file as the probe’s second argument. The probe requires a changed full catalog, unchanged shared native artifact identity for retained native entries, and exact equality between the fetched catalog and a fresh cached read. An unchanged publication fails rather than counting as a promotion. This does not prove a shell generation update.

### Registered Sites game checks

`hosted-web-playtest.py` and `hosted-web-playtest.cjs` test the deployed Space
Invaders assets through the existing Sites service-access boundary. Run from the
feature checkout on its inherited display. Use worktree-local `TMPDIR` and
`PLAYWRIGHT_BROWSERS_PATH`; `PLAYWRIGHT_MODULE` can select an existing Playwright
installation. No browser or driver installation is performed by these tools.

The controller supplies the registered origin and service token through hidden
stdin. The Python process keeps the token in memory and sends it only to the
fixed registered HTTPS origin. Its loopback proxy serves only bounded game
assets, refuses redirects and traversal, and never supplies the token to the
browser. It does not proxy dashboard APIs, visitor cookies or account identity.
Game Start is checked before serving HTML. Reports contain asset hashes and
outcomes. Python clears both outcome receipts before reading stdin or contacting
the service, so malformed input and preflight failures cannot retain an earlier
browser pass. An existing image alone never qualifies a failed run.

The actual desktop test starts the game, sends movement/fire input, waits for a
positive score, pauses/resumes, reloads and starts again. A screenshot is taken
only after the game is visible. This is a service-view integration check, not
proof of visitor sign-in, a new deployment, a catalog promotion or freshness
across two deployed builds. Preserve the Site audience. Public catalog entries
must still use separately reviewed publication receipts. Keep service access out
of launcher packages and catalog metadata.

### Public native CLI selection

`public-cli-playtest.py` runs an existing Linux app image against the public
catalog and its immutable native release. It requires a fresh synthetic profile
and an executable inside the assigned checkout. It uses an empty `PATH` to check
the bundled runtime, bounds the accepted artifact to 100 MiB, and validates its
size, SHA-256 and source against the release manifest. City, Sandbox and editor
must reuse one immutable cache file. Web and unknown IDs must fail without
changing that verified file. No account or service credential is used.

The profile disables automatic shell staging so the test stays on the supplied
package. Explicit `--update-only` still performs the native update. A new run
clears its report before validation, so failed input cannot retain an old pass.
This check may download the published native artifact; it is a manual read-only
integration check, separate from deterministic CI fixtures. Cache path/hash/mtime
observations do not measure network packet counts. It does not launch gameplay,
exercise browser sign-in, publish a release or qualify physical hardware.
