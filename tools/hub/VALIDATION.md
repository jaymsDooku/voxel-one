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
