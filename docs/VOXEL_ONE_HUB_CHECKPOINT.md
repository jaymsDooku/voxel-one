# Hub checkout recovery and controller integration handoff

Feature HEAD: 0e1683eebb356a15b3f46d25a6937a523c3dbf98
Observed origin/master: 26b8761ff9c355299aa1df83608a6f6ebcba7e0b
Review base: 403033b9f53ce66063982879c6e2daff684a427b
PR: https://github.com/jaymsDooku/voxel-one/pull/72

All11 preserved dirty engine files were exact truncated prefixes of current
master, cut on4KiB boundaries. Their complete original bytes/hashes are saved
inside target/hub-disk-recovery-20261019. Restored these11 tracked contents to
feature HEAD without modifying Git index or refs. No distinct valid local
performance/lighting patch was discarded. Complete master versions must be
imported by controller integration; do not carry truncated source forward.
Old build class directories/status/JARs were preserved in that ignored backup,
then compilation ran from empty classes/test-classes directories.

Executed baseline validation: Maven21 passed (zero failures/errors/skips),
publisher integration4 passed, actual inherited-display HubPlaytest passed.
Fresh library and empty-search captures inspected. No source deletion against
review base; source/tools/workflow working contents now match feature HEAD.
No open helper tasks. No developer commit/push/merge/deployment performed.

Controller next action: integrate latest master into this feature branch while
preserving its complete performance/lighting features and the Hub changes.
There is no current unmerged index or pending rebase for the developer to
continue. Normal controller sync/commit/publication handoff is authorized.
When source integration changes content, resume the developer for final-head
checks and fresh media BEFORE independent review. These baseline checks do not
qualify the future integrated source. Recheck whole diff for unrelated deletions.

After integration/review, regenerate publication bundle and exact-source inputs
from the resulting committed reviewed source. Existing0e1683 publication plans
are historical and must not be relabelled. Controller retains all publication,
lease, budget and approval fences. No new owner permission/session IDs needed.

Full acceptance remains incomplete. Prior exact-head hosted Windows native
API0x10006 and Mac framebuffer format0x10009 failures are historical; they are
not passes. Physical GPU/gameplay/monitor DPI and published browser reload,
independent native/web promotion and two published shell-generation upgrades
remain qualification gates. Keep normal stable release behavior and opt-in
preview gating. No production catalog/site/preview release was published here.

Evidence: dashboard/evidence/hub-disk-recovery-audit.json,
hub-recovery-20261019-validation.json and hub-recovery-20261019-*.png.

## Integrated-source validation, 19 October 2026

Controller integration completed at `46788fe2f16115b5457d621353a7a41c5c08fbce`
against review base `26b8761ff9c355299aa1df83608a6f6ebcba7e0b`.
The full proposed diff has no deleted paths. Engine mesh, world, lighting,
transport and render-pipeline source remains identical to the review base.
Only this task's progress entry changed during validation.

Fresh compilation/package and 68 selected Java tests passed, with no failures,
errors or skips. Four publisher tests and 31 portal/ground/alternate-Earth tests
passed. Nine website assets byte-match their game source. Live Hub navigation,
empty search/catalog, synthetic catalog membership, refresh fallback, keyboard
focus and light theme passed on the inherited Linux display with synthetic data.
New media is named `dashboard/evidence/hub-integrated-46788-*`.

Packaged Linux checks passed updater failure/cancel/concurrency/migration,
empty-PATH bundled-runtime launch, two fixture shell generations, JVM scales
1/1.5/2, synthetic pinned-TLS login and fixed GLFW failure reporting. The checker
exited 143 after recording City and Sandbox rendering passes. It is not marked
as a fully passing checker run. Editor rendering and offline installation were
rerun separately and passed. Each native observer captured four frames on
llvmpipe; these are software-renderer smoke checks, not GPU performance claims.
See the package-completion and media-decode reports for exact commands/results.

The fresh exact-commit publication bundle is preserved at
`target/hub-integrated-46788-publication`. The controller must recheck the source,
expected destination head and immutable tag availability, retain all existing
fences, and publish only an accepted preview scope. Catalog metadata, shell
metadata and the preview asset still returned HTTP 404. Earlier source plans
remain historical. No production publication occurred in this developer turn.

