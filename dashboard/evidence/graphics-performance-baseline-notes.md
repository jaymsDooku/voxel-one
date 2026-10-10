# Graphics delivery validation

Current implementation includes a desktop Graphics draft/Apply screen, versioned local profiles, capability resolution, safe renderer/display transactions, bounded detailed mesh jobs, staged uploads, conservative opaque border closure, bounded distant meshes and cached selection, shader uniform caching, effect work gates, stationary shadow reuse, texture storage reuse and engine performance capture. The native SceneKit subset adds AA/frame-rate/detail/bloom/opt-in atmosphere preferences and real touch controls.

Desktop controls and capture instructions: `docs/graphics.md`.

Reference Windows 1080p performance, recorder-on/off measurements, packaged-client workflow and physical iPhone budgets remain follow-ups. No 60 FPS, 16.7 ms or hardware-specific percentile threshold is claimed. Quality tables are provisional. Linux software rendering is functional evidence only. Native build/touch checks require the controller's exact-source Mac run; mock contract tests do not prove the app runs.

The earlier `graphics-timing-validation.json` and timing gallery are historical timing-only evidence, clearly labelled and superseded by the current delivery report. Its earlier Main timeout was a full-world software-renderer fixture issue; current production-loop fixtures use isolated small synthetic scenes and exercise the actual renderer, UI and input callbacks. They do not stand in for Windows performance.

Diff audit against the locally inspected base `a7436345bfe20551935c4681331981619048b01a`: no unrelated file deletions. Intended removals replace render-thread synchronous detailed meshing and its full-frame list sort, fixed upload counts, repeated uniform lookups and unconditional production HiZ CPU depth readback. Ordinary draws replace the default per-mesh compute path. Existing server physics/loading radii and other merged features remain. The controller still must integrate current master and provide the recorded review base; source changes require refreshed exact-source tests/media before review.

No commit, push, merge or deployment was performed. Evidence publication belongs to the controller. Full release validation remains open.

Integrated-source validation now covers the shared Rendering preference bridge, migration precedence and persistence-failure rollback. The prior Mac run passed nine native tests on `62669fd6f264eb589262f01b36d71846ed858954`; its inspected screenshot shows gameplay controls facing sky, not the Graphics sheet. The later exact-source Mac run on `b22e6001fc32f9b9bbd6f942070bd9f3a84856e1` passed all nine native tests; its actual Graphics sheet image was inspected and receipt-verified. These are simulator functional results, not physical-device performance.

Review follow-up: the duplicate model shadow call is removed, with actual 1/2/3 cascade draw counts verified. Sustained World.stream/edit/GL upload validation passed 64 cycles and 130 seconds across eight 512-block-separated regions. Queues, retained heap, geometry disposal and 128 installed GPU border comparisons are recorded in graphics-sustained-streaming.json/CSV. This addresses the short-teleport validation gap for a bounded low-density workload; it is not reference performance or maximum-density stress.
