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
