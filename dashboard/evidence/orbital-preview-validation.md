# Experimental orbital descent preview validation

Base HEAD: 7a6ab3adeb881d053f4cc8ac5216d4f505f23b74. Review base: aac5ca8ee0fa5ed6e5f84949c66e9949085f1d85. Tested working-source and generated assets: orbital-source-audit.json (22 matching pairs). Controller owns final commit/publication, PR metadata and latest-master synchronization. If synchronization changes runtime source, repeat meaningful checks/media. No staging, commit, push, merge or deployment performed.

## Scope and rollout boundary

Both new Canvas and WebGL2 rendering require the unchecked, session-only Experimental orbital descent preview checkbox before Start. Native keyboard Space operates that checkbox; the gameplay keyboard handler excludes settings. The flag is locked during playing/paused states and reload clears it. Ordinary play retains the original eight-second entry, terrain palette and face lighting. It does not prepare an orbital profile, Worker cache, GL context or orbital canvas. Preview gets the twelve-second shared-profile sequence, bounded readiness hold and external combat-camera handoff. Restart cancels jobs, rejects stale results, clears profile/lighting handoff and disposes GL resources. The page-visit choice remains selected across explicit restart; reload resets it.

This is an experimental preview delivery. The 60 FPS named desktop GPU and stable 30 FPS physical Safari iPhone rollout gates remain unmet. Both new renderers must stay opt-in until actual hardware reports and exact-source independent review pass. Manual testing/report steps are in orbital-device-validation.md and require no remote session IDs.

## Executed checks

Environment: Linux headless software Chromium/WebKit, isolated synthetic game contexts, localhost port 8779. No private account profile or external art. Unit command: node --test games/space-invaders/*.test.mjs. Observed: 53 passed, 0 failed, including 45 shared Java atmosphere/multiple-scattering reference comparisons and ordinary/preview timing/readiness fixtures. Build: python3 deploy/build_site.py, generated assets aligned.

Playtest: ordinary default and opt-in gate. Command: node deploy/run_orbital_web_check.cjs deploy/test_orbital_preview_gate.cjs with PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers and TMPDIR="$PWD/.tmp/browser". Expected: unchecked preview, no preview settings or resource preparation, real final-fighter kill, eight-second ordinary entry, score 130/lives 2 through active level 3, working flight/fire, pause/portrait resize/restart, keyboard opt-in and reload reset. Observed: both browsers passed. The harness throws on any ordinary-path orbital prepare call and asserts no profile, cache Worker, GL or orbital canvas. Captures show unchecked desktop and checked portrait start UI plus ordinary entry/level 3.

Playtest: enabled preview. Full Chromium/WebKit matrix at 1280x900, 390x844, 844x390 and 568x320 passed before the final keyboard/overlay refinements. After those refinements, both browsers repeated normal desktop and 568x320 descent, waking and external combat-camera handoff; the exact commands/settings/exit results are in orbital-preview-final-commands.json. Synthetic isolated final level 2 fighter, real Space fire and actual animation loop. Expected: one sequence, frozen combat, score 130/lives 2, stable profile/landing identity, distinct external camera, flight/fire, pause/rotation, skip-once and restart to wave 1. Observed: passed.

Playtest: preview failure paths. Both browsers passed Worker-disabled immediate last-kill entry with Reduced motion selected through UI, then focus returned to the canvas before real Space fire. Zero Worker cache frames and no fine scene, retained parent/landing story and normal completion. Final successful command is in orbital-preview-final-reruns.json. Earlier fixed-pose Worker-failure runs passed at 0/1.5/2.9/4.5/6/9/11.5 seconds, pause/rotation/restart, with zero terrain-generation calls inside render callbacks. GL fixed poses, actual context loss, airless control, lab lock/retry, prewarm restart/stale callback rejection and generated launcher checks passed; see orbital-preview-commands.json and the corresponding diagnostic reports.

Playtest: regressions. Seven existing entry/cockpit/fighter/fighter-edge/cockpit-edge/collision/lab suites passed. Initial layout suite found the new title overlay covered a touch control. The final compact landscape grid and bounded dialog fixed that defect. Reran the unmodified layout/touch assertions through a temporary localhost-port adaptation: node deploy/run_orbital_web_check.cjs .tmp/regression-test_space_invaders_layout.cjs; exit 0. Final ordinary and preview small-landscape runs also passed. The initial failing report remains preserved; no unexecuted pass is claimed.

Playtest: media. Fresh H.264/yuv420p normal and Worker-disabled Reduced-motion MP4s were encoded at original speed from these final desktop runs. Trimming ends after active level 3 flight and before separate synthetic edge checks. Both played start-to-ended in Chromium at playbackRate 1 with no decode error. The first fallback playback process exited 143 without a result; the scoped retry passed. Inspected 0.5-second frame strips throughout orbit, atmosphere, low terrain, impact, waking and external combat, plus the new gate/default captures. Canvas still blurs enlarged features and may hold prepared views while replacements finish; those are experimental limitations. New contact sheet labels ordinary default separately from enabled preview and fallback.

## Isolated software measurements

One browser at a time, 1280x900, Low Canvas preview explicitly enabled, three seconds of level 2 preparation, real final kill, full normal sequence; no video/diagnostic workload concurrently. Commands/settings and exit 0 results: orbital-preview-final-reruns.json.

Chromium frame p50/p95/p99/worst: 16.7/16.7/16.8/33.4 ms; world callback p95/worst 1.9/6.0 ms; initial preparation 205.1 ms.
WebKit frame p50/p95/p99/worst: 62.0/87.0/196.0/274.0 ms; world callback p95/worst 1.0/3.0 ms; initial preparation 359.0 ms.
Sampled summed Chromium RSS peak 597032960 bytes, 112 samples. Fresh browser process scope; shared pages may be counted twice, short peaks may be missed, JS heap excludes Worker/GPU/native memory. WebKit peak process memory was not measured. Software WebKit targets remain unmet; neither set of numbers establishes physical qualification. Initial preparation and occasional job slices can still hitch.

## Audit and handoff

No file deletions against the recorded review base. Intended behavioral removals are the unconditional orbital prepare/render path and unconditional new terrain lighting; both now require preview opt-in. Other merged features and progress entries are retained. All four developer helper tasks are reviewed: three failed helpers were taken over as self; the gate checklist was independently inspected and accepted, then implementation and checks were performed locally. No delegated execution claimed.

PR title/body draft: orbital-preview-pr.md. Ready for independent review of this scoped experimental preview. Media URLs are pending controller publication.
