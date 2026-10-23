# Structure review fix checkpoint

Current published base: 3f7ab54c500e77b664312a780f39fdf4bed901d9, PR #76.
Review base: b1d7b8d796b97a87a3d63bb08baf2bb9746b8ca6.

Review collision finding is being fixed. Both authorities now call shared
StructureOccupancy before changing models, edits, geometry or saves. This uses
live city frames and current player poses under the authority lock. Offline F12
placement/undo receive a live player supplier from Main. Nonempty resulting cells
reserve the entire cell; empty undo cells may safely clear geometry around bodies.

Four new offline/server authority tests passed, including horses and players
entering after staging, no partial geometry/dependencies/save, retry after leaving,
and guarded restoration versus safe empty undo. Existing structure/fractional/model
focused regressions passed. Exact totals/commands will be in the review-fix report.
Fresh native workflow (including player collision rejection capture) and 32-session
functional regression are running. Raw logs and synthetic profiles stay in target/.
No earlier CI is claimed for these edits. Hosted checks need controller publication
once local tests/media are complete. Full city/max, physical GPU, shaped-network
performance qualification and stable rollout remain gated.

DeepSeek review task 3d9470ff-84f8-4f41-b8bb-74b2c48dd252 failed; explicitly resolved
self. Independent local inspection confirmed validation precedes mutation, server
city advancement shares the authority lock, and undo uses the same commit guard.

## Completed local validation for collision fix

32 focused tests passed (five new collision tests, including production TLS
protocol). Compile/test commands are recorded in structure-collision-validation.json.
32-session functional regression passed in 345.913 s; 50 ms movement cadence,
production authentication throttle preserved. This is single-host functional load,
not reference distributed/network qualification.
Playtest: python3 deploy/run_structure_playtest.py --display "$DISPLAY" passed on
llvmpipe with isolated synthetic profile. Live-player rejection message and placed
world captures inspected. Four rotations, cancellation, undo, repeat placement,
restart, inventory and model editor regression checks passed. The first harness
attempt failed because it mutated a returned position copy; corrected setup rerun
passed. No production failure was concealed.

Fresh sanitized media, report and source manifest are in dashboard/evidence,
pending controller publication. Native source manifest hash:
665d25b1bb96d248b7fdc279db5b8042ce34b049eb27bfbba5df103a8302def9.
No source changed after tests. No deleted files or unmerged paths; diff check passed.
Next: controller feature_validation handoff for revised source CI, then inspect
exact-head hosted summaries and published media before ready. Previous-head CI
remains historical. Full qualification and stable rollout remain gated.

## Published collision fix

Controller published exact head 2c67bc1f754c8644f98d7ce29ddd47db489094bf.
354 collision-source and 207 native-source hashes match; production source did
not change. Downloaded exact-head rejection image/video/report/native manifest
match local bytes. Rejection screenshot and decoded video frame inspected.
Hosted run: https://github.com/jaymsDooku/voxel-one/actions/runs/39485576505
Linux/Windows/Intel Mac running; ARM Mac queued. No revised-head hosted pass
claimed. Resume on run completion or any job failure; do not repeat unchanged
push or idle status turns. No owner answer needed. Qualification stays gated.

Hosted status update: exact head `2c67bc1f754c8644f98d7ce29ddd47db489094bf` now has eight checks across runs `39485576505` and `39485579789`: seven in progress and one ARM Mac check queued. No final results or passes claimed. Source and media are unchanged. Resume on completion or failure; qualification and stable rollout remain gated.

