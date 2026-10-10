# Graphics delivery validation

Current implementation includes a desktop Graphics draft/Apply screen, versioned local profiles, capability resolution, safe renderer/display transactions, bounded detailed mesh jobs, staged uploads, conservative opaque border closure, bounded distant meshes and cached selection, shader uniform caching, effect work gates, stationary shadow reuse, texture storage reuse and engine performance capture. The native SceneKit subset adds AA/frame-rate/detail/bloom/opt-in atmosphere preferences and real touch controls.

Desktop controls and capture instructions: `docs/graphics.md`.

Reference Windows 1080p performance, recorder-on/off measurements, packaged-client workflow and physical iPhone budgets remain follow-ups. No 60 FPS, 16.7 ms or hardware-specific percentile threshold is claimed. Quality tables are provisional. Linux software rendering is functional evidence only. Native build/touch checks require the controller's exact-source Mac run; mock contract tests do not prove the app runs.

The earlier `graphics-timing-validation.json` and timing gallery are historical timing-only evidence, clearly labelled and superseded by the current delivery report. Its earlier Main timeout was a full-world software-renderer fixture issue; current production-loop fixtures use isolated small synthetic scenes and exercise the actual renderer, UI and input callbacks. They do not stand in for Windows performance.

Diff audit against the locally inspected base `1ca97ff1b2c62484346425f60d756a1c7d3ca02b`: no unrelated file deletions. Intended removals replace render-thread synchronous detailed meshing and its full-frame list sort, fixed upload counts, repeated uniform lookups and unconditional production HiZ CPU depth readback. Ordinary draws replace the default per-mesh compute path. Existing server physics/loading radii and other merged features remain. The controller still must integrate current master and provide the recorded review base; source changes require refreshed exact-source tests/media before review.

No commit, push, merge or deployment was performed. Evidence publication belongs to the controller. Full release validation remains open.
