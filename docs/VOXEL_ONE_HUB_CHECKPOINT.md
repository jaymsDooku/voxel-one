# Hub publication handoff

Local validation resumed and completed. Preserve all unpublished edits and media.
Controller owns source publication and the remaining release integration steps.
Full acceptance and stable rollout remain incomplete; no owner answer is needed.

Branch: feature/queue-62383835386364312d346130642d346262652d383736632d393534373062343337356161
Base HEAD: 32d93e09768167a188e7507cd4fe65d1880443b7
Review base: 403033b9f53ce66063982879c6e2daff684a427b
PR: https://github.com/jaymsDooku/voxel-one/pull/72
No developer commit/push/merge/deployment. No open delegate task.

Unpublished work:
- Bootstrap shell metadata now uses the fixed experience-catalog branch instead
  of normal engine latest releases, which do not publish a shell manifest.
- Read-only publication bundle preparation/verification, source/root fencing,
  expected destination head, immutable preview generation, hash checks and a
  read-only manual workflow. Clean platform package preparation is included;
  neither metadata nor release assets were published.
- Native receipt/bundled compatibility refreshed to actual published playtest-565
  at 403033b9f53ce66063982879c6e2daff684a427b. CI38977822347 succeeded and
  published update.properties matched. Web provenance was not guessed/changed.
- Renderer checks retain normal OS PATH while invoking bundled Java by absolute
  path. Empty-PATH launcher/MSI checks remain. Remote repair is not claimed.
- Legacy download link opens all releases so previews remain discoverable
  without changing the normal engine latest pointer. HTTP200 verified.

Executed checks (the existing Maven operation finished during pause):
- Final Maven selected suite/package finished exit0 during pause:19 tests,
  zero failures/errors/skips. No new tests started after the pause.
- Publisher1 and publication bundle integration3 passed. Fresh-directory
  publisher CLI passed. Both workflows parsed; embedded Python compiled.
- Linux package10 passed before metadata565/test-harness refinements; report
  explicitly marks that source boundary.
- Real public HTTPS native install passed: source/SHA256/size matched565.
- Actual bundled-runtime Hub City/Sandbox/editor workflow using verified565
  bytes passed shared-download, incompatible latest status/fallback, cached
  refusal, corruption, cancellation and offline cases. Recording disabled.
- Actual published565 renderer passed four frames each for City/Sandbox/editor
  on Linux llvmpipe. Three clips decoded and first frames inspected. No smooth
  performance or physical GPU claim.
- Exact published32d93e CI38981202633 inspected: Windows MSI/shortcuts/uninstall,
  updater/bootstrap/UI/TLS passed; native GLFW creation failed all three hosts.
  Required full-size Windows/Mac ARM and physical DPI remain unqualified.
- Live catalog, shell manifest and preview asset checks each returned404.
  This is a failed publication integration gate, not a passed test.

Resume:
1. Final Maven passed19. Resumed online login playtest passed City/Sandbox,
   custom server/port, invalid-port rejection and pinned TLS. Both fresh captures
   inspected; download link visible. Browser activation was not tested. Initial
   null-title helper failure fixed and rerun passed. Publisher1/bundle3 passed.
   Module-style unittest invocation failed import; direct script commands passed.
2. Finish compact validation/source audit and current evidence receipts. Last
   audit found no deletions versus review base; original release workflow and
   unrelated game/website code were preserved.
3. Controller synchronizes/commits/publishes feature source. Regenerate bundle
   from exact committed reviewed source: existing target bundles are explicitly
   working-tree diagnostics and must not be published under the base SHA.
4. Controller dispatches hub-publication-prepare.yml, checks actual native/web
   receipts, and performs authorized immutable preview asset + CAS metadata
   publication. Expected experience-catalog head was absent (Git API404).
5. Re-run real public delivery and two actually published shell generations,
   inspect new exact-source Windows/macOS results/media, complete required
   physical GPU/DPI qualification. Keep full acceptance and stable rollout gated.

Evidence: dashboard/evidence/hub-publication-*, hub-published-565-render/* and
hub-published-565-*-frame.png. Final diagnostic bundle:
target/hub-resumed-publication-handoff (regenerate after controller commit).
All artifacts remain local pending controller publication. No owner answer needed.

Final audit: no deleted files against review base; unrelated dashboard entries,
website and ordinary build workflow preserved. git diff --check passed.
Current compact report: dashboard/evidence/hub-publication-validation.json.
No open delegate tasks. No commit, push, publication or deployment performed.
