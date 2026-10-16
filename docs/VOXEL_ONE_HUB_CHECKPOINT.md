# Hub compatibility correction checkpoint

Base head: 7fde413407a4388ecba47e6350b849c67157f6a8. Changes are unpublished.
Review/preservation base: 403033b9f53ce66063982879c6e2daff684a427b.
No developer commit, push, merge or deployment.

Native entries now require an explicit bounded source-set-v1 engine/client
compatibility contract. Published source must be included. Update checks run
under the installation lock before download/state changes; fallback and final
Hub/experience CLI launches must match the permitted sources. Disabled update
checks do not bypass this contract. Publisher and synthetic fixtures updated.

Maven selected suite/package: 19 passed, zero failures/errors/skips. Publisher:
1 passed. Actual Linux Hub Play workflow passed: City, Sandbox/editor, one shared
download, incompatible latest fallback, incompatible cache refusal with updates
off, corrupt download, cancellation and offline cache. Two new captures viewed.
Recording was explicitly disabled for this workflow; no native motion claim.
Portal/Earth/ground regressions: 31 passed. All 10 Linux package checks passed before a final availability-label correction.
Final rebuild passed all 19 tests. A fresh package 1.0.2 matched the final launcher
JAR byte-for-byte; its bundled-runtime Hub workflow passed after correcting a
null-label test-helper error. This retry also asserted the incompatible-latest
status, fallback and refusal with updates disabled. Current captures inspected.
No final-source full hardware qualification is claimed.

Hosted run 38979556752 at 7fde413 inspected. Windows MSI install, native launch,
desktop/menu links and uninstall preservation passed. All hosts passed updater,
fixture shell generations, available UI extents and synthetic TLS login. Native
GLFW creation failed on all hosts. Windows/Mac ARM full-size captures remain
unqualified; physical GPU/DPI and two published shell generations remain pending.
Reports are historical for these new compatibility edits. Rollout gates remain.

Diff audit: no deleted files against preservation base. Space Invaders source
and website assets unchanged. All changed evidence paths belong to Hub/review.
Helper failed; resolved SELF with independent analysis. No open helper task.
Controller owns final source sync/publication and exact-head hosted rerun.