Return controller results for exact-source Windows/macOS packaged checks,
physical GPU gameplay and Windows monitor DPI, published browser gameplay and
cached reload, independent native/web promotion, and upgrades between two
actually published shells. These remain acceptance gates. Stable rollout stays
unqualified. The ready handoff covers local integration validation and controller
publication/dispatch only; it does not declare full release acceptance.

## Review fix: refresh before Play

The review at `b249ce6e2a34169765577869e205e5df89cbaee3` found that open detail
buttons retained old catalog entries. Play now refreshes off the Swing event
thread, resolves the stable ID again and applies the fresh entry to both native
launch and browser dispatch. It retains the last verified catalog on fetch or
parse failure, refuses removed/unsupported entries and honors updates-off mode.
The detail page also shows the refreshed entry. Repeated Play clicks cannot
start another catalog/client operation while the first check is pending.

Fresh Maven selected tests/package passed: 15 tests, no failures/errors/skips.
Four Python publisher tests passed. The live synthetic promotion helper passed
native A-to-B selection, browser URL A-to-B dispatch, responsiveness under a
blocked fetch, duplicate clicks, malformed fallback, removal and updates-off.
This helper is part of hosted packaged checks. New evidence starts with
`hub-preplay-b249`; its source fingerprints identify working-tree changes.
Controller must regenerate the publication bundle from the resulting committed
source before fenced preview publication. No delivery or platform result is
assumed from this local synthetic test. Stable rollout remains gated.

The fresh full Linux packaged check completed with exit 0: all 12 checks passed,
including catalog promotion before Play, three JVM scales, pinned-TLS login,
bootstrap fixture generations, updater faults/concurrency/migration, all three
native render modes and offline installation. This supersedes the interrupted
Linux checker for this fix; it does not supersede missing Windows/macOS or
physical qualification. Fresh shell and native clips decoded and were inspected.
Each native clip contains four frames on software OpenGL; no smooth-performance
or physical-GPU claim is made. Public metadata and preview delivery still return
404. Controller publication and returned exact-source qualification remain next.

Playtest: the explicit environment/steps/expected/observed record for committed
application source `45f894964164905bf0c687ec1aec1f5c512aaaf1` is
`dashboard/evidence/hub-exact-45f894-playtest.json`. It records the executed
promotion, shared-client launch, update/offline and 12 packaged Linux checks.
All recorded application/tool fingerprints match this source. This documentation
update reruns no application checks and claims no new platform result. Synthetic
browser dispatch, fixture shell upgrades, software rendering and JVM scaling
remain distinct from the pending published/physical acceptance checks.

## Platform report inspection for the next controller handoff

Run 39338968452 has completed with failure at older source 45f8949. All three
sanitized platform reports were downloaded and their source fingerprints checked;
Windows path separators and 259 CRLF source files were normalized for comparison.
No source mismatch remains. Launcher clips from Windows and both Macs each decode
as 20 frames over 2.917 seconds; their Downloads frames were inspected.

Native City/Sandbox/editor checks failed with GLFW 0x10006 on Windows and 0x10009
on both Macs. The pre-Play promotion checks also failed and remain unqualified;
the report does not establish a more specific cause. Updater, fixture bootstrap,
login and Windows MSI/shortcut/uninstall checks passed. Windows and Mac ARM had
unavailable full-size desktop extents. Do not call the overall runs passing or
infer physical GPU/DPI qualification from launcher captures.

No scoped packaged run exists for current submitted source edc5736. Its
application/tool/workflow source is unchanged from 45f8949, but the required
exact-head run is still pending. The controller command, source fences, bundle
and publication sequence are recorded in
`dashboard/evidence/hub-exact-edc5736-controller-handoff.json`. Publication remains
controller-owned. Public catalog, shell metadata and preview asset still return
404. No new owner permission is needed. Stable rollout remains gated.

## Paused at owner/controller quota boundary: shell activation rollback

