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
