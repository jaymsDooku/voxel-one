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