Preserved branch `feature/queue-62383835386364312d346130642d346262652d383736632d393534373062343337356161`
at base HEAD `6fc5bd1494c7349750286d361d6b63110aac1944`, PR #72. All unpublished
source, tests, workflow changes, documentation, package fixtures and evidence stay
in this worktree. No Git commit/push/merge or publication was performed.

Implemented explicit startup readiness in Bootstrap/Launcher, separate verified
`acknowledged.properties`, locked rollback to the last working downloaded shell,
failed candidate rejection, and protection against overwriting newer staging.
If acknowledgement cannot be persisted, a ready UI stays open but stages nothing.
Added executable tests for exit after ready, live-but-unacknowledged timeout,
later upgrade and candidate snapshot/concurrent staging. Hosted/package workflows
now include these tests. The package harness stages a real exit-7 shell and checks
that the downloaded working shell resumes, its pointer restores, failed bytes
are not restaged and immutable JARs remain.

Executed validation: 23 Java tests and four publisher tests passed. Full Linux
packaged run `dashboard/evidence/hub-rollback-6fc5-packages/report.json` exited 0
with all 13 checks passing, including executable failed-shell rollback. Inspected
its recovered Hub center-crop image. Reports and working-tree source fingerprints
are in `dashboard/evidence/hub-rollback-6fc5-validation.json`. No paths were deleted
against review base 26b8761. The only subsequent application edit makes activation
marker cleanup best-effort so cleanup errors cannot orphan an accepted child or
mask interruption. A Maven test/package operation for that final small edit was
already active when pause arrived; its completion is recorded below. No further
implementation or new tests may start during this pause.

Resume after authoritative quota/service clearance: inspect preserved source and
active-build result, run final-source packaged workflow/media checks if still
needed for the cleanup edit, update source fingerprints and reports, then
regenerate the preview bundle from the resulting committed source through the
controller. Current `target/hub-rollback-6fc5-publication` was prepared before the
last cleanup edit; it must not be published as final source. Capture/decode final
media and retain exact-source labels. Request controller commit/push and scoped
Windows/macOS checks for the resulting exact head; older failed platform runs
are historical. Public catalog/shell/preview targets last returned 404. Actual
published upgrades, independent native/web promotion, browser gameplay/cached
reload and physical GPU/DPI remain incomplete. Stable rollout remains gated.
All helper tasks are resolved; no extra agents or background jobs were scheduled.

Active operation completed before stopping: Maven selected
`HubShellActivationTest,HubShellTest,UpdaterTest,HubCatalogTest` test/package exited
0 with the final cleanup edit. No further tests were started after pause. The
remaining resume task is final-source packaged workflow/media validation and
report/bundle regeneration, followed by controller-owned publication/dispatch.

## Resumed: final rollback source validated

The final cleanup edit is now included in a fresh full Linux packaged run.
`dashboard/evidence/hub-rollback-final-packages/report.json` exited 0 with all
13 checks passing. A fresh full selected Java run passed 23 tests with zero
failures/errors/skips; four publisher tests passed. These results supersede the
pre-cleanup package snapshot for local validation. The new executable failure,
timeout and snapshot-race tests are included in scoped platform workflows.

Playtest: on the inherited Linux role display with synthetic profiles and the
packaged Java runtime, staged an executable that exits 7, launched the native
package, and verified that the acknowledged downloaded shell resumed and its
pointer restored. Rejected bytes were not restaged; immutable JARs remained.
The recovered-shell center crop and full library were inspected. Fresh launcher
video decoded as 20 frames over 2.917 seconds; native City/Sandbox/editor clips
each decoded four frames on software OpenGL. No smooth-play or physical GPU
qualification is claimed. All recorded final application/tool fingerprints match.

Final working-tree evidence and controller actions are in
`dashboard/evidence/hub-rollback-final-validation.json` and
`dashboard/evidence/hub-rollback-final-controller-handoff.json`. The fresh bundle
is `target/hub-rollback-final-publication`; regenerate after the controller commits
the resulting exact source. Include the new executable regression files when
staging. Request new exact-head Windows/macOS reports/media, not historical
6fc5bd14 or 45f8949 qualification. Public metadata and preview artifact still
return 404. Published upgrades/browser cached reload/independent promotion,
physical GPU/DPI and reference hardware performance remain acceptance gates.
No source deletion against review base 26b8761; unrelated engine features remain
preserved. No owner input, developer publication or extra agent is required.


