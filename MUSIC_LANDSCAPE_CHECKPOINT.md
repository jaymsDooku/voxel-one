# Living landscape: implementation and validation handoff

Worktree branch: `feature/queue-32303665306237372d623933612d346464642d383435342d346162633836393964313931`.
Original baseline: `2dae61300dd6a7a4a0cc5a9b5ece32b7acb50b15`.

Implementation replaces the core with 225 terrain columns, rooted groves/grass, water, bass waves, midrange foliage movement, high-frequency fireflies, beat rings and fading trails. Fixed pools and instanced box rendering bound resources. D toggles drift. Native audio, picker, playback, seek, palette, motion, fullscreen and F10 remain integrated. Seeking, replacement and completed-track restart clear transient history. Explicit pause freezes the scene; completion eases to calm.

Decisions: density was reduced from 625 to 361 to 225 columns after measuring frame-time regressions. Face lighting is calculated per vertex. Fireflies fade in over 300 ms, then fade out by 8 seconds, so short high-frequency passages remain visible. Bass/mid/high smoothing remains 150/900, 100/600 and 40/350 ms. Atmosphere uses 5/10-second transitions. Effects cap at 8/256/128 with 6/8/20-second lifetimes; aggregate terrain displacement caps at 0.18708 units (8% of the actual 2.33849-unit terrain range). Root placement uses base height for stable vegetation density, with root elevation sampled from the displaced surface.

Local results and commands are in `dashboard/evidence/landscape-validation.md`. `landscape-performance.json` contains three alternating measured pairs; raw exploratory reports are retained and distinguished. Screenshots and F10 MP4 are local evidence pending controller publication, not published receipts.

The local catalog integration passed. Sandbox/City regression execution was terminated with signal 143 before result files appeared. No pass is claimed; no orphaned synthetic regression JVM remained. Hosted retry is required. Physical GPU and audible speaker qualification remain pending; no desktop/session access is requested and no stable rollout is claimed.

Controller continuation: preserve/stage only this feature's edits and sanitized landscape evidence, synchronize the assigned feature branch as authorized, and push it for feature validation. `.github/workflows/music-landscape-native.yml` triggers on this assigned branch. It checks the exact SHA and runs Java 17 tests, native lifecycle/media/MP3/navigation, alternating performance checks and a real 1800-second playback soak. Allowlisted evidence and command status are uploaded even when qualification fails; a passed receipt is written only if all commands pass. Observe checks without model polling, then resume with actual results and media. The developer must inspect hosted media and heap/RSS trends; compile/preflight or a simulated soak is insufficient.

After integration, provide the recorded review base. Audit the entire diff against it and rerun meaningful checks/media if source changed. Intended removals are the old Resonance Core, its mirrored reflection/grid and obsolete three-second trace renderer. Historical evidence and unrelated merged features must be preserved. No commit, push, merge or deployment was performed by the developer.


## Rebase recovery against the recorded review base

Review base: `8c68a1cd72ec3f344711ef721cdcaffd86c45638`. The current conflict was only `dashboard/progress.json`. Combined the landscape entry with the current branch's full dashboard, preserving every existing entry and release metadata, including Hub preview registration and Music MP3. JSON parsing and entry-preservation assertions passed. No tracked file deletions appear against the review base. Intended code removals remain the obsolete core/reflection/grid/trace renderer described above; no unrelated feature removal is intended.

Conflict markers are removed from the working files. The unmerged index is deliberately left for the controller to stage and continue. No Git continuation, commit, push or publication was performed. Earlier native media, timing and test reports remain historical until the rebase finishes and final-source checks run. The 1800-second hosted soak and physical GPU/audio qualification remain pending.

## Current recovery continuation

Resolved the new `dashboard/progress.json` conflict against current master. Preserved every current-master entry and release metadata, and added the landscape entry. JSON assertions and `git diff --check` pass. Controller must stage and continue the preserved rebase; no Git continuation was performed here.

Published historical head `384322d7fa9b83745b20db78d5ef3073aa25f67d` has two successful build runs. Native run `39515239868` failed. Downloaded its scoped artifacts into `target/hosted-384322`. Exact-head status reports exit 0 for WAV lifecycle, media decode, MP3, performance comparison and real soak, but exit 1 for navigation regressions. These command statuses are not final qualification: inspect regression details, media, soak duration and resource trends after Git recovery. Revalidate resulting source. Physical GPU/audio remains pending.
