# Hub controller publication checkpoint

Source HEAD: d840a9815d454fe399a3eba71412c2038489d907
Branch: feature/queue-62383835386364312d346130642d346262652d383736632d393534373062343337356161
Review base: 403033b9f53ce66063982879c6e2daff684a427b
PR: https://github.com/jaymsDooku/voxel-one/pull/72

No implementation change this turn. Recorded source fingerprints still match.
No Git conflict. No deleted files against review base. Diff check passed.
No open delegate tasks. No commit, push, release or deployment performed.

Concrete controller handoff:
1. Exact committed-source bundle is target/hub-exact-d840-publication.
   sourceKind=exact-commit; controllerMustRegenerateFromCommittedSource=false.
   Small plan copied to dashboard/evidence/hub-exact-d840-publication-plan.json.
2. Dispatch hub-publication-prepare.yml on the assigned feature ref with
   source_sha=d840a9815d454fe399a3eba71412c2038489d907,
   preview_tag=hub-preview-1, expected_catalog_head=absent.
   Dispatch before advancing the feature head. If head changes, regenerate
   bundle and exact-source inputs; do not bypass the workflow fence.
3. Controller validates native/web publication receipts. Preserve existing
   stable/latest release behavior. Publish immutable preview shell asset first,
   verify SHA/size, then atomically promote catalog.properties/shell.properties
   with expected-head CAS. Git API confirmed destination ref absent; recheck.
4. Resume developer for real delivery, independent native/web promotion and
   packaged bootstrap tests. Publish a second distinct reviewed generation and
   test the actual public upgrade. Fixture generations do not satisfy this.

This is an execution handoff, not a request for owner approval. Publication is
reserved to the controller. Acceptance is not ready and stable rollout stays
gated until remaining qualification passes.

Executed this turn:
- Exact-source bundle preparation passed; existing source/test fingerprints
  matched. No rerun of unchanged Java tests is claimed (prior21 passed).
- Public bundle verification exit1: catalog, shell manifest and preview asset404.
- Fresh desktop WebKit browser attempt: start control unavailable; failed game
  workflow. No sign-in content/cookies/screenshots retained. Exit0 from this
  harness is not a pass; JSON passed=false. Current website receipt was not
  changed to invent fresh browser validation.
- Exact-head CI38986864061 remained in_progress at last inspection. Do not use
  older platform results to qualify this head. Physical GPU/DPI remains pending.
- No new native rendering or two-published-generation upgrade test was run.

Reports: hub-controller-publication-handoff.json, hub-exact-d840-delivery.json,
hub-exact-d840-catalog-ref.json, hub-browser-result.json under dashboard/evidence.
All unpublished evidence preserved. No new owner answer is needed.

## Numeric GLFW diagnostic continuation

Current unpublished change captures glfwGetError's fixed hexadecimal code when
Window creation fails, before cleanup discards that context. OpenGL hints and
4.3-to-3.3 fallback are unchanged. No graphics repair is claimed. This intended
Window.java change supports platform failure diagnosis; unrelated engine,
website, release workflow and historical evidence remain preserved.

Maven selected suite/package21 passed; zero failures/errors/skips. Python
harness syntax passed. Actual invalid-width GLFW workflow returned0x10004.
Normal Linux City renderer regression passed four frames in23861ms on llvmpipe.
Movie decoded:4 frames,18.062seconds. Native screenshot and decoded first frame
inspected; controls overlay/scene visible. No smooth-performance claim.
Commands, source fingerprints and limits are in
 dashboard/evidence/hub-glfw-diagnostic-validation.json.

CI38986864061 is now complete: Windows, Mac ARM and Mac Intel all fail native
City/Sandbox/editor window creation. All exact-source fingerprints checked
(Windows CRLF conversion accounted for). Reports omit native descriptions, so
no driver/context cause is established. New diagnostic source requires a fresh
exact-head hosted run after controller publication. No open delegate tasks.

The prior exact d840 publication bundle remains specific to d840. Regenerate
from the resulting committed reviewed head if publishing the new source; do
not relabel that bundle. Existing controller publication handoff still applies.
Catalog/shell delivery, physical GPU/DPI, actual published browser gameplay and
two published shell-generation upgrade checks remain incomplete. Stable rollout
stays gated. No owner answer is needed. No commit/push/deploy performed.