## Promotion test isolation after exact-head platform failures

Reviewed run 39341981018 matches source 6307ffede5a191e917a3b6d248d7d44df2b361fc
and failed overall on Windows and both Macs. Executable failed-shell rollback
passed on all three. Native rendering failed with GLFW 0x10006 on Windows and
0x10009 on both Macs. The old generic promotion failure does not identify its
exact phase. Sanitized results and verified fingerprints are recorded in
`dashboard/evidence/hub-promotion-6307-hosted-results.json`.

The promotion helper previously required a real native graphics startup before
checking the browser cases. Added fixed stage diagnostics and verified-install
status, plus a separately compiled synthetic command receiver for packaged
catalog tests. The actual Hub, updater and structured launch path still run.
Real City/Sandbox/editor rendering checks remain mandatory and unchanged.
No production Java source changed from 6307ffed in this follow-up.

Playtest: on the inherited Linux display, isolated synthetic profiles, JDK 21
and software Mesa, forced a graphics failure using test-only
`MESA_GL_VERSION_OVERRIDE=2.1`. Expected the real-client helper to fail after
installing B, the command receiver to verify `--game city --offline` and finish
the remaining catalog checks, and the real renderer to fail independently.
Observed all three expected outcomes. The real helper reported native-start
failure with promotedNativeInstalled=true; the renderer reported 0x10007.
This negative control does not qualify GPU gameplay or prove the old remote
failure's precise cause. Commands and results are recorded in
`dashboard/evidence/hub-promotion-isolation-diagnostic.json`.

Controller must include `tools/hub/PromotionClient.java`, commit the final
working source and dispatch new exact-head packaged checks. The new helper is
never a shipped game client. Public delivery, two published shell upgrades,
independent native/web publication, real browser gameplay/cached reload and
physical GPU/DPI/performance remain acceptance gates. Stable rollout stays gated.


## Controller pause: promotion diagnostics preserved

Base HEAD remains 6307ffede5a191e917a3b6d248d7d44df2b361fc. Unpublished
changes are confined to promotion test isolation, safe diagnostics, validation
docs, this task's progress entry and sanitized evidence. New file
`tools/hub/PromotionClient.java` must be included by the controller. No Git
index/ref changes or publication were performed.

Four publisher tests and the fault-injection playtest completed before pause.
The first full package attempt exited 143 after four recorded passes. The retry
was stopped on the new controller pause; only completed checks in
`dashboard/evidence/hub-promotion-pause.json` count. Neither interrupted attempt
is a full passing run. Diagnostic images were inspected. Public catalog, shell
metadata and preview JAR independently still returned HTTP 404.

Resume only after authoritative service/quota clearance. Run a fresh complete
Linux packaged check with the preserved tools; inspect its reports/media and
record final source fingerprints. Regenerate any publication bundle after the
controller commits the resulting source. New exact-head Windows/macOS checks
are required; 6307ffed hosted jobs failed overall. Keep real native render checks
mandatory. Public upgrades/browser reload/independent promotion and physical
GPU/DPI/performance remain pending; stable rollout stays gated.


## Resumed promotion validation completed for controller handoff

Four publisher tests passed. The fresh package run recorded ten passing checks
then exited 143; it is not a full passing checker run. Direct corrected runs
of Sandbox/editor rendering and offline installed resolution passed. All 13
local scenarios therefore have passing executed results across these runs.
An initial direct run used the wrong shell classpath and failed; its report
is retained and explicitly superseded by the corrected-command report.

Fresh launcher video decoded20 frames/2.917 seconds. City/Sandbox/editor clips
decoded four frames each; all selected frames were inspected. Software rendering
only. Source fingerprints match the preserved working tools/application files;
production source remains unchanged from 6307ffed. No paths deleted against
review base26b8761. Final results and exact commands are in
`dashboard/evidence/hub-promotion-final-validation.json` and linked reports.

