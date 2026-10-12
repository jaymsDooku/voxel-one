# Device validation handoff

Status: required tests have not run on physical Safari iPhone or a named desktop GPU. Linux software results do not establish device performance. Keep WebGL2 preview opt-in.

Use the exact source hashes in `orbital-source-audit.json`. Record the eventual controller commit SHA with the report. Serve the assigned branch game locally or through a controller-authorized test route. Use a fresh synthetic game profile. Record hardware model, GPU, OS, browser version, viewport, DPR, quality and rendering mode.

Playtest: start level 1; verify move/fire and touch multi-input. Reach level 2. For a repeatable synthetic last-kill setup, use the browser harness setup from `deploy/test_orbital_descent.cjs`; the final fighter must die through actual keyboard/touch fire and the frame loop. Record the complete normal-speed descent, blackout, waking and active level 3. Expected score 130, lives 2 for that setup. Verify ALT follows scene altitude. Inspect curved globe, terminator/limb, cloud passage, shared terrain, moons and landing anchor.

Repeat with Reduced motion. Pause during descent, rotate/resize, background and return; all motion must stay frozen until resume. Skip twice; only the first call completes descent. Restart returns to level 1. Test Canvas, WebGL2 preview, Low/Medium/High and airless. Airless must have no rim, scattering, clouds or heat. Test context loss where supported and blocked/retried Planet lab generation.

Measure entry frame p50/p95/p99, worst hitch, preparation time and peak memory. State measurement limits; browser JS heap excludes GPU/worker/native allocations. Do not call JS heap total peak memory. Targets to validate: 60 FPS desktop and stable 30 FPS physical Safari iPhone. Include full-sequence media and fixed-altitude screenshots from the tested source. Keep MP4 under 6 MB. Exclude private account data, prompts, answers and credentials.
