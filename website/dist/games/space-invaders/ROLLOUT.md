# Orbital descent milestones and remaining acceptance

The original orbital descent goal remains open. This PR submits **Milestone 1: opt-in experimental preview** for independent review. It does not submit default rollout or claim full delivery. No follow-up queue item is created by this document; the owner/controller controls scheduling.

## Milestone 1 — submitted for review

Included: explicit session-only opt-in for both new Canvas and WebGL2; frozen planet identity across approach, impact, waking and level 3; bounded preparation and parent fallback; pause, resize, skip, restart and failure recovery; browser reference fixtures and recorded software-browser workflow checks. Ordinary play keeps its eight-second entry and prior terrain rendering, without orbital preparation. Independent review decides acceptance of this milestone.

## Remaining work — open, required before default rollout

| ID | Acceptance work | Required evidence / exit condition | Status |
| --- | --- | --- | --- |
| R1 | Named desktop GPU performance | Exact-source cold and warm full-entry runs; hardware/OS/browser/settings; p50/p95/p99, worst hitch, preparation and peak memory with scope; demonstrate the 60 FPS target and fix measured failures. | Open; hardware qualification unmet |
| R2 | Physical Safari iPhone performance | Same report on a named physical iPhone, including thermal/power state and real touch input; demonstrate stable 30 FPS and fix measured failures. Software WebKit and simulator results cannot close this item. | Open; physical qualification unmet |
| R3 | Final visual continuity and quality qualification | Exact-source fixed orbital/limb/cloud/low/waking poses and full normal-speed video through active level 3. Independently assess geography, thin sun-responsive limb, continuous horizon/shell crossing, palette, landmarks, sun/moons and clouds in Canvas and WebGL2, including fallback. Resolve remaining blur, held-frame and preparation-hitch limits. | Open for rollout; preview evidence available |
| R4 | Rollout compatibility and regression matrix | After performance/visual fixes, rerun Chromium/WebKit desktop, 390×844, 844×390 and small landscape; real final kill, score/lives, single completion, touch, safe areas, combat/collision/cockpit/lab, reduced motion, pause/background/resize/DPR, early/late jobs, Worker/shader/extension/context failures and restart/stale-result rejection. Capture resulting-source media. | Open for rollout; preview checks recorded |
| R5 | Enable qualified default rendering | Only after R1–R4 pass: controller latest-master integration, source/bundle alignment, unrelated-deletion audit, renewed exact-head checks/media and independent review. Then make a separately reviewed default-path change; retain coherent failure fallback. | Open; blocked by R1–R4 |

The original acceptance criteria apply to the future default path as well as the preview. Passing preview state tests does not close R1–R5. The existing ordinary entry is intentionally retained during this milestone; it does not meet the new visual goal.

Use the [manual hardware plan](../../dashboard/evidence/orbital-device-validation.md) without any remote session IDs. Attach results and source identity to the private work item. Record unsupported metrics as unmeasured. The [preview validation report](../../dashboard/evidence/orbital-preview-validation.md) separates software results from hardware qualification. Track each row until its evidence is accepted; do not mark the parent goal complete on milestone acceptance.