Ready only for controller commit/push, exact resulting-head platform checks and
authorized publication handoff. Include the new command-receiver fixture source.
No application acceptance, hosted pass, public upgrade/browser reload or physical
GPU/DPI/performance qualification is claimed. Stable rollout remains gated.


## Exact93c34ff publication handoff

Application/tool fingerprints match the completed local validation. No new
application tests were run. Scoped run39344321446 is still in progress on all
three platforms and has no published artifacts at inspection. No platform pass
or media inspection is claimed for that run.

Prepared exact-commit bundle `target/hub-exact-93c34ff-publication`. Bounded
read-only delivery verification failed with HTTP404 on catalog, shell metadata
and preview JAR. Results are in `hub-exact-93c34ff-delivery.json`; controller
actions are in `hub-exact-93c34ff-controller-handoff.json` under dashboard/evidence.
Controller publication and return of completed reports/media need no new owner
permission. Stable rollout and all published/physical acceptance remain gated.


## Completed parent-platform reports inspected on20 October

Run39344321446 finished failed on parent93c34ff; no scoped b8ef2e79 run
was found. Downloaded sanitized artifacts, verified all source hashes and
inspected all three decoded launcher frames. Windows/ARM clips contain20
frames; Intel17; each lasts2.917s. Promotion passed all3. Real rendering still
failed0x10006 Windows/0x10009 Macs. Intel also failed expected-shell-process
checks; this is not qualified as a pass or assumed to be only a test issue.

Compact results, Playtest entry and exact-head controller dispatch command are
in dashboard/evidence/hub-platform-93c34-inspection.json. Controller must return
new exact-current-head reports/media; source changes require final checks.
Published-delivery and physical GPU/DPI/performance acceptance stay pending.
No production changes or developer publication occurred; stable rollout gated.


## Shell process observer race addressed

At base9dd20aec, changed only the package test observer to poll25 seconds for
the exact expected shell JAR, with100ms backoff and a live-parent guard.
Bootstrap permits20 seconds of startup; the old observer checked only once
after3 seconds. Windows own-child fallback remains inside the repeated scan.
No production source changed and no test requirement was removed. Fixed
failure fields expose counts/liveness only. This does not prove the historical
Intel failure was only a race; new exact-head Intel confirmation remains required.

Playtest: inherited Linux display, isolated synthetic profiles, actual Hub shell
JAR and JDK21. Delayed5-second and immediate shell startup passed; a live wrong-
JAR edge failed as expected. Desktop captures succeeded and delayed capture
was inspected. Four publication tests passed. Initial javac omitted helper
source and failed; compiling both helper sources succeeded, including final
formatting. Exact commands/results and probe hash are preserved in
dashboard/evidence/hub-process-wait-validation.json. No deleted paths against
review base26b8761. No commit/push/dispatch/publication performed.

Controller must commit/push the new tools source to trigger exact-resulting-head
Windows/macOS checks, then return reports/media. Complete the authorized
publication handoff and verify actual published upgrades/browser cached reload.
Physical GPU/DPI/performance acceptance and stable rollout remain gated.


## Full startup evidence and exact09706929 reports

Inspected completed run39345961161: exact09706929 fingerprints match all
platform reports. Shell update/rollback passed on Windows and both Macs,
including Intel. Real City/Sandbox/editor rendering still failed0x10006 on
Windows and0x10009 on Macs. All three launcher clips decoded20 frames/2.917s
and extracted frames were inspected. Compact evidence:
`dashboard/evidence/hub-platform-097069-inspection.json`.

Removed guessed40px startup screen insets from PackageUiProbe. Whole primary
assigned desktop now captures every visible Hub edge. Playtest: inherited Linux
role display, bundled Java, synthetic profile; launched actual Hub, set own
window1280x800 then900x600, captured through modified probe. Both captures
succeeded and were inspected; Hub is fully visible, unused desktop black.
Four publisher tests, helper compilation and diff whitespace checks passed.
No production source changed; no deleted paths against26b8761.

