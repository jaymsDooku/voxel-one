# Device validation handoff

Status: physical Safari iPhone and named desktop GPU rollout qualification is unmet. Both new Canvas and WebGL2 are experimental opt-in; ordinary play retains its prior renderer. Software results do not establish device performance. These checks gate future default rollout, not access to the experimental preview.

Use the exact source hashes in `orbital-source-audit.json`. Record the eventual controller commit SHA with the report. Serve the assigned branch game locally or through a controller-authorized test route. Use a fresh synthetic game profile. Record hardware model, GPU, OS, browser version, viewport, DPR, quality and rendering mode.

Playtest: start level 1; verify move/fire and touch multi-input. Reach level 2. For a repeatable synthetic last-kill setup, use the browser harness setup from `deploy/test_orbital_descent.cjs`; the final fighter must die through actual keyboard/touch fire and the frame loop. Record the complete normal-speed descent, blackout, waking and active level 3. Expected score 130, lives 2 for that setup. Verify ALT follows scene altitude. Inspect curved globe, terminator/limb, cloud passage, shared terrain, moons and landing anchor.

Repeat with Reduced motion. Pause during descent, rotate/resize, background and return; all motion must stay frozen until resume. Skip twice; only the first call completes descent. Restart returns to level 1. Test Canvas, WebGL2 preview, Low/Medium/High and airless. Airless must have no rim, scattering, clouds or heat. Test context loss where supported and blocked/retried Planet lab generation.

Measure entry frame p50/p95/p99, worst hitch, preparation time and peak memory. State measurement limits; browser JS heap excludes GPU/worker/native allocations. Do not call JS heap total peak memory. Targets to validate: 60 FPS desktop and stable 30 FPS physical Safari iPhone. Include full-sequence media and fixed-altitude screenshots from the tested source. Keep MP4 under 6 MB. Exclude private account data, prompts, answers and credentials.

## Manual test without a remote device service

1. Check out the exact controller-published feature head on a computer and run `python3 -m http.server 8779 --bind 0.0.0.0` from that checkout on a trusted local test network. Open `/games/space-invaders/index.html` on that computer and in Safari on a physical iPhone using the computer’s local network address. No session IDs are needed. Do not expose a private checkout on a public network.
2. Record the commit and runtime hashes, device/GPU model, OS/browser versions, viewport/DPR, quality and power mode. First leave the preview unchecked and verify ordinary combat/entry. Reload, explicitly check **Experimental orbital descent preview (unqualified)**, select Canvas, then Start. Test WebGL2 separately.
3. Use the isolated final-fighter setup in `deploy/test_orbital_descent.cjs` through developer tools, or play to the final level 2 enemy. Kill with real fire input. Record full normal-speed descent through active level 3 and the pause/rotation/skip/restart checks above.
4. Use browser performance tools to record frame intervals throughout entry. Report p50/p95/p99 and the maximum interval, preparation time, and browser/process peak memory from the desktop OS or Safari Instruments on a connected Mac. Specify the memory scope; JS heap alone is not process/GPU peak memory. If an instrument is unavailable, mark that metric unmeasured rather than passed. Run timing separately from video capture to avoid recording overhead, then repeat with recording for visual evidence.
5. Attach the trace, numerical report, fixed poses and MP4 below 6 MB to the private work item. Keep the 60/30 FPS qualification unmet until actual measurements pass and an independent reviewer accepts the exact source. Fix measured failures before enabling any new renderer by default.

For a repeatable synthetic setup, enable preview and press Start, then run this in that page's developer console. This resets only the synthetic game instance. Use the actual Fire control to kill the final fighter after setup:

```js
const {game} = await import('./app.mjs');
game.wave = 2; game.newWave(); game.score = 90; game.lives = 2;
game.enemyClock = 100; game.invulnerable = 100;
game.aliens = [{x:240,y:490,baseX:240,baseY:490,
  phase:0,age:0,type:'fighter',points:40}];
```

Repeat timing once after at least three seconds of preparation and once with an immediate kill. Include both results and preparation time; do not hide an early-entry hitch behind the warmed run. On iPhone use real touch Fire and record whether the device was thermally throttled or in Low Power Mode. Report unsupported measurements as unavailable, never as zero.
