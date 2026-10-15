# Milestone 1: opt-in experimental orbital/descent preview

This PR requests acceptance of Milestone 1 only. It does not complete the original default-rollout goal. `games/space-invaders/ROLLOUT.md` tracks open acceptance items R1–R5, required evidence and exit conditions. Hardware qualification, final visual/performance fixes, the rollout regression matrix and the separately reviewed default switch remain open. No follow-up work is auto-queued.

Ordinary Space Invaders keeps its original eight-second entry, terrain palette and terrain lighting. The start overlay offers an unchecked **Experimental orbital descent preview (unqualified)** checkbox. Both the new Canvas and WebGL2 paths require that explicit choice. The choice is held for the page visit, locked while playing or paused, and cleared by reload. Ordinary play creates no orbital canvas, profile, cache Worker or GL context.

The preview carries one frozen planet profile through a twelve-second orbital approach, atmosphere, local voxel terrain, impact, waking and an external level 3 combat camera. Its reduced Canvas fallback retains that identity and story completion path when Worker or GL preparation fails. Bounded preparation, restart cancellation, pause/skip and Planet lab controls remain part of preview validation.

This PR is an experimental preview, not default renderer rollout. Named desktop GPU and physical Safari iPhone qualification remain unmet. Software/browser measurements cannot establish the 60 FPS desktop or stable 30 FPS iPhone targets. Both new renderers must remain gated until hardware reports and exact-source independent review pass. Manual hardware steps are in `orbital-device-validation.md` and need no remote session service.

Validation commands, observed results, source hashes and newly recorded normal/fallback media accompany the final preview report. Any controller synchronization that changes runtime source requires renewed meaningful checks and media before review. No deployment or merge is performed by the developer.

Validation: 53 unit tests passed. Chromium/WebKit ordinary-default and keyboard opt-in checks passed. Preview desktop, small-landscape and cold Worker-failure/Reduced-motion runs passed on final source; the full eight-case viewport matrix passed before the final UI refinements. Seven legacy suites passed and the layout/touch suite passed on targeted rerun after the dialog fix. GL/context, prewarm cancellation, lab and launcher checks passed.

Fresh normal/fallback MP4s played to completion at speed 1 and were inspected with frame strips. `orbital-preview-validation.md` records commands, failed attempts and successful reruns. `orbital-source-audit.json` records tested source/bundle/media hashes. The contact sheet shows default-off UI, explicit opt-in, ordinary entry, preview and fallback. All new artifacts await controller publication.

Isolated software Low Canvas preview: Chromium frame p50/p95/p99/worst 16.7/16.7/16.8/33.4 ms; WebKit 62/87/196/274 ms. Preparation was 205.1/359 ms. These are software measurements; WebKit misses the proposed targets, and physical qualification remains unmet.