New report: dashboard/evidence/hub-full-window-validation.json. New full
desktop images: hub-full-desktop-1280.png and hub-full-desktop-900.png. These
replace cropped startup evidence for the new implementation. Controller must
commit/push resulting tools source for exact-head checks and publish sanitized
evidence. Authorized preview publication remains a controller handoff; prepared
bundle target/hub-exact-09706929-publication must be regenerated for changed
source if required. Public upgrades, independent publication, browser cached
reload and physical GPU/DPI/performance stay pending. Stable rollout gated.


## Startup preference staging race reproduced and fixed

At base2da57e18, added an executable Bootstrap regression that delays child
readiness2s while another launcher changes updates=false to true and publishes
fixture B. Before the fix it failed: expected1 transfer, observed2. Bootstrap
now captures its update preference before starting/waiting for the shell.
A later setting change applies to the next invocation. This reproduces a real
race relevant to Intel shell-stage ordering; original remote failure resolution
still requires new exact-source evidence. Windows CIM query timeouts are not fixed.

Validation:18 selected Java tests and Maven package passed;4 publisher tests
passed. Existing executable failure, timeout, rollback and snapshot-race checks
passed. Playtest: launched actual rebuilt Bootstrap using bundled Java on the
inherited Linux role display with synthetic profile; visible Hub and full desktop
capture succeeded and were inspected. New image: hub-startup-snapshot-hub.png.
Exact commands, baseline failure, current hashes and results are recorded in
dashboard/evidence/hub-startup-snapshot-validation.json and playtest.json.

Controller must commit/push preserved changes, dispatch resulting-head platform
checks and return reports/media. Regenerate publication bundle from resulting
source; old2da57e bundle predates this production change. No deleted paths
against26b8761; no Git commit/push or publication performed here. Real native
rendering/GPU/DPI/performance, public upgrades/independent promotion and browser
cached reload remain pending. Stable rollout remains gated.


## Exact b173448 qualification and controller handoff

Run 39355282918 returned Windows, Intel Mac and ARM Mac reports for
`b17344808779c5a0556ff696e04bd7b85cf84d18`. All 264 recorded source
fingerprints per platform matched Git, accounting for Windows CRLF checkout
bytes. Three launcher clips decoded to 20 frames and 2.917 seconds each; their
Downloads frames were inspected. These clips do not establish gameplay.
Shell staging and rollback passed on all three platforms. City, Sandbox and
editor rendering failed: GLFW `0x10006` on Windows and `0x10009` on both Macs.
Physical GPU, Windows monitor DPI and declared reference performance remain
pending. Stable rollout remains gated.

The pending local check command ran successfully with JDK 21: 18 Java tests,
Maven packaging and 4 publisher tests. Its failure control returned exit 23 and
skipped the publisher stage. Run `python3 tools/hub/check_local.py` after the
controller commits these edits and integrates master. Repeat live checks and
media if integration changes application source.

The controller must regenerate the publication bundle from that committed head.
`target/hub-b173448-regenerated-publication` preserves the prior exact-head
bundle. Delivery verification still returned HTTP 404 for catalog metadata,
shell metadata and the preview shell JAR. Publication is reserved to the
controller: preserve immutable assets and destination compare-and-swap guards.
Return actual receipts for two published shell generations, independent native
and web promotion, and latest browser gameplay with cached reload before
claiming those acceptance checks passed. No owner permission is missing.

Diff audit: no files were deleted against review base
`26b8761ff9c355299aa1df83608a6f6ebcba7e0b`. Replaced lines in Launcher,
Updater, LoginDialog and Window implement structured experience launch, verified
immutable shell/client updates, labelled login fields and fixed numeric GLFW
diagnostics. The legacy updater path and save locations remain supported.

Local validation selection now matches the scoped workflow. The expanded command
completed with exit 0: 24 Java tests, Maven packaging and 4 publisher tests
passed using JDK 21 and checkout-local cache/temp. This includes Sandbox identity,
save separation and the selected Multiplayer pinned TLS login regression.
See `dashboard/evidence/hub-local-check-validation.json` for suite counts.
The earlier 18-test result records the narrower selection, not a failed run.