Linux exact-head CI job `118844989091` passed. Downloaded sanitized test summary and verified sourceHead `2c67bc1f754c8644f98d7ce29ddd47db489094bf`; totals: {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. Six other checks remain running and one ARM Mac check queued. Reports await controller publication. Qualification and rollout stay gated.

Windows exact-head CI job `118844989452` passed. Downloaded sanitized summary matches `2c67bc1f754c8644f98d7ce29ddd47db489094bf`: {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. Linux also passed; five checks running and one ARM Mac check queued. Supplemental reports await controller publication. Qualification and stable rollout stay gated.

Additional Linux job `118846441790` passed; downloaded report sourceHead `c9c2ff3781a3ab1b76b1347d6d87905b8892fc9d`, totals {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. Four checks remain running and one ARM Mac queued. Qualification and rollout remain gated.

ARM Mac exact-head CI job `118844989423` passed. Downloaded sanitized summary matches `2c67bc1f754c8644f98d7ce29ddd47db489094bf`: {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. Push Intel Mac and PR Windows/Intel Mac still running; PR ARM Mac queued. Qualification and stable rollout stay gated.

Intel Mac exact-head CI job `118844989427` passed. Downloaded summary matches `2c67bc1f754c8644f98d7ce29ddd47db489094bf`: {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. All four feature-head builds now passed; feature publish skipped. PR Windows and Intel Mac still running; PR ARM Mac queued. Await remaining PR checks before readiness. Qualification and stable rollout remain gated.

PR Intel Mac job `118846442089` passed. Downloaded report matches merge head `c9c2ff3781a3ab1b76b1347d6d87905b8892fc9d`: {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. PR Windows remains running; PR ARM Mac queued. All four feature-head builds passed. Qualification and stable rollout remain gated.

PR Windows job `118846442118` passed. Downloaded report matches merge head `c9c2ff3781a3ab1b76b1347d6d87905b8892fc9d`: {"tests": 758, "failures": 0, "errors": 0, "skipped": 0}. Only PR ARM Mac remains queued. All four feature-head builds passed. Qualification and stable rollout remain gated.

PR ARM Mac job `118846442471` completed with conclusion `cancelled`, not passed. All four feature-head builds and three PR merge-head builds passed. Controller must recover the cancelled PR ARM Mac check against the unchanged submitted source before readiness. No source changes needed. Qualification and stable rollout remain gated.

Paused at controller request for shared quota or service control. All unpublished report and checkpoint edits are preserved. No implementation or tests started during this pause. Source head remains `2c67bc1f754c8644f98d7ce29ddd47db489094bf`. All four feature-head builds passed with 758 tests each and zero failures/errors/skips; three PR merge-head builds passed. Last observed PR run `39485579789` remains attempt 1 with ARM Mac job `118846442471` cancelled; controller retry is pending. Resume only after authoritative quota/service control permits work, then inspect actual retry results. Qualification and stable rollout remain gated.


## Paused validation after master integration

Source head: `e351d91bf4de6f17657976e4cd467aeb3f44c15b`. Controller advanced Git recovery; no Git mutation or publication performed by developer. Java production source and native harness match the previous collision-fix source. Diff check passed; no deleted files against recorded review base or unmerged paths were found.

Focused check: `mvn -q -DargLine="-Djava.io.tmpdir=$PWD/target/structure-audit-tmp" -Dtest=StructureOccupancyTest,StructureTest,FractionalWorldTest,VoxelModelTest test` completed with 32 tests, zero failures/errors/skips. Initial attempt failed because `/tmp` was read-only; retry used the worktree temp directory.

Playtest: fresh `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` and 32-client `StructureMultiplayerTest` with suffix `rebase32` started, then interrupted for the requested pause. Neither run is claimed passed. Initial native attempt started before Maven compilation finished and failed to compile the harness; sequential retry remains incomplete. The native harness removes `dashboard/evidence/structure-native-result.json` at startup; its current absence is an incomplete-run artifact, not an intended feature deletion. Rerun the native harness to restore the result and produce fresh inspected media before publication/review. Existing media and hosted reports remain historical.

Resume: verify stopped process state, rerun incomplete native and 32-client checks, inspect fresh media, write sanitized integration validation evidence, and request controller feature validation publication. Hosted checks for this head, city/max fixtures, physical GPU, shaped-network and 20-run performance qualification remain pending. Readiness and stable rollout remain gated. All unpublished edits preserved.


## Resumed integration validation complete

Source `e351d91bf4de6f17657976e4cd467aeb3f44c15b`: 32 focused tests passed; fresh native Main/GLFW/X11 workflow passed; 32-client loopback functional check passed in 352.882 s with zero failures/errors/skips. Fresh player-conflict and placed-world screenshots inspected. F10 H264 clip decoded: 17 frames, 39.679 s, 364434 bytes. Source manifest verified unchanged. Sanitized commands, results, prior failed/interrupted attempts and media hashes: `dashboard/evidence/structure-rebase-validation.json`. No deleted files or unmerged paths; master features retained. Controller feature validation publication required before hosted checks can finish. Earlier hosted reports are historical. City/max, physical GPU, shaped-network, 20-run performance and stable rollout remain gated.


## Published-head validation pending

Head `02081b07f5c50622216bd7b61878e2b441b5fd57`, review base `3f877c21c8e6776adba9d2130e2fd92e55853763`. Observed exact-head push run 39496320307 and PR run 39496323358 queued. No hosted pass claimed. Raw exact-head video, player-conflict image, native manifest and rebase validation report match local bytes. Source and harness unchanged since completed playtest and 32-client checks; manifest hashes verified. Diff check passed and no file deletions/unmerged paths found. Resume by inspecting actual completed hosted results and sanitized test artifacts, without repeating publication. Qualification and stable rollout remain gated.


## Exact-head CI executing

Observed push 39496320307 and PR 39496323358 for `02081b07f5c50622216bd7b61878e2b441b5fd57`: Linux/Windows Maven verification running; Intel/ARM Mac queued in both runs. No completed artifacts or passes claimed. Local functional checks and published-media verification remain valid; source unchanged. Controller should hold this assignment until these existing runs finish, then resume for artifact inspection. No retry or republishing is needed while jobs are active. City/max, physical GPU, shaped-network and 20-run performance qualification remain pending; stable rollout stays gated.


## Experimental review validation complete

Verified sanitized artifacts for push run 39496320307: Linux, Windows, Intel Mac and ARM Mac each passed 758 tests with zero failures/errors/skips at feature head `02081b07f5c50622216bd7b61878e2b441b5fd57`. PR run 39496323358 passed the same four platforms and totals at merge source `6c8ea51517813f82d3c6ea3b4628ee9752507952`; these are merge-head results, not mislabeled feature-head results. Aggregated receipt: `dashboard/evidence/structure-final-hosted.json`, pending controller publication. Production source/harness unchanged since complete native playtest and 32-client functional run. Published exact-head media and source manifest already verified. Functional experimental candidate ready for independent review; developer has not approved or merged it. Required city/max, physical-GPU, shaped-network and 20-run performance qualification remain explicitly pending and gate supported-city claims and stable rollout.


## Reviewer fixes after MP3/Hub-preserving rebase

Readiness withdrawn: full acceptance remains unmet. Rebase head `8466c194264d48e355ce9297f4a5aa3bdeb666a9` preserves MP3/Hub files. Fixed 240-character command truncation with bounded 4096-character input and native >240 path-with-spaces regression. 23 Java tests, 5 Hub tests, 18 structure-tool tests passed. Packaged client CBR/VBR decode passed at 44100 Hz and 6.060 s each. Full native Main/GLFW/X11 workflow passed on inherited display, isolated profile and llvmpipe; fresh media inspected (H264 17 frames, 29.751 s). Details in structure-review-fixes.json. Controller publication needed for hosted validation. Continue city/max implementation: 131072-leaf and 256-chunk caps still reject required fixtures; do not merely raise caps without bounded resource design and measurement. Physical GPU, shaped-network and 20-run qualification remain pending and gate completion/stable rollout.


## 5b655 hosted failure diagnosis

Feature Linux and PR merge Linux summaries: 763 tests each, no failures/errors/skips. PR Windows merge source `bdc2b869614394c672eaf843db7fe60d11a09414`: one failure in loadedForwardAndSideEdgesDoNotWaitForGlobalFairnessScan; 763 tests total. Scheduler/test unchanged from review base and uses a 10 ms wall-clock budget. Local full scheduler suite passed 19 tests after inspecting code. This does not establish a Windows pass or definitive cause. Request one scoped failed-job retry for PR Windows and cancelled Mac jobs. No unrelated scheduler source altered. Preserve reports; continue unfinished city/max implementation after hosted recovery. Readiness, physical GPU/network performance and stable rollout remain gated.


## Corrected CI source, not an unchanged retry

Changed only loadedForwardAndSideEdgesDoNotWaitForGlobalFairnessScan to allow a bounded 10-second functional scheduling step instead of 10 ms; retained first-near-ticket and distant-fairness assertions. Production scheduler/frame budgets unchanged. Full 19-test scheduler suite passed. Windows timing cause is suspected, not proven; exact scoped assertion unavailable. Production Java and native harness match completed playtest hashes. No deletions against review base. Publish corrected source for new Windows/Mac validation and integrate latest master through controller. City/max implementation and all qualification gates remain unfinished; no readiness claim. Details: structure-sky-ci-fix.json.

Delegate review `9b0658c9-c032-49e5-801d-d86268ed8f7f` still running at handoff; collect and explicitly decide on resumption before any ready claim. No delegated validation claimed.


## City staging progress after corrected CI passes

Verified ea2c feature and 4714a96 PR merge artifacts: four platforms each, 763 tests each, zero failures/errors/skips. This does not cover later engine changes. Implemented bounded per-prepare immutable tree sharing and newest-placement undo retention above history target, without raising admission caps. 21 Java tests passed (15 structure, 5 occupancy, 1 real TLS multiplayer). A 16384-cell CPU probe used one staged tree versus 16384 on parent source; single-run preparation 487.771 ms versus 716.828 ms, not statistical performance qualification. Full native Main/GLFW/X11 synthetic-house workflow passed; fresh media inspected, H264 17 frames/27.376 s; source manifest verified. Report: structure-sharing-validation.json. Controller publication/new hosted checks required. Continue partitioned admission/persistence/commit redesign; city/max implementation, physical GPU, shaped-network and 20-run budgets remain unfinished. Not ready. All delegates resolved.


## Paused during body-local collision optimization

Current published parent: `643807dc8d5e120c2b395e478564e523c9e2507e`. Inspected all eight hosted Maven summaries (push 39588925421 and PR 39588929127): 764 tests each, zero failures/errors/skips. Sanitized aggregate saved locally as `dashboard/evidence/structure-643807-hosted.json`.

Unpublished edit: StructureOccupancy.validate now enumerates integer cells under each live body and probes the changed-cell map, instead of scanning all changed cells against every body. Preserves existing citizen/horse/cow float bounds and player double bounds, strict whole-cell overlap, existing authority locks and rejection messages. Uses long counters with int-coordinate clamps; invalid nonfinite bounds reject. THIS EDIT IS UNTESTED. No new tests were started after pause. Delegation was rejected as paused/unauthorized; no paid retry or bypass attempted and no delegate ID was created.

Resume: independently review edge cases (negative/exact boundaries, huge coordinates and float precision), add deterministic reference-equivalence and no-full-map-scan tests, run occupancy/offline/server regressions, then actual native workflow and fresh inspected media. Verify all hosted artifact source heads explicitly in the new aggregate. City/max admission, persistence/commit redesign, physical GPU, shaped-network and 20-run performance remain unfinished. Not ready. All edits preserved; no staging, commit, push or rebase action.


## Body-local collision validation complete

Paused draft reviewed and overflow in huge negative finite coordinate conversion fixed before tests. Live body-local cell probes replace O(cells*bodies) scan. 24 tests passed, including 800 seeded exhaustive-reference comparisons, exact/negative boundaries, empty undo, no-map-scan and extreme/nonfinite positions. Offline/server live-entering-during-staging regressions retained. CPU-only microbenchmark: 1m synthetic cells, 32 poses, 3 warmups/20 measured runs; new median/p95/worst 0.044533/2.215676/2.855196 ms versus parent 75.622697/86.661445/92.663381 ms. These do not qualify city placement/render/save/network budgets. Full native Main/GLFW/X11 workflow passed; fresh rejection screenshot and decoded 17-frame/30.791-s F10 clip inspected; source manifest verified unchanged. Aggregate parent hosted source heads explicitly verified. Report: structure-body-local-validation.json. All delegate tasks resolved. Publish for new exact-source hosted checks; continue city/max admission, persistence and owner-thread redesign. Full acceptance remains unmet; physical GPU, shaped network and 20-run system budgets remain pending.


## Hosted receipt invalidated by controller fence change

Verified local branch and head remain assigned feature at `28d03920e68e693532713d0cfbd3803b101f1ddf`; tree clean before this note, diff check passed. Controller observation is stale because assignment/lease changed. Do not reuse prior receipt or claim hosted qualification. Internal repair must bind current assignment to this exact head/base `8c68a1cd72ec3f344711ef721cdcaffd86c45638`, inspect existing push 39592245599 and PR 39592248486 and deliver a fresh fenced observation (no republish/retry unless actually needed). Local tested evidence remains preserved. City/max implementation and performance qualification remain unfinished.


## Rebased source and sparse capture validation

Rebase parent `0663f242877e1e441a6abc8780f96de2223a64a7` production Java/harness matched previous source. Added conservative known-empty snapshot hint; default unknown keeps existing Source one-read behavior. 31 Java and six Hub tests passed, including default reads, sparse progress/bounds/cancel, terrain/fractional/model queries and snapshot independence. Initial LED test-fixture setter error corrected before final passing run. CPU integration probe: 1048576 cells/one occupied, octree reads 1048576 -> 1, byte-identical packages; single-run capture 285.947 -> 218.369 ms, not qualification. Full native Main/GLFW/X11 workflow passed; fresh capture screenshot and decoded F10 17-frame/28.728-s clip inspected; manifest hashes verified. No files deleted; MP3 assets/code match review base and master Hub fixes retained. Report: structure-sparse-capture-validation.json. All delegates resolved. Publish new source for hosted checks, then continue compact package/chunk transactions, admission/persistence and owner-thread redesign. Full city/max and GPU/network/system performance qualification remain unfinished. Not ready.


## Current hosted observation stale

Local assigned branch/head verified as `2010e9fb8150f25be8b9d629dd6c9eb3c1cbf7cf`, review base `fa010c1aa8fa61383aa63569f2bd952611aaa095`; working tree clean before this note, diff check passed. Controller reported assignment/lease mismatch, so no hosted pass inferred. Request scoped repair of the watcher binding for existing push 39600880756 and PR 39600886275, then a fresh exact-source observation under the current assignment. Do not repeat the stale wait or republish unchanged source. Local native/testing evidence is preserved. Full city/max implementation and physical-GPU/network/system qualification remain unfinished.


## Asset Manager integration validation

Recovered source d10f8c633bdc9f1f0d1b3c02b61ab3b08773efad includes Asset Manager merge 4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a. No files deleted against review base fa010c1aa8fa61383aa63569f2bd952611aaa095; MP3 and Hub retained. Structure/occupancy/TLS multiplayer/MP3/catalog tests passed; optional browser initially skipped, then all three Asset Manager integration tests passed with sandboxed Chromium using worktree-local temp/browser cache. Earlier browser environment failures were read-only /tmp and missing browser binary. Six Hub tests passed. Playtest: full native Main/GLFW/X11 structure workflow passed with isolated synthetic profile on llvmpipe; inspected player rejection image and decoded F10 15-frame/28.023-s clip; production manifest hashes verified unchanged. Report structure-asset-integration-validation.json. Failed design helper closed with self; no pending delegates. Next bounded city step: immutable partition index with primitive local positions/shared palette trees, v1 codec compatibility and equivalence/memory measurements before changing admission. City/max implementation and all physical GPU/network/system qualification remain unfinished. Request fresh exact-source hosted checks after controller publication; not ready.


## Compact retained package storage

Inspected parent `1ff35d4f` hosted summaries: four exact-head push summaries and four PR merge-source summaries, 776 tests each, zero failures/errors and one optional browser skip each. Verified the merge parents and published exact-head video bytes.

New `StructureCells` primitive arrays remove retained Cell/Position records while preserving validated spatial order, frozen snapshots and v1 bytes. Admission caps are unchanged. All 32 Java regressions passed with zero failures/errors/skips. In a synthetic 100,000-cell diagnostic, retained heap delta fell from 7,600,696 to 3,400,480 bytes; encoded SHA-256 matched. With 3 warmups and 20 export samples, parent median/p95/worst was 377.944/403.305/419.344 ms; new was 373.643/436.352/466.481 ms. Concurrent native rendering and GC sensitivity prevent a speedup or city qualification claim.

Playtest: full native Main/GLFW/X11 workflow passed, including live-player and occupied overlap rejection, all rotations, empty terrain preservation, undo, cancellation, malformed import, restart, inventory and editor regressions. Inspected the fresh player-conflict screenshot and a decoded frame from the 17-frame, 31.884-second F10 clip. Source manifest hashes match. Report: `structure-storage-validation.json`. No delegates remain pending. No files were deleted against current base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`.

Next: reduce constructor peak memory and staging maps, then design bounded atomic chunk transactions before revisiting caps. Full city/max implementation and GPU/network/system qualification remain unfinished. Not ready.


## Primitive constructor ordering and duplicate checks

Parent `501e5ded` PR checks passed. Seven hosted summaries were available when inspected: three exact-head push platforms and four PR merge platforms, 777 tests each, zero failures/errors and one optional browser skip each. Push Mac Intel was still in progress and is not claimed passed. Verified merge parents include the assigned parent and current review base.

The constructor now fills primitive builder arrays and sorts packed spatial keys with original indexes. It no longer builds a position hash set or second list of cell records. Duplicate keys fail before publication. Final arrays are copied, so builder state cannot change a published list. Limits and v1 bytes remain unchanged. All 33 distinct targeted regressions passed across the full subset and added ordering test. The seeded 2,000-cell test covers shuffled full-range coordinates, geometry/model associations, canonical section order and separated duplicate rejection.

In a shuffled 100,000-cell constructor diagnostic with 3 warmups and 20 samples, parent/new median thread allocations were 28,854,688/21,201,448 bytes; median times were 112.435/16.512 ms, p95 149.065/34.895 ms and worst 164.806/37.987 ms. Counters exclude prepared input and other threads; these are allocation measurements, not peak memory or city qualification. The existing package probe still produces identical encoded SHA-256.

Playtest: full native Main/GLFW/X11 workflow passed on llvmpipe with an isolated synthetic profile, including all rotations, rejection, empty-space preservation, undo, cancellation, malformed import, persistence and inventory/editor regressions. Inspected the player-rejection screenshot and a decoded frame from the fresh 15-frame, 22.726-second F10 clip. Source hashes match. Report: `structure-constructor-validation.json`. No delegates remain pending; failed helper review was taken over locally. No files deleted against base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`.

Next: stream capture/decode into validated storage and replace placement staging maps with bounded atomic chunk transactions. Full city/max admission, 20-run system budgets and physical GPU/network qualification remain unfinished. Not ready. Publish changed source and evidence for fresh hosted checks.


## Compact capture and import buffers

Inspected all eight parent `565c90be` hosted summaries: 778 tests each, zero failures/errors and one optional browser skip each. Push artifacts identify the exact assigned head; PR artifacts identify merge `91a50f031e970ffa5c1de4de64da601684c02983`, whose parents match the assigned head and current review base.

Capture and import now accumulate cells in bounded primitive buffers. A temporary read-only view goes immediately through full constructor validation and is never published. Capture copies source trees before freezing; import freezes owned trees. Final storage remains independent. Growth cannot exceed MAX_CELLS; fixed constructor capacity, gzip/order/CRC/budget checks and admission caps remain unchanged. All 34 targeted Java regressions passed. Added coverage crosses multiple buffer capacities, keeps source geometry editable and verifies empty capture rejection and byte-identical import.

In a shared-tree 100,000-cell probe (3 warmups, 20 samples), median capture thread allocations were 44,083,960 -> 43,761,416 bytes; median times were 18.111 -> 26.770 ms. Decode allocations were 48,083,512 -> 47,760,968 bytes; median times were 265.703 -> 267.193 ms. No speedup is claimed. With fresh snapshot wrappers per source cell, a separate single GC-sensitive sample before constructor validation showed retained buffer heap 7,683,168 -> 3,531,304 bytes. These do not measure process peak memory or city qualification. Parent/new encoded SHA-256 matched.

Playtest: full native Main/GLFW/X11 workflow passed on llvmpipe with isolated synthetic profile, including rotations, overlap and live-player rejection, empty terrain preservation, undo, cancellation, malformed import, persistence and inventory/editor regressions. Inspected the fresh player-rejection screenshot and decoded frame from a 14-frame, 23.042-second F10 clip. Source manifest verified unchanged. Report: `structure-stream-validation.json`. Failed helper review was taken over locally; no delegates remain pending. No files deleted against base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`.

Next: merge accumulation and validation buffers to avoid their simultaneous retention, then replace placement maps with bounded atomic chunk transactions. Full city/max admission, 20-run system budgets and physical GPU/network qualification remain unfinished. Not ready. Publish changed source and evidence for fresh hosted checks.


## Reuse owned buffers during validation

Inspected eight hosted summaries for parent `18b60ad1`: 779 tests each, zero failures/errors and one optional browser skip each. Verified exact push heads and PR merge parents against base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`.

Private capture/import constructors now validate their detached, frozen buffers directly. This removes the second validation accumulator and redundant geometry copies. Public constructor inputs still copy/freeze caller geometry. Every existing bounds, material, model, surface, count, ordering and duplicate check remains. Safety caps and v1 bytes are unchanged. All 35 targeted regressions passed, including invalid private capture geometry and public input mutation isolation.

In the synthetic 100,000-cell probe with 3 warmups and 20 samples, parent/new median capture allocations were 43,761,448/40,361,368 bytes; median/p95/worst times were 27.905/147.825/211.606 ms and 18.537/62.257/68.315 ms. Decode allocations were 45,361,000/44,360,920 bytes; median times were 261.956/265.998 ms. Encoded SHA-256 matched. These thread allocation diagnostics do not qualify city/max, peak memory, GPU or network budgets.

Playtest: full native Main/GLFW/X11 structure workflow passed on llvmpipe with isolated synthetic profile, including rotations, rejection, empty-space preservation, cancellation, undo, malformed imports, persistence and inventory/editor regressions. Inspected the player rejection screenshot and a decoded frame from the fresh 16-frame, 29.406-second F10 clip. Source manifest hashes match. Report: `structure-owned-validation.json`. Failed helper review was taken over locally; no delegates remain pending. No file deletions against the current review base.

Next: replace placement maps with bounded atomic chunk transactions and measure actual city/max admission before revisiting safety caps. Full city/max implementation, 20-run system budgets and physical GPU/network qualification remain unfinished. Not ready. Publish changed source and media for fresh hosted checks.


## Share empty undo before-images

Inspected eight parent `84fcd75e` hosted summaries: 780 tests each, zero failures/errors and one optional browser skip each. Verified exact push artifact heads and PR merge parents against the assigned head/base.

Placement admission rejects every occupied destination, so each undo before-image was already the same frozen air tree. Prepare now avoids its second LinkedHashMap, and history uses an immutable empty-value view over the placed map keys instead of a second full immutable map. Commit conflict checks and undo later-edit checks are unchanged. Production offline/server authorities consume without retaining the supplied map. This representation depends on the reject-overlap policy.

All 36 targeted Java regressions passed, including irregular negative positions, map/entry mutation rejection, before/after value independence and unrelated later edits surviving undo. A single GC-sensitive retained prepared-operation sample for 16,384 uniform cells fell from 917,528 to 655,400 bytes. Parent engine/nested classes came from exact parent Git source. This is not process peak memory or city/GPU/network qualification.

Playtest: full native Main/GLFW/X11 workflow passed on llvmpipe with isolated synthetic profile, including rotations, overlap/live-player rejection, empty terrain preservation, one-operation undo, cancellation, malformed import, persistence and inventory/editor regressions. Inspected the player rejection screenshot and a decoded frame from the fresh 14-frame, 21.592-second F10 clip. Production/harness hashes match. Report: `structure-undo-snapshot-validation.json`. Failed helper review was taken over locally; no delegates remain pending. No file deletions against current base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`.

Next: bounded placement data and atomic chunk transactions, then actual city/max admission and performance measurements before revisiting safety caps. Full city/max implementation, 20-run system budgets and physical GPU/network qualification remain unfinished. Not ready. Publish changed source and fresh media for hosted checks.


## Known-empty chunk validation reads

Parent `2edf60a5` PR platform checks passed. Seven hosted summaries were available when inspected: three exact-head push and four PR merge summaries, 781 tests each, zero failures/errors and one optional browser skip each. Push Mac Intel remained in progress and is not claimed passed. Verified merge parents against the assigned head and current base.

Detached CPU chunk validation now skips octree lookup only for unstaged positions marked known empty by the existing conservative snapshot hint. Staged geometry takes priority. Unknown sources retain one cell read. Commit-time authoritative revalidation and every chunk/model/mesh cap remain. All 37 targeted Java regressions passed, including negative coordinates, fractional neighbors, unknown fallback and explicit known-empty fragmented staged geometry still rejected by the mesh budget.

In a synthetic 16,384-cell, 64-chunk cached-empty-source probe with 3 warmups and 20 samples, cell reads fell from 262,144 to 16,384. Parent/new median/p95/worst times were 107.442/157.195/203.019 ms and 158.881/275.818/361.166 ms. Native software rendering ran concurrently. The probe uses a cheap cached source and timing increased, so no speedup or city qualification is claimed.

Playtest: full native Main/GLFW/X11 workflow passed on llvmpipe with isolated synthetic profile, including rotations, rejection, empty-space preservation, cancellation, undo, malformed import, persistence and inventory/editor regressions. Inspected the player rejection screenshot and decoded frame from the fresh 16-frame, 25.467-second F10 clip. Production/harness hashes match. Report: `structure-chunk-hint-validation.json`. Failed helper review taken over locally; no pending delegates. No file deletions against base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`.

Next: bounded placement data and atomic chunk transactions, then measure actual city/max admission before changing safety caps. Full city/max implementation, 20-run system budgets and physical GPU/network qualification remain unfinished. Not ready. Publish changed source and media for fresh hosted checks.


## 32-client functional qualification

Exact tested head `86622511b6ef416009c4e9af50d1258fe0d4c3ba` is unchanged. Inspected all eight hosted summaries: 782 tests each, zero failures/errors and one optional browser skip each. Verified exact push heads and PR merge parents against base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`.

Ran `StructureMultiplayerTest` with `-Dvoxel.structure.clients=32` in the test JVM. One test passed with zero failures/errors/skips; the resulting XML confirms the 32-client property. The production TLS server and 32 protocol clients exercised a 512-cell, 512-dependency model road through all rotations, empty-cache synchronization, overlap rejection, undo, cancellation, a single-winner 512-cell race, late join and server restart. The existing 306-second batch pacing honored the production login budget. Credentials, accounts, TLS keys and runtime logs remain private under target.

Playtest: this is noninteractive server/protocol integration; a browser does not apply. Production/harness source hashes still match the existing native run. Downloaded the exact-head published F10 clip, verified its SHA-256 against Git, decoded its 16 frames/25.467 seconds and inspected a frame. It shows the native structure workflow, not 32 rendered clients. Initial video decode command had a mistyped JAR path; corrected before successful decode. Report: `structure-32-client-validation.json`.

This qualifies functional loopback behavior on the small fixture only. It does not qualify city/max, physical GPU, shaped network or memory/frame/tick/traffic budgets; total JUnit elapsed time includes login pacing and is not an operation latency. No production or test source changed. No delegates remain pending. Full acceptance and supported-limit publication remain gated.

Next engineering work must address bounded placement storage, atomic chunk publication and persistence beyond the 2,000,000-edit history ceiling before actual city/max admission. Existing 131,072-leaf and 256-chunk development caps remain; do not raise them without the required fixtures and measurements. Not ready. Publish the new validation evidence, then continue implementation.


## Native corpus admission

Inspected both hosted runs for `57c477db1f6f2512d678fc52a5d8215ea8e660b8`.
All eight Maven summaries contain 782 tests, zero failures/errors and one skip.
Verified push source hashes and PR merge parents against the assigned head/base.
Report: `structure-57c4-hosted.json`.

Added the offline `tools/structures/NativeFixture.java` converter. It uses
production model/octree codecs, native section order and bounded output, without
constructing an admitted package or changing a world. Both complete corpora now
reach the actual production VSTR loader. Both fail with `Experimental geometry
cap`. City native bytes are 38,191,053 compressed / 58,000,837 decoded; maximum
bytes are 55,607,892 compressed / 309,713,208 decoded. The native city also misses
the required 16–32 MiB package range. These are failed scale gates.

Playtest: noninteractive CLI integration, so browser playtesting does not apply.
Compiled current production classes; the converter's self-test matches the
production encoder byte for byte and roundtrips maximum section coordinates,
whole/half/1/16 LED geometry and model rotation. Three malformed CLI inputs reject
without a destination file. Six existing fixture-parser tests passed. The
production and native-harness hashes still match the recorded graphical run;
verified the exact-head published MP4 SHA-256, decoded 16 frames / 25.467 seconds
and inspected a frame. No fresh gameplay run is claimed for this tooling change.

No file deletions against base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`.
No gameplay source changed. All delegate tasks resolved. The new tool and reports
need controller publication. Full city/max storage, atomic chunk publication,
persistence beyond the 2,000,000-edit history ceiling, 20-run system budgets and
physical GPU/shaped-network qualification remain unfinished. Keep safety caps
and rollout gates. Not ready.


## Compact placement storage

All eight hosted summaries for parent `e8a32faf` contain 782 tests, zero failures
and errors, and one optional browser skip. Verified the exact push source hashes
and PR merge parents against the assigned head/base. Report:
`structure-e8a-hosted.json`.

Placement staging now stores sorted 28-bit relative coordinates and frozen tree
references in arrays. An immutable Map view retains the existing authority API.
The private Prepared constructor retains that immutable view instead of copying
its entries back into an object map. Conflict revalidation, cancellation, geometry
budgets, atomic authority publication and undo semantics are unchanged. No caps
were raised. Lookups use binary search, which is a deliberate memory/time tradeoff.

The 43 existing targeted regressions passed, including TLS multiplayer, offline
placement, occupancy, streaming and MP3 support. Two new compact-map tests passed
after correcting non-opaque test colours and the expected frozen-tree exception.
They compare 2,000 shuffled entries with a reference map, exercise coordinate
extremes, reject duplicate/incomplete buffers and reject mutation through views.

A single GC-sensitive 16,384-cell retained Prepared sample fell from 656,808 to
131,392 bytes. The 64-chunk CPU staging probe used three warmups and 20 samples:
parent median/p95/worst 115.908/179.802/210.429 ms; compact
103.245/153.117/155.950 ms. Background software rendering was active. These are
small-fixture diagnostics, not peak-memory or reference city/max qualification.
Parent engine classes were compiled from the exact parent Git source.

Report: `structure-placement-storage-validation.json`. No file deletions against
base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`; all delegate tasks resolved.
City/max admission, atomic chunk publication, persistence beyond 2,000,000 edits,
main-thread work and physical GPU/shaped-network system qualification remain
unfinished. Supported-limit publication and stable rollout remain gated.


Playtest: fresh full native Main/GLFW workflow passed on the inherited X11 display
with an isolated synthetic profile and llvmpipe. Verified all four rotations,
overlap/live-player rejection, empty-space preservation, cancellation, atomic
malformed-import failure, repeated placement, one-operation undo, persistence,
inventory and model editor regressions. Decoded and inspected a frame from the
fresh F10 MP4 (17 frames, 27.687 seconds) and the player-conflict screenshot.
Production/harness source hashes match after execution. Media remains local,
pending controller publication. Publish source and evidence for fresh hosted
checks; full acceptance remains unfinished. Not ready.


## VSTR v2 dependency encoding

Inspected seven available parent `4902a95b` hosted summaries: 784 tests each,
zero failures/errors and one optional skip each. Verified exact push source
hashes and PR merge parents against the assigned head/base. No unavailable eighth
artifact is claimed. Report: `structure-4902-hosted.json`.

New exports use VSTR v2; decoding retains the v1 path. Dependencies use their
existing octree bytes when smaller, otherwise a bounded occupancy bitmap and
opaque RGB triples. Standalone model codecs, fingerprints, saved worlds and
network model transfer remain unchanged. Legacy redundant octree branches retain
their original bytes so dependency identity survives. Model names, 8/16/32 sizes,
200,000-byte/8,192-quad limits and package byte limits remain enforced. Dependency
occupancy, identity and surface caps are now checked during decoding, before
retaining later dependencies or reading cells. No admission cap was raised.

Final targeted reports contain 52 passing cases: 45 existing regressions and
seven codec tests. They cover v1 read/resave, all resolutions, air versus opaque
black, independent bitmap/RGB byte layout, colours/positions, canonical and
redundant-tree identity, truncated data, unknown modes and early duplicate
rejection. Native fixture self-test matches the production writer and decoder.

The same city corpus fell from 38,191,053 to 30,258,485 compressed bytes and from
58,000,837 to 41,073,093 decoded bytes. It now fits 16–32 MiB, but rejects the
unchanged dependency surface cap. Maximum fell from 55,607,892 to 53,637,310
compressed bytes and from 309,713,208 to 305,479,992 decoded bytes; it still rejects
the geometry cap. Two conversions per corpus produce identical SHA-256 hashes.
These are byte sizes and admission failures, not performance qualification.
Generated large files remain under target. Report: `structure-v2-validation.json`.

The first graphical run was discarded by its source-hash guard after a legacy
identity fix. The final native harness adds a dense 8-resolution dependency to
exercise packed capture/export/import and all four placement rotations alongside
the existing 32-resolution lamp. Fresh frozen-source validation is required.
All delegate tasks resolved. No file deletions against the current review base.
Full city/max admission, atomic chunk publication, persistence beyond 2,000,000
edits, main-thread budgets and physical GPU/shaped-network system qualification
remain unfinished. Supported-limit publication and stable rollout remain gated.


## Pause checkpoint — VSTR v2 unpublished

Paused on explicit shared-quota/service-control instruction. Parent head is
`4902a95b47c5e1434b8093e4bd13a292e2fd9d0e` on the assigned feature branch.
All local code, tests, docs and media remain unpublished and preserved. No Git
mutation or publication was performed.

Final frozen-source native process exited 0 immediately before the pause. Its
sanitized report is retained in `structure-native-result.json`. The fresh video
and screenshots have not yet been independently inspected or hashed for this
final run. No new tests or implementation started after the pause request.

Resume only when authorized: inspect fresh media, verify source/media hashes,
finish `structure-v2-validation.json`, then request feature publication and fresh
exact-head hosted checks. Final targeted reports contain 52 passing tests; native
VSTR v2 city/maximum conversions repeat byte-identically. City is 30,258,485 bytes
compressed but rejects dependency surface cap; maximum is 53,637,310 bytes and
rejects geometry cap. Full city/max implementation and reference qualification
remain unfinished. Safety caps and rollout gates remain in place. Not ready.


## Resume — VSTR v2 media verified

Resumed under current controller authorization. The final native run passed with
both the existing 32-resolution lamp and a dense 8-resolution component selected
to exercise packed encoding. Verified the complete production/harness path set
and hashes. Decoded the fresh 17-frame, 27.033-second F10 clip; inspected a decoded
frame, player-conflict screenshot and component closeup. Video SHA-256:
`66d922cfb08acbc6bfbc55587e5b66dd0682d723389de7450b0f4333fbba5671`.

Playtest: real Main/GLFW/X11 workflow on inherited display and isolated synthetic
profile, llvmpipe LOW/detail32/horizon512/scale0.5 with shadows/AA/dynamic OFF.
Capture/export/import, both model encodings, all four rotations, conflict and
live-player rejection, empty-space preservation, cancellation, malformed import,
undo, repeat placement, restart, inventory and model-editor regressions passed.
The 52 targeted test results are retained from before the pause; no unnecessary
rerun is claimed. Updated `structure-v2-validation.json` contains commands,
observed results and the media receipt. No file deletions against the review
base. All helper tasks resolved; no pending assets.

Implementation, docs and media are still unpublished. Request controller feature
validation to publish the assigned branch and get fresh exact-head checks.
City now fits the package-size target but still rejects dependency surface cap;
maximum still rejects geometry cap. Full admission/storage/atomic chunk work,
2,000,000-edit persistence ceiling and 20-run reference GPU/network/system
qualification remain unfinished. Gates stay closed. Not ready.


## Pause checkpoint — chunk copy-on-write snapshots unpublished

Paused on explicit shared-quota/service-control instruction. Parent head is
`a6486ea7d2bb5240969d4a36de8a9a29b06f697e` on the assigned feature branch.
Unpublished changes: `WorldVoxels.java` now uses chunk buckets and ownership tokens
to share frozen cell trees across snapshots and clone a bucket on its next edit.
Cell reads are frozen; edits still go through apply. Persistence formats and
admission caps are unchanged. Added `WorldVoxelSnapshotTest` for nested snapshots,
negative chunk edges, frozen reads, fractional LED edits and randomized fork/edit/
clear isolation. No performance improvement has been measured yet.

The already-running Maven command finished with exit 0 after the pause request.
Its selected reports contain 65 tests; metadata is retained in
`target/structure-snapshot-tests-summary.json`. Command:
`mvn -q -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/structure-audit-tmp" -Dtest=WorldVoxelSnapshotTest,FractionalWorldTest,RegionalSaveCompatibilityTest,StructureModelCodecTest,StructurePlacementCellsTest,StructureTest,StructureOccupancyTest,WorldStreamingTest,MusicMp3Test,StructureMultiplayerTest test`.
No new tests or implementation started after the pause request.

Delegate review `bc893ed1-8508-4b17-b23f-d05029656665` was submitted before the
pause. Collection returned an authorization/pause notice, no proposal. Do not
retry inference until authorized; resolve or take over this task on resume.
No delegate conclusions were used as evidence.

Resume only when authorized: inspect/resolve the pending design review, audit
bucket aliasing and frozen-cell callers, measure snapshot allocation/time and
edit cost, then run the fresh native workflow and capture/inspect media for this
changed source. Current published v2 media does not qualify the snapshot edits.
Hosted artifact IDs for the parent were listed but summaries were not downloaded
in this continuation; inspect actual scoped results before claiming them. No Git
mutation or publication performed. Full city/max admission, 2,000,000-edit history
ceiling, atomic chunk publication and reference GPU/network/system qualification
remain unfinished. Caps and rollout gates stay in place. Not ready.


## Chunk copy-on-write snapshot validation

Resumed under current controller authorization. Took over the interrupted helper
review independently and resolved its task. Audited callers: world cell values
are read/copied; edits use apply. Snapshot ownership invalidation ensures neither
side can mutate a shared bucket. Nested snapshots and clearing remain isolated.
Single-cell buckets use one direct local index; larger buckets use local integer
keys. This removes the first prototype's scattered-edit memory increase. No save,
wire or admission-limit changes. No files deleted against the review base.

Final targeted reports contain 66 passing cases: the selected 65-case run plus
the subsequently added full 4,096-coordinate case in a successful focused rerun.
Coverage includes forks/clear, negative boundaries, singleton/map transitions,
frozen reads, fractional geometry, compatibility, offline/TLS structures,
occupancy, streaming and MP3. All eight parent `a6486ea7` hosted summaries have
791 tests, zero failures/errors and one optional skip each. Exact push hashes
and PR merge parents were verified; parent results do not qualify local edits.

Measured parent/current snapshot allocations for 100,000 compact cells:
7,697,416/17,560 bytes; median/p95/worst 30.824161/80.081332/202.031732 ms and
0.154970/0.272457/0.316526 ms. Scattered snapshot allocations:
7,697,416/4,248,696 bytes. Scattered source retained-heap samples were
17,562,304/17,595,672 bytes, approximately unchanged within GC-sensitive noise.
Compact source retained samples fell from 17,547,208 to 11,566,872 bytes. First
compact edit allocation rises from 208 to 10,528 bytes to copy the shared bucket;
first-edit latency also increases. Report includes all measured operations and
tradeoffs. Separate JVMs, 3 warmups and 20 samples per operation; not peak memory
or city/max/system qualification. Parent classes came from exact parent source.

Playtest: fresh Main/GLFW workflow passed on inherited X11 with isolated synthetic
profile and llvmpipe, including capture/import/export, four rotations, empty-space
preservation, overlap/live-player rejection, cancellation, malformed-import
atomicity, one-operation undo, repeated placement, restart, inventory and editor
regressions. Verified full production/harness path set and hashes. Decoded and
inspected a frame from the fresh 17-frame, 27.983-second F10 clip, plus the player
conflict and component screenshots. Report: `structure-snapshot-validation.json`.

Source/docs/media remain local, pending controller publication and exact-head
checks. City/max admission, atomic chunk publication, 2,000,000-edit history
ceiling and reference GPU/shaped-network/system qualification remain unfinished.
No caps raised or supported limits published. Not ready.

## Whole-cell server staging

Published parent `9eea982587a4b3f4cb8bc9faaa2f7a4eea556712` passed all eight
hosted platform summaries: 794 tests each, zero failures/errors, one skip each.
Verified feature source hashes and PR merge parents. Receipt:
`dashboard/evidence/structure-9eea-hosted.json`. These parent results do not
qualify the new edits.

Server structure staging now installs each complete cell into its private COW
geometry snapshot instead of rebuilding it through leaf edits. The new
`WorldVoxels.replaceCell` checks resolution, copies the caller's tree wrapper,
freezes the copy and detaches shared chunk buckets before changing them. Nodes
remain persistent and shared. Ordinary apply uses the same install path, with
one additional wrapper copy. Save-before-publication order, history generation,
formats and caps are unchanged. No whole-operation atomicity or city support
is inferred from the faster geometry step.

37 selected tests passed, including TLS multiplayer placement/undo and offline
occupancy regressions. The new test compares every voxel against replay,
checks a known coloured sentinel, caller mutation, snapshot isolation, explicit
air and bad inputs. A fixture initially used set for LED alpha 254; corrected
to fill, then reran all selected suites. A stricter sentinel assertion passed
in a subsequent four-test focused run. Helper failed without a proposal and
was explicitly resolved self; local inspection and execution supplied evidence.

Geometry-only probe: 64 cells, 512 fractional leaves per cell, three warmups and
20 samples. Replay median/p95/worst: 20.047156/28.671589/38.647318 ms;
replacement: 0.012723/0.027545/0.031195 ms. Median thread allocations:
13,577,760/7,840 bytes. Both paths use current APIs, not different source heads.
Checks compare every voxel and preserve snapshots/unrelated geometry each run.
History, save, rendering and networking excluded; not peak memory or city/max
qualification. Commands and details: `structure-whole-cell-validation.json`.

Fresh native Main/GLFW workflow passed on inherited display with isolated
synthetic profile. Inspected live-player conflict and component screenshots and
a decoded F10 frame: 17 frames, 27.148 s, 362,256 bytes. Complete production Java
and harness path set and hashes verified unchanged. Video SHA-256:
`9ce34c04f7b03883f375fb24d308558f463f9b72cf16cb0cfd771f4c34f1ef63`.
llvmpipe rendered about 1 FPS; no physical GPU/frame-time qualification.
Source, report and fresh media await controller publication and new checks.
No files deleted against recorded review base; existing MP3/Hub/Asset Manager
features retained. Full city/max admission, 2,000,000-edit persistence ceiling,
chunk transaction/publication, reference GPU/network and 20-run system budgets
remain unfinished. No readiness claim or rollout-gate changes.

## Compact per-cell history index

Resumed with completed `98e80d57` checks. Independently inspected all eight
sanitized Maven summaries: 795 tests each, zero failures/errors and one skip.
Verified feature hashes and merge parents. No repeated completed-check wait.
Receipt: `dashboard/evidence/structure-98e80-hosted.json`.

Unpublished history index now stores one key directly for single-edit cells;
multiple keys promote to an ordered set and removal demotes back to one key.
Global replay order and save/wire formats remain unchanged. Explicit read-only
key/value views match the existing documented collection-view contract.
38 selected tests passed, including 5,000 seeded operations versus unindexed
history, promotion/demotion/replacement, snapshots, fractional geometry,
offline placement and actual TLS multiplayer regression. Earlier compile type
error and inherited mutable values-view behavior were corrected, then all
selected tests rerun. Delegate failed without output and was resolved self.

100,000-edit diagnostic: parent/current single-cell retained-heap samples
47,447,120/29,813,792 bytes; copy allocations 40,164,480/22,564,480 bytes.
Single-copy median/p95/worst 110.389284/214.227179/223.786557 ms versus
89.522193/170.099534/176.431547 ms. Fractional layout (25,000 cells, four
edits each) retained samples 27,793,416/28,392,320 bytes; allocations
30,487,584/31,087,584 bytes. Fractional median/p95/worst
71.032499/173.765805/234.258529 ms versus
62.088027/202.613545/303.684588 ms. Three warmups/20 samples in separate
JVMs; heap samples are GC-sensitive, not peak memory. Fractional memory and
tail-latency costs are recorded. These are storage diagnostics, not city/max
or main-thread qualification; full-history copy remains O(edits) and too slow.
Report: `structure-history-index-validation.json`.

Fresh native workflow passed on inherited X11 with a synthetic profile.
Complete production/harness source set and hashes verified. Inspected fresh
conflict/component screenshots and decoded F10 frame: H264, 17 frames,
29.365 s, 367,895 bytes. SHA-256:
`8c302317fa5ca8405d0b69e269fa56c8ee7750aca9d2bda978f545fd6058fd68`.
llvmpipe about 1 FPS; not reference GPU performance. Source, report and media
need controller publication and fresh exact-source checks, including current
master integration by the controller. No completed old check wait requested.
Next larger work remains versioned region transactions and moving full-history
construction/save/publication off the interactive owner path, with concurrent
edit revalidation and bounded persistence/synchronization. No limits raised.
City/max admission and reference GPU/network/system qualification unfinished.

## Cancellable import and server decode

Verified all eight completed parent `82556042` Maven summaries: 797 tests each,
zero failures/errors, one skip. Feature hashes and PR merge parents checked;
receipt `structure-82556-hosted.json`. No repeated old check wait requested.

Decode/load now accept cancellation and progress callbacks while old entry
points preserve their codec/limits. UI saved browsing/import passes its flag
and progress. Server decode observes peer cancellation before staging. Parsing
and final constructor validation poll the flag between bounded units; gzip
integrity, byte limits and material/dependency checks remain intact. Individual
file reads, model meshing and final cell sort are not preempted. Export/save
cancellation is unchanged. No responsiveness timing budget is claimed.

35 selected tests passed: three new cancellation/CRC/roundtrip/retry cases,
24 structure cases, seven model-codec cases and one actual TLS multiplayer
regression. New cases also pass in a focused rerun after closing a test stream.
Helper failed without output and was explicitly resolved self. Inspected UI
completion gating, server cancellation/failure cleanup and decoder cleanup.

Fresh native workflow now includes cancelling a heavy import and checking that
selection, asset library, geometry and definitions remain unchanged, alongside
all existing workflow/regression steps. Run passed; inspected fresh import-cancellation screenshot and decoded F10
frame. H264: 16 frames, 30.776 s, 458,140 bytes; SHA-256
`030984005a3915ded68423aa7695c3eaedda1b7bee13d5cd85ab0433d7a3043e`.
All 213 production/harness source paths and hashes verified unchanged.
Captures show llvmpipe 0–1 FPS; functional evidence only. Source and media need
controller publication and fresh exact-source checks. Report:
`dashboard/evidence/structure-decode-cancel-validation.json`.
Full city/max admission, 2,000,000-edit persistence ceiling, atomic chunk
publication and reference GPU/network/system qualification remain unfinished.
No caps raised and no readiness claim. Controller owns latest-master integration
and subsequent publication/checks; source changes require renewed validation.

## Cancellable encoding and atomic asset-file save

Parent `96315221` push and PR checks completed. Inspected all eight sanitized
summaries: 800 tests each, zero failures/errors, one skip. Exact feature hashes
and PR merge parents verified in `structure-96315-hosted.json`.

New encode/save overloads preserve default APIs and byte formats while checking
cancellation between models, cells and temporary-file writes of at most 64 KiB.
Capture final validation now polls its flag. UI capture/import/export and upload
preparation use cancellation/progress. Asset files stage beside their destination
and check cancellation before atomic replacement; failed staging cleans its temp.
A successful replacement commits the file. No callbacks/cancellation checks or
cleanup errors follow success; the UI uses a fresh completion token so late
Cancel does not misreport a committed save as cancelled. Save progress stays
below 100 during preparation; owner-thread completion marks 100. No fsync or
power-loss durability claim. World save/commit design is unchanged.

38 selected tests passed: encoding cancellation/identity, interrupted entry,
old-file preservation, fresh-file cancellation, callback error and temp cleanup,
retry, decoder regressions, offline placement, codecs and actual TLS multiplayer.
Helper failed without output and was explicitly resolved self. Inspected commit
boundary, cleanup and callback/late-cancel behavior independently.

Fresh native Main/GLFW workflow includes heavy export cancellation with no
published package, then existing import cancellation and all prior workflow
checks. Run passed; inspected fresh export-cancellation screenshot and decoded F10
frame. H264: 16 frames, 24.874 s, 355,502 bytes; SHA-256
`a3ade5feca837cf02b27ead3e2b5e845f058bda0c8925e34a51de07c33c43aec`.
All 213 production/harness source paths and hashes verified unchanged.
llvmpipe about 1 FPS; no physical-GPU/frame-time qualification. New source
and media need controller publication and fresh exact-source checks. Report: `structure-encode-cancel-validation.json`.
Full city/max admission, history persistence ceiling, atomic world chunk commit,
physical-GPU/shaped-network/32 rendered-client and 20-run system budgets remain
unfinished. No caps raised; not ready. Controller integrates current master and
publishes the assigned feature source for fresh hosted validation.

## Detached chunk subtree grafting

Parent `b598be2b` has eight inspected hosted Maven summaries: 803 tests each,
zero failures/errors, one skip. Exact feature hashes and PR merge parents
verified in `structure-b598-hosted.json`. No repeated completed-check wait.

Added aligned octree subtree replacement using persistent immutable nodes.
Destination-only normalization handles legacy redundant branches without
changing source bytes or fingerprints. Chunk.replaceCell updates model IDs,
fractional-cell count and invalidation metadata once per complete cell.
Detached structure chunk validation now uses this instead of replaying leaves;
existing CPU mesh/instance budgets and world commit paths remain unchanged.

41 selected tests passed on final source, including 100 seeded replacement
comparisons against replay, exact generated meshes, model/LED/air metadata,
source/snapshot independence, neighbours, uniform collapse, bad regions and
legacy source-byte preservation. Earlier 40-case source passed before the
legacy normalization refinement; final selected run includes its new case.
Helper failed without output and was explicitly resolved self.

Assembly diagnostic: 16 cells sharing a 512-leaf fractional LED pattern,
three warmups/20 samples per path in separate JVMs. Replay median/p95/worst:
16.207705/21.599483/24.641474 ms; graft:
0.339242/0.460220/0.536126 ms. Median thread allocations:
5,854,816/4,640 bytes. Source-node normalization included. Exact tree/source
and mesh equivalence checked outside timing. Excludes mesh generation, full
validation, history/save/commit/network/render; not peak memory or city/max
qualification. Report: `structure-chunk-graft-validation.json`.

Fresh native full workflow passed. Inspected component screenshot and decoded
F10 frame: H264, 15 frames, 25.268 s, 364,257 bytes. SHA-256:
`82eae82c249b9b67e17245826106203e66447a11899139da16c03e448adc9dca`.
All 213 production/harness source paths and hashes verified unchanged.
llvmpipe about 1 FPS; not physical-GPU/frame-time qualification. New source
and fresh media require controller publication and exact-source checks. No deleted files against review
base; merged MP3/Hub/Asset Manager features retained. Required city/max fixtures
still fail unchanged provisional admission limits. Full history persistence,
atomic world transaction and reference GPU/network/system qualification remain
unfinished. No supported-city/readiness claim; rollout gates stay closed.

## Trusted server chunk-validation reuse

Parent `1f90b789` has eight inspected hosted summaries: 806 tests each, zero
failures/errors, one skip. Exact feature hashes and merge parents verified in
`structure-1f90-hosted.json`; no repeated completed-check wait.

Server snapshots now supply trusted per-chunk geometry stamps. Stamps cover
terrain identity/stable generation and bucket identity/edit revision. Surface
generation changes use odd/even revision fencing; snapshot trust is lost after
any change, including ABA. Validation caches only a matching before/after stamp
for each successfully validated chunk, bounded by unchanged 256-chunk admission.
Commit reuses only matching stamps under the authority lock. Unknown/offline
sources retain full validation. Destination occupancy, dependency revision,
cancellation and authority live-body guards remain active. No wire tokens.

42 selected tests passed: unchanged reuse, unknown fallback, same-chunk and
outside-chunk edits, terrain ABA, repeated owned-bucket edits, snapshot stability,
changed destination/models and cancellation. A stronger test adds 171 fragmented
model neighbours after staging and proves the existing chunk surface cap still
rejects before commit, preserving neighbour edits and leaving destination empty.
Existing real TLS, offline, occupancy and snapshot regressions passed. Initial
41-case run passed before this added case. Helper failed and was resolved self.

CPU diagnostic: 16 unchanged 512-leaf cells in one chunk, three warmups and 20
samples per source mode in separate JVMs. Unknown/stamped final cell reads:
4,096/16. Median/p95/worst: 62.724459/120.903876/131.616655 ms versus
0.234065/0.447535/0.514930 ms. Median thread allocations:
74,834,720/6,600 bytes. Fixture authority installs geometry only; real history,
disk save, body simulation, traffic and rendering excluded. Not peak memory or
full commit/city/system qualification. Source: `PreparedValidationProbe.java`;
report: `structure-validation-stamp-validation.json`. Current VPS hardware/OS
checked separately. Per-bucket revision adds storage overhead, not a memory claim.

Fresh native offline regression passed, retaining full unknown-source validation.
Changed server reuse path exercised by actual TLS tests. Inspected fresh conflict
screenshot and decoded F10 frame: H264, 16 frames, 26.62 s, 439,154 bytes;
SHA-256 `591134870b071fb669a8d24d68706588d2e5200dff546777e27b71fccb7eb73a`.
All 213 production/harness source paths and hashes verified unchanged. llvmpipe
about 1 FPS, not reference GPU/frame-time qualification. New source/report/media
need controller publication and fresh exact-source checks. Full city/max admission, history
persistence, atomic world transaction and reference GPU/network/system gates
remain unfinished. No caps raised; not ready. Controller owns latest-master
integration, publication and fresh exact-source checks for these new edits.

## Reuse of the staged empty-destination proof

Inspected seven available hosted summaries for parent `bbe453df`: three feature
platforms and all four PR merge platforms. Each reports 811 tests, zero failures
and errors, one skip. Verified merge parents against the feature head and review
base. Live feature Intel Mac was still running despite the controller success
observation; no feature Intel pass inferred. Details: `structure-bbe453-hosted.json`.

Final server commit now reuses the staged empty-destination proof only when every
affected chunk has a matching non-null trusted stamp under the authority lock.
Any changed or missing stamp restores the complete destination scan. The fallback
checks cancellation every 128 destinations. Model revisions, mesh caps, final
cancellation and live-body guards remain active. Unknown/offline sources still
scan fully. Preparation counts affected chunks in a temporary bounded set; no
peak-memory or end-to-end performance improvement claimed. Admission caps remain.

43 selected tests passed, including real TLS multiplayer, offline, occupancy and
snapshot regressions. Added late scan cancellation coverage, then all seven stamp
tests passed: 44 distinct tests across both runs, zero failures/errors/skips.
Unchanged trusted chunks perform zero repeated destination reads. Partial/changed
trust cannot hide a conflict in a second chunk. Cancellation after 128 reads
leaves destinations unchanged and performs no commit. Mesh surface overflow,
terrain ABA and dependency changes still reject safely. Review helper failed and
was resolved self; all reported execution was local.

No files deleted against review base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`.
Intended source-line replacement guards reuse of an existing validated proof;
full fallback remains. MP3, Hub and Asset Manager features retained. Full city/max
admission, 2,000,000-edit persistence ceiling, atomic chunk publication and 20-run
reference GPU/network/system qualification remain unfinished. Not ready; rollout
gates stay closed. Controller owns latest-master integration and publication.

Fresh native playtest passed on the unchanged production/harness source set:
213 paths and hashes verified. Inspected 180-degree preview, player-conflict
screenshot and decoded F10 frame. H264: 16 frames, 26.309 s, 357,491 bytes;
SHA-256 `e33738a71efb03b0e0b6088a8c116b0bbb87fd7d107e524fea5e7f83fc6baa5e`.
Native manifest digest: `2b75840ac8c75edfb3b74f5f197dd36a3437d2267bc603e4834dc35ae7203955`.
llvmpipe about 1 FPS; native functional regression only. Full workflow included
capture, export/import, four rotations, preview, conflicts, empty-space
preservation, cancellation, undo, repeated placement, restart and inventory/model
editor regressions. Changed server shortcut exercised by real TLS tests.
Report: `structure-empty-proof-validation.json`. Local media pending controller
publication; request fresh exact-source hosted validation after integration.

## Shared transaction history indexes

Parent `2f08be25` has eight inspected hosted summaries: 813 tests each, zero
failures/errors and one skip. Exact feature hashes and PR merge parents verified
against review base in `structure-2f08-hosted.json`; no repeated completed wait.

Offline/server structure staging now uses `WorldVoxels.History.copyOf` to clone
ordered maps and share per-cell index entries. Each fork rotates the source's
owner token and gives the copy a unique token. Either side clones a shared index
entry before put/remove. Fractional key sets retain insertion order. Generic map
sources retain validated rebuilding. Clear only drops map entries. This preserves
rollback sources and replay order without rebuilding every cell key/index.

55 selected tests passed: nested independent copies, source/copy edits, clear,
promotion/demotion, generic map fallback, read-only views, 5,000 seeded mixed
operations across up to 12 histories, actual TLS multiplayer, offline
persistence/undo/failure, occupancy, snapshots and fractional-world regressions.
Review helper failed and was resolved self. Only local executed checks claimed.

Copy-only diagnostic: 100,000 edits, three warmups and 20 measured runs in separate
JVMs per layout/mode. Single-cell rebuild/shared median/p95/worst:
68.177886/192.465249/235.413659 ms versus
17.122267/20.852202/75.093599 ms. Median copy thread allocations:
22,564,496/9,297,424 bytes. Fractional (25,000 cells, four edits each):
53.048838/108.863314/133.103174 ms versus
10.905382/25.684047/77.299449 ms; allocations:
33,487,600/6,110,992 bytes. First-remove allocations rise from 96 to 120 bytes
(single) and 48 to 424 bytes (fractional). Exact replay order and source
immutability checked outside timed copy regions in every sample.

Copies remain O(history size); both top-level maps still clone under the existing
lock. No peak-memory/full save/tick/frame/traffic improvement claimed. Full city/max
admission, atomic chunk publication, persistence beyond the 2,000,000-edit ceiling,
and reference GPU/network/system qualification remain unfinished. No caps raised;
rollout gates stay closed. No files deleted against the review base. Intended
source-line replacements select the shared-index copy for structure staging.
MP3, Hub and Asset Manager retained. Source: `HistoryCopyProbe.java`; report:
`structure-history-copy-validation.json`. Controller owns publication/integration
and new exact-source hosted validation after fresh native media is complete.

The strengthened offline save-failure test also passed independently. Two valid
fractional air-history entries at a destination force the staged history to
replace a shared multi-key index. Failed persistence restores original ordered
history, geometry and dependencies; a subsequent whole-cell edit still matches
the unindexed reference, proving the restored cell index remains usable.

Fresh native workflow passed on all 213 unchanged production/harness paths and
hashes. Capture/export/import, all four rotations, preview/conflicts, empty-space
preservation, live-player rejection, cancellation, undo, repeated placement,
restart and inventory/model-editor regressions passed. Inspected fresh conflict
image and decoded F10 frame. H264: 17 frames, 31.983 s, 364,851 bytes;
SHA-256 `1a3e2a2b636784db38031c18ed6a026e7658ebfb46f4c3b07a7174f4af68307f`.
Native manifest digest: `aa844f6336e8042ea913a6f86abbd584d69bfa3c80040fea56376e876af95c63`.
llvmpipe about 1 FPS; no physical GPU/frame qualification. Local report and media
pending controller publication. Request feature_validation for new exact-source
hosted checks; this is not ready for review or a city/max support claim.

## Buffered world-save writes

Parent `1bd9a971` has eight inspected hosted summaries: 815 tests each, zero
failures/errors, one skip. Exact feature source and PR merge parents verified
against the review base. Downloaded published parent video equals the inspected
recording SHA-256 `1a3e2a2b636784db38031c18ed6a026e7658ebfb46f4c3b07a7174f4af68307f`.
Details: `structure-1bd9-hosted.json`; no unchanged completed-check wait.

Offline/server world temp files now buffer primitive DataOutputStream writes in
64 KiB. Try-with-resources flushes and closes output before the existing rename.
IOException still exits before rename or follows the existing move-failure path,
feeding structure rollback. File formats, caps, locks and rename policy unchanged.
File forcing and directory barriers are absent before and after this change;
power-loss durable save remains unqualified.

36 selected tests passed: actual offline persistence/undo, save-failure rollback
with shared fractional history, actual TLS multiplayer/restart, fractional world
and history regressions. Review helper failed and was resolved self. Only actual
local executions claimed.

Actual LocalGame.save probe: 100,000 valid edits in 40,000 cells (20,000 whole,
20,000 with four fractional edits), 3 warmups and 20 measured saves in separate
JVMs per parent/current and version-8/version-9 mode. Model identity/fingerprints,
all ordered edits and complete file hashes checked after every reload outside
timed saves. Parent LocalGame class compiled from exact `1bd9a971`; dependencies
otherwise identical. Timing includes temp close and rename, without fsync.
No concurrent native run during measurements. Warm cache, not cold/durable/system
qualification. Server timings not inferred from this offline diagnostic.

Version-8 parent/buffered median/p95/worst:
2,171.138335/2,237.950293/2,252.269726 ms versus
31.096953/35.523462/36.036487 ms. Both files 2,400,101 bytes, SHA-256
`76bc1e642310fb2a58487f28934e16137a95fedf1d7b1b1c4f673a109d3bb1dc`.
Version-9 (one 8-resolution structure definition):
2,211.362687/2,413.592056/2,459.205748 ms versus
31.333659/32.494069/37.281032 ms. Both files 2,400,166 bytes, SHA-256
`072c1aa5600d23320a0695f60cf044a7de3a5822b8162869b609e960dd82492b`.
Source: `WorldSaveProbe.java`; report: `structure-buffered-save-validation.json`.

No files deleted against review base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`.
Intended source-line replacements only buffer existing writes. MP3, Hub and Asset
Manager retained. Full city/max admission, atomic chunk publication, persistence
beyond 2,000,000 edits, power-loss durability and reference GPU/network/system
qualification remain unfinished. No caps raised; rollout gates stay closed.
Controller owns integration/publication and new exact-source checks after fresh
native workflow/media completes. This is not ready for review.

Fresh native workflow passed on all 213 unchanged production/harness source paths
and hashes. Capture/export/import, preview/all four rotations, conflict rejection,
empty-space preservation, live-player guard, cancellation, undo, repeated
placement, restart and inventory/model-editor regressions passed. Inspected fresh
conflict screenshot and decoded F10 frame. H264: 17 frames, 27.268 s, 366,969 bytes;
SHA-256 `2915e96a5b1b2037f03dc5cb17f00e33e6edffe6dcc07ab3addbf7cedbfcfb77`.
Native manifest digest: `0bffd3d9ca3e7973c97a1e1d28b099963d927d02dfd6ad5630638ebe821c1943`.
llvmpipe about 1 FPS; no physical GPU/frame qualification. Report/media pending
controller publication. Request feature_validation for new exact-source hosted
checks after integration; no full city/max or readiness claim.

## Force temp-file contents before publication

Inspected seven available parent `9b6ba716` hosted summaries: three feature
platforms and all four PR merge platforms, 815 tests each, zero failures/errors,
one skip. Exact feature source and PR merge parents verified against the review
base. Live feature run remained in progress; no missing platform pass inferred.
Details: `structure-9b6b-hosted.json`. No repeated unchanged wait requested.

Offline/server saves now write the buffered stream through an explicit
FileChannel. After serialization, out.flush() drains all buffered bytes;
channel.force(true) runs on that same writing channel. Resources close before the
existing rename. Failures before rename feed the existing rollback path. No new
fallible I/O runs after rename. Directory durability barriers are not added;
complete power-loss durable publication remains unqualified. Formats, temp paths,
locks, admission caps and rename fallback unchanged.

36 selected tests passed: actual offline persistence/undo, move-failure rollback
with a shared fractional history index, actual TLS multiplayer/restart,
fractional geometry and history regressions. Force-failure injection and
power-loss recovery were not executed. Review helper failed and was resolved
self; all reported execution was local.

Actual LocalGame.save comparison: 100,000 valid edits in 40,000 cells, three
warmups and 20 measured samples per parent/current and version-8/version-9 mode
in separate JVMs. Current timing includes explicit file force, close and rename.
Every sample reloads and verifies all ordered edits/model fingerprints and the
complete file hash outside timing. Exact parent class compiled from `9b6ba716`;
other dependencies identical. No concurrent native run during measurements.
Warm cache, not cold load or reference city/max/system qualification. Actual
server path covered by TLS tests, not this offline timing diagnostic.

Version-8 parent/forced median/p95/worst:
32.081784/33.967632/35.528827 ms versus
34.889206/39.481846/42.401668 ms. Both files 2,400,101 bytes, SHA-256
`76bc1e642310fb2a58487f28934e16137a95fedf1d7b1b1c4f673a109d3bb1dc`.
Version-9 (one 8-resolution structure definition):
33.674132/36.254955/41.932500 ms versus
35.202119/39.019061/39.140592 ms. Both files 2,400,166 bytes, SHA-256
`072c1aa5600d23320a0695f60cf044a7de3a5822b8162869b609e960dd82492b`.
Probe source: `WorldSaveProbe.java`; report: `structure-force-save-validation.json`.

Full-history traversal and force latency still run under the existing lock;
no main-thread/tick/frame budgets claimed. Full city/max admission, atomic chunk
publication, persistence beyond the 2,000,000-edit ceiling, directory/power-loss
durability and reference GPU/network/system qualification remain unfinished.
Rollout gates stay closed; no caps raised. No files deleted against review base
`4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`. Intended replacements retain buffered
writes on a forceable channel. MP3, Hub and Asset Manager retained. Controller owns
integration, publication and fresh exact-source checks after native media completes.

Fresh native workflow passed on all 213 unchanged production/harness source paths
and hashes. Capture/export/import, all four rotations, preview/conflicts,
empty-space preservation, live-player guard, cancellation, undo, repeated
placement, restart and inventory/model-editor regressions passed. Inspected fresh
rejection image and decoded F10 frame. H264: 16 frames, 26.056 s, 355,792 bytes;
SHA-256 `c264eee8c8f35bcebe6690001366cb36874c5116f4ce3919ffcf13a68141fe8c`.
Native manifest digest: `c97029896fe1e607e57726adc086ec844f6077529a385d85dc9f289bc65d0435`.
llvmpipe about 1 FPS; no physical GPU/frame qualification. Local report/media
pending controller publication. Request feature_validation for new exact-source
checks after integration; not a complete durability/city/max/readiness claim.

## Require atomic save replacement for structures

Parent `2b81b4e5` has eight inspected hosted summaries: 815 tests each, zero
failures/errors, one skip. Exact feature sources and PR merge parents verified
against the review base in `structure-2b81-hosted.json`; no repeated completed wait.

A shared package-private WorldSavePublication policy now performs save
replacement. Structure placement/undo offline and server pass requireAtomic=true:
AtomicMoveNotSupportedException propagates without a non-atomic retry. Existing
rollback handles pre-publication failure. Ordinary offline save/server persist
retain the compatibility fallback. Other IOException never retries. Buffer
flush/file force/resource close remain before rename; no new fallible I/O follows
a successful rename. Directory/power-loss durability remains unqualified.

40 selected tests passed: four policy tests, actual offline save/undo/rollback,
real TLS multiplayer/restart, fractional world and history regressions. Injected
unsupported mover in strict mode is called once with atomic/replace options,
rethrows the same exception and preserves original target/staged temp. Ordinary
mode retries only unsupported atomic moves, then publishes the complete temp.
Generic IOException in either mode is not retried and preserves both files.
Actual same-filesystem strict replacement of 131,073 synthetic bytes over an
existing file preserves every byte and removes temp. Unsupported-provider testing
uses an injected operation before file mutation, not a physical unsupported
filesystem or full-authority injection. Strict placement/undo and ordinary mode
call-site routing independently inspected; actual admitted positive workflows
exercise real file moves. Report: `structure-atomic-save-validation.json`.

No files deleted against review base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`.
Intended source-line removals consolidate duplicate move/fallback code into the
shared tested policy. MP3, Hub and Asset Manager retained. Full city/max admission,
atomic chunk publication, persistence beyond the 2,000,000-edit ceiling,
directory/power-loss durability and 20-run reference GPU/network/system
qualification remain unfinished. Rollout gates stay closed; no caps raised.
Controller owns integration/publication and fresh exact-source checks after
native workflow/media completes. Not ready for review.

Review helper failed and was resolved self. Policy, strict/ordinary call-site
routing and exception ordering independently inspected; no delegated execution
claimed and no pending delegate remains.

Fresh native workflow passed on all 214 unchanged production/harness source paths
and hashes. Capture/export/import, all four rotations, preview/conflicts,
empty-space preservation, live-player guard, cancellation, undo, repeated
placement, restart and inventory/model-editor regressions passed. Inspected fresh
rejection image and decoded F10 saved-placement frame. H264: 15 frames, 27.915 s,
379,232 bytes; SHA-256
`24569b4083af93edf703c1e2c1a1596013c7df9e03870b84cd601df57fd864cd`.
Native manifest digest: `116182b94ed6d55bd5a1aa14f3caa8cac50636e9aa87ca12adbf45c9c1d0b9fc`.
llvmpipe 0–1 FPS; no physical GPU/frame qualification. Local report/media pending
controller publication. Request feature_validation for new exact-source checks
after integration; no full city/max, durability or readiness claim.

## Buffered world-file reads

Parent `025df735` has eight inspected hosted summaries: 819 tests each, zero
failures/errors, one skip. Exact feature sources and PR merge parents verified
against review base in `structure-025d-hosted.json`; no repeated completed wait.

Offline/server world loaders now wrap primitive reads in a bounded 64 KiB input
buffer, closed by the existing DataInputStream resource. Parsers, count limits,
file formats, save publication and EOF validation unchanged. ModelDefinition's
trailing-byte check operates on its own bounded ByteArrayInputStream; outer file
buffering does not affect that check. Adds one bounded buffer per loader.

40 selected tests passed: actual offline persistence/undo/rollback, actual TLS
multiplayer/restart, fractional world, history and atomic publication policy.
Review helper failed and was resolved self. Stream ownership, parser transparency
and nested byte-array validation independently inspected; no delegated execution.

Actual LocalGame-constructor probe: known SHA-256 version-8/version-9 synthetic
files, 100,000 valid edits in 40,000 cells, one 8-resolution definition in v9.
Three warmups and 20 measured loads per parent/current and format in separate
JVMs. Every load verifies all ordered edits and model identity/fingerprints outside
timing. Each mode/format rejects the complete fixture truncated by one final byte
and an invalid version, then reloads the good fixture after each error. Input
bytes remain unchanged. Parent class compiled from exact `025df735`; dependencies
otherwise identical. No concurrent native run during measurements. Warm cache,
not cold region load, nearby usable geometry, peak memory or reference system
qualification. Server loader covered by actual TLS restart, not timed here.

Version-8 parent/buffered median/p95/worst:
1,235.332631/1,390.108253/1,433.839175 ms versus
119.011969/241.571668/286.532681 ms. File 2,400,101 bytes, SHA-256
`76bc1e642310fb2a58487f28934e16137a95fedf1d7b1b1c4f673a109d3bb1dc`.
Version-9:
1,211.579218/1,345.296766/1,430.185849 ms versus
118.726687/232.537076/295.772445 ms. File 2,400,166 bytes, SHA-256
`072c1aa5600d23320a0695f60cf044a7de3a5822b8162869b609e960dd82492b`.
Source: `WorldLoadProbe.java`; report: `structure-buffered-load-validation.json`.

Full history reconstruction remains O(history size). Full city/max admission,
atomic chunk publication, persistence beyond 2,000,000 edits, complete power-loss
durability, cold region load and 20-run reference GPU/network/system qualification
remain unfinished. Rollout gates stay closed; no caps raised. No files deleted
against review base `4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a`. Intended source-line
replacements only buffer existing file reads. MP3, Hub and Asset Manager retained.
Controller owns integration/publication and new exact-source checks after fresh
native media completes. Not ready for review.

Fresh native workflow passed on all 214 unchanged production/harness source paths
and hashes. Capture/export/import, all four rotations, preview/conflicts,
empty-space preservation, live-player guard, cancellation, undo, repeated
placement, restart and inventory/model-editor regressions passed. Inspected fresh
rejection image and decoded F10 saved-placement frame. H264: 15 frames, 25.588 s,
408,219 bytes; SHA-256
`35e799eb67e662dc62d8c43ec89543736b3a5ce71b299bd201f9dbbb73098829`.
Native manifest digest: `b873d8a4d778237eee64ba137a9c96297c2fa696d62c9a5492dbcb8a06f8f53a`.
llvmpipe about 1 FPS; no physical GPU/frame qualification. Local report/media
pending controller publication. Request feature_validation for new exact-source
checks after integration; no full city/max, cold-load, durability or readiness claim.


## Server publication staging continuation

Published parent `1c87a086cccacbfc5f8b13f4f3297b513a9af0dc`: inspected
push run 39880661480 and PR run 39880666355. All eight Maven artifacts report
819 tests, zero failures/errors and one skip each. Verified PR merge source
`d8114f8646375e2cdffae7e9217c49f526676252` has the exact feature and recorded
review-base parents. These results do not cover new edits.

Server placement and undo now build synchronization cells and compressed bytes
before the final authority lock. A short locked dependency lookup checks revision;
final locked validation checks revision, destination/chunks and live bodies again.
No publication before save succeeds. Serialization cancellation checks entry,
each definition/cell and completion. Undo accepts the server cancellation flag,
rechecks models/cells after staging and retains its record on failure. Default
offline authority behavior and wire format/limits stay unchanged. History copy,
geometry staging, save and broadcast remain locked; late-join compression unchanged.

Actual command: `mvn -q -Dmaven.repo.local="$PWD/target/structure-m2"
-DargLine="-Djava.io.tmpdir=$PWD/target/structure-audit-tmp"
-Dtest=StructurePublicationStagingTest,StructureTest,StructureMultiplayerTest,StructureValidationStampTest,StructureOccupancyTest test`.
Result: 46 tests, zero failures/errors/skips. Six new tests cover lock ownership,
concurrent destination edits during a latch-blocked stage, publication failure,
cancellation/retry, undo history retention and model/cell revalidation, wire byte
identity, cancellation boundaries and interrupt recovery. Actual three-client TLS
workflow covers 512 dependencies, rotations, observer movement, overlap, contested
placement, cancellation, undo, late join and restart. Synthetic profiles only.
Two initial test-compilation failures were corrected before the successful run;
no failed run is counted as passed. Helper failed and was resolved self; all
concurrency reasoning and tests were independently inspected and executed locally.

No deleted files against review base. Removed server cell-list/encoding lines were
moved into staging. MP3, Hub and Asset Manager remain. No caps raised. Full city/max
admission, atomic chunk publication, persistence scaling, physical-GPU and shaped
32-client qualification remain unfinished. Rollout gates stay closed.

Repeated `StructurePublicationStagingTest` after adding a model-definition wire
fixture: six tests passed again, zero failures/errors/skips. Cancellation before
the definition, both cells and final completion preserves retry bytes; decoded
definition fingerprint matches. This is a focused repeat, not six extra unique tests.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed
on inherited X11/GLFW with an isolated synthetic offline profile. Named capture,
anchor, browse, self-contained export/import, all four rotations, footprint/conflict
preview, empty-space preservation, overlap/live-player rejection, placement and
export/import cancellation, malformed import atomicity, undo, repeated placement,
32-resolution dependency/restart and inventory/model-editor regressions passed.
All 214 production/harness manifest paths and hashes match this implementation.
Inspected fresh decoded video frame and player/occupied rejection screenshots.
F10 H264 video: 16 frames, 32.204 s, 358,474 bytes, SHA-256
`9c91f8da2940df63f2ab9783fd095aaa697d2ac0989e424cff504ac018e876dd`.
Manifest SHA-256: `53ebc46f450fe8e7702971494028e1639cdbc7a7243d6eb7d5a64c3e5024fdfb`.
Renderer llvmpipe LLVM 15.0.6, 0–1 FPS: functional evidence only, no GPU/frame
qualification. Local report/media pending controller publication. Controller
feature_validation handoff needed for integration/publication and new exact-head
checks. No full city/max, tick/frame, network, durability or readiness claim.


## Bounded server tick measurement continuation

Published parent `9efc41f8e6f55f52fe1f9973d80d1ecea7d89af5`: inspected all
eight Maven artifacts from push 39883557379 and PR 39883560722. Each reports
825 tests, zero failures/errors and one skip. PR merge source
`abb29d1070c58cd40b980d497d24560d729bb6a6` has this feature and recorded
review-base parents. These results exclude the new timing instrumentation.

Before changing the slow-tick command window, measure real cadence and lock cost.
Added a 4,096-sample ring for tick start timestamps, invocation-to-lock waits
and work duration, plus lifetime tick/failure counts. Immutable snapshots copy
under only the metrics lock, then sort outside it; they never acquire the server
lock. Tick work retains the original synchronized authority scope. Failed ticks
are recorded through finally; callbacks after shutdown do not create fake ticks.
Clock arithmetic uses elapsed monotonic subtraction, including wrap. Rate is
window start intervals, not a claimed scheduler target. Percentiles use nearest
rank, including lower-middle median for even sample counts. No player/account
data is retained. Storage is three fixed 4,096-long arrays plus fixed metadata.

The 100 ms fixed-delay scheduler, city elapsed-time integration, every-second-tick
broadcast cadence, item behavior and world formats remain unchanged. This cannot
qualify 20 Hz: the fixed delay alone limits actual cadence below 10 Hz once work
is included. Full city/max, GPU, network and system budgets remain pending.

Actual local tests: `mvn -q -Dmaven.repo.local="$PWD/target/structure-m2"
-DargLine="-Djava.io.tmpdir=$PWD/target/structure-audit-tmp"
-Dtest=ServerTickTimingTest,StructureMultiplayerTest,StructurePublicationStagingTest,CityMultiplayerTest,MultiplayerTest test`: 26 tests, zero failures/errors/skips.
Timing tests cover empty/single snapshots, exact nearest-rank statistics, separate
wait/work/interval accounting, ring eviction, lifetime failure retention, immutable
old snapshots and monotonic wrap. Existing real TLS structure workflow validates
nonempty coherent timing samples alongside rotations, dependencies, cancellation,
undo, late join and restart. City and multiplayer regressions pass.

CLI: compiled `tools/structures/ServerTickProbe.java` with `javac --release 17
-cp target/classes -d target`; ran `java -Xmx1g` with target/classes and JOML jars,
fresh `target/structure-tick-probe-profile`, sanitized JSON result. One real TLS
client moves/polls for five seconds, then the fixture deliberately holds the
authority lock for 350 ms. Callback counters advance after release and measured
lock wait exceeds 100 ms. Private synthetic profile/runtime log stay in target.
Native llvmpipe playtest shared this host; no isolated performance comparison,
20-run, city/max, 32-client or reference-system claim. Helper failed and was
resolved self; implementation and measurements independently checked locally.

Actual diagnostic: normal cumulative window includes startup/TLS handshake plus
five seconds of moving/polling: 65 ticks over 6,498.892826 ms, 9.847831 starts/s;
interval median/p95/worst 100.857373/103.743055/137.621229 ms; work
0.658436/1.870528/37.441715 ms; lock wait
0.000705/0.000963/0.001477 ms. After the deliberate lock hold: 68 ticks,
worst lock wait 338.871400 ms and worst interval 439.055548 ms. No failures.
This verifies attribution on a small shared-host fixture, not the required budgets.

Repeated `ServerTickTimingTest,StructureMultiplayerTest` after strengthening the
real TLS regression: four tests pass, zero failures/errors/skips. A second thread
reads `tickTiming()` while the test holds the server authority lock; the future
completes and returns coherent counters. This checks diagnostic access does not
wait on the authority lock. Four repeated tests are not four extra unique tests.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed
on inherited X11/GLFW with isolated synthetic profile. Capture/anchor/browse,
export/import, all four rotations, footprint/conflicts, empty-space preservation,
occupied/live-player rejection, placement/export/import cancellation, malformed
import atomicity, undo, repeated copies, dependency/restart and inventory/model
editor regressions passed. All 215 production/harness paths and hashes match.
Inspected decoded footprint/placement-input video frame, fresh saved-placement
and player-rejection images. F10 H264: 16 frames, 26.54 s, 355,033 bytes, SHA-256
`b3aae5513a6c10630957cb9ac0d207092619b7a06d39184e272242b12296c68a`.
Manifest SHA-256 `fbac5bab26885aadff7e65a9f745f6e3ef1601bd0029ff990244591c9aaeeedf`.
llvmpipe LLVM 15.0.6 at 0–1 FPS is functional evidence only, not GPU/30 FPS
qualification. No files deleted against review base; original tick body moved
behind timing wrapper with the same authority lock scope. MP3 support/fixtures,
Hub workflow/tests and Asset Manager retained. Helper resolved self.

Local report/media pending controller publication. Controller feature_validation
handoff needed for integration, publication and exact-head checks. No scheduler
fix, city/max support, 20-run/32-client/GPU/system qualification or readiness claim.
Next engineering gates remain atomic chunk publication, scalable persistence and
measured admission for city/max, then scheduler work against actual reference
measurements. Existing caps and rollout gates remain unchanged.


## Deadline-based server tick continuation

Published parent `aeca8f67a4a0f4240f0fb151781f78274c8c8654`: inspected all
eight Maven artifacts from push 39886030546 and PR 39886032772. Each reports
828 tests, zero failures/errors and one skip. Verified merge source
`29e6f2d7770e543d378c5d83cc512754de96e4bf` has the exact feature and recorded
review-base parents. These checks do not cover new cadence edits.

Replace the measured <10 Hz fixed delay with 50 ms monotonic tick deadlines.
Exactly one callback reschedules after successful completion. Slow work, monitor
waits and shared-scheduler saves skip missed slots; no fixed-rate catch-up burst.
Every next callback has at least a 1 ms scheduled gap after completion, allowing
command processing but not guaranteeing monitor fairness. Exceptions retain the
prior stop-on-failed-callback behavior. Shutdown/rejected scheduling race checks
volatile running state. City elapsed-time integration and authority lock scope
remain. Separate 100 ms item and 200 ms city-broadcast deadlines preserve their
target rates and do not replay missed slots. Item .3-cell fall step stays unchanged.

Pure cadence tests cover normal work/deadline retention, skipped slow slots,
minimum gap, independent item/broadcast rates, no replay after pauses, monotonic
wrap and day-long pauses. Real structure/city/multiplayer workflows and a fresh
native run are required before handoff. Full city/max, shaped 32-client, GPU,
frame/tick/traffic/memory and persistence-scaling qualification stay pending.
No admission caps or rollout gates changed. Helper failed and was resolved self;
implementation/deadline arithmetic and lifecycle reasoning independently reviewed.

Local command: `mvn -q -Dmaven.repo.local="$PWD/target/structure-m2"
-DargLine="-Djava.io.tmpdir=$PWD/target/structure-audit-tmp"
-Dtest=ServerTickCadenceTest,ServerTickTimingTest,StructureMultiplayerTest,CityMultiplayerTest,MultiplayerTest test`.
Result: 24 tests, zero failures/errors/skips. Four new cadence tests, three timing
tests, actual TLS structure workflow, city multiplayer and 15 multiplayer tests.
Pickup exclusivity/drop handling and city behavior regressions retained.

CLI: existing compiled `ServerTickProbe`, actual TLS server/client with fresh
`target/structure-cadence-probe-profile`, result `target/structure-cadence-probe-result.json`.
`java -Xmx1g -cp target:target/classes:<JOML jars> ServerTickProbe ...`.
Normal cumulative window (startup/TLS handshake plus five seconds of movement):
133 ticks over 6,598.498500 ms, 20.004551 starts/s; interval median/p95/worst
50.000375/50.143518/51.799518 ms; work 0.015104/1.148276/26.119777 ms;
lock wait 0.000705/0.001050/0.105691 ms. After deliberate 350 ms authority-lock
hold: 137 ticks, worst lock wait 306.469201 ms and interval 356.514961 ms;
ticks resume, no failures. Maven city/multiplayer checks shared this host during
the probe; native run started afterward. Not a controlled work-time comparison
against historical baseline, reference hardware, city/max, 20-run or 32-client
qualification. Report: `structure-cadence-validation.json`.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed
on inherited X11/GLFW with isolated synthetic profile. Capture/anchor/browse,
self-contained export/import, four rotations, footprint/conflict preview, empty
space preservation, occupied/live-player rejection, placement/export/import
cancellation, malformed import atomicity, undo, repeated copies, dependency and
restart recovery, inventory/model-editor regressions passed. All 216 production
and harness source paths/hashes match. Inspected decoded staging/preview video
frame, saved placement and live-player rejection images. F10 H264: 17 frames,
32.287 s, 369,804 bytes, SHA-256
`44e081299d111a7b7325038701e9ea562251ff7b77f452854566df57ad0e93f8`.
Manifest SHA-256 `77f04a8c1730a0dceeb3749ac2ea80ab50b9b19f8c39a4598ff498d6cd537fb1`.
Renderer llvmpipe LLVM 15.0.6, 0–1 FPS; functional evidence only, no physical
GPU/30 FPS qualification. No files deleted against review base. Fixed-delay
schedule and every-second-tick counter were intentionally replaced by deadlines;
item/broadcast target rates preserved. MP3, Hub and Asset Manager retained.

Local report/media pending controller publication; feature_validation handoff
needed for integration/publication and exact-head checks. Small-fixture cadence
improvement does not qualify full city/max or system budgets. Admission caps,
scalable/atomic chunk publication, 2,000,000-edit persistence ceiling, full-history
save locks, 20-run reference GPU/network/frame/tick/memory/traffic and power-loss
qualification remain pending. Rollout gates remain closed; not ready for review.


## Selected-bucket geometry staging continuation

Published parent `518c728afb98cd4a36e04f5e877ac3bb2dd75579` hosted checks
inspected before edits. New server staging captures only affected bucket references
and chunk/terrain stamps under the authority lock, rotates the live owner token,
and builds cell replacements outside the lock. Final merge requires every stamp
to match, copies current top-level chunks and substitutes staged buckets. Unrelated
current chunks are retained. Changed/unknown stamps return no candidate and use
the existing full restage path under lock after engine revalidation.

Merge rotates staging ownership too, preserving a previously returned candidate
if the patch is edited or reused. Frozen tree wrappers protect caller input.
Terrain identity/generation, including ABA, and bucket identity/revision fence
the proof. Engine model/destination/mesh guards and final live-body guard remain.
Save still precedes swapping world/models/history and broadcasting. Stage failure,
cancellation or discarded candidates do not change live cells. This does not
remove history copying, history-cell updates, top-level map copying, save I/O or
changed-chunk fallback from the final lock. Existing caps unchanged.

Actual command: `mvn -q -Dmaven.repo.local="$PWD/target/structure-m2"
-DargLine="-Djava.io.tmpdir=$PWD/target/structure-audit-tmp"
-Dtest=WorldCellPatchTest,WorldVoxelSnapshotTest,StructureTest,StructureMultiplayerTest,StructureValidationStampTest,StructurePublicationStagingTest test`.
48 tests, zero failures/errors/skips. Six new patch tests cover no live mutation,
latest unrelated chunks, same-chunk neighbor conflicts/retry, snapshot/patch/
candidate independence after reuse and caller edits, terrain ABA/different world,
cleared buckets, invalid chunk/resolution writes, fractional detail and air undo.
Actual TLS structure rotations/dependencies/conflicts/cancellation/undo/late join/
restart and shared rollback/stamp/snapshot regressions passed. Helper failed and
was resolved self; COW ownership and commit ordering independently inspected.

Geometry CLI: `javac --release 17 -cp target/classes -d target
tools/structures/CellPatchProbe.java`; `java -Xmx1g -cp target:target/classes
CellPatchProbe target/structure-cell-patch-probe.json`. Three warmups and 20
measured iterations: 131,072 uniform cells in 32 chunks, 40,000 unrelated overrides.
Every result verifies all placed cells, source isolation and unrelated geometry
outside timing. Same source, legacy fallback first; no isolated reference-system
or full-placement claim. Native run and hosted-artifact collection started afterward.

Legacy guarded snapshot+cell-copy median/p95/worst: 7.792619/21.703015/23.446623 ms.
Selected capture: 0.044856/0.109805/0.119756 ms. Outside-lock cell staging:
8.834858/43.966731/53.549414 ms. Guarded merge:
0.231073/0.375509/0.443603 ms. Combined capture+merge:
0.274444/0.461286/0.540705 ms. This moves work outside the critical section;
extra chunk checks/staging can increase total CPU/latency. No overall placement
speedup, peak-memory, city/max, GPU or 32-client qualification claimed.

Published-parent artifact details: all eight summaries from push 39888936197 and
PR 39888938217 report 832 tests, zero failures/errors and one skip. Merge source
`af1646e6706b7c7793d53565f23005c2c6c95a8d` has exact parent feature and
review-base commits. These results do not cover pending cell-patch edits.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed
on inherited X11/GLFW, isolated synthetic profile. Capture/anchor/browse,
export/import, rotations/footprint/conflicts, empty-space preservation, occupied/
live-player rejection, placement/export/import cancellation, malformed import
atomicity, undo, repeated placement, dependency/restart recovery and inventory/
model-editor regressions passed. All 216 production/harness paths and hashes
match. Inspected fresh decoded staging/preview video frame, saved-placement and
player-rejection images. F10 H264: 17 frames, 26.807 s, 364,787 bytes, SHA-256
`67e0ce813bde582ac2a10de641b98d132d17af63d021278402bf9b096916fc1b`.
Manifest SHA-256 `9ae27642e652da0bfca2a595e5686781ca2caf513005db67e685223dbfc8bb29`.
llvmpipe LLVM 15.0.6 at 0–1 FPS is functional evidence only, not physical-GPU
or 30 FPS qualification. No files deleted against recorded base. Server per-cell
geometry copying intentionally moved into staging and retained as mismatch
fallback. MP3, Hub and Asset Manager preserved. Helper resolved self.

Local report/media pending controller publication. feature_validation handoff
needed for integration/publication and exact-head checks. No supported-city or
whole-placement performance claim. Full city/max admission, atomic/scalable world
publication, persistence beyond 2,000,000 edits, remaining history/save lock work,
20-run reference GPU/network/frame/tick/memory/traffic and complete power-loss
qualification remain pending. Rollout gates closed; not ready for review.


## Whole-cell offline application and history continuation

Published parent `fb9baa7dd6eae403e0fe9e873abda64ce11b51b3`: inspected all
eight Maven artifacts from push 39891673797 and PR 39891676965. Each reports
838 tests, zero failures/errors and one skip. Verified merge source
`f2989644ac6eec986b68fa8a2c4314540996bf91` has the exact feature and recorded
review-base parents. These checks do not cover new whole-cell/history edits.

Offline commit/rollback use owner-thread `World.replaceCell`: same canonical
edit sequence/history and final editsVersion, one geometry graft and coalesced
sky/replay/neighbor invalidation per cell. Destination-only canonicalization
matches the legacy replay tree, including redundant legacy branches, leaving
source bytes intact. Resident terrainEquivalent/canonicalMutation guards remain.
Collision geometry and EditedCell metadata remain readonly copies; cold resident
replay still uses the exact canonical edit history. Live-body checks, durable
history/preimages and save-before-success/rollback ordering remain unchanged.

Complete-cell History replacement removes old cell keys once without mutating
shared copy indexes, then inserts air prefix plus nonoverlapping leaves directly.
Uniform full-cell leaves replace the same-key prefix; final global replay order
matches repeated coverage scans. Generic Map histories retain the old per-edit
fallback. Offline/server durable history staging and renderer-facing World cell
replacement use this linear path; ordinary brush edits are unchanged.

Successful local command: `mvn -q -Dmaven.repo.local="$PWD/target/structure-m2"
-DargLine="-Djava.io.tmpdir=$PWD/target/structure-audit-tmp"
-Dtest=WorldCellReplacementTest,WorldHistoryIndexTest,ChunkCellReplacementTest,StructureTest,StructureMultiplayerTest,WorldStreamingTest,FractionalWorldTest,WorldVoxelSnapshotTest test`: 54 tests, zero failures/errors/skips.
Then strengthened binary/sky and legacy byte tests: `-Dtest=WorldCellReplacementTest,WorldHistoryIndexTest,SkyVisibilitySchedulerTest,DistantTerrainTest` with the
same Maven options: 35 tests, zero failures/errors/skips. 80 unique test cases
across these two runs; nine repeated cases. First fixture errors (using public
opaque-color set for internal LED encoding, and querying a nonresident getBlock)
were corrected before these successful runs; failed runs are not counted.

Tests compare exact history order/revisions, resident binary trees, sky edit and
geometry revisions, model/fractional metadata, cold reload, immutable snapshots,
source/copy independence, air restoration, malformed input atomicity and redundant
legacy source bytes. Indexed whole-cell history matches generic legacy scans for
random mixed edits, COW copies and a dense 4,096-leaf cell. Existing actual TLS
placement/undo/late join/restart, forced-save rollback, streaming, fractional world,
GI scheduler and distant-terrain regressions pass. Initial review findings were
independently checked against source; destination canonicalization was added.
Failed follow-up was resolved self; no delegated execution is counted.

CLI: `javac --release 17 -cp target/classes -d target
tools/structures/WorldCellProbe.java`; `java -Xmx1g -cp target:target/classes
WorldCellProbe target/structure-world-cell-probe.json`. Actual World API update
for one 4,096-leaf coloured LED cell plus unchanged neighbor, resident/unloaded
cases. Three warmups and 20 measured iterations per case, legacy first; exact
geometry/history/revisions/neighbors/resident metadata verified outside timing.
No browser applies to this headless CPU routine; native app playtest separately
required. Shared VPS, hosted-artifact retrieval overlapped; no reference-system,
GPU, memory or full-placement qualification. Native run started afterward.

Unloaded legacy/whole-cell median/p95/worst:
509.601388/663.529914/667.829352 ms versus 2.727095/7.527578/8.988965 ms.
Resident: 499.557478/646.631728/652.890204 ms versus
2.173246/3.072189/5.291470 ms. Geometry/history phase only; excludes durable
save, CPU/GPU mesh publication, frame times, city/max and 32-client budgets.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed
on inherited X11/GLFW with isolated synthetic profile. Capture/anchor/browse,
export/import, rotations/footprint/conflicts, empty-space preservation, occupied/
live-player rejection, placement/export/import cancellation, malformed import
atomicity, undo, repeated copies, dependency/restart recovery and inventory/model
editor regressions passed. All 216 production/harness paths and hashes match.
Inspected fresh decoded staging/preview video frame, saved-placement and
player-rejection images. F10 H264: 17 frames, 25.979 s, 345,446 bytes, SHA-256
`d5362b34cf4ea471eff221c1508bb4a69ce2d5a9bd78e565a370c5b1f385590d`.
Manifest SHA-256 `83231f15398b796821b56d9271889a2eb7f3eb69acb272fda6dcdc523d2318c5`.
llvmpipe LLVM 15.0.6 at 0–1 FPS remains functional evidence only, not physical
GPU/30 FPS qualification. No files deleted against recorded base. Offline
leaf-replay loops were intentionally replaced by whole-cell calls; complete-cell
history loops by equivalent bulk indexing. Ordinary edits, MP3, Hub and Asset
Manager preserved. Helper resolved self; no delegated execution counted.

Local report/media pending controller publication; feature_validation handoff
needed for integration/publication and exact-head checks. No full-placement,
supported-city or full-budget qualification claimed. Full city/max admission,
scalable/atomic publication, 2,000,000-edit ceiling, remaining history/application/
save lock work, 20-run reference GPU/network/frame/tick/memory/traffic and complete
power-loss qualification remain pending. Rollout gates closed; not ready for review.

## Legacy octree undo repair after fd866b30

The exact published fd866b3042fe8fd26b624f125666973416ad4126 passed all eight
push/PR platform builds. Inspected Maven artifacts report 844 tests, zero failures,
zero errors and one skip each; the PR merge parents include this feature head and
recorded review base 4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a. Sanitized receipt:
structure-fd86-hosted.json. These passes exclude subsequent undo edits.

A decoded legacy cell with eight identical branches reproduced undo rejection
after canonical world publication. Undo now uses allocation-free recursive voxel
value comparison, preserving immutable source layouts and later-edit rejection.
49 focused tests passed, including real offline durable placement/undo and the
existing production TLS multiplayer suite. The pre-fix reproduction failed with
one IOException; it is not counted as a pass. The diagnostic compares independently
decoded equal trees across three warmups and 20 measured runs. For 256 dense cells,
legacy/semantic p95 was 78.985082/14.110230 ms; thread allocation p95 was
109645824/0 bytes. This ran alongside native llvmpipe; it excludes authority lookup,
locks, save, networking, rendering and peak memory. Full city/max acceptance remains
unfinished; no provisional admission cap or rollout gate was weakened.

Playtest: python3 deploy/run_structure_playtest.py --display "$DISPLAY" passed with
an isolated synthetic profile and inherited display/auth. Fresh F10 H264 clip:
17 frames, 29.58 s, 421526 bytes, SHA-256
8a2681d68e711b2a4a0ef3c09c7eb1f5d0ede5ef3b2d1233039c55e9dc4cc099.
All 216 manifest paths/hashes match the tested source. Decoded video frame,
placed-world and live-player rejection screenshots inspected. llvmpipe at 0–1 FPS
is functional evidence only. Local media and sanitized reports await controller
publication and new exact-source hosted checks. No readiness claim.

## Server save-prefix staging after 8dc67be4

Verified all eight hosted parent summaries: 847 tests each, zero failures/errors,
one skip. PR merge parents include exact feature 8dc67be4a0f8ba92e0ff8bdcd0df9ecdbe00b010
and base 4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a. Receipt:
structure-8dc6-hosted.json. These passes exclude new save-staging edits.

Stage server structure header/models/history bytes outside the authority lock;
retain live transient tail, force and strict atomic rename inside final guarded
publication. History identity/revision rejects concurrent changes rather than
losing outside edits. Resource cleanup covers stage/final cancellation, failed
revalidation and file errors. Unique siblings avoid ordinary .tmp collisions.
57 unique focused tests passed, including production TLS multiplayer and actual
server save/reopen, stale-history durable-byte preservation and cancellation.

Actual authority diagnostic: three warmups, 20 runs, 131072 existing whole-cell
edits plus one replacement. Direct-commit fallback median/p95/worst:
87.402302/238.923021/267.954182 ms. Staging:
82.620994/161.387811/179.472714 ms. Guarded final publication:
3.021576/7.639779/19.790220 ms. Save bytes identical (3145788 bytes). Baseline
includes direct-path one-cell wire compression; not an isolated replay of prior
staged production code. No rendered clients/ticks/dependencies/inventories/drops
in this diagnostic. History-copy lock time remains unmeasured and guarded; tail
and force also remain guarded. No system performance claim. Native workflow is
running on frozen source; new media and hosted checks remain pending. City/max,
physical GPU, shaped network and stable rollout remain gated.

Playtest: python3 deploy/run_structure_playtest.py --display "$DISPLAY" passed on
frozen source using the inherited display/auth and isolated synthetic profile.
All 217 source paths/hashes match. Fresh F10 H264 clip: 17 frames, 31.075 s,
362141 bytes, SHA-256
b2a34d98f01a29b5b24762351eeb87f7c28e8c5073303b6f1228d0ea3bc53a61.
Decoded video frame, placed-world and live-player rejection screenshots inspected.
llvmpipe 0–1 FPS is functional evidence only. New code, media and sanitized report
structure-save-staging-validation.json await controller publication and exact-source
hosted validation. No readiness or full qualification claim.

## Bounded server history copy after a2bf03d1

All eight exact parent hosted summaries inspected: 855 tests each, zero
failures/errors, one skip. PR merge parents contain a2bf03d116bcb83dc3c8836a3f0d1c46a20c3911
and review base 4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a. Sanitized receipt:
structure-a2bf-hosted.json; these passes exclude new batch-copy changes.

Server save staging reads at most 1024 immutable edits per authority-lock hold,
rebuilding its private ordered history/index outside. Cursor checks revision
before iterator access, including completion; server checks source identity.
Final identity/revision guard remains. Offline copy retains shared-index clone.
53 focused tests passed, including production monitor/concurrent-edit latch,
stable/cancelled copies, exact order, mutation invalidation and existing actual
save/reopen, production TLS, source snapshot and placement/undo regressions.

20-run copy diagnostic, 262144 edits, three warmups: whole-clone guarded
median/p95/worst 50.540579/193.049155/207.736418 ms. Maximum guarded batch per run
0.075254/0.146869/0.157552 ms; guarded sum 8.454132/11.909500/13.481683 ms.
Total batched copy 299.527839/385.274064/398.501998 ms. Median thread allocations
23068952 bytes (clone) versus 72337480 bytes (batch). More CPU/allocation for less
guarded work, not a frame/tick/stall or peak-memory qualification claim.
No local test/native process overlapped this diagnostic. Fresh native run underway
on frozen source. Full city/max, physical GPU, shaped network and rollout gated.

Playtest: python3 deploy/run_structure_playtest.py --display "$DISPLAY" passed with
an isolated synthetic profile and inherited display/auth. All 217 source paths and
hashes match. Fresh F10 H264 clip: 15 frames, 25.413 s, 397463 bytes, SHA-256
eecfe2177af82fd295cd188984da4aeb72f02ee090f23af33442e0af840a212a.
Decoded video frame, placed-world and player-conflict images inspected. llvmpipe
0–1 FPS is functional only. Local code, media and sanitized report
structure-history-batch-validation.json await controller publication and new
exact-source hosted checks. No full city/max/system qualification or readiness.

## Reusing batch keys after 5f4ce0ac

All eight parent hosted summaries inspected: 858 tests each, zero failures/errors,
one skip. Merge parents contain 5f4ce0ac6269ba7fbaa7cf6f535e1145fae54b4b and recorded
review base 4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a. Receipt:
structure-5f4c-hosted.json. These passes exclude new copy-key changes.

Cursor captures immutable validated key/value pairs without retaining Map.Entry
objects. Worker copy reuses keys through ordinary index/revision updates; public
put validation remains intact. Cancellation checks remain before each edit.
54 focused tests passed, including snapshot independence, bad-key rejection,
fractional index order, cancellation and existing production save/TLS regressions.

Two separate sequential diagnostic JVMs, 262144 edits, three warmups and 20 runs:
rebuild versus reuse total copy median/p95/worst:
264.209773/313.733590/317.726842 versus 195.776655/279.573552/308.566410 ms.
Median thread allocations 73377864 versus 50331400 bytes. Reuse maximum guarded
batch median/p95/worst 0.092402/0.190105/0.761216 ms. Control had a
148.055859 ms worst guarded sample, cause uninstrumented. No 100 ms stall-budget
pass; whole-clone allocation baseline is still lower. No local test/native overlap
with probes. Native workflow running on frozen source. Full city/max, GPU/network,
GC/peak-memory and stable-rollout qualification remain gated.

Playtest: python3 deploy/run_structure_playtest.py --display "$DISPLAY" passed with
an isolated synthetic profile and inherited display/auth. All 217 source paths and
hashes match. Fresh H264 F10 clip: 17 frames, 26.298 s, 346316 bytes, SHA-256
5e9a9dcc2ac2c5cee61037f2aaaf0dcb62edf74e0c56dde3e0cb364279e07359.
Decoded video frame, placed-world and live-player conflict images inspected.
llvmpipe 0–1 FPS remains functional evidence only. Ten history tests repeated after
canonical fractional-material fixture correction also passed; main source unchanged.
Local code, media and structure-copy-keys-validation.json await controller publication
and exact-source hosted checks. Full city/max and system qualification unfinished.
