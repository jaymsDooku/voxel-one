## Production fractional placement storage

Hosted platform builds passed for parent b87c1e7435a5f6153ef1961d112c6b40959f6e59; publication skipped. This is not QA approval or readiness.

Production placement now calls `SparseVoxelOctree.packedCellCopy` for nonuniform 16-resolution geometry. The immutable cell wrapper and map identity remain stable, but a private packed-root marker retains the exact bounded wire image and logical counts instead of the child-node graph. Uniform geometry/model-instance sharing remains unchanged. Normal octree wrappers and ordinary nodes gain no fields. Queries reconstruct one temporary cell graph; no decoded cache is retained. `WorldVoxels.replaceCell` still takes a normal independent mutable copy before freezing its stored wrapper. This trades extra query work for lower retained placement/undo geometry, not a measured full-memory or speedup claim. Per-cell records remain at most65536bytes; all admission caps remain unchanged.

Tests cover every voxel/query/region/copy/rotation operation, exact bytes and redundant legacy branches, including all-air branch membership behavior; frozen mutation rejection, source independence, output-stream isolation, concurrent readers, full4096-leaf fragmentation and visitor failure. A production preparation test checks stable map value identity, symmetric equality/hash with a copied map, fractional geometry and uniform sharing. The initial targeted suite passed. A preliminary full-suite run was deliberately interrupted with exit130 after representation refinements; it is not counted as a pass. The final full Java suite finished with exit0:970 tests in191 current suites;969 passed,1 skipped,zero failures/errors. The skipped AssetManagerIntegrationTest.browserWorkflow requires opt-in asset.browser=true and was not executed. Playtest: fresh `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed with real Main/GLFW/X11 input and an isolated synthetic profile. Capture/import/export, all rotations, occupied/player conflicts, empty-space preservation, cancellation, malformed import atomicity, one-operation undo, repeated placement, dependency/restart and inventory/editor regressions passed. All225 source hashes match; no synthetic-profile .tmp files remain. All19 F10 frames decoded and midpoint inspected. Video34.679s,372938bytes,SHA-256 `6713ba35f349080c5598fdbd996a00581f989b8efbdd82116b46878b6fbb2fc0`. llvmpipe HUD0-1FPS is functional evidence only. Evidence awaits controller publication. The full Java result above is final.

Production region persistence and full capture/placement scaling, city/maximum admission and20-run budgets, physicalGPU,shaped32-client and all frame/tick/traffic/memory/stall/durability qualification remain pending. Stable rollout stays gated.


No tracked deletions against review base; no intended deletions. Only the reproducible local target/structure-handoff/structure-prerequisite-v1.tar archive (88002560bytes) was removed to recover space. Source, full fixtures and evidence remain.

## Production capture/export streaming save

Hosted platform builds passed for parent 1b63c6f79315d57c194a7f8928c7d5055f657961; publication skipped. No readiness or QA approval follows from these checks.

`StructurePackage.save` now sends shared gzip encoding straight into the destination-directory temporary file through a 65536-byte disk-output buffer. Capture saves and exports use this path. The full compressed byte array is no longer allocated by file save; network encoding keeps its existing byte-array API and wire format. Compressed/decoded budgets, per-model limits and all admission caps remain unchanged. Streams close before cancellation revalidation and atomic replacement. No callback runs after commit. Failure/cancellation before replacement removes the stage and preserves the prior package. This does not qualify crash durability or bound all decoded/staging memory.

All 167 current Java tests in 33 selected suites passed with zero failures, errors or skips. Two new tests use an admitted random-colour four-model asset to prove bytes reach disk before encoding completes while the old file stays intact, compare exact file/array bytes and restart, inject a partial-output callback failure, reject atomic replacement of a nonempty directory, check cleanup and retry. Existing cancellation, gzip/payload compatibility, resource-budget, multiplayer and restart checks remain passing. Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed on current production source with real Main/GLFW/X11 input and an isolated synthetic profile. Capture/export/import, all rotations, occupied/player conflicts, empty-space preservation, placement/export/import cancellation, malformed import atomicity, one-operation undo, repeated placement, model dependency/restart and inventory/editor regressions passed. All225 manifest entries match and no synthetic-profile .tmp files remain. All16 F10 frames decoded; midpoint and occupied-conflict image inspected. Video28.159s,415132bytes,SHA-256 `33ffa00371e388a96ce1ee896e4c754c4dc6a1f5a899d20531b4f0a424634bac`. llvmpipe HUD1FPS is functional evidence only. Evidence structure-stream-save.json, structure-stream-save-media.json, structure-stream-save-midpoint.png and refreshed structure-workflow.mp4 await controller publication. No tracked deletions against review base; no intended deletions.

Production region-backed capture/placement/persistence, both full fixture admission and 20-run budgets, physical GPU, shaped32-client and frame/tick/traffic/memory/stall/durability qualification remain unfinished. Stable rollout stays gated.

## Disk repair verified; production import staging

Controller disk repair restored more than 9 GiB. The previously blocked maximum-v2 diagnostic passed with `-Xmx256m`: 4000000 cells, 7967232 leaves, 16384 instances, 512 models and 8192 regions. Its 305479992-byte decoded cache was removed on close. Index 15.513335 s, full region/model revisit 13.037053 s, process elapsed 28.825887 s, peak RSS 182392 KiB. This is one read-only diagnostic run, not production admission or performance qualification. The earlier disk failure below is historical and resolved.

Production `StructurePackage.load` now reads one opened source into an owned compressed disk stage in at most 65536-byte batches. It enforces the existing 64 MiB compressed ceiling, checks truncated/growing sources, exposes progress/cancellation and feeds a shared strict gzip/body decoder without allocating the entire compressed file in memory. Model, geometry, instance, decoded-size, CRC, ordering and constructor admission gates remain unchanged. The worker deletes its stage after success, cancellation, callback failure or malformed input; process-crash cleanup is not qualified. Source-path replacement cannot redirect the opened read. Decoded assets still reside in memory and placement/persistence integration remains unfinished.

All 165 current Java tests in 33 selected suites passed with zero failures, errors or skips, including loopback multiplayer/restart and six file-import tests. Three added file-import tests exercise four malformed-footer/trailing variants, cancellation during read/decode/completion, callback failure, cleanup/retry and path replacement. Six Hub registration tests passed. Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed on current production source, real Main/GLFW/X11 input and an isolated synthetic profile. Capture, import/export, all four rotations, conflicts, empty-space preservation, cancellation, malformed import atomicity, one-operation undo, repeated placement, model dependency/restart and inventory/editor regressions passed. All 225 source manifest entries match; no import stages remain. All 16 video frames decoded with JCodec; midpoint and 270-degree preview inspected. Video: 28.388 s, 399575 bytes, SHA-256 `55efc98f9ce520f1a6017e8513e61ffe0eeb626fca142043c16c175e41a138ed`. llvmpipe HUD1FPS is functional evidence, not physical-GPU qualification. Artifacts await controller publication. No tracked deletions against review base; no intended deletions.

Next production work: connect bounded region storage to captured geometry and staged placement/save roots, preserve ordinary edits and late-join/restart consistency, and replace full-region resident work with bounded batches/nearby geometry. Do not increase admission caps until measured geometry/instance/rendering costs and complete atomic persistence/transfer are safe. Full city/maximum production admission, 20-run budgets, physical GPU, shaped 32-client and frame/tick/traffic/memory/stall/durability gates remain pending. Stable rollout stays gated.

## Read-only disk package index diagnostic — current continuation

Parent c8f029dcd574f8d3c849c4fe26f2b1f0d3feb7e0 has four successful hosted platform builds; publication was skipped. This is not readiness.

Added a private diagnostic that strictly inflates a bounded native VSTR package into an owned disk cache, then indexes model and region offsets/lengths/CRC rather than retaining all decoded cells/models. It validates one model/cell at a time. Region visits verify their CRC before callbacks; model reloads verify CRC and fingerprint. Cancellation/failure removes the partial cache. No production admission cap or importer changed. Cache ownership excludes concurrent writers; CRC is corruption detection, not an adversarial file-authentication guarantee.

Playtest: the actual CLI selftest roundtrips five cells across three regions against production encoding, preserves fractional LED colour and model identity, rejects 18 malformed/order/bounds/reference/CRC/cancellation/closed/cache cases, retries and cleans caches. Three targeted CLI tests passed in 14.658 s, including existing bounded-codec and stored-world cursor regressions. The fixed-child CLI image was captured on inherited DISPLAY and inspected. Browser/game playtesting does not apply to this read-only diagnostic; no new native game workflow run is claimed.

One city-v2 run read all 978288 cells, 968048 leaves, 10240 instances, 256 models and 2173 regions with -Xmx256m. Index 6.991678 s, subsequent full region/model read 4.867280 s, process elapsed 12.004040 s, Linux peak RSS 178536 KiB. Decoded file 41073093 bytes. This is diagnostic traversal, not production capture/import/placement performance or 20-run qualification.

Maximum-v2 attempted twice and failed with `java.io.IOException: No space left on device`. Its decoded cache requires 305479992 bytes. After removing only two reproducible v1 VSTR outputs in this assigned target directory, 169 MiB was free; original VXBF inputs and v2 packages remain. Partial caches were removed. Request bounded internal shared-disk repair outside this worktree; do not weaken budgets or alter other worktrees. Preserve at least 1 GiB free for this scoped continuation, then rerun maximum indexing. Maximum result remains pending.

Evidence: dashboard/evidence/structure-disk-index.json and structure-disk-index-cli.png, pending controller publication. No deleted tracked paths against review base; no intended deletions. Production backend/streaming/live installation, full city/maximum admission and 20-run budgets, physical GPU, shaped 32-client and all frame/tick/traffic/memory/stall/durability qualification remain unfinished. Stable rollout remains gated.

# Structure review fix checkpoint

## Stored-world cursor for private backup

Added a production CellSnapshot cursor over frozen stored overrides without consulting the flat-history index. Each cursor belongs to one reader thread and yields at most1,024 cells per batch. Explicit air survives; implicit terrain is omitted. It enforces a caller cell budget and poisons itself after budget, terrain or invalid-position failure. Detached COW geometry survives later world edits, but owner validation must reject publication from a changed source. Private owner backup now uses this cursor and validates between batches and before commit. Old history cursor retained.

Final49 production tests passed,0failures/errors/skips. Seven affected integrations passed in34.692s. Playtest:2,054 stored cells with zero flat records, fractional LED, explicit air over solid terrain and a custom model survive strict private restart. World-only stale commit rejects without changing prior generation; retry generation2 captures current glass. Models, health20, empty flat source and absent legacy file remain correct. Production tests cover2,053 cells in3 batches, negative coordinates, frozen results, COW isolation, reader threads, budgets, invalid positions, terrain failure and poisoned cursor/retry. Fresh actual child exit0 image inspected. Initial fixture setter and CLI method-name failures were corrected; failed attempts excluded. Commands/results in structure-world-override.json.

The affected path is noninteractive persistence; browser/native rendering does not apply. WorldVoxels is the only production file changed since native media, so that video is historical for this helper. Main UI/rendering unchanged. Parentcc65772f platform builds passed; publication skipped. No deleted paths against review base. Private history-position enumeration intentionally replaced; no unrelated deletions,diff check passed. Fresh CLI image/report pending controller publication.

Normal Main startup still replays local.edits.values(); normal save still caps2million records. Production region backend/streaming/live installation and both full fixtures remain unfinished. Private caps retained;1,024 values per batch is not a100ms/peak-memory guarantee. Full20-run,GPU,shaped32-client,frame/tick/traffic/stall/memory/durability qualification remains pending. Stable rollout gated.


## Generation payload range decoding

Private version2 generation loading now decodes the whole-library payload from the validated file array at offset20, removing its extra payload copy. Legacy dependency version1 keeps its old loading path. Envelope decode accepts an explicit offset/length; CRC, section boundaries and byte budgets use that range. The CRC loop advances by remaining bytes without overflowing its cursor. Caller must not mutate the source during decoding. Formats and limits unchanged.

Final seven affected integrations passed in37.856s, no skips. Playtest: envelope versions1–4 round-trip exactly at offsets1/37 with unrelated padding. Negative, huge and incorrect ranges reject. Four actual generation-file stage/commit/strict restarts retain geometry, unused models and generation1; payload bytes remain exact. Seventy-six rejection cases, retry and source preservation pass. Existing publication-fault, authority, owner and server-bundle checks pass. Fresh child exit0 image inspected. Noninteractive persistence integration; browser/native rendering does not apply.

Only private tools changed;224 production Java/harness hashes match the current native manifest. Parent41cb77d5 platform builds passed; publication skipped. No deleted paths against review base. Version2 copy intentionally replaced; legacy version1 path retained. No unrelated deletions, diff check passed. Report structure-generation-range.json and image structure-generation-range-cli.png pending controller publication.

Bounded file bytes and decoded/staging state still allocate; no peak-memory or speedup claim. Production backend/streaming/live installation and full city/maximum20-run,GPU,shaped32-client,frame/tick/traffic/peak-memory/stall/durability qualification remain pending. Existing caps and stable-rollout gates retained.


## Bounded envelope section views

Private envelope decode now gives the library and geometry parsers bounded views of the already CRC-checked envelope array. This replaces two `readNBytes` section copies and uses the new region stream API. Each view ends at its declared section length; the parent cursor advances by exactly that length. Versions 1–4, validation and limits remain unchanged. Outer envelope bytes and decoded state still allocate; no peak-memory or speedup claim.

Final six affected integrations passed in 31.351 s, no skips. Initial library/complete-restore checks and the new section check also passed. Playtest: four envelope versions round-trip exactly, totaling 14,632 fixture bytes. Used/unused models, model geometry, fractional glass and explicit air survive. Offline health 17, server health 15 and city bytes remain intact. Forty invalid section-length, inner-CRC and cancellation cases reject; source bytes and retry remain unchanged. Fresh actual child exit0 image inspected. Noninteractive persistence integration; browser/native rendering does not apply.

Only private tools changed. All 224 production Java/harness hashes still match the current native manifest. Parent 0e82863e platform builds passed; publication skipped. No deleted paths against review base. Two section-copy reads intentionally replaced; no unrelated deletions, diff check passed. Evidence structure-envelope-view.json and structure-envelope-view-cli.png pending controller publication.

Production backend/streaming/live installation and full city/maximum 20-run budgets, physical GPU, shaped 32-client, frame/tick/traffic/peak-memory/stall/durability qualification remain pending. Existing caps and stable-rollout gates retained.


## Bounded private region stream API

Added InputStream/OutputStream codec entry points. Byte-array methods delegate and retain exact version1 bytes. Decode consumes one exclusive EOF-terminated frame, checks bodyCRC/trailer, cancels between reads and limits consumption to byte cap plus one trailing probe. Caller streams remain open. Writer enforces byte budget; failure can leave a prefix, so callers must stage before publication. Blocking IO belongs on a worker. Decoded geometry remains materialized under existing caps; no peak-memory improvement claimed.

Final seven affected integrations passed with no skips. Stream CLI passed16rejectioncases and236-byte golden roundtrip,short reads,caller ownership,bounded trailing probe,actual temporary-file restart,fractional geometry/explicit air preservation,cancellation/IO failure and retry. Fresh child exit0 image inspected. This is a noninteractive codec path; browser/native rendering does not apply. Initial wrapper count mismatch and later nonexistent test name were corrected; failed attempts are excluded. Details/commands in structure-region-stream.json.

Only private tools changed;224production Java/harness hashes match the current native manifest. Published exact-head native MP4 at ab1db177 fetched and matched423245bytes,SHA256611a2ec0e0e3c096ad262db8af3d38870cf28ef68f0fd4c2ee7b41e95ea0d432. Parent platform builds succeeded;publication skipped. No deleted paths against review base. Old byte-array parsing intentionally replaced by shared stream path;no unrelated deletions,diff check passed. New CLI image/report pending controller publication.

Private8MiB/100000cell/1024region/131072leaf caps retained. Production backend/streaming/live installation and full city/maximum20-run,GPU,shaped32-client,frame/tick/traffic/peak-memory/stall/durability qualification remain pending. Stable rollout gated.


## Current-source native workflow validation

Tested production source at `b1eac551` in Main with real GLFW/X11 input and an isolated synthetic offline profile. Full native workflow passed: named capture with fractional geometry/custom models, browsing, long-path import/export, four rotations, footprint/conflict previews, empty-space preservation, occupied-cell/player rejection, undo retaining unrelated edits, placement/export/import cancellation, malformed import atomicity, repeated placement, 32-resolution/packed dependency geometry and colour retention, restart, inventory and model editor. No production source changed this turn or during the run; all 224 source hashes still match the fresh manifest.

Command: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"`. Exit 0. Linux6.1.0-50-cloud-amd64,Java25.0.3+9-LTS-195,llvmpipe LLVM15.0.6,1GiB game heap; LOW/detail32/horizon512/scale0.5,shadows/AA/dynamic OFF. HUD1FPS observed; this does not qualify physical-GPU30FPS. New F10 video:18 frames,32.936s,423245 bytes,SHA256 `611a2ec0e0e3c096ad262db8af3d38870cf28ef68f0fd4c2ee7b41e95ea0d432`. Every frame decoded with bundled JCodec; midpoint,270-degree preview and player-conflict image inspected. `ffprobe` unavailable; JCodec provided actual decoding.

Six Hub registration tests passed in0.060s. Current compiled classes plus cached SPI JARs decoded CBR stereo and VBR mono to44100Hz,6.060s each,exit0. This invocation is not a newly built shaded-JAR test despite the helper's stdout label. Exact-head CI has eight successful platform builds and two skipped publication jobs. No new Maven run needed: production source unchanged. No deleted paths against review base,no intended deletions,diff check passed. Fresh media/report `structure-current-native-validation.json` pending controller publication. Older native media is historical for these changed persistence paths.

Production region backend/streaming/live installation and both full fixtures remain unfinished. Full20-run budgets,physicalGPU,shaped32-client,peak memory/frame/tick/traffic/stall/durability qualification remain pending. Caps and stable-rollout gates retained.


## Capture iteration after map churn

World chunk map and History distinct-cell index now use linked iteration, including history copies. This removes retained sparse hash-table scans between capture entries. Per-bucket maps, wire formats and limits unchanged. Extra entry links add unmeasured memory overhead.

Final78 production regressions passed,0failures/errors/skips. Four tools passed in19.341s. Initial tool runs overlapped Maven class rebuilding and failed missing classes; not counted. Final tools ran after Maven exit0. Playtest:8192 synthetic chunks/history cells inserted then cleared;257 current glass chunks captured in3 advances, all cells checked each of20 runs, source history unchanged; partial snapshot rejects after world clear. One warmup. Actual fresh child exit0 image inspected. Noninteractive persistence helper; browser/native rendering does not apply.

Standalone diagnostic wall median/p95/worst: before1.011380/2.298334/2.386494ms; after0.863352/1.849949/2.491312ms. Max advance per run: before0.132040/0.163979/0.179130ms; after0.054572/0.064336/0.075041ms. Separate media rerun was slower: wall2.008968/2.770358/3.428056ms, max advance0.098451/0.206827/0.727128ms. Shared VPS/JIT/GC contention; no general speedup claim. Final raw rows retained; baseline summary only. Wrapper independently checks final statistics.

Parent0ba583de platform success,publication skipped. No deleted paths against review base,no intended deletions,diff check passed. Evidence structure-chunk-churn.json and structure-chunk-churn-cli.png pending controller publication. Production region backend/live installation,full city/maximum20-run budgets,physicalGPU,shaped32-client,frame/tick/traffic/peak memory/stalls and durability remain pending. Rollout gates retained.


## Incremental owner chunk-map capture

Added owner-only snapshot builder. Each advance copies at most128 chunk entries and checks world/history identity and revisions plus terrain epoch. Source bucket ownership changes before sharing begins. No detached handle escapes until completion. Private indexed backup pumps the builder with cancellation between calls; one-shot API retained.

All37 targeted production tests passed,0failures/errors/skips. Four persistence integrations passed in22.171s. Initial attempt during compilation skipped4, not counted. Extended CLI and final wrapper assertions passed separately. Playtest:257 chunks yield twice, complete third call; partial stale copy rejects; old captured stone survives live glass edit. Dense history backup/restart and stale generation/retry regressions pass. Fresh child exit0 image inspected. Noninteractive persistence path; browser/native rendering does not apply.

128 entries is a count bound, not a100ms wall-time guarantee. HashMap resizing/allocation, GC and metadata owner work remain unqualified. No production region backend/live installation or supported-size increase. Full city/maximum20-run, GPU, shaped32-client, frame/tick/traffic/peak memory and durability qualification remain pending. Rollout gates retained. Parent5ecf5e32 platform builds passed; publication skipped. No deleted paths against review base; no intended deletions. Evidence structure-incremental-capture.json and structure-incremental-capture-cli.png pending controller publication.


## Dense history admission for indexed private backup

Indexed backup no longer inherits the replay path's 200,000-record input cap. It accepts the existing offline ceiling of 2,000,000 source records without replay; the replay overload stays at 200,000. Final cells/chunks are checked against 100,000/1,024 before cloning the world chunk map. Existing geometry, leaf and envelope caps stay unchanged. The production snapshot overload accepts caller budgets; final owner validation still checks identities/revisions before metadata reads.

Final 35 production tests passed with 0 failures/errors/skips. Four affected integrations passed in 21.717 s. Initial checks passed before adding pre-clone budgets; final checks reran after that change. Playtest: 200,704 actual depth4 edits describe 49 full stone cells. Replay rejects; indexed capture and strict restart preserve all 49 cells in a 1,205-byte generation. Stale same-value revision rejects without altering generation; retry passes. Source history and absent legacy file unchanged. Oversize cell/chunk preflight and exact/zero/negative budget tests pass. Fresh actual child exit0 image inspected. This is a noninteractive persistence path; browser/native rendering does not apply.

The fixture is redundant replay history, not maximum fragmented geometry or either supplied full fixture. No 2-million-record performance claim. Parent `77e70689` has eight successful builds; publication skipped. New edits await publication/exact-head checks. No deleted paths against review base; diff check passed. Evidence `structure-dense-history.json` and `structure-dense-history-cli.png`, pending controller publication. Historical native media remains insufficient for changed persistence source.

Next: production region persistence/streaming and live owner installation. Full city/maximum admission and 20-run budgets, physical GPU, shaped 32-client, stalls/peak memory and durability remain pending. Final geometry caps and rollout gates retained.


## Twenty-run small-fixture owner capture diagnostic

Measured replay and fenced world-cell capture on the same synthetic generated-city fixture: 4,829 cells, 6,786 flat records, one warmup and 20 runs per path with alternating order. Intel Haswell/no-TSX virtual CPU, 4 vCPUs, Linux6.1.0-50-cloud-amd64, Java25.0.3+9-LTS-195, 512MiB heap. Capture median/p95/worst: replay35.38/66.42/76.83ms; cells38.15/54.04/67.75ms. Maximum owner-pump median/p95/worst per run: replay6.01/31.12/40.57ms; cells6.17/10.56/11.14ms. Median increased; p95 decreased. No general speedup claim.

Heap samples are live-JVM used heap, not true peak/allocation/RSS. Last output per path retained; old outputs discarded. Initial raw-byte comparison failed due unspecified region insertion order; normalized complete payloads and strict source-cell restarts match. Failed attempts are not passes. Three affected integrations passed in16.582s, no skips. Summary script independently checks20 raw rows/statistics; fresh actual-report image inspected. Playtest: actual synthetic LocalGame/world owner queues and private generation restart; persistence-only benchmark, browser/native rendering does not apply. Production Java unchanged; no Maven/native rerun.

Parent `be98b4b9`: controller feature-platform success; direct PR rollup7SUCCESS/1mac-intel running. New tools/evidence require publication/checks. No deleted paths against review base; diff check passed. Raw timings/host/method/limits in `structure-owner-capture-benchmark.json`; image `structure-owner-capture-benchmark.png`, pending controller publication.

This is not either supplied full fixture or reference GPU/network qualification. Live backend/owner installation, full city/maximum admission and20-run operation budgets, physical GPU, shaped32-client, true peak memory/frame/tick/traffic and durability remain pending. Caps and rollout gates unchanged.


## Private owner backup from fenced world cells

Added capture overload using current-world supplier and production cell snapshot. Owner reads game/state and world together, checks seed/terrain version, supplies position batches≤1024; worker reads frozen cells without flat replay. Existing geometry/envelope caps remain. Final encoding and owner commit revalidate game metadata/library/city plus current-world identity/revisions. Existing replay path retained. Added immutable terrain identity getters to production snapshot.

Thirty-four targeted production tests passed,0 failures/errors/skips. Four affected integrations passed in22.951s. Playtest: actual synthetic LocalGame, WorldVoxels and generated city with Ground.apply updating world/history;4829 cells captured and strict restart compared cell-by-cell. Models, survival and city/history retained. Ten stale/cancel/wrong-seed cases reject with prior generation bytes unchanged; retry generation2 captures latest state.69 owner pumps observed. Legacy world/city/history files unchanged. Fresh actual child exit0 image inspected. Noninteractive persistence path; browser/native rendering does not apply.

Caller must supply initially consistent world/history and serialize all world/terrain/game mutation on the same owner through commit. Snapshot/metadata owner cost remains unqualified. Last post-test edit clarified this contract in Javadoc only. Parent `c04b4a70` has eight successful builds; publication skipped. New edits await publication/exact-head checks. Historical native baseline221/224unchanged; old native media is not proof for these persistence changes. No deleted paths against review base; diff check passed. Evidence `structure-owner-cells.json` and `structure-owner-cells-cli.png`, pending controller publication.

Next: production region persistence/streaming and live owner installation. Full city/maximum admission and20-run budgets, physical GPU, shaped32-client, stall/memory and directory durability remain pending. No caps raised or gates weakened.


## Production owner-fenced world cell snapshot

Added WorldVoxels.CellSnapshot combining distinct-cell cursor and COW geometry. Owner-only batches/final unchanged check fence world/history identities and revisions plus terrain generation. Worker cell reads return frozen detached cells with terrain checks before/after. World revision advances on replacement, nonempty clear and patch-result merge. Requires same owner thread/authority lock and final validation immediately before publication; no automatic publication.

Thirty-seven production regressions passed,0 failures/errors/skips: snapshot5, cursor3, index10, offline staging9, cell replay3, validation stamp7. Three affected integrations passed in15.195s. Playtest: actual synthetic v9 LocalGame save/reload,2054 cells in3 owner batches, worker geometry reads, owner revalidation and CellState wire36980bytes reconstruction. Fractional geometry, air, negative coordinates/models, source-save immutability and mutation fences passed. Edge tests cover world-only edits, identity/history replacement, clear/terrain ABA, thread checks and patch isolation. Fresh actual child exit0 image inspected. Noninteractive data path; browser/native rendering does not apply.

Capture still clones the world chunk map on the owner.100ms stall/peak-memory budgets are unqualified; callers must separately fence models/survival/publication. No backend selected. Historical native baseline221/224files unchanged; LocalGame, WorldVoxels and CityHistory differ, so old native media is not exact-source proof. Parent `7a148dd4`: controller feature-platform success; PR rollup direct7SUCCESS/1mac-intel running, publication skipped. No polling. New edits await publication/exact-head checks. No deleted paths against review base; diff check passed. Evidence `structure-cell-snapshot.json` and `structure-cell-snapshot-cli.png`, pending controller publication.

Next: use fenced worker cell reads in region persistence and owner installation. Full city/maximum admission and20-run budgets, physical GPU, shaped32-client sync, stall/memory and directory durability remain pending. No caps raised or gates weakened.


## Production distinct-cell history cursor

Added History.cellCopyCursor over the existing cell index. Returns detached immutable position batches≤1024, including explicit air and one position per fractional cell. Begin/read under same owner lock; source revision changes invalidate before iteration even after exhaustion. Independent COW copy mutation does not invalidate source. No spatial/order guarantee.

Twenty-six production regressions passed,0 failures/errors/skips: new cursor3, history index10, edit history1, offline staging9, cell replay3. Three affected integrations passed in15.341s. Playtest: actual synthetic v9 LocalGame save/reload;2054 distinct cells in3 batches reconstructed through existing CellState wire36980bytes. Fractional geometry, explicit air, negative positions and model definitions retained. Same-value mutation fence, retained batch/source-save immutability and world snapshot isolation passed. Fresh child exit0 image inspected. Noninteractive data path; browser/native rendering does not apply. This small fixture is not latency/memory or large-fixture qualification.

Historical native baseline224files now has221unchanged; LocalGame, WorldVoxels and CityHistory differ. Old native media is not exact-source proof for these persistence changes. Parent `2123a2cd`: controller reports success; direct GitHub showed7SUCCESS and1mac-intel running, publication skipped. No polling. New edits await publication and exact-head checks. No deleted paths against review base; diff check passed. Evidence `structure-cell-cursor.json` and `structure-cell-cursor-cli.png`, pending controller publication.

Next: use indexed cell batches in production region persistence with owner/world snapshot fences. Region streaming/backend installation, full city/maximum admission and20-run budgets, physical GPU, shaped32-client, stall/peak-memory and directory durability qualification remain pending. No caps raised or gates weakened.


## Production normal-save integrity guard

Parent `1c67f555` has eight successful hosted builds; publication skipped. Found normal LocalGame.save could write more than2,000,000 edits although its loader rejects that count. Added preflight before directory creation/temp opening; reader, normal writer and structure admission share existing limit. No caps raised.

Nineteen targeted production regressions passed,0 failures/errors/skips: LocalGameSaveLimitTest2, StructureOfflineSaveTest6, StructureOfflineHistoryStagingTest9, CitySavesTest2. Three affected tool integrations passed in14.348s. Playtest: actual synthetic normal save versions8/9, generated city/history, oversized reported map count rejects without traversal and preserves prior world/city/history/temp bytes. Normal retry with fractional glass geometry retains models, health12, inventory3 stone and latest city frame on reload. Oversized new save creates no directory. Count fixture does not allocate2,000,001 records and is not a performance benchmark. Fresh actual child exit0 image inspected. Persistence-only operation; browser/native rendering does not apply.

Historical native baseline224files now has222unchanged; LocalGame and CityHistory differ. Old native media is not exact-source proof for these persistence changes. No deleted paths against review base; diff check passed. Evidence `structure-save-bounds.json` and `structure-save-bounds-cli.png`, pending controller publication.

Production region backend/owner integration and full city/maximum admission still unfinished. Full20-run budgets, physical GPU, shaped32-client, stall/memory and directory durability qualification remain pending. Rollout gated.


## Strict private authority startup

Added explicit expected-game/cancellation constructor for private StructureEngine authority. Loaded whole-library generation must pass terrain identity, game mode and full city/history decode before root assignment. Known-cell reconstruction checks cancellation. Opaque inspection constructor remains; no production backend selected or simulation installed.

Four affected integrations passed in21.237s, no skips. CLI passed31 total rejection cases. Playtest: actual StructureEngine placement, strict restart generation2, overlap rejection, one-operation undo and strict restart generation3 preserve geometry, survival metadata and exact city/history bytes. Wrong identity/game, legacy format, malformed history, pre/mid cancellation reject; missing save starts generation0. Rejected source bytes unchanged. Fresh child exit0 image inspected. Tools-only persistence; browser/native rendering does not apply. Production Java unchanged, no Maven/native rerun.

Parent `f6ebdccc`: controller reported platform success; direct GitHub observation showed seven successful builds and one mac-intel running, publish skipped. No polling. New edits await exact-head publication/checks. No deleted paths against review base; diff check passed. Evidence `structure-authority-restart.json` and `structure-authority-restart-cli.png`, pending controller publication.

Next: production owner/backend integration. Full city/maximum admission and20-run budgets, physical GPU, shaped32-rendered-client sync, stall/peak-memory and directory durability qualification remain pending. No caps raised or gates weakened.


## Strict private generation restart

Parent `54938283` has eight successful hosted builds; publication skipped. New store loadComplete validates expected terrain and generation identity, requires whole-library format, then strictly restores city/history, geometry, models and survival metadata. Returns detached generation/state only. Empty directories stay absent; legacy dependency-only generations require explicit migration. No live installation or disk writes.

Final4 affected integrations passed in17.393s, no skips: complete restore, generation publication, actual engine authority and whole library. Direct CLI passed24 total rejection cases. Initial capture argument compile error and nonexistent test name were corrected; failed attempts are not passes. Playtest: synthetic actual city/history generation restart, identity/game/cancellation/legacy/semantic failure rejection, exact source bytes retained and absent-save handling. Fresh child exit0 image inspected. Tools-only persistence; browser/native rendering does not apply. Production Java unchanged; no repeated Maven/native run. No deleted paths against review base; diff check passed. Evidence `structure-generation-restart.json` and `structure-generation-restart-cli.png`, pending controller publication.

Next: owner/backend integration. Full city/maximum admission and20-run budgets, physical GPU, shaped32-rendered-client synchronization, stall/peak-memory and directory durability qualification remain pending. No caps raised or gates weakened.


## Detached restore game-mode guard

Parent `1526b3ab` has eight successful hosted builds; publication skipped. Added optional expected game configuration to private complete restore. City/sandbox mismatch rejects before physical restoration, following LocalGame policy. Matching modes and missing legacy sidecars pass; detached inspection overload remains. No live owner installation.

Three affected integrations passed in17.315s, no skips. CLI passed17 total rejection cases and source preservation. Playtest: synthetic actual city/history and private generation restore, both cross-game rejects, matching modes and legacy absence. Persistence-only tools; browser/native rendering does not apply. Fresh child exit0 image inspected. Evidence `structure-restore-mode.json` and `structure-restore-mode-cli.png`, pending controller publication. No production Java changes or repeated Maven/native run; no deleted paths against review base; diff check passed.

Live owner/backend integration and full city/maximum20-run, physical-GPU, shaped32-client, stall/memory and durability qualification remain pending. No caps raised or gates weakened.


## Strict detached complete restore

Parent `02da3dbb` has eight successful hosted builds; publication skipped. Private world/library restore now optionally decodes city frames and matching history through strict production codecs before restoring geometry. Missing history remains explicit. Malformed or cancelled decoding exposes no result and leaves source bytes and private generation unchanged. No live owner state is installed.

Five affected tool integrations passed in25.765s, no skips. Direct CLI passed15 rejection cases, offline/server metadata, exact decoded frame/history, isolated restored models, retry and17 empty legacy city formats. Actual restored simulation preserves core state and advances; its constructor normalizes frame state, so full simulation record equality is not claimed. Initial stronger assertion failed and was corrected after inspecting constructor behavior.

Playtest: actual synthetic city/history, private generation restart and strict failure/cancellation paths. Tools-only persistence; browser/native game rendering does not apply. Fresh actual child exit0 image inspected. Evidence `structure-complete-restore.json` and `structure-complete-restore-cli.png`, pending controller publication. Production Java unchanged from validated parent; no repeated Maven/native run. No deleted paths against review base.

Next: coordinated live owner installation and region backend integration. Full city/maximum admission and20-run performance, physical GPU, shaped32-client synchronization, main-thread stall/peak memory and directory durability remain pending. No limits raised or rollout gates weakened.


## Strict bounded production city-history stream reader

Exact parent `fe29ac08` has eight successful hosted builds; publication skipped. Added `CityHistory.readSnapshot(InputStream,String,GameConfig,double,int)` by extracting existing v1 parse/CRC/count/clock validation. Strict malformed streams throw and expose no partial history; input is not closed and no files are opened. Encoded consumption is capped at caller0..64MiB plus one over-limit probe; zero-progress bulk reads reject. Existing file loader delegates while retaining its fresh-history fallback and sanitized note. No live world is mutated.

Final14 targeted Maven production regressions passed with0 failures/errors/skips; final5 affected tool integrations passed, no skips. CLI passed34 rejection cases,239147-byte populated production roundtrip, caller ownership, exact/small limits, bad binding/CRC/truncation/trailing data, count/clock corruption with repaired CRC, throwing source, explicit zero-progress guard, fallback, source immutability and retry. After the first successful checks, inspection added zero-progress handling; final production and tool checks reran on that source. Fresh actual child exit0 own-window image inspected.

Playtest: actual synthetic CitySimulation/history save, strict stream restore and normal file fallback. Persistence-only operation; browser/rendering does not apply. Only CityHistory differs from retained native baseline; old video is not claimed exact-source proof for this reader. No deleted paths against review base; diff check passed. Evidence `structure-history-reader.json` and `structure-history-reader-cli.png`, pending controller publication.

This enables a strict coordinated restore but does not install a live region backend or restore automatically. Caller owns cancellation/timeouts and discards consumed partial input on failure. Full city/maximum admission/performance, owner-thread stall/peak memory, physical GPU, shaped32-client and directory durability gates remain pending. No caps raised or rollout gates weakened.

## Coordinated private active-city owner snapshot

Exact parent `1c370953` has eight successful hosted builds; publication skipped. Actual LocalGame owner backup now captures active city frame17 plus matching bounded history1 bytes read-only, without calling collectHistory/save. City/history use exact binding and combined4MiB cap inside unchanged16MiB private envelope. City-mode history must be initialized and not ahead of frame time; sandbox retains no-history-file policy. Snapshot token includes simulation/history identities, history revision and exact bytes. Final owner revalidation catches clock/events mutations without revision as well as frame/history replacements. No simulation state enters reusable structures.

Five affected integrations passed, no skips. CLI passed28 rejection cases, full generated city/history restoration through production loaders, snapshot purity, cancellation and retry. Production CityHistory source unchanged from its validated published receipt; no blanket Maven/native rerun. Fresh actual child exit0 own-window image inspected. Playtest: actual synthetic LocalGame/city owner queue/private commit/restart, stale frame/history/clock/flags/identity rejection and sandbox absence. Browser/rendering does not apply to tools-only persistence. No legacy save written. No deleted paths against review base; diff check passed. Evidence `structure-city-owner.json` and `structure-city-owner-cli.png`, pending controller publication.

Owner serialization/revalidation remains synchronous and unqualified for100ms stalls/peak memory. Requires exclusive owner-thread state management. No live region backend, auto restore, server owner adapter or ordinary-edit persistence selected. Directory durability and full20-run city/maximum physical-GPU/shaped32-client qualification remain pending; no caps raised and rollout gated. Next: bounded restore/owner installation before switching a backend.

## Bounded production city-history snapshot writer

Exact parent `b4735e8a` has successful hosted platform builds. Extracted `CityHistory.writeSnapshot(OutputStream,String,int)` from existing file serialization, retaining HIS1 v1 field order, city SHA-256 binding and CRC. New read-only stream API opens no files, collects no samples and leaves caller stream open; output cap0..64MiB includes checksum and applies before writes. Partial frames on failure must be discarded. Existing file save delegates with its unchanged64MiB cap/publication policy; serialization/fingerprint failures now clean temporary history files.

Final14 targeted Maven regressions passed with0 failures/errors/skips: CityHistoryTest8, CityHistoryIntegrationTest1, CityHistoryNetworkTest1, CitySavesTest2, CityFormat16RegressionTest2. Four affected tool integrations passed, no skips. Initial Maven default-cache failure and missing temp-directory14-error run are not passes; switched to existing worktree offline cache, created assigned temp dir and reran. CLI passed independent empty-v1 wire contract, populated stream/file equality239147 bytes, production loader, immutable state, exact/small bounds, throwing sink, invalid binding, failed file-save cleanup and11 rejection cases.

Playtest: actual synthetic CitySimulation/history writer/save/loader and failure/retry, with fresh actual child exit0 own-window image inspected. Persistence-only operation; browser/native rendering does not apply. Of224 native baseline source files,223 unchanged and only CityHistory.java changed. Historical native media is not claimed exact-source proof for this writer; targeted tests and fresh CLI evidence cover it. Final edit after tests was Javadoc warning about partial output only. No deleted paths against review base; diff check passed. Evidence `structure-history-snapshot.json` and `structure-history-snapshot-cli.png`, pending controller publication.

Active city coordination still pending; owner adapter continues to reject active city until frame/history snapshots can be captured and fenced together. No region backend selected. Full city/maximum admission/performance, owner-thread stalls/peak memory, physical GPU, shaped32-client and directory durability gates remain pending. No cap raised or rollout gate weakened.

## Actual LocalGame owner-queue private backup adapter

Exact parent `cb927db3` has eight successful hosted builds; publication skipped. Added a tools-only adapter over actual synthetic LocalGame and existing StructureOwnerQueue. Worker captures versioned history/model identity+revision and detached survival state on owner, streams at most1,024 edits per owner request, builds bounded region payload/stages private file on worker, and publishes directly on original owner after full revalidation. No mutation goes through the read-only queue. No running-game state or legacy source file is replaced.

CLI passed20 rejection cases, exact geometry/model/survival restoration, retry and cleanup. Stale identity/revision/ABA, inventory/health/drop/model changes, changes during capture, wrong thread/destination, competing stage, closed queue, cancellation, active city and unversioned history reject. Native hashes unchanged. Eight unaffected integration regressions passed before final adapter-only destination guard; final four plus three tests passed after it, including actual adapter check. One7-test batch stopped with exit143 after four dots, no summary, not counted. Smaller final batches passed with no skips. Fresh actual CLI child exit0 image inspected; evidence `structure-offline-owner.json` and `structure-offline-owner-cli.png`, pending controller publication.

Playtest: actual synthetic LocalGame/owner queue/private commit/restart. Browser/game rendering does not apply to tools-only adapter. All224 native hashes match retained tested source; no blanket native/Maven rerun. No deleted paths against review base; diff check passed.

Owner metadata copy/validation and rename remain synchronous and unqualified for100ms stalls/peak memory. Requires all game mutations on its owner thread. Active city rejects pending coordination; this does not select or restore a live region backend. Server ownership, ordinary-edit region persistence, directory durability and full city/maximum physical-GPU/shaped32-client qualification remain pending; no caps raised or rollout gate removed.

## Private server file-bundle source fence

Exact parent `a1bd2ead` has eight successful hosted platform builds; publication skipped. Added explicit `captureServer` entry to the common private bundle coordinator. Immutable snapshot mode chooses the correct decoder; all source monitor/revision, fixed-slot, byte/absence revalidation, cancellation, strict private generation commit and cleanup rules are shared. No source files are replaced.

All 14 affected integration tests passed in two batches of 7, no skips. Actual server CLI passed 26 rejection cases, including wrong selected codec before publication. Complete encoded payload comparisons prove geometry, full library, survival data and sidecars survive initial/final private restart. Unsaved revision, changed/removed/new files, missing source monitor, malformed sources, closed stages and competing destination publications reject. Actual local synthetic symlink case checked. Source fixture bytes restored/unchanged and private stages cleaned up.

Playtest: actual synthetic server bundle capture/stage/commit/restart and failure/retry; fresh CLI child exit0 own-window image inspected. Browser/game playtesting does not apply to tools-only migration; all224 native hashes unchanged. Hand-built server schema bytes plus actual synthetic CitySimulation.save sidecars, no account/auth files and no server started. No deleted paths against review base; diff check passed. Evidence `structure-server-bundle.json` and `structure-server-bundle-cli.png`, pending controller publication.

Requires trusted directories and all writers honoring one monitor/monotonic revision. This does not integrate live owner state or exclude arbitrary external writers. Automatic migration, ordinary-edit region persistence, semantic sidecar validation, directory durability and full city/maximum physical-GPU/shaped32-client qualification remain pending. No caps raised; rollout gated. Next: owner-state adapter and bounded region publication before full qualification.

## Private modern server base-file import

Exact parent `b1c8eb1b` has eight successful hosted platform builds; publication skipped. Added a tools-only bounded parser for server save markers `-3` through `-9`, retaining seed/generator, final geometry, full saved model definitions and aliases, synthetic player names/inventories/health and drops. Uses existing per-format production field codecs. Unversioned files reject explicitly; no authentication data is read and no server starts. Source bytes are copied, never changed; candidate admission completes before return. Optional typed sidecars remain outside reusable structures.

Limits remain conservative: 16 MiB source/envelope, 8 MiB model section, 200,000 source edits, 100,000 final touched cells, 1,024 regions and 131,072 leaves; 10,000 saved players and 100,000 drops within 4 MiB server metadata. Both full qualification fixtures still exceed production caps. No supported-city claim.

Playtest: 13 hand-built schema fixtures across seven server markers, all three stored terrain versions where present, private generation commit/restart, exact state, retry, malformed input and cancellation. Actual CLI passed 283 rejection cases; 28-byte empty modern file accepted. Fresh actual child exit 0 image inspected. Initial fixture incorrectly put placed-only ID 4096 in inventory; corrected to use inventory ID 42 while retaining placed ID 4096. A later full suite stopped with exit 143 after 37 dots, without a summary; it is not counted as passed. Final bounded batches passed 7 + 6 + 33 = 46 tests, with no skips; exact test names and summaries are in the report.

The fixture oracle uses production field readers, not a running server loader or actual server save writer. Prefix-only city bytes test framing only. Automatic server file-bundle migration, source fencing/live owner-state integration, ordinary edits, directory durability and full city/maximum physical-GPU/shaped-32-client qualification remain pending. All 224 native hashes unchanged; no blanket native/Maven rerun. Evidence: `structure-server-import.json` and `structure-server-import-cli.png`, pending controller publication.

## Windows source-encoding CI repair

Exact head `65fee8e9` failed Windows structure-tool validation in `test_server_metadata`. The Python wrapper hid child stderr. Compiling the fixture with explicit Windows-1252 locally reproduced exit 1 and `Invalid/duplicate server player`: UTF-8 omega source bytes became two characters, violating the 16-character fixture boundary. The wrapper now passes `javac -encoding UTF-8` and reports at most 4,000 characters of synthetic child stderr on failure. No fixture limits or assertions were weakened. All 12 affected integration tests passed with `JAVA_TOOL_OPTIONS=-Dfile.encoding=windows-1252`; no skips.

Playtest: fresh actual synthetic server-metadata CLI child exited 0; its own-window image was captured and inspected. This CLI-only wrapper fix needs no browser/game rerun; all 224 native source hashes remain unchanged. See `structure-server-encoding.json` and `structure-server-encoding-cli.png`. Hosted Windows qualification must be rerun on the fixed source; no Windows pass claimed locally. Full production integration and city/maximum physical-GPU/32-client gates remain pending.

## Private server survival metadata

Exact parent `999c7987`: eight hosted builds passed; publication skipped. Added tools-only server survival metadata and private inner envelope format 4. Synthetic identifiers, inventories, health and drops survive private restart and later geometry saves. Offline/server mode changes and metadata omission reject; stale staged roots reject. Limits: 10,000 saved players, 100,000 drops, 4 MiB metadata, unchanged 16 MiB envelope. No authentication data was read and no multiplayer server started.

Final broad suite: 45 tests passed in 54.912 s, no skips. Actual CLI passed 34 rejection cases; maximum synthetic metadata was 2,610,020 bytes. Existing format, offline import, bundle/source fencing, dependency and generation regressions passed. Playtest: actual child CLI exit 0 and fresh own-window image inspected. Browser/game playtesting does not apply to tools-only metadata. All 224 native hashes unchanged; no blanket game reruns. No deleted paths against review base; diff whitespace check passed.

Evidence: `structure-server-metadata.json` and `structure-server-metadata-cli.png`, pending controller publication. Server importer/live ownership, ordinary-edit persistence, durable directory publication and full city/maximum physical-GPU/32-client benchmarks remain pending. Manual tail schema parity is not a real server save. Prefix-only city bytes test transport, not semantic city validity. Keep all rollout gates; not ready. Next: bounded server base-file migration and actual ownership integration before full qualification.

## Private offline file-bundle capture and source fencing

Exact parent `01dd6302` has eight successful platform builds; publication skipped.
All 224 native source hashes remain unchanged; no unchanged game/Maven reruns.

Added bounded capture of only the base file and its fixed city/history siblings.
The caller must hold a shared source monitor and supply a monotonic revision
before reading. Regular files use NOFOLLOW, same-opened-handle size, reads at
most 64 KiB and cancellation checks. Base limit is 16 MiB; combined sidecars
4 MiB. Orphan/empty history reject. Capture records bytes and absence, then
rechecks both plus revision. Parsing/private staging can run outside the monitor;
publication requires the same monitor, unchanged revision and exact current
source bytes/absence before existing private generation commit. No legacy
source files are replaced. All writers must honor this caller contract.

Eleven initial integration tests and 44 broad tool tests passed. Inspection found
an empty-history file could be treated as absent; added rejection without changing
the encoded absent-history case. All 11 affected integration tests passed after
that fix. Final actual CLI: restart and 25 rejection cases passed, source files
unchanged, local symlink case actually checked. Platforms without synthetic
symlink creation report that subcase false rather than invent a pass.

Base-only and actual generated base/city/history bundles preserve state after
private restart. Missing owner monitor, changed unsaved revision, changed/removed
source files and newly present sidecars reject before publication. Revision
changes during capture also reject. Competing destination stages remain fenced;
closed-stage reuse rejects. Orphan/empty history, directory, sparse oversize and
symlink sources reject. Pre/mid-cancel and interruption reject, cleanup succeeds
and retry commits. Test-injected source mutations are restored; migration itself
does not modify source bytes and leaves no private stages behind.

Playtest: actual synthetic file-bundle CLI from capture to stage/commit/restart.
Expected preservation and source/destination fences; observed passed. Browser/
game playtesting does not apply to tools-only migration. Fresh assigned-display
child exit0/result captured window-only and inspected. Image:
`structure-offline-bundle-cli.png`; report: `structure-offline-bundle.json`.

Not production game/server integration, arbitrary external-writer exclusion or
coordinated legacy-file replacement. Source parent directories/caller ownership
are trusted. Full-source copying/revalidation remains synchronous and unqualified
for 100 ms game-thread budgets. Unsaved changes require owner revision updates.
Sidecar semantic validation, remaining server state, ordinary-edit persistence,
restart undo, directory durability and full city/maximum physical-GPU/network
qualification remain pending. No ready/stable claim or production cap changes.

## Private world sidecar preservation

Exact parent `ad46906f` has eight successful platform builds; publication skipped.
All 224 native source hashes remain unchanged; no unchanged native/Maven reruns.

Added typed immutable city and optional city-history byte preservation in private
world/library envelope v3, inside the existing private generation frame. Combined
content is at most 4 MiB, below existing CityHistory's 64 MiB maximum; larger
valid files reject rather than truncate. Whole payload still must fit 16 MiB.
Only fixed city/history slots exist; no arbitrary paths or names. These bytes
never enter reusable structure packages. Offline metadata is required with them.

Known city headers and history framing/version, CRC and SHA-256 binding to exact
city bytes are checked. Bodies are preserved verbatim, not semantically validated
by this envelope. Source/getter arrays copy. Root updates invalidate staged work;
later geometry saves retain sidecars and a store stage cannot omit saved sidecars.
Private base importer has an explicit typed sidecar overload. No production save
format, city simulation behavior or admission cap changed.

Ten affected integration tests and all 43 final tool tests passed, zero failures,
errors or skips. Actual CitySimulation generated 39,949 city bytes and 239,147
history bytes. Both survive import, private restart and later geometry save.
Normal city loading returns the original frame; history loading retains nonempty
catalog, identity, revision and latest time. Source files stay unchanged.
CLI passed 21 rejection cases for binding/CRC, omissions, stale stages, bounds,
malformed envelopes, required metadata, cancellation and interruption. Exactly
4 MiB prefix-only synthetic bytes test transport capacity only; they are not a
semantic CityFrame fixture. The first checker used a nonexistent history note
getter; changed to real public state APIs before final checks.

Playtest: actual generated city/history CLI through private import/store and
normal loaders. Expected byte/loader parity and safe failure; observed passed.
Browser/game playtesting does not apply to tools-only storage code. Fresh assigned
display child exit0/result captured window-only and inspected. Image:
`structure-sidecar-preservation-cli.png`; report:
`structure-sidecar-preservation.json`. No real user/account/city files read.

Not automatic production migration, arbitrary city-body validation or coordinated
legacy multi-file restoration. Live simulation ownership, server-player state,
ordinary-edit persistence, restart undo and responsive production integration
remain pending. Synthetic updates stay dirty until later publication. Full
city/maximum, physical GPU/shaped-network 32-client performance and storage
durability remain gated. No ready/stable claim.

## Private importer covers offline versions 3 through 9

Exact parent `38e87716` has eight successful platform builds; publication skipped.
All 224 native source hashes remain unchanged; no unchanged game/Maven checks rerun.

Expanded the bounded detached importer using the exact LocalGame reader branches:
version 3 has no model table; v4..8 use the legacy model library and v9 the full
world codec. Generator version is explicit from v7, otherwise legacy terrain1.
Edits use readLegacy for v3/4, readV7 for v5 and read for v6 onward. Old default
LED color and pre-v8 inventory/drop item restrictions are retained. Minimum v3
size is 93 bytes. Private byte/count/cell/region/leaf budgets remain unchanged.
The old v8/v9 unsupported-version test now uses version2, which remains invalid.

Nine affected integration tests and all 42 final tool tests passed, with zero
failures/errors/skips. Seven hand-built old-format fixtures use existing codecs
and correct edit prefixes; the actual LocalGame loader is the oracle. Formats
3..6 cover implicit terrain1, v7 covers terrain1/2/3. Six existing v8/v9 fixtures
also pass: 13 fixtures across all seven formats. Full/fractional/model geometry,
explicit air, seeded terrain, implicit/explicit LED colors, every inventory byte,
health, drops and exact model IDs/authors/unused aliases match. Private restart
retains metadata and unused models; legacy source bytes remain unchanged.

Old-format CLI passed 77 rejection cases, including modern item190 rejected from
legacy inventories and drops, unsupported versions0/2/10, truncation/trailing
bytes, cancellation and retry. A minimal v3 default-only world also passes.
No real user/account files were read. Source fixtures are synthetic and removed.

Playtest: actual LocalGame loader through the private importer and generation
store. Expected version-specific parity and safe rejection; observed passed.
Browser/game playtesting does not apply to tools-only code. Fresh assigned-display
window showed actual child exit0/result and was captured window-only and inspected.
Image: `structure-offline-legacy-import-cli.png`; report:
`structure-offline-legacy-import.json`.

Automatic production migration, city-sidecar/server-player handling, ordinary-edit
persistence, restart undo and responsive integration remain pending. Full
city/maximum, physical GPU/shaped-network 32-client budgets and storage durability
remain gated. No ready/stable claim and no production admission cap changes.

## Bounded offline v8/v9 base-file migration

Exact parent `db970e8d` has eight successful platform builds; publication skipped.
All 224 native source hashes still match; no unchanged native/Maven checks rerun.

Added a detached byte-array parser for v8/v9 base files. Input is at most 16 MiB,
model bytes at most 8 MiB, source edits at most 200,000, final cells at most
100,000 and regions at most 1,024. Existing final-leaf/envelope/metadata budgets
also apply before a candidate is returned. Edits stream into private replay;
there is no second complete Edit list. Existing production readers validate
models, Inventory, edits and drops. Counts, terrain, health, model dependencies,
exact EOF and cancellation reject malformed input without a returned result.
No source file or live world is mutated. No production format/cap changed.

Eight affected integration tests and all 41 final tool tests passed. Actual CLI:
six synthetic fixtures, v8/v9 times terrain 1/2/3, and 91 rejection cases passed.
LocalGame.save wrote base fixtures; terrain-version headers were changed for the
three cohorts, then the actual LocalGame loader supplied the replay oracle.
Full/fractional/model geometry, explicit air over seeded terrain, untouched
terrain, exact model IDs/authors/unused aliases and survival fields match.
Private generation publication/restart retains metadata and unused assets.
Original bytes remain unchanged. Cancel/interruption rejects and retry matches.

Initial fixture compilation used a wrong Blocks.piece signature; eight tests
could not compile. Subsequent inconsistent piece type/depth was correctly
rejected by LocalGame. Corrected both fields before final passing checks. The
wrapper now reports bounded compiler stderr instead of repeated argument lists.

Playtest: actual LocalGame save/load CLI through the importer and private store.
Expected existing loader parity and safe failures; observed passed. Browser/game
playtesting does not apply to tools-only migration. Fresh assigned-display CLI
window showed child exit 0 and results; captured window-only image inspected.
Image: `structure-offline-import-cli.png`; report: `structure-offline-import.json`.

This is not automatic or complete world migration. Versions 3..7, city sidecars,
server-player state, ordinary-edit persistence and responsive game integration
remain pending. No real user/account files were read. Count/byte admission is
not peak-memory qualification. Full city/maximum, physical GPU, shaped-network
32-client performance and storage durability remain gated. No ready/stable claim.

## Private offline survival metadata

Exact parent `ea0bfa2f` has eight successful platform builds; publication skipped.
All 224 production/native harness hashes remain unchanged. No unchanged native
or Maven tests were rerun.

Added bounded private offline metadata: existing 36-slot Inventory, health 0..20
and at most 100,000 ItemDrop records in a 2 MiB CRC envelope. Inventory getters
copy, lists are immutable, IDs are unique and model dependencies must exist.
Existing read validation is reused; capture also rejects byte-truncating inputs.
The maximum synthetic drop count round-trips in 1,800,089 bytes. No performance
or complete-world capacity claim follows from this codec boundary check.

Optional metadata is attached to world/library envelope v2 within private
outer generation v2. Old files keep absent metadata rather than invented state.
The authority includes metadata in its root, validates updates and retains it
on later publication. Source-root identity fences a staged save after a newer
metadata update. A store stage cannot omit metadata already on disk. The same
16 MiB overall payload limit, strict file publication and rollback rules remain.
These fields never enter structure packages. No production save format changed.

Seven initial integration tests passed; all 40 final structure-tool tests passed
with zero failures, errors or skips. Actual CLI: 107-byte sample metadata,
100,000-drop boundary, private-generation restart and 34 rejection cases passed.
Inventory/source isolation, health/drop field preservation, malformed fields,
missing models, CRC/count/length/version, cancellation and interruption pass.
Invalid metadata cannot enter a root without its model. Metadata omission and
stale publication reject; retry retains the current state. A final JavaDoc-only
change describes optional metadata; functional source is unchanged since tests.

Playtest: actual Java metadata and private-generation CLI. Expected exact field
and model preservation across restart and safe rejection; observed passed.
Browser/game playtesting does not apply to tools-only code. Fresh assigned-display
window showed actual child exit 0 and results; window-only image inspected.
Image: `structure-offline-metadata-cli.png`; report: `structure-offline-metadata.json`.
Viewer cleanup is separate from child test success.

LocalGame/server integration, complete old-world migration, city-sidecar/server
player state, ordinary-edit persistence, restart undo and responsive publication
remain pending. Updates are dirty until later private publication. No actual
account data was read. Full city/maximum admission, physical-GPU/shaped-network
32-client budgets, directory fsync/power loss and stable rollout remain gated.

## Whole-library private generation publication

Exact parent `5924e149` has eight successful platform build entries; publication
skipped. Production/harness source still matches all 224 native hashes.

The private generation store now reads v1 dependency manifests and v2 whole-library
envelopes. Loaded records require exactly one typed payload. Authority staging
writes v2 using the existing strict force/atomic-replace, source-root and saved
generation fences. Restart retains exact IDs, authors, alias definitions and
unplaced assets. The v1 load path keeps original dependency-remapping semantics;
the next authority save upgrades to v2. A v1 stage cannot downgrade an existing
v2 generation. Assets already omitted by an older v1 file cannot be recovered.

39 broad structure-tool tests passed before the final downgrade guard/result
assertions; all six affected integration tests passed after those changes.
Actual CLI: 13 placements, seven undos, 37 rejection cases, all four rotations.
Five library entries survive placement, undo and restart, including unplaced
custom aliases and the component now unused after undo. An actual v1 file loads,
upgrades and preserves its remapped component after restart and later undo.
Downgrade rejection leaves file bytes unchanged and no private stage behind.
Existing force/move/cancel, stale-root/disk and post-rename recovery checks pass
through v2. Older v1 store/codec regression checks pass too.

Playtest: actual production StructureEngine API through synthetic private roots
and files. CLI expected library/geometry preservation and safe failure; observed
passed. Browser/game playtesting does not apply to tools-only storage code.
Fresh child command/result, exit 0 and qualification:false were captured on the
assigned display and inspected. Image: `structure-library-generation-cli.png`.
Report: `structure-library-generation.json`. Viewer cleanup is not a test failure.

LocalGame/server and production admission caps remain unchanged. Inventories and
player/transient metadata remain absent. This is not a complete world-save path;
runtime undo is not restored on restart and ordinary synthetic edits remain
dirty until later publication. Responsive complete-world migration, directory
fsync/power-loss qualification and full city/maximum physical-GPU/network budgets
remain pending. No ready/stable claim.

## Detached whole-library region codec

Parent `c5ed6f02` has eight successful platform build entries; publication
skipped. No unchanged game checks were rerun. All 224 native source hashes match.

Added `RegionWorldLibraryPrototype` as a separate bounded codec. It retains
unplaced definitions, duplicate-fingerprint aliases, exact IDs and authors via
the existing world-library codec. The restore uses original IDs, frozen cells
and copied libraries. CRC and lengths/counts reject malformed input before a
result is returned. Cancellation checks cover library I/O, region processing
and each restored cell. Existing per-model validation remains indivisible.
Private bounds are 16 MiB total, 8 MiB library and the existing region limits.

39 broad tool tests passed before the last cancellable restore change. Six
affected integration tests passed after it. Final viewer wording was compiled
and executed. Actual CLI output: four entries, two unplaced custom entries,
286 envelope bytes and 27 expected rejection cases. Source and returned-library
mutations do not affect a later restore. Empty/default library and explicit air
also pass. An initial malformed-ID test wrote the existing ID and correctly
failed its rejection assertion; the test now writes an invalid ID.

Playtest: actual detached Java CLI capture/encode/decode/restore with relevant
malformed/cancel edges and exact-ID/source-isolation regressions. Browser/game
playtesting does not apply to tools-only code. Fresh assigned-display window
showed actual child exit 0 and results. Window-only image was inspected:
`structure-library-cli.png`. Report: `structure-world-library.json`.

No production code or admission cap changed. This codec is not connected to
private generation publication, LocalGame or the server. Inventories and
player/transient metadata are still missing. No complete-world-save, disk
durability, performance or full-city qualification claim. Complete remaining
world metadata and responsive integration next; full city/maximum, physical GPU
and shaped-network/32-rendered-client gates remain pending.

## Actual engine with a private generation authority

Fresh hosted observation matched parent `2f3bca64`: eight successful platform
build entries; publication skipped. No unchanged native or Maven checks were
rerun. Production/harness source still matches all 224 native manifest hashes.

`RegionAuthorityPrototype` implements the actual StructureEngine.Authority over
isolated synthetic roots and files. A root contains frozen geometry, copied model
library, immutable final-cell index, source revision and saved generation. Staging
captures a root, builds private geometry/dependencies and forces a generation
file. Under the engine lock, apply requires exact source-root identity and a
matching disk generation. Strict file publication is followed by preallocated
root assignment. Final-cell indexing avoids a global Edit replay. Loading checks
the original seed and terrain version. Returned model libraries are copies.

38 structure-tool tests passed, then five final affected integration tests after
updating the evidence viewer. Zero failures, errors or skips. Actual production
engine API completed 11 placements, five undos and 36 expected rejection cases.
All four rotations preserve anchor alignment, fractional geometry and component
definitions across private restart loading. Overlap fails, empty source space
preserves occupied destination geometry, and undo retains unrelated later edits.
Old snapshot views stay unchanged. Altered placed cells prevent undo without
changing the current root or saved file. Initial compilation captured a changing
rotation loop variable in a lambda; corrected before executed checks.

Deterministic edits between staging and apply reject a stale root without losing
the intervening edit; retry succeeds. A separately updated disk generation cannot
be overwritten by an outdated authority. Force, pre-move and unsupported-atomic
failures publish no root/model/file/undo state, release engine busy state and
remove private stages. Cancellation after force behaves likewise. Exception and
cancellation after an actual rename recover the committed root; subsequent real
engine undo succeeds. Wrong identity, invalid edits, unknown model references,
unlocked apply and malformed final-cell inputs reject.

Playtest: actual StructureEngine prepare/commit/undo through the private CLI
authority. Browser/game playtesting does not apply to this tools-only adapter.
Fresh assigned-display window showed the actual child command, exit 0 and
results. `structure-authority-cli.png` was captured window-only and inspected.
Viewer cleanup is separate from the child test exit 0. Report:
`structure-region-authority.json`.

This is not LocalGame/server integration or a full world-save format. Inventory,
player/transient metadata and unplaced library definitions are not preserved by
the physical manifest. Synthetic direct edits are dirty until later publication;
runtime undo is not restored after restart. Owner-thread budget, complete metadata
migration, live atomic publication, late joining, directory/power-loss durability
and full city/maximum admission remain unfinished. Physical-GPU/shaped-network/
32-rendered-client/20-run qualification remains pending. Readiness and stable
rollout stay gated. New local tools/docs/report/image need controller publication
and exact-source checks.

## Private generation-file publication

Fresh PR observation matched parent `b88d73c3`: eight successful platform build
entries; publication skipped. No unchanged checks were rerun. The new work is
tools-only; all 224 native source paths still match the recorded game workflow.

The generation store stages a self-contained manifest in a private sibling file,
writes at most 64 KiB at a time, forces the file and publishes with strict atomic
replacement under an OS file lock. It checks the expected prior generation and
exact staged bytes before the publication point. Loader bounds the file before
allocation and validates CRC, metadata and nested dependencies. Its byte ceiling
is the private 16 MiB envelope plus 24 bytes of generation framing; no production
limit changed. File size is read from the opened handle, not a pre-open path
stat. A deterministic atomic replacement with a differently sized valid file
between stat and open exercises that race.

37 structure-tool tests passed, followed by four final affected tests after the
evidence-viewer compile was added. Zero failures, errors or skips. The actual
file CLI passed 23 rejection cases: stale writer across store instances, force
and pre-rename failures, unsupported atomic replacement, busy lock, changed or
corrupt staged bytes, corrupt/truncated/oversized/invalid current files,
generation exhaustion, cancellation and interruption. Initial large-fixture
setup accidentally constructed an unused empty dependency; removed before final
checks. Fresh-instance loading restores models, air and fractional LED geometry.

An injected IOException after a completed rename returns a committed receipt
when exact published bytes prove the outcome. Cancellation after the publication
point does not pretend the operation was uncommitted. Other ambiguous I/O errors
still require reloading; no blanket old-state guarantee is claimed. The normal
cohort reached generation 4. A 168,763-byte generation stopped after 65,536 bytes
before force and left no stage or published file; full retry and cancelled-read
regressions passed. Private stages remain invisible to a fresh reader.

Playtest: actual synthetic CLI, local NIO force, OS locks, atomic rename,
fresh-instance loading and injected failures. Browser/game playtesting does not
apply to this tools-only storage experiment. A fresh assigned-display window
showed the actual child command, exit 0 and result. Window-only screenshot
`structure-generation-cli.png` was captured and visually inspected. The viewer
was stopped after capture; its cleanup does not change the child test exit 0.
Report: `structure-region-generation.json`.

No live-game save/load, geometry publication, undo, multiplayer or late-join
integration has been made. Directory fsync, process-crash/power-loss guarantees,
orphan-stage recovery and owner-thread responsiveness remain unqualified. Full
city/maximum admission and 20-run physical-GPU/shaped-network/32-rendered-client
budgets remain pending. Readiness and stable rollout stay gated. New local
tools/docs/report/image need controller publication and exact-source checks.

## Private global dependency manifest

Parent `7cf035c0` has successful required platform builds per controller. Fresh
PR observation matched that head with seven successful entries and one extra
Intel Mac build still running; publication skipped. No repeated wait was made.

The tools-only region prototype now has a self-contained private dependency
envelope. It captures only referenced definitions, freezes them and deduplicates
by existing fingerprint. Sorted source IDs reference the shared definition table.
No author/account fields are captured. Diagnostic caps are 16 MiB total, 8 MiB
definition bytes, 512 unique definitions and 1,024 alias bindings. Existing
per-model resolution/size/8,192-quad caps remain enforced. Aggregate bytes are
checked after each bounded existing ModelDefinition parser; a single extra
record can parse before rejection. This is not peak-memory qualification.

Private restore copies the destination library, stages/remaps definitions and
changes model IDs only in a private restored world. No live authority is changed.
Actual CLI checks passed empty-cache restoration, a conflicting destination
ID1024, alias reuse, built-in ID8, legacy custom ID9, source-asset edit isolation,
and preservation of air, fractional LED and ordinary geometry. 512 distinct
simple 8-resolution definitions round-trip in 50,658 bytes. 1,024 alias bindings
deduplicate to one definition; 513 unique definitions and 1,025 bindings reject.
The representative mixed manifest is 9,195 bytes, four bindings/three definitions.
These are small synthetic checks, not full city/maximum performance evidence.

47 rejection cases pass, including model meshing caps, missing/extra dependency
references, empty/duplicate/unused/identity-mismatched definitions, bad binding
IDs/indices/order, malformed lengths, CRC, trailing/truncated/oversized input,
cancellation during capture/encode/decode/private restore and before final result
return, preserved interruption and retry. Golden container bytes are constructed
independently of the envelope encoder. The source destination library remains
unchanged on cancelled restore. All 36 structure-tool tests passed with zero
failures, errors or skips; focused three-test integration also passed.

Playtest: ran the detached CLI against actual ModelLibrary remapping and world
geometry. Browser/native playtesting does not apply to this tools-only change.
All 224 native source paths remain unchanged, so existing game media still
matches. Report: `structure-region-dependencies.json`.

Next: versioned save/load and atomic generation publication using bounded region
and dependency storage, then conflict-safe undo and late-join integration. Actual
production migration and full city/maximum admission remain unfinished. The
20-run physical-GPU/shaped-network/32-rendered-client budgets remain pending.
Readiness and stable rollout stay gated. Local tools/docs/report need controller
publication and exact-source hosted checks.

## Private bounded region-history codec

Parent `d809f700` has successful required platform builds per controller. Fresh
PR observation matched that head: seven successful build entries and one extra
Intel Mac build still running; publication skipped. No repeated wait was made.

The tools-only prototype now has a private uncompressed version-1 region codec.
It bounds bytes to 8 MiB, cells to 100,000, regions to 1,024 and leaves to 131,072.
It retains seed and terrain version, explicit air and whole model IDs. It checks
unique region coordinates, cell order/ownership, existing validated size-16 SVO
payloads, CRC and EOF before returning a private decoded result. Leaf totals are
checked after each cell, permitting one extra tree of at most 4,096 leaves before
rejection. This is not a measured heap bound. Encoding and decoding check
cancellation during I/O and again before returning complete results.

35 structure-tool tests passed, followed by two final affected tests after an
extra final-copy cancellation check: 37 test executions, zero failures, errors
or skips. Actual Java CLI checks passed 47 rejection cases and an independently
constructed 96-byte golden container. Semantic corruptions recompute CRC so
parser checks are exercised. Truncation, duplicate/order/coordinate/material
errors, all budget categories, cancellation, interruption, empty/exact budgets
and retry are covered. The first mid-decode cancellation fixture assumed more
than 40 callbacks; it now measures the JDK callback count before choosing its
midpoint. The codec failure and no-result checks pass on final source.

Playtest: all three terrain-version cohorts round-trip to 15,362-byte payloads,
re-encode byte-for-byte and replay every touched cell against raw and legacy
worlds. Each has 5,004 raw edits, 489 canonical records, 102 cells and 35 regions.
Browser/native playtesting does not apply to this CLI-only experiment. All 224
native source hashes remain unchanged; existing game media remains applicable.
Report: `structure-region-history-codec.json`.

Next: bounded global dependency manifest, then real versioned save/load,
atomic generation publication, conflict-safe undo and late-join integration.
The private format has no game importer. Production saves, history, networking
and admission caps remain unchanged. Full city/maximum, 20-run budgets and
physical-GPU/shaped-network/32-rendered-client qualification remain pending.
No readiness or stable-rollout claim. New local prototype/docs/report need
controller publication and exact-source hosted checks.

## Private region-history migration prototype

Fresh hosted observation matched `fb76de63`: eight successful platform builds;
publication skipped. The worktree was clean at the start. No unchanged native
or Maven checks were rerun. All 224 native source paths still match the recorded
workflow manifest.

The next architecture step is now a tools-only executable prototype. It imports
a detached canonical replay into private geometry and groups frozen final cells
by 16-cell regions. It keeps explicit air, model IDs, terrain seed and generator
version. It does not publish a partial result on cancellation or failure.
Count budgets are at most 200,000 source edits, 100,000 cells and 1,024 regions;
these are diagnostic bounds, not a measured memory/decoded-byte qualification.

Actual Java checks passed for terrain versions 1, 2 and 3. Each cohort replays
5,004 raw edits and 489 canonical records into 102 cells across 35 regions.
Every touched cell matches both raw and legacy indexed replay. Checks include
fractional LED/material edits, models, explicit air over natural terrain,
untouched seeded terrain, negative boundaries and extreme legal coordinates,
snapshot isolation after later edits, immutable maps/lists/trees, exact/overflow
budgets, invalid inputs, cancellation during final snapshot, preserved interrupt
flags, retry and empty history. Initial fixtures incorrectly used Blocks.piece
for air and y=-100 as natural terrain; corrected before final checks. Retaining
the terrain generator version was added before checking all old versions.

Playtest: compiled and exercised the CLI migration against production replay.
Browser/native playtesting does not apply to this tools-only change. All 34
structure-tool tests passed with zero errors, failures or skips. Report:
`structure-region-history-prototype.json`. Production history, saves, networking
and admission caps remain unchanged. Current game media remains applicable.

Next: bounded region codec and global dependency manifest, then real integration
with old-save migration, atomic generation publication, conflict-safe undo and
late-join transfer. Full city/maximum and physical-GPU/shaped-network/32-rendered-
client qualification remain pending. Stable rollout and independent review stay
gated. New local prototype/docs/report need controller publication and hosted
checks; this is not readiness.

## Canonical region-history cost diagnostic

The controller reports successful required platform builds for `d637c329`.
Fresh PR observation matched that exact feature head: seven successful build
entries and one additional Intel Mac PR build still running; no repeated wait
was requested. The worktree was clean before this diagnostic change.

Fixture verification now counts current canonical replay records per 16-cell
chunk using bounded primitive counters. Both complete retained inputs passed
header/dependency/geometry/overlap/CRC/EOF validation. City: 978,288 occupied
cells, 30,240 fractional leaves, 1,008,528 canonical records, 2,173 chunks,
maximum 1,528 records per chunk. Maximum: 4,000,000 occupied cells, 7,967,232
fractional leaves, 11,967,232 canonical records, 8,192 chunks, maximum 1,536
records per chunk. Counter arrays use 32,768 and 262,144 bytes. These are counts,
not peak-memory or latency measurements. The retained input hashes differ from
older receipts; the new report records their actual bytes and hashes.

Six malformed-parser tests and all 33 structure-tool tests passed. The new Java
CLI checks the count rule against actual indexed and legacy canonical history
for whole geometry, model instances, one fractional leaf and two separated
fractional leaves. All four cases pass, including repeated replacement and a
later whole-cell edit. Playtest: both fixture CLIs and the Java integration
oracle ran successfully. Browser playtesting does not apply to this CLI-only
diagnostic. Production game source is unchanged; the existing 224-path native
source manifest and published native media still match it. No blanket native or
Maven rerun was performed. Report: `structure-history-partition-cost.json`.

Next: implement and replay-test a private bounded region-history index before
migrating save/load, atomic generation publication, undo and late-join transfer.
The maximum exceeds the 2,000,000-record persistence cap; both fixtures exceed
the 256-chunk placement cap. No cap was raised. Full city/maximum admission and
physical-GPU/shaped-network/32-rendered-client qualification remain pending.
Independent review and stable-rollout gates remain. Local diagnostic/docs/report
changes need controller publication and exact-source hosted checks.

## Current uniform-material cache validation

Parent `520ccf5a02074c2ed78d743a0c6685817aa3bcb1` has eight successful platform
builds; publication skipped. Preparation's existing uniform-cell sharing now uses
primitive keys/references. It retains every admitted material, grows within the
geometry-cell budget (at most 131,072 entries / 262,144 slots per final array),
and checks cancellation during growth and long collision scans. No format or
admission change. The separate model-instance cache is unchanged.

170 final targeted tests passed across 37 suites. Five new tests cover manual
rotation/fractional/model/wire oracles, early and late palette reuse, frozen
values, cancellation, preparation isolation, 20,000 seeded keys across all bits,
capacity overflow, replacement, invalid bounds and cancelled growth/retry.
The initial fixture used model-only set for a world LED; changed to fill before
final checks. Real TLS and offline save/undo/restart regressions passed.

The rejected 1,024-entry proposal retained 76,000 after-image trees for 4,096
repeating materials. Parent/final primitive both retain 4,096. Three post-GC live
WorldVoxels + actual engine-undo estimates have medians 11,217,400 / 14,530,272 /
11,246,680 bytes for parent/rejected/final. Undo was checked after every sample.
Do not claim lower retained memory or peak-memory qualification.

Final three-warmup/20-run preparation probes use 100,000 repeated/unique/palette
cells and 10,000 mixed cells. All ordered wire hashes match. Allocation medians
fell in all four cases. Palette p95 rose 183.201 to 184.161 ms; mixed p95 rose
842.425 to 876.300 ms. An earlier 100,000-cell mixed diagnostic took parent
median 9,033.051 ms; the smaller final mixed probe does not replace qualification.
Details: `structure-uniform-cache-validation.json`. Fresh native workflow passed
with inherited display, isolated synthetic profile and llvmpipe. All 224 source
hashes match. Current H264 recording: 418,769 bytes, 18 frames, 29.808 s;
SHA-256 `7f2a4d87e298ab8bfffe4c63a958e5fad5d4da8a3d184b02592b01503032e9b2`.
Decoded clear/progress frame and fresh blocked-conflict screenshot inspected;
HUD 1 FPS is functional software evidence only. The decoded frame is saved as
`structure-uniform-workflow.png`. Local source/media need controller publication
and exact-source hosted checks.

Full city/maximum admission and reference hardware/physical GPU/shaped network/
32 rendered client qualification remain pending. No supported-city/stable rollout
claim. Source/media still need controller publication and exact-head checks.

## Current offline model-write cancellation validation

Parent `888b8b1b35af6c9c05885e462a8d3acff504be83` has eight successful platform
builds; publication skipped. Private offline save output now checks cancellation
before each delegated file write of at most 64 KiB. This includes model bytes
before history. Formats, caps and final owner publication are unchanged.

15 final affected tests passed: six save tests and nine history/integration tests.
The last case interrupts one 32-resolution model larger than 128 KiB. It stops
partway through model bytes, preserves the interrupt flag, never reaches history
progress, leaves prior save bytes intact and removes its private file.
The new eight-model checker fixture exceeds 128 KiB and cancels before its second
raw write and before history progress. Prior save bytes and live edits remain
unchanged; no private file remains. The initial test assumed an exact full-buffer
flush, but Java flushed 63,354 bytes. Its corrected assertion checks a positive
write of at most 65,536 bytes; final tests passed.

A fresh three-warmup/20-run save-only probe has identical output hashes in both
modes. Owner p95 is 43.115 ms synchronous versus 4.520 ms staged; wall p95 is
43.115 versus 43.863 ms. File force and rename still run on the owner. No hard
100 ms stall or cancellation-time bound is claimed. Fresh native workflow passed
on inherited display with an isolated synthetic profile. All 223 source hashes
match. H264 video: 425,446 bytes, 19 frames, 31.405 s; SHA-256
`c942f7ac68a0f7e3412d39caee457c0e25120271395b72970ed66f164ba20ac4`.
Decoded current frame and fresh blocked-conflict screenshot inspected; software
HUD 0-1 FPS is functional evidence only. Report:
`structure-offline-save-cancellation.json`. Local source/media await controller
publication and exact-head checks.

Next scaling investigation: a bounded per-preparation pool of frozen uniform
16-resolution cells could share repeated block/model cell objects without changing
placement-map identity. Measure actual live World plus undo retention, mixed and
all-unique fixtures, rotations and wire bytes before adopting it. Do not repeat
the rejected packed-undo approach, which increased live-world memory.

Full city/maximum admission and reference-hardware/physical-GPU/shaped-network/
32-rendered-client qualification remain pending. No supported-city or stable
rollout claim. Local edits need controller publication and exact-source checks.

## Current offline private-save staging validation

Exact parent `35325317aa2a585d3f25ef2a7cccc4b20e8f69bd` has eight successful
platform builds; publication skipped. Offline library placement now writes model
and history data on its worker into a private sibling file. Owner publication
patches the fixed inventory/health slot with current values, appends current
drops, forces the file and requires atomic replacement. Model entries and the
exact history/cell plan are checked before world mutation. Cancel/close/failure
remove private files. Legacy custom history maps and undo keep their old save.

162 targeted tests passed across 35 suites, with zero failures, errors or skips.
The final nine-test focused rerun also passed, including new full rollback after
failed atomic publication: 163 distinct tests, 171 executions total.
Version 8/9 saves match existing codec bytes; fresh survival state, cancellation
before/during write, cleanup, failed atomic replacement and mismatched prepared
models are covered. Real TLS multiplayer regressions passed as part of this run.
A 100,000-edit save-only probe ran three warmups and 20 measured saves per mode
in separate JVMs. Output hashes match. Owner p95 changed 37.802 to 2.697 ms;
wall p95 37.802 to 37.057 ms. The baseline is existing LocalGame.save(), not a
full prior placement. This does not establish a hard owner or full-frame bound.
Fresh native workflow passed on inherited display and isolated synthetic profile;
all 223 production/harness source hashes match. H264 video: 434,951 bytes,
19 frames, 30.552 s; SHA-256
`d1b15a5dddea28d6fd488c39cae328ebb49e1a3f6312131bdd2daed71c80ddb4`.
Decoded video frame and fresh clear/conflict screenshots inspected; HUD 0-1 FPS
is functional software evidence only. Report: `structure-offline-save-validation.json`.
Local source/media await controller publication and exact-head hosted checks.
No Git changes published by the developer. Full city/maximum, physical GPU, shaped-network and 32 rendered
client qualification remain unfinished; supported-city/stable-rollout gates stay.

## Current offline placement-history staging validation

Parent `2b4f9d50ad99eddb3ce11c2e45300b08c6622ba7` has eight successful platform
builds; publication skipped. Offline library placement builds indexed save
history on the worker. Main serves at most one queued owner read per frame;
each read contains at most 32 batches of 1,024 immutable key/Edit pairs (32,768
pairs). Target index construction and placement replay stay on the worker.

The private result is bound to world/source-history identity/revision and exact
immutable prepared-cell map. Source edits require retry before publication.
Model/destination/collision checks, current survival state, atomic save and undo
remain guarded by existing owner-thread commit. Nonindexed maps keep their old
synchronous path. Cancel/close and worker errors release reads/prepared resources.

157 targeted tests passed. Seven new tests cover bounded owner reads, worker
progress, durable replay/current health/inventory/restart/undo, stale history,
mutation between groups, cancellation/close, result binding, legacy maps and an
actual StructureMenu failure followed by successful retry. Initial fixture
assertions were corrected before final checks.

Three warmups and 20 runs per mode compare the prior synchronous history-only
algorithm with actual worker staging for 100,000 source edits plus 1,000 cells.
Ordered replay hashes match. Per-run maximum owner duration median/p95/worst
changed from 32.934/88.845/145.748 ms to 1.949/2.480/3.601 ms. Wall time increased
from 32.936/88.848/145.750 ms to 204.159/266.902/270.702 ms under synthetic 33 ms
owner-pump sleeps. Median allocation changed from 9,617,488 owner bytes to
806,848 owner plus 14,093,016 worker bytes. This is a responsiveness tradeoff,
not lower total allocation or a real-renderer/frame/full-commit qualification.
Report: `structure-offline-history-validation.json`. Fresh native workflow passed
on the inherited display with an isolated synthetic profile and llvmpipe. All
222 source hashes match. Fresh H264 recording is 400,053 bytes, 16 frames and
27.245 s. Decoded history-staging progress frame and fresh blocked-conflict
screenshot inspected; HUD 0-1 FPS is functional evidence only. The decoded frame
is saved as `structure-offline-history-progress.png`. Local edits/media need
controller publication and exact-source hosted checks.

Snapshot capture, validation, geometry publication, disk save and undo still have
owner-thread work. Full city/maximum admission, partitioned storage/transfer and
physical-GPU/shaped-network/32-rendered-client/20-run full-workflow qualification
remain unfinished. Supported-city and stable-rollout gates stay.

## Current immutable-history-key validation

Parent `4f80b31d14631a17ba376ad84d0b07d69487be2a` has eight successful platform
builds; publication skipped. Canonical history now reuses immutable coordinate
keys and computes the whole-cell key once during replay. Equal single-index
keys retain their existing string. Public key validation, map ordering, revision
stamps, copy-on-write isolation, formats and resource caps remain unchanged.

150 final-source targeted tests passed, including indexed history, snapshots,
canonical replay, real TLS multiplayer, model and protocol regressions. Existing
tests cover mixed edits, up to 12 independent forks, copy cursors and invalid
external keys. Source changed during refinement, so these checks were rerun.

Separate parent/current JVMs each ran three warmups and 20 history builds for
50,000 uniform or two-leaf mixed cells; serialized edit hashes match. Median
thread allocation changed from 29,697,640 to 15,697,640 bytes for uniform and
81,546,232 to 50,746,232 bytes for mixed. Uniform median/p95/worst changed from
69.911/136.872/152.520 ms to 22.177/77.927/105.220 ms; mixed from
216.514/279.648/291.513 ms to 148.425/233.366/241.328 ms. No 100 ms bound claimed.

Three post-GC samples per retained mode also keep actual WorldVoxels geometry
alive. World/history median estimates changed from 23,325,192 to 17,834,296 bytes
for uniform and 78,762,728 to 76,013,752 bytes for mixed. With an additional COW
history copy, medians changed from 29,265,808 to 23,534,064 and from 89,516,992
to 86,753,888 bytes respectively. These are estimates, not peak memory or 20-run
full-system qualification. Report: `structure-history-key-validation.json`.
Fresh native workflow passed with an isolated synthetic profile on the inherited
display and llvmpipe; all 221 source hashes match. Fresh H264 recording is
420,903 bytes, 17 frames and 26.660 s. Decoded staging/anchor frame and fresh
blocked-conflict screenshot inspected; HUD 1 FPS is functional evidence only.
Local edits/media need controller publication and exact-source hosted checks.

Persistent edit/map/geometry costs, full city/maximum admission, partitioned
storage/publication/transfer, owner-thread work and physical-GPU/shaped-network/
32-rendered-client/20-run full-workflow qualification remain unfinished.
Supported-city and stable-rollout gates stay.

## Current canonical replay validation

Parent `0b5a8cd67087d8f9ee34bc981cd67413cccd01d5` feature workflow completed all
four platform builds; publication skipped. Three PR comparison builds passed
and macOS Intel was still running at observation. No unchanged wait is needed.

Canonical cell replay now visits stored leaves directly. The existing list API
collects the same air prefix and edits; indexed whole-cell history consumes the
visitor without allocating leaf or replay lists. Formats, ordering, immutable
source/copy isolation and resource caps remain unchanged. 135 targeted tests
passed, including three new legacy-oracle/history-isolation/failure checks and
actual TLS multiplayer, model and protocol regressions.

Separate parent/current JVMs each ran three warmups and 20 canonical-history
builds for 64 cells and 131,072 fractional LED leaves. Serialized edit hashes
match. Median thread allocation fell from 76,654,352 to 67,113,744 bytes. Timing
was mixed: median/p95/worst changed from 120.918/208.163/221.251 ms to
133.338/174.608/176.591 ms. Do not claim lower median latency or a 100 ms bound.
Report: `structure-cell-replay-validation.json`. Fresh native workflow passed
with an isolated synthetic profile on inherited display/llvmpipe. All 221 source
hashes match. Fresh H264 video is 407,292 bytes, 17 frames and 27.541 s. Decoded
clear-footprint/staging frame and fresh blocked-conflict screenshot inspected;
HUD 1 FPS is functional evidence only. Local edits/media need controller
publication and exact-source hosted checks.

Persistent canonical-history edit/string/map costs and owner-thread history/save
work remain. Full city/maximum admission, partitioned storage/publication/transfer
and physical-GPU/shaped-network/32-rendered-client/20-run qualification remain
unfinished. Supported-city and stable-rollout gates stay.

## Current synchronization position-index validation

Parent `24d14a64cebf15e1dfe36ab7b3a7ce71666d329e` has eight successful platform
builds; publication skipped. The rejected undo candidate remains diagnostic-only.

Structure wire decoding now uses an exact primitive position index instead of
per-cell strings and hash nodes. Coordinate validation precedes packing; sorted
keys detect duplicates before returning a delta. Cell order and wire bytes stay
unchanged. The index payload is bounded to 16,000,000 bytes at the existing
2,000,000-cell transfer limit; decoded geometry and sorting memory are separate.

132 targeted tests passed, including real TLS multiplayer and protocol/model
regressions. Three new tests cover exact coordinate edges, wire ordering, late
duplicates, invalid inputs and a full 2,000,000-position index. The first test
attempt lacked a writable temp directory; the corrected worktree-local temp
setting passed. No full maximum-size delta decode is claimed by the index test.

Separate parent/current JVMs each completed three warmups and 20 decodes of the
same shuffled 100,000-cell delta. Median/p95/worst fell from
74.964/102.579/158.642 ms to 38.203/55.839/57.548 ms. Median thread allocation fell
from 21,045,896 to 10,948,616 bytes. Exact wire bytes, cell order and physical
geometry match. This is a codec diagnostic, not full-system qualification.
Report: `structure-wire-position-validation.json`. Fresh native workflow passed
on the inherited display with an isolated synthetic profile and llvmpipe. All
221 source hashes match. The 397,555-byte H264 recording has 16 frames over
26.497 s; decoded clear-footprint frame and fresh blocked-conflict screenshot
were inspected. HUD 1 FPS is functional evidence only. Local edits and media
await controller publication and exact-source hosted checks.

Full city/maximum admission, partitioned canonical storage/persistence/transfer,
owner-thread work and 20-run physical-GPU/shaped-network/32-rendered-client
qualification remain unfinished. Supported-city and stable-rollout gates stay.

## Current undo-sharing diagnostic

Parent `f7742adf5c1a9d5a788057fd49cb01e45ec15039` has eight successful platform
builds; publication skipped. The packed-undo candidate was rejected after checking
live-world sharing. The world copies SVO wrappers but shares immutable node
graphs with placements. Existing maps avoid a duplicate packed representation
while that world geometry remains live. Reconstructing packed expected cells
also adds guarded-undo work.

Production sources and native evidence are restored to that tested parent.
Candidate sources are preserved outside game sources under diagnostic tools;
the integrated trial is retained only in assigned build output. No shipping
undo behavior, format, cap or unrelated feature changes. All 33 tooling tests
passed, including four deterministic candidate invariant groups. Reproducible
storage-only and live-world diagnostics completed. With the world retained,
mixed medians are 35,139,288 bytes for the existing map and 38,417,160 for packing.
The candidate adds 3,277,872 bytes and reconstruction cost. Report:
`structure-undo-sharing-validation.json`. All 220 source hashes and published
native video match the unchanged game source; a video frame was decoded and
inspected. No game test rerun is claimed. New diagnostic publication/checks pending.

Next: measure canonical-region/history storage before changing undo retention.
Full city/maximum admission, partitioned persistence/publication/transfer,
owner-thread work and physical-GPU/shaped-network/32-rendered-client/20-run
qualification remain unfinished. Supported-city and stable-rollout gates stay.

## Current buffered synchronization validation

Parent `74e480d50873d886dfde7c1a5da2198e7a5c0131` has eight successful platform
builds; publication skipped. Structure wire encoding and decoding now use bounded
64 KiB buffers. Existing byte budgets stay below the buffers. Leaf counting no
longer constructs temporary lists. Wire format and all transfer caps stay intact.

132 targeted tests passed, including real TLS structure placement across four
rotations, empty dependency caches, late joining, undo and restart. New tests
compare independent legacy writer bytes across buffers and verify model IDs,
physical cells including air, CRC/size/truncation/duplicates/trailing rejection,
cancellation and decoded-limit enforcement. Small limits test the limiter;
no full 512 MiB allocation is claimed.

Three warmups and 20 codec runs compared separately compiled parent with current
source, in separate JVMs without concurrent tests/native playtesting. The
64-cell, 131,072-leaf delta is 192,676 compressed bytes; wire hashes match.
Encode median/p95/worst: 229.507/236.544/249.087 ms before,
88.774/96.959/98.141 ms now. Decode: 144.300/157.263/164.123 ms before,
22.557/29.997/38.717 ms now. Thread allocation fell from 7,919,896 to 793,952 bytes
for encode and from 13,255,528 to 5,926,304 bytes for decode. These are codec CPU
and thread-allocation measurements, not full synchronization or peak memory.

Playtest: fresh native workflow passed on inherited display with isolated
synthetic profile and llvmpipe. All 220 production/harness hashes match. Fresh
H264 video is 376,781 bytes, 16 frames and 27.442 s. Decoded staging/anchor frame
and occupied-cell conflict screenshot inspected; HUD 1 FPS is functional evidence
only. Report: `structure-wire-buffer-validation.json`. Edits, fresh media and
exact-head checks require controller publication.

Next: partitioned transfer/persistence/publication, owner-thread history/save
work and placement/undo graph retention. Two-million-cell/leaf transfer caps and
other provisional limits remain. Full city/maximum admission and 20-run physical
GPU, shaped-network and 32-rendered-client qualification remain pending. Stable
rollout and supported-city limits stay gated.

## Current count-only chunk validation

Parent `07fd590d26d974a3f3a967c05c8a2900b7a0b853` has eight successful platform
builds; publication skipped. Structure validation now counts chunk faces through
the shared render scanner. It skips full render-buffer emission and preserves
all geometry/material rules and mesh budgets. No admission caps increased.

Initial 135 targeted tests passed. After adding an engine rejection test, all
five focused tests passed: 136 distinct cases, 140 test executions. Count/render
quads match across empty/full, loaded-neighbor, water, material, model and colored
fractional cases. Render arrays stay unchanged after counting. Thirteen separated
checkerboard cells pass at 159,744 quads; fourteen reject the same vertex budget.
The engine rejects before publication, retains model state and clears its busy
lease. Both paths observe interruption.

Three warmups and 20 component runs per fixture compared separately compiled
parent render validation with counting. All three render hashes match. On two
98,304-quad fragmented chunks, p95 fell from 71.883/68.604 to 16.868/11.014 ms;
thread allocation fell from 123,572,272 to 17,832 bytes. The small fixture median
and worst increased slightly. These are CPU component results, not peak memory
or full-system qualification. See `structure-chunk-count-validation.json`.

Playtest: fresh production Main/GLFW workflow passed with isolated synthetic
profile and inherited display on llvmpipe. Capture/export/import, all rotations,
conflicts, empty space, cancellation, undo, repeated placement, dependencies,
restart, inventory and model editor passed. All 220 production/harness source
hashes match. New H264 video: 383,216 bytes, 15 frames, 24.407 s. Decoded preview
frame and placed-world screenshot inspected. HUD 1 FPS is functional evidence,
not physical-GPU qualification. Controller publication and exact-head checks
are needed for these edits and fresh media.

Next: placement/undo graph retention, partitioned persistence/publication,
owner-thread history/save work and bounded rendering. Full city/maximum admission
and 20-run physical-GPU, shaped-network and 32-rendered-client qualification
remain pending. Stable rollout and supported-city limits stay gated.

## Current decoded-size admission diagnostics

Controller reports successful required platform builds for `cf381381868047051f3241da73ad837c75864436`.
The retained native source manifest still matches all 220 production/harness
files; its published 434,292-byte video matches local bytes. No production Java
or native harness changed in this step.

The decode-only CLI now measures complete decoded bytes and dimensions for
admitted packages. Its separate gzip scan uses fixed buffers, checks completion
and enforces the existing 512 MiB ceiling. Early rejections omit decoded size.
All 32 tooling tests passed. An actual synthetic 8,192-cell CLI invocation
reported 18,619 compressed and 122,946 decoded bytes, matching independent Python
decompression. Actual city and maximum invocations still reject at dependency
surface and geometry caps. See `structure-decoded-admission-validation.json`.
This is CLI validation, not game performance qualification. Hosted checks for
these tooling edits require controller publication.

Next: address placement/undo graph retention, partitioned publication/persistence
and remaining model/rendering constraints before raising admission limits.
Placement maps currently promise stable SVO identities; lazy reconstruction
cannot replace their values without changing that API. Full city/maximum
admission, 20-run physical-GPU, shaped-network and 32-rendered-client qualification
remain pending. No rollout gate is weakened.

## Current segmented package-geometry validation

Parent `ff2b269f965eb46b23bc888a000f7ccd75c026c5` has eight successful platform
builds; publication skipped. Structure cells now store uniform values inline and
mixed geometry in bounded immutable 64 KiB segments. Trees are reconstructed only
on access; export copies original bytes directly. Cell equality/hash compare
physical values consistently across lazy reads. No wire version, material rule,
admission cap or placement/persistence limit changed.

114 structure/model tests passed. New tests cover record prefixes and payloads
crossing segment boundaries, snapshot independence after append, exact legacy
writer bytes, redundant branches, coordinate extremes, model references, frozen
values, cell-list equality and white-sentinel handling. A synthetic counter tests
the 512 MiB allocation guard; no full-size allocation is claimed.

Twenty export/visit runs compared admitted 100,000-uniform-cell and
50,000-two-leaf-cell packages with separately compiled parent sources. Package
hashes match. Single post-GC retained-heap estimates fell from 5,843,096 to
981,656 bytes and from 29,722,440 to 4,055,696 bytes. Mixed export p95 fell from
150.319 to 59.427 ms. Access costs increased: uniform visit p95 1.642/4.889 ms,
mixed 17.590/53.822 ms; uniform export p95 also increased 88.975/96.670 ms.
The report retains all median/worst/allocation results and limitations. These are
small package diagnostics, not peak-memory or full-system qualification.

Playtest: fresh production Main/GLFW workflow passed on inherited display with
synthetic profile and llvmpipe. Capture/export/import, rotations, conflicts,
empty-space preservation, cancellation, undo, repeated placement, dependencies,
restart, inventory and model-editor regressions passed. All 220 source hashes
match. Fresh H264 media decoded and inspected: 434,292 bytes, 16 frames, 28.753 s.
Placed-world screenshot inspected. Report: `structure-packed-geometry-validation.json`.
Source and media need controller publication and exact-head checks.

Next: address placement/undo graph retention, partitioned publication/persistence
and remaining model/rendering constraints before raising admission limits.
City/maximum admission and the required physical-GPU, shaped-network,
32-rendered-client, 20-run system qualification remain unfinished. Supported
limits and stable rollout stay gated; no owner answer is needed for engineering.

## Current structure-model admission validation

Parent `e76ff7881b2a5f3ae8e07df82a790392e7c7e43a` has eight successful platform
builds; publication skipped. Structure-specific staging, synchronized acceptance
and world-model reads now validate with the existing exact greedy face counter,
without temporary render arrays. Legacy model paths, 8,192-quad limit, codecs,
fingerprints, IDs, freezing and world dependency budgets remain unchanged.

110 structure/model tests passed with zero failures, errors or skips. Valid
8/16/32-resolution dependencies retained IDs, fingerprints and exact saved bytes.
Excess surface counts still reject with the same error before library mutation.
Twenty measured batches of 64 dependencies compared the separately compiled
parent with current source: median 133.609/108.418 ms, p95 162.922/113.429 ms,
worst 164.750/122.998 ms; median thread allocation 91,433,560/32,242,264 bytes.
Saved model bytes matched. Component staging is not full-system qualification.

Playtest: fresh production Main/GLFW workflow passed on the inherited display,
isolated synthetic profile and llvmpipe. Capture/export/import, all rotations,
conflicts, empty-space preservation, cancellation, undo, repeated placement,
custom dependencies/restart, inventory and model-editor regression checks passed.
All 219 source hashes match. Fresh H264 video: 380,640 bytes, 15 frames, 24.598 s.
Decoded frame and placed-world screenshot inspected. Report:
`structure-model-admission-validation.json`; source and media await controller
publication and new exact-head hosted checks.

Full city/maximum admission, partitioned publication/persistence and required
physical-GPU, shaped-network, 32-rendered-client, 20-run system qualification
remain unfinished. Supported limits and stable rollout remain gated. No owner
answer is needed for the next engineering steps.

## Current octree rotation validation

Parent `c9b52c3e047eca42ff58a96628b4c552bab3ce8f`. Direct octant remapping now
replaces repeated leaf-fill rotation for structure geometry and model definitions.
All four turns match the old algorithm's bytes, including mixed legacy branches,
fractional materials, air, source independence and frozen results. Interruption
cancels without source changes. No package, placement or persistence cap changed.

108 structure/model tests passed with zero failures, errors or skips. A 20-batch
component diagnostic recorded reference/current median 62.311/3.007 ms, p95
84.953/3.944 ms and worst 88.427/4.773 ms per 64 rotations. Median thread
allocation was 48,290,816/2,697,216 bytes. This is not full-fixture qualification.

Playtest: fresh production Main/GLFW workflow passed on inherited display with an
isolated synthetic profile and llvmpipe. Four rotations, capture/export/import,
invalid anchor, conflicts, empty-space preservation, cancellation, atomic malformed
import, undo, repeated placement, custom model/restart, inventory and model-editor
checks passed. All 219 source hashes match. Fresh 379,552-byte H264 video decoded
and inspected: 15 frames, 24.311 s; placed-world screenshot also inspected. Report:
`structure-rotation-tree-validation.json`. Media awaits controller publication.

Last observed parent CI had seven successful builds and one Intel Mac build
running; no unchanged wait was repeated. New source needs exact-head hosted
validation. Full city/maximum admission, partitioned persistence/publication and
physical-GPU/network/system qualification remain unfinished. Continue those
implementation gates after hosted checks; do not mark ready or raise supported
limits from this rotation result.

## Current admission prerequisite validation

Production head `d2bafe0d8fa4a1fd02ce3ccd472406c709553d43` has eight successful
platform builds across feature and PR checks; publication was skipped. Added
`tools/structures/CheckAdmission.java` as a decode-only prerequisite gate, with
30 passing tooling tests. It checks a trusted package SHA-256 against the same
bounded byte snapshot passed to the production decoder. Missing/changed input,
identity errors, size rejection and decoder rejection have distinct exit/result
semantics. Output excludes paths, asset names and exception messages.

Actual retained VSTR v2 city and maximum CLI runs exited 2, with verified package
identities and first rejection categories `dependency_surface_cap` and
`geometry_cap`, respectively. This is failed admission, not successful import
or performance qualification. Report: `structure-admission-validation.json`.
Production source and native harness remain unchanged; all 219 manifest hashes
and exact-head published workflow video bytes match the passed native run.
No blanket game-test or graphical rerun was needed for the CLI-only change.

Next implementation must address bounded model rendering/dependency resources,
partitioned geometry/history publication and durable persistence before raising
the current safety caps. The 256-chunk placement limit and 2,000,000-edit world
persistence limit remain further gates even after package admission. Then run
the full workflows and 20-run system qualification. Physical-GPU, shaped-network,
32-rendered-client and all city/maximum performance gates remain pending.
No supported-limit or readiness claim is made.

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

## Empty-chunk capture skipping after d637f36c

All eight exact parent summaries inspected: 859 tests each, zero failures/errors,
one skip. Merge parents contain d637f36cd7f85ea92a3e95864c10eb88bdd049ac and recorded
review base 4a0bbd6a3fa831d7ad9a3c3fa93d7328826fca6a. Receipt:
structure-d637-hosted.json; these passes exclude new capture changes.

Capture validates both corners with long arithmetic before hints, then caches a
conservative per-chunk proof in at most 71825 bytes. Snapshots only prove empty
outside natural terrain's complete [-32,95] height interval with no occupied
overrides. Preserve y/z/x encounter order, dependency IDs, cell validation, caps,
cancellation and progress. 48 unique focused tests passed across runs of 47, 5
and 5 tests (repeats included), including exact bytes for negative unaligned
capture with models/fractional geometry, terrain fallback, COW source independence,
invalid bounds before hints, cancellation and y=-1024 preservation.

Three warmups and 20 runs per sparse-box mode: actual World snapshot with one cell.
512x128x512 control/hint capture median/p95/worst:
563.252185/725.220276/833.815266 versus 25.661882/30.513305/32.654733 ms.
1024x256x1024: 4543.203166/4626.064057/4641.366858 versus
198.389395/204.199894/206.134547 ms. Untimed cell checks 33554432 or 268435456
versus 4096; one octree read each. Exact encoded bytes match. These are sparse
bounding boxes, NOT populated acceptance fixtures. No placement/save/peak-memory,
GPU/network or full-system pass. Native run underway on frozen source. All full
qualification and stable rollout gates remain.

Playtest: python3 deploy/run_structure_playtest.py --display "$DISPLAY" passed with
an isolated synthetic profile and inherited display/auth. All 217 source paths and
hashes match. Fresh F10 H264 clip: 14 frames, 21.737 s, 309263 bytes, SHA-256
f09ba4fe22ff0134e0607f731638747f921641cdd34f048b19edb61c5d32b17e.
Decoded video frame, placed-world and live-player conflict images inspected.
llvmpipe 0–1 FPS remains functional evidence only. New code, media and sanitized
structure-capture-chunk-validation.json await controller publication and exact-source
hosted checks. No populated city/max or full-system qualification claim.

## 2026-10-30 post-rebase hosted validation

Exact feature head `06a12e0894df6d503a68d243278f40521f536297` is based on review base `71612f09bfec4549456b3317676e76cdced085a4`. Direct PR inspection found all eight feature/PR platform builds successful; publication jobs skipped. Runs: 41109659422 and 41109669833. Do not repeat their wait.

The preceding local checks passed 79 structure/MP3 Java tests and six Hub registration tests. Playtest: the fresh native capture/export/import/rotation/conflict/cancel/undo/editor workflow passed; all 218 production/harness hashes match. Published exact-head video is 270568 bytes, SHA-256 `dc3edd56c0af6c5ae16440dbe45a73ffcf90986a9e78f11c0fe0d3284e3de317`, decoded as H264, 13 frames, 19.704 s. Software rendering remains functional evidence only. No deleted paths were found against the updated review base.

Implementation is incomplete: package geometry remains capped at 131072 leaves, placement at 256 affected chunks, and world history at 2000000 edits. These reject required populated city/maximum cases. Next work must implement partitioned immutable geometry/history publication and durable region persistence, preserve one-operation atomicity/undo across regions, and bound nearby render/dependency uploads and synchronization before measuring populated fixtures. Do not raise caps without measured resource bounds. Full 20-run city/max and physical-GPU/shaped-network 32-rendered-client qualification remain pending. Stable supported-limit claims, readiness and rollout remain gated. No new owner answer or internal repair request is needed to continue engineering.

## Cancellable spatial ordering implementation

Capture/import and placement builders now pass their cancellation supplier into stable packed-key radix ordering. Both 14-bit passes, prefix sums and final storage loops check cancellation every 1,024 entries. Published storage, spatial order and duplicate rejection remain unchanged. Temporary sort arrays are bounded at 20 bytes per cell plus 65,536 bytes, excluding headers. This is 80,065,536 bytes at 4,000,000 cells; no admission cap was raised.

Command: `mvn -q -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/structure-audit-tmp" -Dtest=Structure*Test test`. All 75 current tests passed. New tests compare a stable reference across all 28 key bits, preserve inputs, and check invalid keys/counts, cancellation phases and interruption. The first two new cancellation assertions expected IOException incorrectly; they were corrected to the existing CancellationException contract and rerun.

The first index-only radix design regressed 4,000,000-key p95 to 2,553.957 ms and was replaced. Final 20-run packed-key sort diagnostics measured control/new p95 136.783/101.631 ms at 1,000,000 keys and 528.801/445.349 ms at 4,000,000 keys. These are shared-VPS component probes, not populated-fixture or system qualification. An overlapping intermediate native run was discarded because production source changed; final-source native validation is running.

Final-source Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed. All 219 production/harness source hashes match the manifest. Fresh video decoded and inspected: H264, 17 frames, 27.47 s, 365404 bytes, SHA-256 `e0c71941ce41ccce0c66c2b3e0caf86a840d482800b15c06d681eb25196738f7`. Placed-world screenshot inspected. Media and validation report await controller publication. Physical-GPU and full city/max qualification remain pending.

## Bounded package codec buffering

The exact published parent is b6eda2246afcb5cf06e89010605a4dbf51092857. The controller reports successful required feature platform builds. Direct PR inspection confirmed seven successful builds and one PR Intel Mac build still running at that observation; no unchanged wait was repeated.

Added one 65,536-byte buffer per package encode/decode call above the existing decoded-budget stream. Byte ceilings, CRC/trailing checks, cancellation and file publication rules remain in place. The separately compiled exact parent codec and new codec ran three warmups and 20 measured encode/decode trials each on a 65,536-cell admitted geometry package. Encode p95 improved from 359.727 to 117.925 ms; decode from 312.870 to 68.864 ms. Shared-VPS tests ran concurrently, so these are component diagnostics only. Full city/maximum admission, geometry/history partitioning, physical GPU and 32 rendered clients under shaped networking remain unfinished and gate readiness.

The initial new test compile used the wrong material constant and missed a model import; corrected. The first executed suite passed all 75 existing structure tests but failed a new fixture-size assertion. Enlarged that fixture from 8,192 to 16,384 cells so its raw payload crosses two buffer boundaries. Focused rerun and fresh native workflow are in progress. No production source will change during native validation.

Focused retry passed all three codec tests with no failures, errors or skips. Combined with the 75 existing tests that passed in the preceding full run, 78 distinct structure tests passed. The test covers a legacy unbuffered writer, exact decoded payload compatibility across two 64 KiB boundaries, truncated input, CRC corruption, bounded read-ahead and output flush limits. Full-run and focused commands are in `structure-buffer-validation.json`.

Audit against review base 71612f09bfec4549456b3317676e76cdced085a4 found no deleted tracked paths at the published parent. MP3 support, packaged MP3 smoke checks, Hub publication workflow and registration tests have no diff from that base. This codec change intends no deletions. The native result file is replaced by the running harness and will be verified after it completes.

First native attempt exited 1 without a workflow failure report. Filtered diagnostics showed IOException in cleanup saves for CitySimulation and Jeep; the filesystem was at 100% use with 12 MiB available. This attempt is not counted as passed. Removed only the reproducible local browser cache and duplicate synthetic fixture directory under this assigned worktree (`target/structure-browsers`, `target/structure-fixtures-repeat`), leaving primary fixtures and reports intact. Available space rose to 359 MiB. Retrying the same production source and isolated native workflow; no owner access or permission is needed for this local cache cleanup.

Native retry passed with exit 0. Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` used inherited role display/auth and an isolated synthetic profile. Capture/export/import, long import path, all four rotations, occupied and live-player conflict rejection, empty-space preservation, placement/export/import cancellation, malformed import atomicity, one-operation undo, repeated placement, custom models/restart, inventory and model editor checks passed. All 219 production/harness source hashes match. Renderer: llvmpipe (LLVM 15.0.6, 256 bits); observed 1 FPS is functional evidence only.

Fresh F10 video decoded as H264, 17 frames, 28.484 s, 366956 bytes; SHA-256 `0fa509fc53cf8444976461e4007062f06915bca122a0dc38a2e504aa205c0c94`. Inspected decoded frame and fresh placed-world screenshot. New codec source, tests, probe, report and media await controller publication and exact-source hosted validation. No full city/maximum or physical-GPU/network qualification was claimed. Stable-rollout and readiness gates remain enforced.

## Repeated model dependency capture

Direct inspection confirmed all eight feature/PR platform builds passed for 8c032a97bbe12b90455213ed6ca0a237f8d45836; publication skipped. No unchanged CI wait was repeated.

Capture now memoizes up to 512 frozen definition identities while preserving fingerprint deduplication for aliases and uncached records. All 81 current structure tests passed. New tests check reference bytes, encounter order, aliases, changed definition identity under the same world ID, source-copy independence, cancellation and retry. The optional helper failed and was resolved by local takeover; no delegate task remains open.

Twenty-run capture-only diagnostic with 10,240 instances and four complex-interior/small-surface definitions measured control/new p95 3557.839/19.782 ms. Every capture matched independent reference bytes. The 30,608-byte package is not the required city/maximum fixture; full qualification remains pending. Source is frozen while fresh native validation runs.

Disk space was bounded locally by deleting four byte-identical generated repeat fixtures only after SHA-256 comparison with retained primary fixtures. No source, evidence or primary fixture was deleted.

Final-source Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed with exit 0. All 219 production/harness hashes match. The fresh F10 video decoded as H264, 17 frames, 26.954 s, 373009 bytes; SHA-256 `3d4c8c78b722b7f918294191cb3ceb39affd722c185b7fe0a236de656fdab058`. Inspected a decoded frame and the placed-world screenshot. llvmpipe (LLVM 15.0.6, 256 bits) at observed 1 FPS supplies functional evidence only. Long import path, four rotations, conflicts, cancellation, undo, restart and inventory/model-editor checks passed. New source, report and media await controller publication and exact-source hosted validation. No tracked deletions against review base; no intended deletions. Full city/maximum and physical-GPU/shaped-network 32-rendered-client qualification remain pending, with readiness and stable rollout gated.

## High-column streaming readiness race

Actual hosted inspection contradicted the controller's success summary for parent 9fca0f9d50d9c6b21fed68dbfbbef64672d21446. Feature run 41123809129 failed Intel Mac Maven verification; PR run 41123817810 passed. The sanitized test summary and filtered assertion identify WorldStreamingTest.highStructureKeepsNaturalBaseCoverageAndItsEditedChunkResident: natural base chunk y=-2 missing after only 0.085 s. No unchanged CI wait was repeated and no failed check was relabeled passed.

High chunk requests could fill the 16-slot queue before base requests marked their column pending. skyColumnReady then returned true too soon. All three streaming request paths now mark pending base coverage when a request is accepted and invalidate cached rendered-column completeness for new markers. Queue/worker/frame bounds are unchanged.

All 113 tests in 17 targeted structure, streaming, terrain scheduler and sky visibility suites passed. A blocked-worker integration probe against separately compiled exact parent/current World classes reproduced parent readiness=true and fixed readiness=false with zero resident base chunks. After worker release, all ten base chunks became resident and fixed readiness became true. Optional helper failure was resolved by local takeover; no open delegate tasks remain. Fresh native validation is running on frozen production source. Full populated city/maximum and GPU/network qualification remain pending.

Final-source native workflow passed with exit 0. Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` used inherited role display/auth and an isolated synthetic profile. All 219 production/harness hashes match. Fresh F10 video decoded as H264, 17 frames, 26.885 s, 358565 bytes; SHA-256 `6cec361faef81b97fb76fc3241ab7ee8561b8711905b24ad43ad4fa9c6499e51`. Inspected the decoded frame and placed-world screenshot. Capture/import/export, long import path, all four rotations, conflicts, empty-space preservation, cancellation, atomic failure, undo, restart and editor/inventory regressions passed. llvmpipe (LLVM 15.0.6, 256 bits) at 1 FPS is functional evidence only. High-only readiness was separately exercised by the deterministic integration probe. No deleted paths against the review base; no intended deletions. New source, report and media await controller publication and exact-source hosted validation. Full city/maximum, physical-GPU and shaped-network qualification remain pending and gate readiness/stable rollout.

## Conservative offline chunk validation reuse

Direct PR inspection confirmed all eight feature/PR builds passed for f2457456ece76b88200ed54d8d1f93eca3aac837. No unchanged hosted wait was repeated.

Added offline canonical chunk proofs only where the snapshot and final authority both have unloaded geometry. Snapshot residency is frozen; terrain revisions/ABA invalidate the proof. Resident chunks return unknown and retain live collision/mesh checks, including manually supplied geometry. This does not change admission caps or publish behavior.

All 92 targeted structure/streaming tests passed. New real LocalGame tests cover proof invalidation, manual resident collision and model-surface rejection, successful unloaded placement, saved reload and undo. Optional helper failed and was resolved by local takeover; no open delegate task remains.

The initial diagnostic assumed the wrong edit-record count. It was corrected to reconstruct geometry from saved edits and rerun both exact-parent/current implementations in fresh synthetic directories. Twenty measured runs after three warmups placed 16 cells across 16 unloaded chunks. Commit cell reads fell from 65,552 to 16; commit and durable-save p95 fell from 30.416 to 3.990 ms. This is a small offline diagnostic, not city/maximum or frame/network qualification. Fresh native workflow is running on frozen source; full qualification and readiness remain pending.

Final-source Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed with exit 0. All 219 production/harness hashes match. Fresh F10 video decoded as H264, 16 frames, 27.945 s, 425076 bytes; SHA-256 `24b7e3ec0e755867ab827ef235ba109f8b5793eb56e250eaeebcc4c606516149`. Inspected decoded frame and placed-world screenshot. Native capture/import/export, long import path, four rotations, conflicts, empty-space preservation, cancellation, atomic failure, undo, restart and inventory/editor checks passed. Renderer llvmpipe (LLVM 15.0.6, 256 bits), observed 1 FPS: functional evidence only. Resident-geometry and proof-invalidation edge cases also passed in real LocalGame tests. No deleted paths against the review base; no intended deletions. New source, report and media await controller publication and hosted validation. Full populated city/maximum and physical-GPU/shaped-network qualification remain pending and gate readiness/stable rollout.

## Bounded cancellable structure file reads

Direct inspection confirmed all eight feature/PR builds passed for f9b9df3c0e45c51c13624a0d528d264b6aa51401. No unchanged wait was repeated.

Structure import previously read the entire compressed file before cancellation/progress resumed. It now reads at most 65,536 bytes per call with cancellation and progress between reads, using one array sized to the observed file length and capped at 64 MiB. File growth/truncation rejects the import before decode; unchanged files retain all existing format, gzip and material checks. Reading maps to progress 0–10, validation to 10–100. A blocked filesystem read itself is not interruptible through this supplier.

All 88 structure tests passed. New real filesystem cases cover valid input larger than two read chunks, byte compatibility, monotonic progress, cancellation before malformed decoding, growth/truncation and oversize sparse-file rejection. Optional helper failure was resolved by local takeover; no pending delegate tasks remain. Fresh native workflow is running on frozen production source. Full populated city/maximum admission and GPU/network qualification remain unfinished and gate readiness.

Final-source Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed with exit 0. All 219 production/harness hashes match. Fresh F10 video decoded as H264, 17 frames, 26.717 s, 336463 bytes; SHA-256 `5318852a455c65a95be2a8d020d10f90638e3df1b71d5d60cf4b9933afade36d`. Inspected decoded frame and placed-world screenshot. Long-path import, import/export/placement cancellation, malformed import atomicity, capture, four rotations, conflicts, empty-space preservation, undo, restart and inventory/editor checks passed. Renderer llvmpipe (LLVM 15.0.6, 256 bits), observed 1 FPS: functional evidence only. Read-phase mutations and cancellation were also checked through real filesystem tests. No deleted paths against the review base; no intended deletions. New source, report and media await controller publication and exact-source hosted validation. Full city/maximum and physical-GPU/shaped-network qualification remain unfinished and gate readiness/stable rollout.

## Count-only model surface validation

Direct inspection confirmed all eight feature/PR builds passed for bde34351639f5f3de6b10d3b2a68781387282278. No unchanged CI wait was repeated.

Added count-only traversal to ModelMesher, shared with the rendered geometry scan. Only vertex/index emission is skipped. Package and placement face accounting use the new mode; no cap or format change. Render array SHA-256 matched the exact parent across 12 fixtures: `39a6a09f93231f026425c3d20eda01ee780ac1508328e2c4ba57b68a4e367dc0`.

The full targeted run passed all 105 existing tests and one new boundary test, but a new hollow 32-resolution fixture exceeded the unchanged 8,192-face cap. Updated that case to require identical rejection from both modes. Both new tests passed on focused retry. Total: 107 distinct tests passed across runs; no claim of a single full 107-test rerun.

Twenty-run batches of 64 models measured full/count-only median current-thread allocation 85284352/140800 bytes and p95 time 111.575/66.905 ms. Thread allocation is not peak memory. This component probe does not qualify full city/maximum or physical-GPU/network budgets. Optional helper failure was resolved by local takeover; no pending delegate task remains. Fresh native validation is running on frozen production source.

Final-source Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed with exit 0. All 219 production/harness hashes match. Fresh F10 video decoded as H264, 16 frames, 26.526 s, 371968 bytes; SHA-256 `83e782ed7ee9c2d4ee5d1e019bda916e3bd4cf1cb344c24362c9d03b236e50cb`. Inspected decoded frame, placed-world screenshot and component close-up. Native capture/import/export, long path, four rotations, conflicts, cancellation, atomic failure, empty-space preservation, undo, restart and inventory/editor checks passed. llvmpipe (LLVM 15.0.6, 256 bits) at observed 1 FPS is functional evidence only. No deleted paths against the review base; no intended path deletions. Render emission was grouped under a mode switch and its exact output digest matches the parent. New source, report and media await controller publication and hosted validation. Full populated city/maximum admission and physical-GPU/shaped-network qualification remain pending and gate readiness/stable rollout.

## Render scratch allocation continuation

Parent `a41b2c8033d03cc8c69bf1cc95023772148c2c3e` passed all eight push/PR platform builds. The current edit removes per-face temporary coordinate, corner-remap and index-order arrays from `ModelMesher` while retaining exact render output. Twelve deterministic fixtures match the separately compiled parent buffer digest. The existing 107 targeted tests passed. A fixed parent-digest regression test and fresh native workflow validate the new source before publication. See `dashboard/evidence/structure-render-allocation-validation.json` for executed commands and final results.

The component allocation probe uses 20 measured batches of 64 coloured 16-resolution models after three warmups. Median current-thread allocation falls from 85,284,352 to 59,332,096 bytes. This is cumulative allocation, not peak memory. Tests overlapped the probe, so measured times are diagnostic. No caps, package formats or model limits change. Required populated fixtures, physical GPU, full 32-client shaped-network checks and stable rollout remain gated. No unrelated path deletions are intended.

Final validation for render scratch removal: 108 distinct targeted tests passed across the full 107-test command and focused three-test command. Native attempt one ended with exit 143 and no result; it is not a pass. The retry passed the full Main/GLFW workflow on inherited display with an isolated synthetic profile and llvmpipe. Playtest: named capture, long-path import/export, all four rotations, conflicts, empty-space preservation, cancellation, atomic failure, undo, repeated placement, dependency restoration/restart, inventory and model editor regression all passed. Production source remained unchanged across all 219 manifest paths. The fresh H264 video has 16 frames over 25.31 s, 364,085 bytes, SHA-256 `b6efdfb474fb2d8ab01852b2da4ebd3fe229ce1f7b61952ae8d8a77bf8c6314c`. A decoded frame and placed-structures image were inspected. Media is pending controller publication. HUD 1 FPS confirms this is not GPU performance qualification.

## Full-fixture receipt checker

All eight platform builds passed for parent `5f652c7e3e6fc1feb2fe9b326aa280c84e8b2039`. Added a bounded CLI receipt checker with six executed synthetic tests. It requires exact source/reference environment, both admitted full fixtures, 20–100 runs, measured metrics, rendered 32-client conditions and workflow evidence. Operation p95 and per-run responsiveness budgets fail closed; maximum upload uses worst-run 45 s. Actual CLI execution rejects the existing component report with exit 1, `NOT QUALIFIED: Source head mismatch`. This is the expected negative result, not a game test failure.

Playtest: CLI integration accepts complete synthetic data and rejects duplicate JSON fields and incomplete/component evidence. Browser playtesting does not apply to this CLI-only change. Production Java and native harness source remain unchanged; all 219 source manifest entries and the published native workflow video match the previously tested implementation. New game media recording was unnecessary. No unrelated path deletions are intended. Required full-fixture admission and qualification remain pending. Checker output never substitutes for independent hardware, measurement, fixture, media and exact-source review.

## Direct leaf counting

Parent `74a41bff0ebcf2f45caf026e087048eca3cd4e6a` passed all eight push/PR platform builds. Added `SparseVoxelOctree.leafCount()` and replaced capture/decode list-size calls. This counts stored leaves, preserves legacy redundant branches and does not change caps, material validation or formats. All 97 targeted structure/model tests passed, including two new traversal, persistent snapshot and legacy-byte regressions.

The 20-run shared-VPS component probe compares 128 counts of a frozen 2,048-leaf tree per batch after three warmups. List/direct p95 is 23.462/2.604 ms; median current-thread allocated bytes are 14,789,632/0. Targeted tests overlapped this diagnostic. These are neither peak-memory nor populated-fixture measurements. See `dashboard/evidence/structure-leaf-count-validation.json` for exact commands/results.

Playtest: fresh Main/GLFW workflow passed on inherited display, isolated synthetic profile and llvmpipe. Capture/import/export, four rotations, conflicts, empty-space preservation, cancellation, malformed-import atomicity, undo, repeated placement, dependency/restart and editor regressions passed. All 219 production/harness manifest entries match the tested source. Fresh H264 media has 17 frames, 26.269 s, 364,676 bytes and SHA-256 `4ef64486283b7d848e30a401b8957351fc96d6be0e21d0e73d6a483793bd8576`. A decoded frame and placed-structures screenshot were inspected. Media awaits controller publication. HUD 1 FPS does not satisfy physical-GPU performance qualification. Full city/maximum admission and all qualification gates remain pending. No unrelated deletions are intended.

## Structure tooling CI integration

All eight push/PR platform builds passed for parent `da423f64819f67cc1bd18f9fc917a6080af905e9`. The build matrix now runs `python3 -m unittest discover -s tools/structures -p "test_*.py" -v` after Maven. The exact command passed all 24 local tests covering malformed corpus data, handoff archive integrity, probe statistics and qualification receipt checks. The existing Java verification, packaged MP3 decoder smoke, reports, packaging and publication controls remain intact. Actual execution of the new step on all four hosted platforms is pending.

Playtest: the affected CLI suite exercised valid synthetic receipts and rejection/regression paths. Browser playtesting does not apply to this CI-only change. Production Java and native harness remain unchanged; all 219 manifest entries match the passed native workflow. Published parent media matches local bytes and SHA-256 `4ef64486283b7d848e30a401b8957351fc96d6be0e21d0e73d6a483793bd8576`. No new game recording was needed. Full city/maximum admission and qualification remain pending. No unrelated deletions are intended.

## Streaming package material validation

Replaced the package constructor's temporary leaf list with a checked visitor. Octant traversal order, coordinates, side lengths, colours, material rejection messages and geometry caps remain unchanged. Per-leaf cancellation now stops validation without collecting the rest of the list. Existing list APIs remain available. All 99 targeted structure/model tests passed, including visitor order/count comparisons, checked failure stopping, source immutability, null rejection and clean retry.

The 20-run traversal component probe uses 128 frozen 2,048-leaf trees per batch after three warmups. It checks the same ordered coordinate/colour checksum on every traversal. List/visitor median is 11.619/4.534 ms, p95 22.313/4.619 ms and worst 25.068/4.674 ms. Median current-thread allocated bytes fall from 14,793,728 to zero, excluding reused callback/counter setup. Tests ran concurrently. These are neither complete package-validation nor peak-memory/full-fixture measurements. Commands and results are in `dashboard/evidence/structure-leaf-visitor-validation.json`.

Playtest: fresh Main/GLFW workflow passed on inherited display, synthetic isolated profile and llvmpipe. Capture/import/export, four rotations, conflicts, empty-space preservation, cancellation, malformed-import atomicity, undo, repeated placement, dependency/restart and editor regressions passed. All 219 production/harness manifest entries match. Fresh H264 video: 17 frames, 26.774 s, 382,128 bytes, SHA-256 `e8a1cac476d5e50b2998ec3cc4277f93635126fe5cc5141754fa77434a609706`. A decoded frame and placed-structures screenshot were inspected. Media awaits controller publication. HUD 1 FPS is not physical-GPU performance qualification. Full city/maximum admission and qualification remain pending. No unrelated deletions are intended.

## Decoded-size receipt guard

All eight platform builds passed for parent `72e63d2a9c4b061c425d9da30b5b66e0431a12c6`. The receipt checker now requires positive integer `decoded_bytes` no greater than 512 MiB for each fixture. This closes a missing receipt resource check; the production importer already enforces its decoded budget. The exact CI discovery command passed all 25 tooling tests. New regression checks accept the exact boundary and reject missing, invalid and oversized values for both fixtures, including an actual CLI rejection path. Playtest: synthetic CLI integration passed; browser testing does not apply. Production source and the passed native workflow remain unchanged across all 219 manifest entries. Published parent media matches local bytes/hash. New tooling hosted checks remain pending. Full city/maximum admission and physical/network qualification remain unresolved. No unrelated deletions are intended.

## Invalid placement anchor input

The UI `at X Y Z` command now validates the candidate with existing protocol world/build-height bounds before changing the target or scheduling preview work. Invalid input reports `Placement anchor outside world` and keeps the prior target. Valid negative coordinates and exact bounds remain accepted. Server checks and all asset limits remain unchanged. All 101 targeted structure/model tests passed, including two new UI edge/regression tests.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed with actual Main/GLFW input, inherited display and isolated synthetic profile. The harness entered `at -2147483648 128 0`, checked the unchanged target, idle preview state and unchanged world edits/dependency counts, then continued with `at 20 128 26`. The invalid-anchor screenshot shows the expected error and retained target `22, 135, 40`. Capture/import/export, four rotations, conflicts, cancellation, atomic failure, empty-space preservation, undo, repeated placement, dependencies/restart and editor regressions passed. All 219 production/harness source entries match.

Fresh H264 media: 17 frames, 32.539 s, 426,611 bytes; SHA-256 `125c0053be133f2155f983b5572afe7efea5b089694c51b1a72ee16d5e034edc`. The invalid-anchor screenshot and decoded video frame were inspected. Artifacts await controller publication. llvmpipe HUD 1 FPS is functional evidence, not physical-GPU performance qualification. Full city/maximum admission and qualification remain pending. No unrelated deletions are intended.

## Resident snapshot validation reuse

Offline snapshots now capture actual immutable resident chunk geometry rather than treating it only as canonical terrain/history. Their proofs include chunk identity, geometry version and canonical geometry/terrain stamp. Chunk edits/replacement, residency transitions and terrain-generation changes invalidate proof reuse. Worker reads use only detached trees. Model-library revision checks and changed-state authoritative collision/surface validation remain intact. No admission cap increases.

All 110 targeted structure, streaming and model tests passed. Tests retain unloaded proof behavior, reject resident collisions during prepare or commit, check detached resident geometry after manual edits, verify persistence/undo after unchanged resident placement and reject a changed resident neighbour that exceeds the model-instance surface cap. Existing tests now expect earlier rejection when snapshot geometry already contains a collision; that rejection gate was not removed.

`ResidentStampProbe` compared the separately compiled exact parent World with current source after three warmups and 20 real offline commits each. The fixture has 16 occupied cells across 16 resident chunks. Parent/current commit cell reads are 65,552/16; median is 21.243/2.640 ms, p95 40.857/3.614 ms and worst 43.310/6.453 ms. Commit includes durable save; preparation and post-run placed/reloaded geometry assertions are outside timing. Exact commands and source scope are in `dashboard/evidence/structure-resident-proof-validation.json`. This is not full-fixture, memory, GPU, frame, tick or network qualification.

Playtest: fresh Main/GLFW workflow passed on inherited display, synthetic isolated profile and llvmpipe. Capture/import/export, invalid-anchor rejection, four rotations, conflicts, cancellation, atomic failure, empty-space preservation, undo, repeated placement, dependencies/restart and editor regressions passed. All 219 source manifest entries match. Fresh H264 media: 15 frames, 24.487 s, 347,963 bytes; SHA-256 `386b5d39bcc2b7c0d31a33beda5e20aae3253148752db634195a710fe0c54726`. A decoded video frame and placed-structures image were inspected. Media awaits controller publication. HUD 1 FPS is functional evidence only. Full city/maximum admission and qualification remain pending. No unrelated deletions are intended.

## Streaming world-cell decode checks

Parent `ab0364cdc9c900d82c1ddc98a370487b2a20a5e3` passed all eight platform builds. `Protocol.CellState.read` now validates materials through the checked leaf visitor rather than allocating a leaf list. Wire bytes, protocol version, material/position checks and frozen decoded results remain unchanged. All 111 distinct targeted tests passed across the structure/model and protocol compatibility commands. New tests cover exact wire roundtrips for air, uniform blocks/models, fractional geometry and coloured light, plus malformed material, position, resolution and truncation rejection. The targeted suite includes actual loopback TLS multiplayer transfer and restart integration.

The separately compiled exact parent Protocol and current source each ran three warmups and 20 measured batches of 64 decodes. Each cell contains 2,048 leaves and 12,886 wire bytes. Parent/current median time is 18.631/16.529 ms, p95 28.378/20.904 ms and worst 31.603/26.592 ms. Median current-thread allocated bytes are 13,245,440/5,853,696 per batch. Values/counts were checked per decode and exact bytes before timing. Allocation is cumulative, not peak memory; this is not full synchronization or populated-fixture qualification. Exact commands/results are in `dashboard/evidence/structure-cell-read-validation.json`.

Playtest: fresh native offline Main/GLFW regression passed on inherited display, synthetic isolated profile and llvmpipe. Capture/import/export, invalid-anchor rejection, four rotations, conflicts, cancellation, atomic failure, empty-space preservation, undo, repeated placement, dependencies/restart and editor regressions passed. This graphical run is distinct from the wire decoder integration tests. All 219 source hashes match. Fresh H264 media: 17 frames, 27.581 s, 404,819 bytes; SHA-256 `c39d99e8fac9b0ce711f9090811c3d9e390211084365e690a158d56e3eb34f60`. A decoded frame and placed-structures image were inspected. Media awaits controller publication. HUD 1 FPS is functional evidence only. Full city/maximum admission and physical/network qualification remain pending. No unrelated deletions are intended.

## Bounded production offline replay sections

Structure placement now stages version-10 offline saves through `RegionReplay`. Each section contains consecutive edits from one chunk, at most 1,024 records and 24,576 payload bytes, with an exact payload length and CRC32. Regions may recur so replay order stays unchanged. The loader validates each complete bounded section before applying it to its private history. Model definitions, the fixed survival slot and dropped-item tail remain self-contained. Version-10 loads require an exact file end. Versions 3–9 still load, and ordinary worlds that have not used the new structure save path retain their existing version-8/9 writes. Successful version-10 worlds retain that format on later saves.

This is a production persistence step, not a disk-backed live world or indexed lazy loader. The live canonical History and the 2,000,000-record persistence cap remain. Full city/maximum admission, geometry/instance/chunk limits, 20-run reference benchmarks, physical-GPU rendering and shaped-network synchronization to 32 clients remain unresolved. Stable rollout and supported-limit claims stay gated. No unrelated deletions are intended.

Initial focused validation exposed an invalid fractional LED test fixture; the fixture now uses the required depth-encoded material type. The corrected focused command passed all 26 tests, including exact section/replay ordering, malformed count/length/checksum/position rejection, cancellation and progress callback failure, model/inventory/health/drop restoration, version-10 resaves, failed normal-write cleanup, atomic-publication failure and retry. Full regression and native playtest results will be recorded after execution.

Final validation: `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" test` exited 0. All 192 current source test classes have fresh reports: 975 tests, 974 passed, one optional `AssetManagerIntegrationTest.browserWorkflow` skipped, zero failures/errors. Stale reports were excluded by matching current source class names and checking report times against the unique command-output file birth time.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed in production Main with real GLFW/X11 input, inherited display/authentication and an isolated synthetic offline profile. Capture, anchor, naming, browsing, long-path self-contained import/export, all four rotations, footprint/conflict preview, empty-space preservation, occupied/player collision rejection, single-operation undo, cancellation, malformed import atomicity, repeated placement, 32-resolution model dependencies, restart, inventory and editor checks passed. The resulting world header is version 10. All 226 production/harness source hashes match. Browser playtesting does not apply to this native desktop workflow.

The fresh F10 video fully decoded: 19 frames, 32.396 s, 432,309 bytes, 960×544; SHA-256 `7b197b3617175f12cfd203547504401d5494ee2919cdad5b931b2ae36bd87434`. The decoded midpoint and occupied-conflict screenshot were inspected. Renderer is llvmpipe (LLVM 15.0.6, 256 bits); HUD 1 FPS is functional evidence only. Media and `structure-region-replay.json` await controller publication. Required qualification remains pending. `git diff --check` passed and there are no tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`.

## Bounded production server replay sections

Controller-delivered hosted results for `3e840d3546cdb91f3a62424df2ab68cd40ec0ae5` passed all four platform builds; publication was skipped. Production server structure staging now writes marker -10 with bounded replay sections. Restart validates model references within each section before changing private history, retains player state and rejects trailing bytes. Ordinary saves upgrade only after atomic structure publication or loading a version -10 world. Legacy save readers, network bytes and the city sidecar remain unchanged.

The initial real TLS multiplayer integration passed. Focused replay/server save tests also passed, including exact byte resaves and malformed/missing-dependency rejection. A final atomic-publication-failure regression and the full suite are now running. Full city/maximum admission and all physical/network performance qualification remain pending; rollout remains gated. No unrelated deletions are intended.

Final server replay validation: the focused command passed 15 tests. `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" test` exited 0: 193 current test classes, 979 tests, 978 passed, one optional `AssetManagerIntegrationTest.browserWorkflow` skipped, zero failures/errors. All reports are fresh and match current source classes. The five production staging tests passed, including a blocked atomic rename that preserves live geometry, prior disk bytes and the legacy format flag, removes its private stage and later saves marker -8.

Playtest: the focused `StructureMultiplayerTest` exercised the running server through real loopback TLS with three synthetic protocol clients, all four rotations, empty dependency caches, occupied conflicts, cancellation, contested placement, late joining and restart. Successful placements write marker -10. New server restart tests preserve exact replay/model/player/inventory/health/drop bytes across ordinary resaves and reject missing dependencies, truncation and trailing bytes without replacing input. These are functional integration checks, not shaped-network or 32-rendered-client qualification.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed again in production Main/GLFW with inherited display/authentication and an isolated synthetic offline profile. Capture, naming, anchor, browse, long-path import/export, four rotations, conflict/empty-space policies, cancellation, malformed import, atomic failure, undo, repeated placement, 32-resolution dependencies/restart and editor/inventory regressions passed. All 226 production/harness source hashes match. Browser playtesting does not apply to this native workflow.

Fresh F10 video fully decoded: 18 frames, 32.634 s, 428,766 bytes, 960×544; SHA-256 `ba658c0bb8906e0795181380e35102ce847e4463c1d27653814c72589e9967b3`. The midpoint and occupied-conflict screenshot were inspected. Renderer llvmpipe (LLVM 15.0.6, 256 bits), HUD 1 FPS: functional evidence only. Video and `structure-server-regions.json` await controller publication. `git diff --check` passed; no tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`. Full city/maximum admission and performance qualification remain pending; rollout stays gated.

## Server model-prefix cancellation

All four hosted platform builds passed for `936c92dd0fff4162192d57b880d3aa30fea0c5e9`; publication skipped. Server structure save staging now checks cancellation below its 64 KiB buffer, including oversized model writes, before/after prefix construction and on flush. Publication remains an uncancellable atomic commit. Initial cancellation happens before creating the private stage directory. Failures still close/remove the stage and preserve the target.

The first targeted run passed 21 tests and failed one new test's exact-file-size assumption: a 43-byte metadata prefix precedes the first 65,536-byte model slice, so observed staged length is 65,579 bytes. The revised test checks the bounded write window and that the model body is incomplete before replay, rather than treating total staged length as a single write size. Standalone bulk-write cancellation still requires exactly 65,536 staged bytes. Production source did not change for this test correction. Final targeted retest and fresh native capture remain pending. Full city/maximum admission and qualification remain gated.

The corrected tests are rerunning with the explicit `compiler:testCompile surefire:test` goals. These compile only test classes and execute the selected suite, leaving production classes unchanged while the native capture runs.

Corrected retest passed all 22 tests with zero failures/errors/skips across `StagedWorldSaveTest`, `StructureSaveStagingTest`, `StructureMultiplayerTest`, `ServerRegionSaveTest`, `RegionReplayTest` and `HistoryProtocolCompatibilityTest`. Every selected report is fresh. This turn used targeted checks for the staged-save path; no new full-suite pass is claimed. Exact commands and the initial test-assumption failure are retained in `dashboard/evidence/structure-server-cancel.json`.

Playtest: fresh `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed with production Main/GLFW input, inherited display/authentication and isolated synthetic offline profile. Capture/anchor/naming/browse, long-path import/export, all four rotations, bounds/conflicts, empty-space preservation, cancellation, malformed import/atomic failure, undo, repeat placement, 32-resolution dependencies/restart and model/inventory regressions passed. This native offline regression is separate from real server cancellation/TLS integration. Browser testing does not apply. All 226 production/harness source hashes match.

Fresh F10 video: 16 decoded frames, 28.956 s, 418,444 bytes, 960×544; SHA-256 `aaffb8aefa8fee8126b98802a6818b4368818ee543a308e30e6ed9551a21b6b0`. Midpoint and red occupied-conflict preview were inspected. Renderer llvmpipe (LLVM 15.0.6, 256 bits), HUD 0–1 FPS: functional evidence only. Report `structure-server-cancel.json` and media await controller publication. `git diff --check` passed; no tracked deletions against base `71612f09bfec4549456b3317676e76cdced085a4`. City/maximum admission and all remaining qualification stay pending; rollout stays gated.

## Capture-path audit and disk preflight

Controller-delivered exact-head results for `0b1061415b5d89192f3bdf989dfd592c4d2a47e2` passed all four platform builds; publication skipped. Source is unchanged from the passed targeted/native run and published, byte-verified media. Full acceptance remains unfinished.

The next production capture bottleneck is in `StructureMenu.execute`: capture, preview and offline placement call `World.structureSnapshot` on the owner before worker submission. That method clones the canonical chunk map and snapshots resident chunks in one call. It also runs before `start` rejects a busy operation. The next implementation should use bounded owner-queue batches for only relevant chunks, preserve resident geometry and model/terrain stamps, reject changed snapshots, and avoid an iterator invalidated by unrelated streaming. The existing 128-entry snapshot batching is a provisional starting point, not a measured stall guarantee. This audit does not claim the change is implemented.

Disk preflight prevented another full-fixture staging run. Removed only explicitly named owned force/save/load probe outputs, all reproducible from retained tools. Reclaimed 28,803,104 bytes. Remaining free space: 86,581,248 bytes. The maximum fixture's decoded staging file requires 305,479,992 bytes, leaving a 218,898,744-byte shortfall before any world/save staging. Full fixtures, source and recorded evidence remain intact. Exact private cleanup/preflight details are retained in `target/structure-disk-preflight.json`. No new maximum diagnostic or production qualification pass is claimed. Browser playtesting does not apply to this filesystem preflight; no production code changed. Large-fixture execution requires disk recovery. Physical-GPU/32-client and all stable-rollout gates remain pending.

## Recovery resumed: bounded production region snapshots

Disk repair is verified locally: 9.9 GiB free at resume. The existing maximum-v2 index diagnostic reran with `-Xmx256m`, exited 0 and read every region/model: 4,000,000 cells, 7,967,232 leaves, 16,384 instances, 512 models, 8,192 regions, 305,479,992 decoded bytes. Index/revisit were 14.826210/13.130519 s; total process elapsed 28.191259 s and peak RSS 188,648 KiB. Its owned cache was removed. This is the existing read-only diagnostic, not production admission or qualification. Exact private execution receipt is retained in `target/structure-recovered-maximum.json`.

The production UI now requests only footprint/selection chunk snapshots through the owner queue, at most 128 chunk entries per request. It marks operations busy before starting snapshot work and rejects additional busy commands before making snapshots. Each batch retains canonical COW bucket references and actual resident geometry/stamps. Owner checks cover canonical edits, resident geometry epoch, terrain generation and model-library identity/revision before each batch and at completion. Workers assemble a region-only Source and reject terrain changes while reading. Reads outside captured chunks fail; unknown proof stamps return null. The existing synchronous whole-world Source remains for compatibility.

The chunk grid is built/assembled on the worker, so no live-map iterator spans frames. Pristine unrelated streaming need not invalidate this plan; changes to the global canonical/resident geometry epochs may conservatively reject it and require retry. Model metadata and the final selected-chunk lookup still materialize. Batch counts do not prove a 100 ms stall or memory budget. No geometry, model, affected-chunk or persistence cap was lifted; all full city/maximum and GPU/32-client qualification gates remain pending.

Initial compilation caught a missing model-package import, corrected before test execution. Focused capture/occupancy/snapshot/menu checks passed. Tests compare capture bytes with the legacy whole snapshot, preserve negative/fractional/model/resident data and source independence, verify 320 chunks use three owner batches plus begin/finish calls, reject stale/cancelled captures with retry, reject oversized/wrong-thread requests and confirm busy menu commands create no extra snapshots. Full regression and fresh native workflow validation are running.

## Requested pause — preserved production region snapshot edits

Paused on 2026-11-07. All unpublished source, tests, docs and evidence are preserved in this feature worktree. No Git commit/push/merge was performed. The two active validation runs were safely interrupted with exit 130: full Maven run (`target/structure-region-snapshot-full.txt`) and Main/GLFW capture (`target/structure-region-snapshot-native.txt`). Neither is passed. Local capture files may be incomplete and must not be treated as validated media. Do not read the harness private runtime log.

Completed before pause: disk repair verified; maximum-v2 read-only index rerun passed with owned staging cleanup; initial focused capture/snapshot/occupancy/menu tests passed. Production changes add region-only COW snapshot batches (at most 128 chunks per owner request), model/geometry/terrain revision checks, worker assembly and early busy rejection. The later resident-change assertion is included in the interrupted full run and still needs final execution. No caps or rollout gates were lifted.

Resume from the retained edits, not the old disk blocker. Inspect the current source/status; rerun meaningful snapshot/structure regressions and the full suite as needed. Rerun `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` with inherited authentication and a synthetic profile, then decode/inspect fresh media and verify source hashes. Update `structure-region-snapshot.json` with actual completed results before requesting controller feature validation. Full city/maximum admission, disk-backed production region/root work and physical-GPU/32-client performance qualification remain pending.

The harness had removed its prior result while starting the interrupted run. The last committed result and manifest are archived in ignored `target/structure-pause-previous-structure-native-{result,source}.json`; the current result now honestly records interruption. No tracked evidence deletion is intended.

## Requested pause after resumed native validation

Paused again on 2026-11-07. Unpublished source, tests, docs and fresh media are preserved. The resumed full Maven command (`target/structure-region-snapshot-resumed-full.txt`) was interrupted with exit 130 at the pause request. Last observation: 134 completed test-class reports, zero observed failures/errors. This is not a full-suite pass. No further implementation or tests started after the pause.

Playtest: the resumed native command `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` completed with exit 0 and passed. It exercised production Main/GLFW capture, anchor/naming/browsing, long-path import/export, preview/four rotations, occupied/player conflict rejection, empty-space preservation, cancellation, atomic failure, undo, repeated placement, 32-resolution dependency/restart and inventory/editor regressions. Isolated synthetic profile, inherited display/authentication, llvmpipe (LLVM 15.0.6, 256 bits). All 227 production/harness source hashes match. No source changes followed this run.

Fresh F10 video fully decoded: 23 frames, 44.489 s, 499,171 bytes, 960×544; SHA-256 `414102c44d1a43b62655bb1608e25b5dcb34b6b7e75cbbd93fe50dd0fb472828`. The decoded midpoint and red occupied-conflict screenshot were inspected; inspected HUD is 1 FPS. This is functional evidence only. The completed native result, source manifest, video, screenshots and `structure-region-snapshot-media.json` are preserved. Do not replace them merely because the separate full suite was interrupted.

Resume: finish the full Java regression against the preserved source, record actual fresh report totals and update `structure-region-snapshot.json`. Native media can be reused if all 227 source hashes still match and integration changes no production/harness source. Then request controller publication/exact-head hosted validation. Existing city/maximum admission, disk-backed live region/root and physical-GPU/32-client qualification gates remain pending. No ready/review/rollout claim.

## Completed unchanged-source regression corpus

The source, changed-test hashes and native video are unchanged from the completed playtest. All 227 production/harness source hashes and video SHA-256 were verified again. The interrupted run actually left 142 completed current-class reports before exit 130 (134 was the last earlier observation). Their reports postdate their source files. The remaining 52 current classes ran with explicit `compiler:testCompile surefire:test` goals and `-Dtest` selecting those fully qualified names; this resumed command exited 0. All 52 reports are fresh. The exact class selection and command are recorded in `structure-region-snapshot.json` and ignored `target/structure-region-snapshot-resume-selection.json`.

Combined exact unchanged-source corpus: 194 classes, 985 tests, 984 passed, one opt-in `AssetManagerIntegrationTest.browserWorkflow` skipped, zero failures/errors. This is combined validation across two runs; the interrupted full command is not called passed. No production/harness source changed after the passed Main/GLFW workflow, so its inspected media is retained rather than rerun.

Playtest evidence remains 23 fully decoded frames, 44.489 s, 499,171 bytes; SHA-256 `414102c44d1a43b62655bb1608e25b5dcb34b6b7e75cbbd93fe50dd0fb472828`. Fresh native capture from this implementation passed all recorded requested/edge/regression steps. The midpoint and occupied-conflict screenshot were inspected; software-rendered functional evidence only. Source/media await controller publication and new exact-head hosted validation. `git diff --check` passed, with no tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`; no deletions are intended. MP3 support/fixtures/tests/smokes and unrelated Hub publication/registration checks remain restored. Full city/maximum admission and performance/GPU/32-client qualification remain pending. Rollout stays gated.

## Dependency codec cancellation

Controller-delivered exact-head hosted checks for `e56e30709a07cc4ed8d30b565bdae56403d8862e` passed all four platform builds; publication skipped. The source-matched workflow video was published and byte-verified before this change.

Production version-2 package import/export now passes cancellation into the dependency codec. Dense voxel scanning, RGB extraction and canonical octree reconstruction check cancellation within a dependency, rather than only between dependencies. Existing overloads retain their API and bytes. Legacy model serialization/validation remains bounded by the existing per-model limit and is not made incremental by this change; no 100 ms latency claim is made. All package, geometry, instance, chunk and persistence caps remain in force. Full city/maximum admission and physical-GPU/32-client/performance qualification remain pending. No unrelated deletions are intended.

Focused codec/package/buffered-wire checks passed 41 tests with zero failures/errors/skips. New regressions cancel dense 32-resolution dependency encoding and decoding at several internal checkpoints, retry with identical bytes and model fingerprints, and exercise real file export cancellation preserving prior target bytes and cleaning its private stage. The additional staging, real TLS multiplayer and region-snapshot regressions passed 11 tests. The final codec retest also cancels file import inside its dependency and verifies unchanged input bytes and a successful retry. Total unique targeted tests: 52, zero failures/errors/skips. No full-suite rerun is claimed this turn. A fresh native workflow is running.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 and passed in production Main/GLFW, inherited display/authentication and a synthetic isolated offline profile. Capture/anchor/naming/browse, long-path import/export, all four rotations, bounds/conflicts, occupied/player collision rejection, empty-space preservation, cancellation, malformed import atomicity, undo, repeated placement, 32-resolution dependencies/restart and editor/inventory regressions passed. Browser playtesting does not apply to this native desktop workflow. All 227 production/harness source hashes match.

Fresh F10 video fully decoded: 25 frames, 43.186 s, 505,619 bytes, 960×544; SHA-256 `1bcbea6c501eccbd0b1ac767ba5da478492cf76f10b0df41ddaaa7847d5aa822`. The decoded midpoint and occupied-conflict screenshot were inspected. Renderer llvmpipe (LLVM 15.0.6, 256 bits); HUD 1 FPS is functional evidence only. Media and `structure-dependency-cancel.json` await controller publication/exact-head hosted validation. All seven selected test-class reports are fresh. No full-suite pass is claimed this turn. No tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`; no unrelated deletions intended. Full city/maximum admission, disk-backed live region/root implementation and all outstanding qualification remain pending; rollout stays gated.

## Production position-only conflict preview

Controller-delivered hosted results for `6aee678a0d75b59dd135b7804a854428a18e9968` passed all four platform builds; publication skipped. The existing import representation already retains fractional geometry as packed segments. The next production preview bottleneck was `StructureMenu` iterating full `Cell` values: each position query reconstructed packed geometry despite needing only its position.

Packages now expose an immutable occupied-position view over the existing compact index. The production UI calls `StructurePreview.blocked` on its worker. The query reads only that index, uses conservative destination empty-cell hints, falls back to actual occupancy for unknown hints and stops on its first conflict. Source holes remain irrelevant, and final UI completion still rejects stale selection/target/rotation results. Cancellation is checked before reads, after hints and before returning results; progress covers the query. Bounds/anchor/orientation rendering and authoritative placement revalidation remain unchanged.

Focused checks passed 45 tests, zero failures/errors/skips: four rotations with negative destination coordinates, non-origin anchor and holes; empty/unknown hints; first-conflict exit; cancellation during a hint and completion; retry; outside-world rejection; immutable position views and package-byte round-trips; packed geometry, region snapshot and structure/occupancy regressions. Fresh native workflow and a bounded admitted-fixture CPU microbenchmark are running. No full-suite or city/maximum qualification pass is claimed. All geometry/instance/chunk/persistence caps and rollout gates remain in place. Disk-backed live root/region integration and physical-GPU/32-client/performance qualification remain unfinished.

Bounded CPU query measurement completed: 50,000 fractional cells/100,000 leaves, three warmups and 20 runs per case/path. Hinted-clear p95 is 2.653814 ms versus 50.848158 ms for the prior UI loop, with median query allocation 1,200,248 versus 35,200,024 bytes. Unknown-hint clear p95 is 4.180094 ms versus 29.084022 ms. First-conflict p95 is 0.047378 ms versus 35.285556 ms; the new query reads one destination instead of 50,000. Whole probe peak RSS 157,316 KiB with `-Xmx256m`. Snapshot construction, UI acknowledgement, frame/tick/GPU/network/durability are excluded. This admitted fixture is not either required full fixture, and these results do not qualify supported limits. Exact environment, commands and results are in `structure-position-preview.json`. The absent `/usr/bin/time` wrapper did not execute the probe; the actual completed runs used Python subprocess/time/resource collection.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 and passed. Production Main/GLFW, inherited display/authentication and isolated synthetic offline profile; capture/anchor/naming/browse, long-path import/export, all four preview rotations, occupied/player conflict rejection, empty-space preservation, placement/export/import cancellation, malformed import atomicity, undo, repeated placement, 32-resolution dependency/restart and inventory/editor regressions passed. Browser playtesting does not apply to this native desktop workflow. All 228 production/harness source hashes match.

Fresh F10 video: 24 fully decoded frames, 40.819 s, 464,291 bytes, 960×544; SHA-256 `68d6a9d752446b345509da119b2414bd7ead9552e8e5aaa3cd3707268bf77b7b`. Midpoint and occupied-conflict screenshot inspected. Renderer llvmpipe (LLVM 15.0.6, 256 bits); HUD 1 FPS is functional evidence only. New media/report await controller publication and exact-head hosted checks. `git diff --check` passed. No tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`; named restored MP3/Hub files match that base, and the build workflow only adds the structure tooling test step. No unrelated deletions intended. Full city/maximum admission, disk-backed live region/root integration and required physical/network/performance qualification remain unfinished; rollout stays gated.

## Worker-staged offline undo

Controller-delivered exact-head checks for `e7e55c2149ecb69a40db52b4c7e9d669ffdc3e48` passed all four platform builds; publication skipped. Offline menu undo previously copied history and wrote saves synchronously on the owner. It now marks busy immediately and reserves the newest immutable undo record. Its worker scans occupied bounds, captures the detached region through bounded owner requests, validates placed values/proofs, copies history and writes the private version-10 save. Cancellation before publication closes private resources and retains the record.

Owner publication rechecks the model revision and reserved record, reuses the worker equality proof only when all trusted chunk stamps still match, and otherwise performs authoritative equality comparisons. It removes the undo record only after successful publication. Existing synchronous server/custom undo APIs remain unchanged. Final rollback snapshots, cell replacement, fallback equality checks and legacy-map saving still run on the owner; no 100 ms stall or latency guarantee is established. All admission caps and qualification/rollout gates remain in place.

All 62 unique targeted tests passed with zero failures/errors/skips across nine fresh current-class reports. Checks cover reservation/busy behavior, cancellation before owner reads and after validation, byte-identical retained save files, geometry/history/model protection, stale destination/model rejection, trusted/missing stamp behavior, real history/save work off owner, current inventory/health/drop publication and restart, failed atomic rename with live rollback and retained-record retry, legacy undo, TLS multiplayer, publication/save and snapshot regressions. No full-suite rerun is claimed. Native Main/GLFW playtest is running with new real-input undo cancellation and screenshot capture.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 and passed. Production Main/GLFW, real X11 input, inherited display/authentication and isolated synthetic offline profile. New undo cancellation preserved placed geometry, history and exact committed file bytes; subsequent undo succeeded. Capture/anchor/naming/browsing, long-path import/export, all four rotations, conflicts/empty-space policy, other cancellation, malformed import atomicity, repeat placement, 32-resolution dependencies/restart and inventory/editor regressions passed. Browser playtesting does not apply. All 228 production/harness source hashes match.

Fresh F10 video: 25 fully decoded frames, 43.567 s, 503,528 bytes, 960×544; SHA-256 `45e3c9e062ab67cceb65e85e335dcf6c5364ec7ef011e45eb2b30a5d35be8a82`. Green footprint/anchor midpoint and new `structure-undo-cancelled.png` were inspected. Cancelled undo leaves the house visible. Renderer llvmpipe (LLVM 15.0.6, 256 bits); HUD 1 FPS is functional evidence only. Report/media await controller publication and exact-head hosted checks. `git diff --check` passed and no tracked files are deleted against review base `71612f09bfec4549456b3317676e76cdced085a4`; no unrelated deletions intended. No full-suite rerun or performance qualification pass is claimed. Full city/maximum admission, live disk-backed region/root work and required GPU/32-client/frame/tick/network/durability qualification remain pending; rollout stays gated.

## Verified rollback images and list-free replay counts

Controller-delivered exact-head checks for `5881aeb29fff05d15efabab99ebafaecd9ef1040` passed all four platform builds; publication skipped. Final local publication was rebuilding live rollback maps after engine revalidation. The engine now supplies its detached frozen before-images through a new staging overload. Default dispatch preserves existing server/custom staging overrides. Local publication reuses these images only in that trusted engine path; direct legacy commits still capture live rollback state. Placement, prepared undo and synchronous undo still perform their existing authoritative checks before publication. Wire messages do not carry or control before-images.

`World.replaceCell` no longer creates a replay list only to count records and read the clear edit's cell key before canonical history traverses the source again. It counts through the existing canonical edit visitor, preserving material preflight before any world mutation, and obtains the clear edit key directly. Per-record traversal/construction remains; the count no longer retains a list. Canonical replay order, version counters, saved formats and resident updates stay unchanged. This removes duplicate work; final per-cell publication, history/model snapshots, root mutation and fallback checks still involve owner work. It does not establish a 100 ms stall budget, add a disk-backed root or lift admission limits.

The first focused run passed 48 tests and errored in a new fixture: `SparseVoxelOctree.set` rejects the world LED colour used there. The fixture now uses the existing world-material `fill` API. No production code changed for that correction. Corrected retest passed all 49 tests, zero failures/errors/skips. Tests verify trusted placement/prepared undo avoid duplicate live rollback reads, synchronous undo retains its required equality read, direct legacy publication retains rollback capture, packed fractional and redundant-air replay counts match, and existing publication, failed rename/rollback, concurrent-edit, model, undo and source isolation regressions hold. Further regression and fresh native workflow runs are in progress. No full-suite rerun or performance qualification pass is claimed. Full city/maximum admission and all physical/network qualification remain pending.

Independent source audit caught that the initial raw leaf-count shortcut would move invalid fractional-material rejection after mutation. The final code uses `CellState.replayEditCount`, which invokes the existing canonical visitor before mutation without retaining its edit list. New mutable/packed invalid-model-cell tests require unchanged history, revisions, sky stamp and geometry on rejection. The in-progress native run was intentionally interrupted with exit 130 before applying this fix; it is not a pass and its partial media is not evidence for the final source. The complete 79-test selection is rerunning, followed by a new native run.

Final corrected source passed all 79 selected tests across 13 fresh current-class reports, zero failures/errors/skips. This includes restored pre-mutation material validation for mutable and packed invalid fractional model cells. Canonical edit traversal/construction remains; the counter avoids retaining the edit list. No allocation-free or measured owner-latency claim is made.

Playtest: the fresh final-source `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 and passed. Production Main/GLFW, inherited display/authentication, real X11 input and isolated synthetic offline profile. Capture/anchor/naming/browse, long-path import/export, all four rotations, conflict and empty-space policy, placement/undo/export/import cancellation, malformed import atomicity, one-operation undo/retry, repeated placement, 32-resolution dependencies/restart and inventory/editor regressions passed. Browser playtesting does not apply to this native desktop workflow. All 228 production/harness source hashes match. The earlier interrupted run is not counted.

Fresh F10 video: 26 fully decoded frames, 48.108 s, 533,057 bytes, 960×544; SHA-256 `6e9843d4ea21f362f12686c85f2be8aa6ac6801a8f50d98867be15e445596ee6`. Midpoint and new undo-cancellation screenshot inspected. Renderer llvmpipe (LLVM 15.0.6, 256 bits); HUD 1 FPS is functional evidence only. Report `structure-publication-images.json` and fresh media await controller publication/exact-head hosted checks. `git diff --check` passed and there are no tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`; no unrelated deletions intended. No full-suite rerun or full-fixture performance pass is claimed. City/maximum admission, disk-backed live region/root implementation and physical-GPU/32-client/frame/tick/network/durability qualification remain pending. All rollout gates remain in force.


## Lower-memory cancellable spatial ordering

Controller-delivered scoped hosted checks passed for `c7497c6bd55905d9bbdd728caa00c13b43f6226c`; publication skipped. Direct PR inspection showed seven successful builds and one feature Mac Intel build still running at that moment. No QA approval or readiness follows from either observation.

Production sorting now keeps two integer index buffers plus an integer key buffer in first-pass order, returning the final index buffer directly. This removes the former two packed-long buffers and separate result allocation. At 4,000,000 cells, sort-array payload decreases from 80,065,536 to 48,065,536 bytes, excluding headers and caller input. Stable duplicate order, input immutability, key/count rejection and cancellation remain. Package formats, builder duplicate rejection and all admission limits remain unchanged.

A first two-index-only draft showed 1,063.116 ms p95 at 4 million keys and was replaced before publication. Its native run was stopped with exit 130, not counted as passed. The retained key buffer avoids scattered source reads in the second pass. Twenty component runs, after two warmups, measured prior/current median/p95/worst of 74.852/87.814/89.253 versus 63.700/78.315/80.258 ms at 1 million keys; 319.260/382.178/503.763 versus 369.961/443.111/547.835 ms at 4 million keys. The final version reduces sort arrays by 40% with some measured maximum-size sort slowdown. Shared VPS, sequential processes and overlapping local checks limit the comparison. Complete stable order matches a packed-key/index reference for both sizes. No populated-fixture, peak-memory or workflow-budget claim follows.

Final targeted checks passed all 73 tests in 13 fresh current-class reports, zero failures/errors/skips. Includes stable order over every key bit, duplicates, prefix isolation, independent results, cancellation through both passes and final completion, interrupted workers, capture/import/export, packed geometry, occupancy, rotations, preview and TLS multiplayer. The first selected command also named nonexistent StructurePackageIoTest; no test from that name was executed or counted. The final combined command uses only actual current test classes. Full regression and a new native workflow are running. Geometry/instance/chunk/history caps and full city/maximum, live region/root, physical GPU, shaped 32-client and all performance/durability gates remain pending. Stable rollout stays gated.


Final full Java regression exited 0: all 198 current default-Surefire source suites have fresh reports; 1,002 tests, 1,001 passed, one skipped, zero failures/errors. `dev.jayms.assets.AssetManagerIntegrationTest.browserWorkflow` requires opt-in `asset.browser=true` and was not executed. This is a completed full run, unlike the historical interrupted runs above. Exact targeted/full commands are in `dashboard/evidence/structure-sort-memory.json`.

Playtest: final-source `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 in production Main/GLFW with real X11 input, inherited display/authentication and an isolated synthetic offline profile. Capture/anchor/naming/browse, long-path import/export, all four rotations, footprint/conflicts, empty-space preservation, occupied/player collision rejection, placement/undo/export/import cancellation, malformed import atomicity, one-operation undo/retry, repeated placement, 32-resolution dependencies/restart and inventory/editor regressions passed. All 228 source hashes match; no synthetic-profile temporary files remain. Browser playtesting does not apply to this native desktop workflow.

All 25 F10 video frames decoded. Fresh clip: 47.371 seconds, 474,708 bytes, 960×544, SHA-256 `555203631239008a9594aabee5397c4775b0c0e52e4aad59b66d882116bcd4fe`. Midpoint green footprint/anchor and undo-cancelled screenshot inspected. llvmpipe (LLVM 15.0.6, 256 bits), HUD 1 FPS: functional evidence only. The earlier interrupted draft run is excluded. New sanitized report/media await controller publication and exact-source hosted validation. No tracked deletions against review base; no intended unrelated deletions. Named restored MP3 tests/fixtures and Hub workflows match the review base. Full city/maximum admission, live disk-backed regions/roots and physical GPU/shaped 32-client/frame/tick/traffic/memory/stall/durability qualification remain unfinished. No supported city limits or readiness claimed; rollout stays gated.


## Worker-prepared offline cell replacements

Controller-delivered scoped hosted checks passed for `0ce4f0938864ceb45884b2821c844fe0b66087d5`; publication skipped. This is not QA approval or readiness.

Production `World.prepareCellReplacement` now creates a detached immutable replay state, exact replay count, cell key and canonical frozen geometry without touching a world. Offline structure history staging builds these replacements on its worker and uses the same detached replay state for saved history. Publication consumes that exact staged list only after the existing map identity/history revision/model/player/conflict checks. Direct legacy cell replacement delegates through synchronous preparation; legacy structure publication and rollback retain it. Invalid dimensions, positions and fractional materials reject before installation. Redundant source branches retain their original replay records/counts even when installed geometry folds them.

All 66 targeted tests passed in 11 fresh current-class reports, zero failures/errors/skips. Tests exercise real worker preparation of packed fractional LED/glass geometry, caller mutation and frozen result rejection, reusable isolated replacements, resident install/unload/reload, redundant branch replay/history/revision equality, invalid material rejection without freezing callers, actual menu worker undo/cancellation, TLS multiplayer, save publication failure and a new synthetic failure on the second prepared cell install. That failure restores world/history/file state, leaves no undo record, releases busy and allows a successful retry/restart/undo. Existing staged history tests verify owner installation bypasses the synchronous preparation entry point. Full Java regression and a fresh production Main/GLFW playtest are running.

This moves validation/canonicalization earlier but retains more prepared geometry during staging. Owner publication still installs cells, walks replay history, updates resident geometry/metadata and may perform fallback checks, rollback or legacy save work. No measured 100 ms stall, memory or complete city/maximum budget claim is made. Package/world formats and geometry/instance/chunk/history limits remain unchanged. Live disk-backed regions/roots, full fixture admission and physical-GPU/shaped 32-client/frame/tick/traffic/memory/stall/durability qualification remain pending; rollout gated.


Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 on final production source with Main/GLFW, real X11 input, inherited display/authentication and an isolated synthetic offline profile. Capture/anchor/naming/browse, long-path import/export, four rotations, footprint/conflicts, empty-space preservation, occupied/player collision rejection, placement/undo/export/import cancellation, malformed import atomicity, one-operation undo/retry, repeated placement, 32-resolution dependencies/restart and inventory/editor regressions passed. All 228 source hashes match; no synthetic-profile .tmp files remain. Browser playtesting does not apply to this desktop workflow.

All 24 F10 frames decoded; midpoint green footprint/anchor and cancelled-undo screenshot inspected. Clip44.425s,446636bytes,960×544,SHA-256 `488bd051b46b8561f8e0c32afe20ddf12f723ccb115847bc9c753c1b495574ad`. Renderer llvmpipe (LLVM15.0.6,256bits),HUD1FPS: functional evidence only. New report/media await controller publication and exact-source hosted checks. Full regression remains running; no full-suite pass claimed yet. Qualification and rollout gates remain unchanged.


Final full Java regression exited 0: all 200 current default-Surefire source suites have fresh reports; 1,006 tests, 1,005 passed, one skipped, zero failures/errors. The optional `dev.jayms.assets.AssetManagerIntegrationTest.browserWorkflow` requires `asset.browser=true` and was not executed. Exact commands, environment, expected/observed workflow results and remaining limits are in `dashboard/evidence/structure-cell-prepare.json`. No production source changed during or after native validation. Final diff check passed; no tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4` and no unrelated deletions intended. Named restored MP3 tests/fixtures and Hub workflows still match that base. New edits/media require controller publication and hosted exact-source checks. Full city/maximum admission, live disk-backed regions/roots and physical-GPU/shaped 32-client qualification remain unfinished; stable rollout gated.


## Admitted prepared-cell component measurement

All eight feature/PR platform builds for `afc6dc64a4bfeacf09fbf47667a3d0464618fec8` succeeded; both publication jobs skipped. No production Java or native harness source changed in this continuation. All 228 hashes still match. Exact-head published video was fetched and matched 446,636 bytes, SHA-256 `488bd051b46b8561f8e0c32afe20ddf12f723ccb115847bc9c753c1b495574ad`. Prior completed full Java/native results remain applicable to that unchanged source; no new Maven or Main/GLFW run claimed.

Added a fixed, bounded CLI benchmark of current production direct and prepared cell APIs. Both 64×1×64/4,096-cell fixtures pass StructurePackage admission: uniform 4,096 leaves, fractional 131,072 leaves, no models/instances. The fractional source repeats 32 spaced unit leaves alternating LED 0x123456/glass. Fresh nonresident worlds contain one unrelated brick edit. Three warmups, 20 measured runs per path/fixture; path order alternates. Actual worker threads prepare cells. Every run verifies all geometry/colours/materials, exact history map contents, exact per-chunk replay order, revisions and unrelated-edit preservation. Invalid fractional models reject. Setup/verification, worker start/queue/transfer, conflict checks, resident meshes, save/network and full placement are excluded from timings.

Playtest: actual CLI and its own Swing evidence window completed with exit0. Screenshot captured from that child on inherited assigned display/authentication and inspected. Browser/native game rerun does not apply to this benchmark-only change. The first warmup attempt failed because its oracle incorrectly assumed global World history order. World groups history through unordered chunks. The final oracle checks exact map contents and exact order within every chunk; the failed attempt produced no accepted measurements. All 57 tooling tests passed in 119.830 s. All 120 measured phase samples and every reported median/nearest-rank p95/worst were independently checked.

Fractional direct owner median/p95/worst 162.945373/186.831847/193.220087 ms; worker preparation 91.544849/115.997464/127.934529 ms; prepared owner 81.536372/106.842094/110.901081 ms. Per-run worker+owner phase sum median/p95/worst 179.668086/199.660728/209.749606 ms. The owner component still exceeds 100 ms without mesh/save/conflict/network work. Fractional median owner allocation 43,114,376 versus direct 69,486,856 bytes; combined 69,503,312 bytes. Allocation was shifted, not eliminated. Uniform results/raw samples and all commands/environment are in `structure-prepared-cell-benchmark.json`.

Whole Java probe peak RSS 591,528 KiB with `-Xmx512m`, including fixture setup, warmups, verification and Swing; not retained or per-phase peak memory. Intel Core Processor (Haswell, no TSX),Debian 12 / Linux 6.1.0-50-cloud-amd64 / x86_64 / glibc 2.36, Java 25.0.3+9-LTS-195. No driver/GPU performance measured. Shared VPS, host activity uncontrolled; tooling tests finished before timings. New report/image/tool require controller publication and source checks. No tracked deletions against review base; no intended unrelated deletions. These measurements reinforce the need for bulk live region/root/history publication, not a cap increase. Full city/maximum, physical GPU, shaped 32-client and all remaining budgets stay pending; stable rollout gated.


## Worker-prepared replay records and shared COW indexes

Controller-delivered hosted checks for `de6cd3928595c7ad97a4705179032bdead4983ac` passed all four platform builds; publication skipped. This is not readiness.

`WorldVoxels.CellReplay` now prepares immutable edit records, key strings and a private cell index from the existing canonical visitor. It retains no geometry. Its index uses a private owner token that can never match a history owner. Indexed histories reuse its records/keys and index; subsequent edits fork the index through the existing `writable` check. Each history still owns its ordered-map entries. Clear/full-cell writes to the same key preserve raw revision counts and final order. Generic legacy maps retain their existing per-edit path. World cell preparation validates through this visitor and derives its record count. Offline staged global history and owner chunk history reuse the same replay data. No wire/world format, admission cap, overlap rule or authoritative validation changed.

All 99 targeted tests passed in 17 fresh current-class reports, zero failures/errors/skips. Coverage includes all cell kinds (empty, uniform, model, fractional, packed and redundant branches), exact indexed/legacy order and raw revisions, mutable source isolation, reuse across two histories and COW snapshots, same-cell leaf removal and overwrite, cursor copying, invalid material rejection, partial install rollback/retry, worker undo/cancellation, restart and TLS multiplayer. Full Java regression and fresh Main/GLFW workflow are running.

Playtest: the unchanged admitted component benchmark completed 20 runs per path/fixture after three warmups with alternating path order. All geometry/material/colour, exact map contents, per-chunk replay order, revision and unrelated-edit checks passed; invalid fractional models reject. The actual child exited 0; its own Swing window was captured on the inherited assigned display/authentication and inspected. All 120 measured phase samples and derived statistics were independently checked. Browser playtesting does not apply to this component CLI; the separate native workflow is running.

For fractional cells, prepared owner median/p95/worst is 17.959288/67.130305/74.876080 ms versus the prior 81.536372/106.842094/110.901081 ms. Median owner allocation is 9,192,264 versus 43,114,376 bytes. Worker preparation is 141.230388/170.318103/181.610022 ms. Per-run worker+owner sum is 167.6186295/194.513675/197.024598 ms versus prior 179.668086/199.660728/209.749606 ms. Combined median fractional allocation is 67,608,528 versus 69,503,312 bytes. Uniform combined allocation increases from 3,427,344 to 4,383,376 bytes; preparation retains extra arrays/index data earlier. Whole-probe peak RSS is 593,980 KiB with -Xmx512m, including fixtures/warmups/verification/Swing, not retained or per-phase memory.

The same helper source measured prior/current production in separate processes at separate times on the shared VPS; versions were not interleaved. No other own check ran during timings. Queue/transfer, setup/verification, conflicts, resident meshes, saves/network and complete placement are excluded. This component improvement does not qualify a 100 ms main-thread stall, frame/tick budget, full city/maximum fixture or supported limit. Per-record map insertion and per-cell root/metadata/resident work remain on owner. Live disk-backed region/root integration and all full-fixture, physical-GPU/shaped 32-client and durability qualification stay pending; rollout gated. New report/image in `structure-prepared-replay.json` and `structure-prepared-replay-benchmark.png` await controller publication.


Playtest: final-source `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 in production Main/GLFW, with real X11 input, inherited display/authentication and an isolated synthetic offline profile. Capture/anchor/naming/browse, long-path import/export, all four rotations, footprint/conflicts, empty-space preservation, occupied/player collision rejection, placement/undo/export/import cancellation, malformed import atomicity, single-operation undo/retry, repeated placement, 32-resolution dependencies/restart and inventory/editor regressions passed. All 228 native source hashes match, including all 227 production hashes from benchmark time. No synthetic-profile temporary files remain. Browser playtesting does not apply to this native desktop workflow.

Fresh F10 clip: all 25 frames decoded; 45.125 seconds, 539,651 bytes, 960×544, SHA-256 `2d71b8b1b90bb248146bbdce12929709af7d4d8c0d4d51146e45b1ca76f08ff3`. Green footprint/anchor midpoint and cancelled-undo screenshot inspected. Renderer llvmpipe (LLVM15.0.6,256bits), HUD1FPS; functional evidence only. Full Java regression remains running. New report/media await controller publication and exact-source hosted validation; no full fixture or physical qualification pass claimed.


Final full Java regression exited 0: all 201 current default-Surefire source suites have fresh reports; 1,009 tests, 1,008 passed, one skipped, zero failures/errors. The optional `dev.jayms.assets.AssetManagerIntegrationTest.browserWorkflow` requires `asset.browser=true` and was not executed. Commands, environment, expected/observed workflow results, all raw component samples and qualification limits are in `dashboard/evidence/structure-prepared-replay.json`. Cell validation/preparation is inside measured phases; package admission and fixture/world setup/verification are outside. No production/harness source changed during or after validation. Final diff check passed; no tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`, no unrelated deletions intended. Restored MP3 tests/fixtures and Hub workflows still match that base. New edits/media await controller publication and exact-source hosted checks. Live region/root integration, full city/maximum admission, physical GPU/shaped 32-client and all remaining qualification stay pending; rollout gated.

## Bounded disk-region cache integration

All eight hosted platform builds for `096aa171ed270b40fb818ec5d5d6c36d79bb3325` passed; publication jobs skipped. This does not establish readiness.

Added a private single-reader LRU over the disk package index, bounded by page count and encoded bytes. Complete CRC, order, material and cancellation checks precede page publication. Failed reads preserve old pages. Frozen geometry survives eviction. Synthetic integration uses actual detached `WorldVoxels.CellPatch` and candidate queries, dependency remapping, source holes, cancellation and concurrent edit rejection. Fourteen rejection cases pass. All 58 tooling tests passed in 128.054 seconds before four added budget-edge cases; the final selected integration rerun passed in 4.208 seconds.

Playtest: actual synthetic CLI and evidence window exited 0. Its own window was captured on inherited display/authentication and inspected. Browser playtesting does not apply. All 228 production/harness hashes remain unchanged from the completed native workflow. No new Maven or native game run is claimed.

One full read-only traversal per fixture matched every cell. City: 978,288 cells / 2,173 regions; index 7.219240 seconds, comparison 1.487032 seconds; peak two pages / 43,140 encoded bytes; whole-process RSS 180,512 KiB. Maximum: 4,000,000 cells / 8,192 regions; index 14.918111 seconds, comparison 41.961276 seconds; peak two pages / 76,800 encoded bytes; RSS 191,052 KiB. Both used `-Xmx256m`; Linux `os.wait4` measured RSS. Shared VPS activity is uncontrolled. The maximum run overlapped the small synthetic evidence child. These are single diagnostic runs, not 20-run qualification, heap bounds or game budget passes.

Production still rejects full fixtures; caps remain unchanged. The live region/root backend, asynchronous game reads, bulk atomic publication/undo/save and renderer/server integration remain unfinished. Physical-GPU/shaped 32-client checks and all full-game budgets remain pending. Rollout stays gated. Details and next integration invariants: `docs/structure-region-cache.md`. Sanitized report/image: `dashboard/evidence/structure-region-cache.json` and `.png`, pending controller publication and exact-source checks. No tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`; restored MP3/Hub files match the base. No unrelated deletions are intended.

## Bounded asynchronous region reads

All eight feature/PR platform builds for `286983b371915aa5165b212e67887d8dc4856449` passed; publication jobs skipped. Added private `AsyncRegionReadPrototype` around the existing disk cache: one storage thread, at most 1,024 positions per request, 1–128 queued requests, explicit pending versus known-absent results, immutable all-or-nothing batches, cooperative cancellation, pending-view generation fences and observable asynchronous cleanup. Source open/read/close happen on the storage thread. No game source, admission cap or save format changed. A completed old result still requires an owner-side current world/root check before any future live publication.

Deterministic tests block workers with latches and exercise queue overflow, running/queued cancellation, stale views, partial batches, startup retry, cleanup failure, immutable results and real disk-cache deletion. Independent inspection found a reentrant cancellation-callback edge: invalidation now clears old queued work before firing callbacks, preserving a callback's fresh request. The final CLI passes 11 rejection cases, four exact disk cells and one known-absent cell. All 59 tooling tests passed in 137.284 seconds; the suite began before final callback hardening. Both affected integration tests then passed on final source in 15.055 seconds.

Playtest: `AsyncRegionReadCheck --evidence` exited 0. Its own Swing result window was captured on inherited display/authentication and inspected. Browser/native game reruns do not apply to this tooling-only change; all 228 production/harness hashes remain identical to the completed native playtest. No new Maven/native pass claimed. Sanitized commands/results and image: `dashboard/evidence/structure-async-region.json` and `.png`; detailed contracts and next live integration: `docs/structure-async-region.md`. New edits/media require controller publication and exact-source checks.

The live world/root backend, explicit loaded/pending/failed game states, atomic root/undo/save publication, renderer/physics/server integration and full city/maximum admission remain unfinished. Physical GPU, shaped 32-client and all required 20-run game budgets remain pending; stable rollout stays gated. No tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`. Restored MP3/Hub files still match the base; no unrelated deletions are intended.

## Production prepared-cell neighbor batching

All eight hosted platform builds for `277012cb77768e7cbc97c16cf80f4cfb7518ae74` passed; publication jobs skipped. Production offline prepared-cell publication now calls `World.replaceCells`. This preserves virtual per-cell order and existing geometry/history/resident updates while coalescing neighbor mesh invalidation by changed source chunk. Nested calls use the outer group. The outer `finally` restores normal notification state and flushes changed chunks after success or partial failure. The helper is not a transaction; existing authoritative validation, rollback and save publication remain caller-owned.

All 121 targeted tests passed in 20 fresh suites, zero failures/errors/skips. New tests compare fractional/air/multi-chunk geometry, replay order/revisions and unload/reload against individual installs; nine source-cell edits notify one loaded neighbor once rather than nine times. Tests also cover nested virtual dispatch, post-install failure, null input and subsequent ordinary edits. Existing partial placement rollback/retry, staged save/undo/cancellation, TLS multiplayer, sky visibility and streaming checks pass. An initial test compilation failed because an inherited overload hid the test helper; the helper is now qualified. That failed compile is not a test pass.

Full Java regression and a fresh Main/GLFW workflow are running on frozen production source. Their final results are pending. No 100 ms stall or wall-clock improvement is claimed. Geometry/instance/chunk/history caps remain unchanged. Live region/root, renderer/server/persistence integration, full fixture admission and all physical-GPU/shaped 32-client/20-run qualification remain unfinished; rollout stays gated. No tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`. Restored MP3/Hub files still match the base. No unrelated deletions are intended.

Final production neighbor-batch validation completed. Full Java regression exited 0: all 202 current default-Surefire source suites have fresh reports; 1,013 tests, 1,012 passed, one skipped, zero failures/errors. The optional `dev.jayms.assets.AssetManagerIntegrationTest.browserWorkflow` requires `asset.browser=true` and was not executed. Exact commands, environment, expected/observed steps and limits are saved in `dashboard/evidence/structure-cell-batch.json`.

Playtest: fresh `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 on frozen final production source. Actual Main/GLFW/X11 input with an isolated synthetic offline profile exercised capture/anchor/naming/browse, long-path export/import, four rotations, footprint/conflicts, source-hole preservation, occupied/player overlap rejection, placement/undo/export/import cancellation, malformed import atomicity, one-operation undo/retry, repeated placement, 32-resolution dependencies/restart and inventory/editor regression. All 228 production/harness hashes match; no synthetic-profile `.tmp` files remain. Browser playtesting does not apply to this desktop workflow.

All 24 F10 frames decoded. Clip: 44.169 seconds, 463,516 bytes, 960×544, SHA-256 `b9df50376f2ee8f36a548095f6a4bb0cf991ad543e4f0456c0bd4b8b042b5ccb`. Green footprint/anchor midpoint and cancelled-undo screenshot inspected. Renderer llvmpipe (LLVM 15.0.6, 256 bits), HUD 1 FPS; functional evidence only. Refreshed video/screenshots and new report/media require controller publication and exact-source hosted checks. Final diff check passed; no tracked deletions against review base and no unrelated deletions intended. All full-fixture, physical-GPU/shaped 32-client and remaining qualification gates stay pending; stable rollout gated.

## Neighbor batching component measurement

All eight hosted feature/PR platform builds for `5eeffa721c9bb1dd18bb1a5fe1c0ea7b2fbe43a7` passed; publication jobs skipped. Production/native harness source remains unchanged; all 228 hashes match the completed native workflow. Added `NeighborBatchBenchmark` to compare current individual and batched prepared installs using the same immutable list, with three warmups and 20 measured runs per path/fixture, rotating three installation paths through execution order. Both 4,096-cell / 64×1×64 fixtures pass package admission: uniform 4,096 leaves and fractional 131,072 leaves. Four empty neighbor chunks are loaded. Every run verifies exact geometry/material/colour, map contents, per-chunk replay order, revisions, unrelated edits and notifications. All 160 timing and 160 allocation samples/statistics were independently checked.

Notifications drop from 1,024 to 4; median owner allocation drops by 186,680 bytes for each fixture. Uniform individual median/p95/worst is 2.767346/5.244825/5.441996 ms versus batch 3.292908/7.119381/12.929391 ms. Fractional individual is 21.173334/33.308374/46.627648 ms versus batch 19.665899/64.387612/75.172040 ms. Timing does not improve consistently; fractional p95 worsens. No speedup, 100 ms stall or supported-limit claim follows. Shared host activity is uncontrolled; no other own check ran during timings. Whole Java child RSS 608,052 KiB with -Xmx512m includes fixtures/warmups/verification/Swing, not retained/per-phase memory. Raw preparation/direct-path samples and exclusions are in the sanitized report.

Playtest: actual benchmark CLI exited 0; its own Swing result window was captured using inherited display/authentication and inspected. Two selected async/cache integration checks passed in 8.681 seconds; their common source list now compiles the new benchmark for CI. Browser/native game reruns do not apply to this tooling-only change. Report/image: `dashboard/evidence/structure-neighbor-benchmark.json` and `.png`, pending controller publication and exact-source checks. No tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`; restored MP3/Hub files match it. No unrelated deletions intended.

Live region/root/history publication, asynchronous game read states and full renderer/server/persistence integration remain the next implementation work. Full city/maximum admission and required physical-GPU/shaped 32-client/20-run game qualification remain pending. Current caps stay unchanged and stable rollout stays gated.

## Owner-side async read view

All eight hosted builds for `01abb0ecf9e5c06c8138dc1c44da3c38d220b2c3` passed; publication jobs skipped. Added a private owner-side read fence with distinct pending/ready/failed states. It revalidates completed reads against world identity, an explicit root publication epoch, model-library identity/revision and affected destination chunk stamps. Unrelated edits remain valid; stale/failed views never revive. Wrong-owner, cancelled/failed, wrong-count and wrong-position results reject. Ready data is not placement authorization. The caller must advance the epoch on every root publication/ABA, revalidate before mutation, hold its owner lock and bound retained views. Live game wiring is not implemented by this helper.

Playtest: actual CLI and real async disk path passed 12 rejection cases, exact cell comparison, post-completion affected/unrelated edit checks and storage-thread cache cleanup. Three affected tooling integration checks passed in 14.183 seconds. Its own Swing result window was captured on inherited display/authentication and inspected. Initial empty-world clear did not change an absent chunk stamp; the corrected fixture populates the affected chunk before capture and verifies real removal. No fence or qualification gate was weakened. All 228 production/harness hashes remain unchanged; no new Maven/native run claimed. Browser playtesting does not apply to CLI-only changes.

Contracts/commands/results: `docs/structure-read-view.md`, `dashboard/evidence/structure-read-view.json` and `.png`, pending controller publication and exact-source checks. No tracked deletions against review base; restored MP3/Hub files match it; no unrelated deletions intended. Live world/root/history, renderer/physics/server/persistence integration, full city/maximum admission and all physical-GPU/shaped 32-client/20-run game qualification remain unfinished; stable rollout gated.

## Production immutable cell images

Hosted platform checks for parent `ce13cf4fdbe94c97504ef8a09669f9589634f410` passed; publication skipped. Prepared offline publication now uses a private-constructor `WorldVoxels.CellImage`. Worker preparation copies/freezes its wrapper; owner installation and edited-cell metadata reuse that immutable image. Ordinary tree-taking replacement keeps its existing copy/freeze API. Later world edits fork the persistent graph. The image factory validates storage resolution only; existing World replay/material preflight and authority/resource checks remain required and unchanged.

All 125 targeted tests passed in 21 fresh current suites, zero failures/errors/skips. Four new tests cover detached worker preparation, cross-world/cell reuse, frozen/caller/snapshot isolation, later edits/copies/clear, byte-identical air/uniform/model/fractional/packed/redundant trees, legacy API/null/invalid-size behavior, shared metadata and resident unload/reload. Existing batch/replay/save/undo/rollback/retry/TLS/sky/streaming checks pass. The unchanged admitted component helper completed its 20-run paths on frozen source; all 160 timing and 160 allocation samples/statistics were independently checked. Detailed final report follows after native/full regression.

Fresh Main/GLFW workflow and full Java regression are running; their final results remain pending. No retained-memory, RSS or speedup qualification is claimed. Caps, formats and policies stay unchanged. Full city/maximum admission, live region/root/history/backend work, physical GPU/shaped 32-client and all required 20-run game qualification remain unfinished; stable rollout gated.

Validated immutable-cell-image source: all 125 targeted tests passed; the full Java run completed with exit 0, 1,017 tests across 203 fresh current suites, zero failures/errors and one optional `AssetManagerIntegrationTest.browserWorkflow` skip. Commands and raw component measurements are recorded in `dashboard/evidence/structure-cell-image.json`.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed in production Main/GLFW on inherited X11, llvmpipe, and an isolated synthetic profile. It exercised named capture, browsing, self-contained export/import, all four rotations, footprint/conflicts, preserved empty space, player/terrain rejection, placement/undo, cancellation, malformed import atomicity, repeated placement, dependency restoration, restart, inventory and model editor regressions. All 228 production/harness hashes match the benchmark, native run and current source. No temporary profile files remain. The fresh F10 video fully decoded: 23 frames, 41.469 seconds, 466,739 bytes, 960×544, SHA-256 `6d4016df78a1babe89e8541d6bd67439226fd4479dca02385388d97f901e305b`. Midpoint and cancelled-undo views were inspected. The 1 FPS software-rendered run is functional evidence only.

The unchanged component benchmark completed three warmups and 20 runs per path/fixture. Prepared owner allocation fell by 261,888 bytes per path/fixture versus the prior separate run; worker allocation increased by 65,536 bytes for uniform and 163,840 bytes for fractional geometry. Whole child peak RSS rose from 608,052 to 625,844 KiB. Fractional batched owner median/p95/worst were 19.352275/32.357289/55.994688 ms; uniform values were 2.644744/4.372926/7.044521 ms. These separate shared-VPS cohorts establish neither a speedup nor lower retained/peak memory. Full placement, changed-cell resident geometry, meshes/GPU, persistence and networking are excluded. Both full fixtures still exceed production admission caps; full city/maximum, physical GPU, shaped 32-client and required 20-run game qualification remain pending. Stable rollout stays gated.

`git diff --check` passed. No tracked deletions exist against review base `71612f09bfec4549456b3317676e76cdced085a4`; restored MP3 tests/fixtures and Hub workflows match it. No unrelated deletions are intended. Source, sanitized reports and fresh media await controller publication and exact-source hosted checks. Continue live region/root/history/backend work after those checks; do not mark ready from this scoped result.

## Private persistent chunk directory

Hosted parent `eb34fb67669d6bd3dbda06ae56bdda2adae673ad`: all eight platform builds passed; publication skipped. A private immutable AVL directory now tests root sharing, bounded update paths and exact coordinate keys. It passed 25,000 oracle-checked mixed operations with eight retained roots, sorted insertion/deletion, collision/extreme-key checks, worker forks and actual frozen world-image independence. Eleven rejection cases and three affected tooling integrations passed (14.837 seconds).

Playtest: the Java CLI completed all checks and three warmups plus 20 runs per component. Its painted Swing result was captured on inherited display/authentication and inspected; an initial pre-paint blank capture was replaced. All 300 raw timing samples across three diagnostic cohorts were independently checked. The painted cohort had 8,192 entries and height 14; 65,536 tree reads had p95 15.133934 ms versus hash reads 5.164133 ms. Whole child RSS was 162,340 KiB with `-Xmx256m`, including checks and UI. The first UI cohort overlapped integration checks; all cohorts are retained. No performance or memory qualification is claimed.

Keep production HashMap: AVL queries add too much cost. No live source changed; all 228 production/harness hashes match the completed native workflow, and the exact-parent published video download matches its local SHA-256. No new Maven/native pass claimed. Browser playtesting does not apply to this CLI-only change. New tool sources enter the common hosted compile list. Details and next backend needs are in `docs/structure-directory.md` and `dashboard/evidence/structure-directory.json`; new image/report/tooling await controller publication and exact-source checks. No unrelated deletions intended. Full city/maximum admission, live backend and physical GPU/shaped 32-client/20-run full game qualification remain unfinished; rollout gated.

## Private shared hash directory

Continued after controller-confirmed platform success for `cd45357c23f726463bb7054c9fe8f2e2bd19e373`; no further unchanged CI wait requested. Added a private 64-shard linked hash directory experiment. Snapshots copy 64 references and rotate ownership; first writes copy affected shards. Global insertion order is not preserved, values need separate immutable/COW protection, and adversarial shard sizes remain unbounded except by the private total entry budget.

It passed 25,000 oracle operations with eight retained roots, both-side writes, stable iterators/clear, collisions/extreme keys, a worker fork and actual frozen world-image independence. Eleven rejections and a deliberate 256-key single-shard copy test passed. Three integrations passed in 15.962 seconds; final affected integration passed in 5.388 seconds after stronger extreme-key checks. A mutable-caller fixture's invalid colour was corrected to an encoded material; no gate weakened.

Playtest: final Java CLI completed all checks and three warmups/20 measured runs per component. Its own painted result window was captured on inherited display/authentication and inspected. All 120 raw timing samples/statistics were independently checked. At 8,192 entries, shared snapshot p95 was 0.007153 ms versus linked snapshot 0.372760 ms, but shared updates p95 2.602306 ms versus linked 0.052788 ms. The 256 distributed replacement calls copied all 8,192 entries; largest original shard 183 entries. Whole child RSS was 163,940 KiB with `-Xmx256m`, including checks and UI. No game or retained-memory qualification claimed.

Keep the live LinkedHashMap. This does not bound placement commit; future backend work needs bounded publication without moving full-map copying into first writes, plus a cheap live query path and explicit region read states. All 228 production/harness hashes remain unchanged; no new Maven/native pass claimed. Browser playtesting does not apply to CLI-only tooling. See `docs/structure-shared-directory.md` and `dashboard/evidence/structure-shared-directory.json`/`.png`; new sources join common hosted compilation and await controller publication/exact-source checks. Full city/maximum admission, live backend and physical GPU/shaped 32-client/20-run full game qualification remain unfinished. Stable rollout gated.

## Live immutable chunk roots — validation running

Added `ChunkDirectory` and wired it into `WorldVoxels`. Root snapshots now share immutable bitmap trie nodes instead of copying the whole linked map. Existing bucket ownership tokens still isolate mutable cell buckets. Updates copy at most seven trie branch arrays plus an AVL collision path. Full-coordinate sequential mixing avoids the mirrored-coordinate collision cluster found during measurement. Primitive coordinate lookup and a single immutable, sampled cache entry retain root/key validation; the miss counter affects hints only. Flat history order, formats, materials and all admission caps remain unchanged.

All 139 targeted tests passed in 23 fresh suites with no failures/errors/skips. Eight new tests cover old roots, forced collisions, signed/deep routes, collapse, budgets, grid distribution, worker transfer, four concurrent frozen-world readers and cache correctness through source edits/snapshots/clear. Four tooling integrations passed in 21.845 seconds. All 420 final timing samples and 320 allocation samples were independently checked. Actual 100,000-cell snapshot and 8,192-chunk lookup diagnostics compare the parent WorldVoxels class in an isolated classpath with current source; these are separate JVM cohorts, not full game qualification. Snapshot capture drops whole-map allocation; mixed random reads remain slower and allocate more. Earlier rejected cache measurements are retained honestly in the report.

The parent feature Mac Intel Maven job failed `ServerRegionSaveTest` despite the controller success summary. A bounded downloaded Maven-summary artifact identifies it. Reproduction with three actual ticks moved its unsupported drop from y=180.0 to 179.7 and changed bytes. The fixture now places a support block and waits for three real ticks at each restart; exact-byte assertions remain. Reproduction now stays y=180.0 and byte-exact; the affected server and directory tests pass. Production server physics stays intact. New hosted checks are required after publication; do not repeat an unchanged old CI wait.

The actual directory CLI result window was captured on inherited display/authentication and inspected. Full Java regression and a fresh Main/GLFW workflow are running on frozen source. All 229 benchmark/native/current source hashes match. Do not claim those two running checks passed. Report draft: `dashboard/evidence/structure-hash-trie.json`; detailed final notes follow after actual exit results and media inspection. No required city/maximum, physical GPU, shaped 32-client, frame/tick/traffic or 20-run full-game qualification pass is claimed. Full geometry/chunk/history admission and remaining region/history/durable-root/renderer/server work remain pending; stable rollout gated.

## Immutable roots — local validation complete

Full Java regression passed with exit 0: 1,025 tests, 204 fresh current suites, zero failures/errors and one optional `AssetManagerIntegrationTest.browserWorkflow` skip. All 31 region-backend integrations passed in 155.717 seconds. The focused 139-test run and corrected server persistence fixture also passed. Native Main/GLFW passed capture/browse/import/export, rotations/conflicts/holes, cancellation/atomicity, placement/undo, restart/dependency and inventory/editor regressions. All 229 benchmark/native/current source hashes match; no temporary profile files remain.

Playtest: fresh F10 video fully decoded and midpoint/cancelled-undo views inspected: 27 frames, 50.138 seconds, 488,581 bytes, 960×544, SHA-256 `6483bab7bfb6b4d7c47ec3e8d1e8de15af2ef0687887948056a214f684d3aad9`. The actual directory CLI image was captured and inspected too. Software 1 FPS llvmpipe is functional evidence only. Detailed contracts, actual commands, benchmark limits, cold-read regression and the parent Mac fixture-race correction are recorded in `docs/structure-hash-trie.md` and `dashboard/evidence/structure-hash-trie.json`.

New source and fresh media require controller publication/exact-source hosted validation; the old parent Mac failure must not be counted passed. No commit/push/merge performed locally. Continue remaining live region/history/durable-root/renderer/server integration after the new checks. Both full fixtures still exceed unchanged admission caps; physical GPU/shaped 32-client/20-run full game qualification remains pending. Stable rollout gated; do not mark ready from this scoped result.


## Repeated cold-read cache admission — serial native retry pending

Controller delivered four successful platform builds for parent `469209c890eaec620a10e7a17d463ef74a4514b8`; publication skipped. Continued locally without an unchanged CI wait. `WorldVoxels` now admits an immutable cache entry after repeated cold reads rather than every fourth miss. Full root/all-coordinate checks still decide every hit. Scalar hints hold no object and races change admission only. Root replacement/clear reset hints. Concurrency tests mix cold/repeated reads; a new regression checks all coordinate axes and absent buckets through edits/snapshots/clear.

Actual 8,192-chunk/65,536-query diagnostics completed three warmups and 20 runs for exact parent and current source in separate JVMs. All 80 timing and 80 allocation samples/statistics checked. Mixed median allocation fell from 524,224 to 416 bytes/batch; p95 was 6.533396/6.218205 ms. Coherent median allocation stayed 131,040 bytes; p95 was 1.991705/1.698109 ms. Shared-VPS component diagnostics do not qualify city/maximum, game performance or retained memory. General cold trie reads still cost more than the earlier linked-map baseline.

Full Java regression passed: 1,026 tests/204 fresh suites, zero failures/errors, one optional browser workflow skip. All 31 backend integrations passed in 272.416 seconds. First native attempt hit the unchanged 600-second guard under concurrent Java regression and is not passed. The isolated synthetic attempt is preserved inside ignored `target/structure-repeat-cache/native-timeout`. A serial retry runs with the same guard. Do not read private runtime logs or count pending media passed. See `docs/structure-repeat-cache.md` and `dashboard/evidence/structure-repeat-cache.json`. All admission caps and full city/maximum/physical-GPU/shaped 32-client/20-run qualification gates remain unchanged and pending.


Serial native retry passed with the unchanged 600-second guard. Playtest: production Main/GLFW on inherited X11 and isolated synthetic profile passed the full capture/import/export/rotations/conflicts/holes/atomicity/cancellation/place/undo/repeat/dependency/restart/inventory/editor workflow. All 229 benchmark/native/current hashes match; no temporary profile files remain. Fresh F10 clip fully decoded and midpoint/cancelled-undo views inspected: 24 frames, 42.244 seconds, 460,506 bytes, 960×544, SHA-256 `c6fd243b093abf463501ed63231f35a25a6b26ae8c76a8a5d86fd249edc3cf27`. Software 1 FPS llvmpipe is functional evidence only. First concurrent-run timeout remains recorded, not passed.

Final scoped result: 1,026 Java tests/204 fresh suites, zero failures/errors, one optional browser skip; 31 backend integrations passed. No unmerged entries or tracked deletions against review base; MP3/Hub source and fixtures still match base; diff check passed. No commit/push/merge performed locally. New source/report/media need controller publication and exact-source hosted checks. Continue remaining live region/history/durable-root/renderer/server integration afterward. Full city/maximum admission and physical-GPU/shaped 32-client/20-run game qualification remain unfinished; stable rollout gated.


## Persistent history roots — validation running

Exact parent `ecc598c6c4e02cf3a5a2acd419829221ac5b362a` read-only rollup shows all eight platform builds passed; both publication jobs skipped. No unchanged CI wait requested. History now shares immutable key/order roots, preserves replay insertion order and rotates existing cell-key COW ownership at copies. The key index uses the tested hash trie with exact-string AVL collision buckets; order uses an AVL ordinal tree. A full-key AVL candidate was rejected for read cost. Offline staging now captures roots once and revalidates once on the owner; server staging captures under its authority lock. Both final history identity/revision fences remain. The private repeated-batch server copier was intentionally replaced, not bypassed.

All 37 focused tests passed in nine fresh suites, zero failures/errors. Five new map tests cover oracle operations, retained roots, exact order, sorted edits, views/iterator contracts, worker transfer and 1,024 full string-hash collisions. Actual 100,000-record history diagnostics completed three warmups/20 runs: copy median allocation 10,097,384/168 bytes parent/current. Reads, iteration and first writes remain slower; do not claim general speedup. Final 160 timing/160 allocation samples checked, with 240/240 development samples retained separately. Whole child RSS 358,984/206,156 KiB is not game memory qualification.

Full Java regression and all 32 backend integrations are running. Native workflow will run alone afterward to avoid repeating the earlier concurrent-run timeout. No running check is marked passed. All 230 production/harness hashes are saved in `dashboard/evidence/structure-history-roots.json`; contracts/tradeoffs are in `docs/structure-history-roots.md`. Preserve all existing admission and qualification gates. Full city/maximum and remaining live backend work are unfinished; stable rollout gated.


All 32 backend checks passed in 208.015 seconds. The first full Java run completed 1,031 tests/205 suites with one `NoSuchMethodException`: `StructureSaveStagingTest` reflected on the intentionally replaced private `copyStructureHistory`. No full-suite pass claimed. Replaced it with actual production `stageCommit`: pause prefix staging outside the server monitor, make a concurrent edit, reject stale apply before disk/geometry publication, verify retained roots/outside edit, retry successfully and cancel with temporary-file cleanup. The repaired focused run passed. Synthetic initial geometry was then aligned with seeded history; full final-test-source rerun is active. Production source remains frozen and all 230 benchmark/current hashes still apply. Native workflow will run alone after regression.


Full retry passed with exit 0: 1,031 tests/205 fresh current suites, zero failures/errors, one optional `AssetManagerIntegrationTest.browserWorkflow` skip, no missing/stale source reports. This includes the repaired production-stage test with matching seeded geometry/history. The first full-run reflection error remains recorded separately. All 32 backend checks already passed; production source has not changed. Serial Main/GLFW workflow is now active on frozen source; no native pass claimed yet. Continue by inspecting its actual result, decoding/inspecting fresh media, checking all 230 source hashes and auditing deletions before controller publication. Full city/maximum and remaining live backend/qualification gates still apply.


## Persistent history roots — local validation complete

Full final-source retry passed: 1,031 tests/205 fresh current suites, zero failures/errors, one optional browser workflow skip. All 32 backend integrations passed in 208.015 seconds. First full-run private-method reflection error remains recorded; its replacement exercises actual production staging outside the monitor, concurrent edit, stale commit rejection, retry and cancellation cleanup. No production source changed after frozen benchmark/focused checks.

Playtest: serial production Main/GLFW workflow passed all capture/browse/import/export/rotations/conflicts/holes/rejection/atomicity/cancellation/place/undo/repeat/dependency/restart/inventory/editor checks on inherited X11 with isolated synthetic profile. All 230 benchmark/native/current source hashes match; no temporary profile files remain. Fresh F10 clip fully decoded and midpoint/cancelled-undo views inspected: 24 frames, 43.317 seconds, 416,629 bytes, 960×544, SHA-256 `ddb345451bad03b11bef0d42a829f35fa3170b643a2b1599a9813cf3d09f2d16`. Software 1 FPS llvmpipe is functional evidence only.

Report/contracts: `dashboard/evidence/structure-history-roots.json`, `docs/structure-history-roots.md`. No tracked deletions, unmerged entries or pending Git recovery; MP3/Hub restored files match review base, diff/new-file checks passed. Private server copier and offline batch-copy loop were intentionally replaced by root capture; no unrelated deletion intended. No local commit/push/merge performed. New source/report/media need controller publication and exact-source hosted checks. Continue live region/history/durable-root/renderer/server work afterward. Full city/maximum admission and physical-GPU/shaped 32-client/20-run full-game qualification remain unfinished. Stable rollout stays gated; do not mark ready from this scoped result.


## Bounded regional replay sink — native validation running

Controller scoped results passed all four platforms for exact parent
`56f92c0a8f4e77715c59f585027f3eea4e3b45a2`; publication skipped. No unchanged
CI wait requested. `RegionReplay.visit` now delivers fully validated immutable
sections of at most 1,024 edits to an IOException-capable staging sink. Both
existing version 10 loaders use the same decoder through the map adapter.
File order, recurring regions, explicit air, predicates, format and persistence
limits stay intact. Cancellation checks precede I/O, each record and delivery.
Earlier sink calls remain private prefixes; no whole-world atomicity claim.

All 16 targeted tests passed in three suites with zero failures/errors/skips:
`RegionReplayTest`, `ServerRegionSaveTest`, `StructureSaveStagingTest`. New tests
cover bounded immutable batches, exact order and tail position, pre-read and
mid-section cancellation, bad later checksum and sink failure. Native Main/GLFW
is running alone on inherited display and isolated synthetic profile. Do not
claim it or new media passed yet. Report: `dashboard/evidence/structure-replay-sink.json`.
The whole live history still materializes; this is a decoder integration seam,
not completed disk-backed loading. Full city/maximum admission, live backend,
physical GPU, shaped 32-client and 20-run qualification remain pending.


Bounded regional replay sink local validation complete. All 16 targeted tests
passed; serial Main/GLFW workflow passed with exit 0. Playtest covered named
capture/import/export, four rotations, conflicts and empty source cells,
rejection/atomicity/cancellation, placement/undo/repeat, dependency restoration,
restart, inventory and editor regressions. All 230 source/harness hashes match.
No temporary profile files remain. Fresh video fully decoded: 24 frames,
43.48 seconds, 515,309 bytes; SHA-256
`fc50add6affd87dff5e97232981d2e68a2a1b9a15edb41e7a4af99c00a1ff3e8`.
Midpoint and fresh conflict screenshot inspected. Software 1 FPS llvmpipe is
functional evidence only. Full current-source Java suite was not rerun for this
bounded refactor; new hosted checks are required. The old full-suite result
remains historical. No tracked deletions/unmerged entries; diff check passed;
restored MP3/Hub files byte-match review base. New source/report/media await
controller publication. Continue live backend integration afterward; full
city/maximum admission and qualification remain unfinished, stable rollout gated.


## Bounded replay spool — full regression running

Controller exact-head results passed Windows, Mac Intel, Linux and Mac ARM for
`a1df01ba319f8c7bea340a5b68382423d65a9f5b`; publication skipped. Continued without
an unchanged CI wait. Added worker-private `RegionReplaySpool` using the shared
validated decoder and section writer. It retains only path/count/size and
streams bounded sections to/from its own temporary file. It checks the sealed
length/count, per-section validation and exact EOF on every traversal. No game
caller, live authority, new world format or admission increase is introduced.
Caller must supply stable source generation, validate tail/dependencies and
retain final authority fences before complete publication. CRC does not prove
source identity; crash orphan recovery remains unimplemented.

All 20 focused tests passed in four suites: bounded immutable ordered replay,
source tail, malformed later sections, cancellation, byte budgets, callback
failure/retry, corruption/count/length changes, close/worker/reentrancy and
interrupt behavior. Full Java regression is running; no full-suite pass claimed.
Playtest: actual streamed CLI under `-Xmx64m` passed one run each at 100,000 and
2,000,000 records. One edit per section stresses header count: 4,400,004 and
88,000,004 bytes. Stage/read timings were 418.555802/174.846951 ms and
3,044.128613/1,645.847365 ms. Exact order/air/fractional colour/materials, unchanged
source SHA-256, unread tail and owned-file cleanup passed. Own Swing result
window captured using inherited display/authentication and inspected. These
single-run component results are not p95, retained/peak memory or game budgets.
Browser playtesting does not apply to the noninteractive staging API. No fresh
native Main/GLFW pass claimed for this source. Report/image:
`dashboard/evidence/structure-replay-spool.json` and `.png` with `-cli` suffix.
Continue by inspecting actual full regression exit/fresh reports and source
hashes, audit diff/deletions, then controller publication/exact-source checks.
All live backend and full city/maximum/physical-GPU/shaped-32-client/20-run
qualification gates remain unfinished; stable rollout stays gated.


Bounded replay spool full Java regression passed with exit 0: 1,037 tests in
206 fresh current suites, zero failures/errors, one optional
`AssetManagerIntegrationTest.browserWorkflow` skip. Every current test source
has a matching fresh suite report; no missing/stale reports. All 230 production
source hashes and CLI/test hashes match the scoped report. Diff and new-file
whitespace checks passed; no tracked deletions or unmerged entries; restored
MP3/Hub files remain byte-exact to review base. No local commit/push/merge.

Playtest for this noninteractive API is the executed streamed worker CLI and
captured/inspected result window, `structure-replay-spool-cli.png`. The existing
native workflow video remains historical to the parent implementation; no new
native Main/GLFW pass is claimed. There is no new game UI or live caller to this
spool. New code/report/image need controller publication/exact-source hosted
validation. Continue live region/history/durable-root startup and publication,
renderer/physics pending states and large multiplayer integration afterward.
Full city/maximum admission and all reference physical-GPU/shaped 32-client/
20-run performance qualification remain unfinished. Keep stable rollout gated.


## Responsive offline startup — full regression running

Controller exact-head results passed all four platforms for
`13eb72c88955ddb699bc4be6c1c040d8c745e16f`; publication skipped. No unchanged wait
requested. Inspection found ordinary startup could draw loading UI before
reading its save. Moved that read to one worker rather than adding a duplicate
disk-spool pass. Cancellable LocalGame overload preserves the old API, adds
bounded read checkpoints and replay-record counts. Main only assigns a finished
private load. Close cancels result publication and interrupts without joining
blocked I/O. Initial offline/network replay yields after 128 records or 2 ms,
retains order and accepts Escape. Scene-ready cleanup guard prevents startup
cancellation from saving world/vehicle state. Explicit asset/city startup writes
retain their own behavior. All formats and admission caps remain unchanged.

All 24 focused tests passed in five suites. Actual native Main/GLFW checks
passed real Escape during a 200,000-record read and a 5,000-record version-3
replay; source files stayed byte-exact and play was never entered. A normal
200-record startup reached play, verified each geometry edit and closed with
source bytes exact. Fresh read/replay loading images captured from own window
on inherited display/authentication and inspected: counts 0 and 1, Escape hint
visible. All 232 startup production/harness hashes match. Initial native pass
used version 8 for both cancellation cases; reran with version 3 for replay to
prove cancellation does not upgrade an older save. Both passed; initial private
run retained under ignored `target/structure-startup-initial`, never read logs.

Full Java regression is running. Run the complete structure workflow alone
after it completes, then decode/inspect fresh media, check source hashes and
audit full diff/deletions. Report draft:
`dashboard/evidence/structure-offline-startup.json`. Software llvmpipe functional
checks are not 30 FPS/100 ms or city/network qualification. Full live backend,
city/maximum admission and reference physical-GPU/shaped 32-client/20-run gates
remain unfinished. Stable rollout stays gated.


First full Java run passed: 1,041 tests/207 fresh suites, zero failures/errors,
one optional browser skip. Before final playtest, inspection found a forced draw
per 128 replay records would add excessive frame waits for cheap edits. Refined
scheduling: keep 128-record/2 ms event checkpoints, acknowledge once, then draw
only after 16,666,667 ns since the previous draw ended. Synthetic-clock tests
show 32,768 cheap records generate 256 polls and one initial draw. Time threshold
and signed nano-time wrap tests pass too. These are scheduling tests, not actual
performance qualification. All 27 focused tests passed. Reran all three actual
native startup cases on this final source: passed again, including byte-exact
version-3 replay cancellation. All 233 production/startup-harness hashes match.
Earlier private run preserved under ignored `target/structure-startup-pre-coalescing`.
Final-source full regression will run next, then serial full structure workflow.
Do not use the earlier full pass as exact-final-source validation.


Final-source full Java regression passed: 1,044 tests/208 fresh current suites,
zero failures/errors, one optional browser-workflow skip, no missing/stale reports.
All 233 startup source/harness hashes still match. Complete structure workflow
will now run alone. Do not claim that new native pass or video yet.


Independent runtime inspection found owner-side interrupt can invoke NIO channel
closure and `FileChannelImpl.implCloseChannel` calls `NativeThreadSet.signalAndWait`.
The current full structure playtest remains frozen on the pre-repair source.
After it finishes, replace active-worker interruption with cooperative cancel
and orderly executor shutdown, assert no worker interruption in the delayed-I/O
test, and rerun affected checks/native media. Do not claim the current close
implementation guarantees a nonblocking owner under stalled channel I/O.


Pre-repair full structure workflow passed with exit 0. Repaired cancellation:
owner sets cooperative flag, uses `Future.cancel(false)` and orderly executor
shutdown; it no longer interrupts active channel I/O. Constructor-failure cleanup
uses the same policy. Delayed-worker test now asserts no interruption and still
proves nonjoining close, publication fencing and eventual worker exit after
release. All 27 focused final-source tests passed. All three real native startup
cases passed again on repaired source; byte-exact version-3 cancellation retained.
Old private runtime folders preserved under ignored pre-cooperative names.
The 1,044-test full run and prior structure playtest are pre-repair evidence,
not exact-final-source full validation. Final structure workflow will now rerun
alone; exact-source hosted full regression remains required after publication.
No physical stalled-filesystem timing or city/network qualification claimed.


## Offline startup — final local validation complete

Final 27 focused tests passed after strengthening worker-load coverage with a
32-resolution custom definition, byte-exact flat/regional saves and cancellation
observations asserted outside the cancelled Future. Actual Main/GLFW startup
cases passed after cooperative close repair: Escape during 200,000-record read
and 5,000-record version-3 replay preserves source bytes; normal 200-record
startup reaches play with exact geometry/source. Complete structure Main/GLFW
workflow passed again on final production source, including capture/library,
import/export/long path, four rotations, conflicts/holes/player rejection,
atomicity/cancellation/place/undo/repeat/dependencies/restart/inventory/editor.
All 232 production hashes match both native manifests (233 files each including
their respective harness). No temporary profile files remain.

Playtest media: fresh F10 video fully decoded, 25 frames, 45.402 seconds,
534,699 bytes, 960×544, SHA-256
`8f32f92f5d9eb93b1314cd7bb9cdb25fcccc73832c8494588533dc42c3e6af63`.
Fresh startup read/replay images, full-video midpoint and conflict view inspected.
Software FPS 1 remains functional evidence only. No physical GPU/frame/stall/
city/network budgets passed. The 1,044-test full Java run is explicitly BEFORE
the cooperative-close repair; not rerun locally afterward. Exact-source hosted
full regression is still required. Detailed commands, environment, steps and
expected/observed results: `dashboard/evidence/structure-offline-startup.json`.

Final audit: diff/new-file checks passed, no tracked deletions/unmerged entries
or pending Git recovery, restored MP3/Hub files still byte-match review base.
Inline refactors replace eager copy/meshing/replay loops with bounded/shared
helpers and preserve city-history fields; inventory skips world-only IDs. No
unrelated deletion intended. No commit/push/merge performed locally. Controller
must publish new source/report/media and run exact-source hosted checks. Continue
live region/history/durable-root/renderer/server integration afterward. City/max
admission and full reference physical-GPU/shaped 32-client/20-run qualification
remain unfinished. Stable rollout stays gated; do not mark ready.

## Dense validated replay load — implementation and focused checks

Parent exact head 1ed366d90e7b8510288105feb77f1dac15a8ed73: inspected PR checks;
all eight builds succeeded and publication skipped. Downloaded sanitized Linux
Maven summary from run 42391291811: 1,044 tests/208 suites, zero failures/errors,
one optional browser-workflow skip. No raw hosted runtime logs read.

Current edits add decoder-only `WorldVoxels.rememberValidated`: valid depth-4
records remove/reinsert only their exact key, keeping coarser ancestors and replay
order. Other depths use prior coverage pruning. LocalGame flat replay and shared
version-10 replay (offline/server) use it. No format or admission cap changed.
Three new tests cover all 4,096 leaves at both world limits, duplicate order,
coarse overwrite, snapshot isolation, seeded mixed-depth records, invalid input,
and byte-exact dense flat/regional load/save. All 40 focused tests passed.

Small diagnostic: two warmups/five runs, 8 cells/32,768 records, shared 4-vCPU
Haswell VPS/Linux 6.1.0-50-cloud-amd64/Oracle Java 25.0.3/512 MiB heap. Baseline
median 9,979.766216 ms, 588,356,936 thread-allocated bytes; validated path median
57.763836 ms, 47,804,328 bytes. Part overlaps full regression. This is neither
peak memory nor city/maximum/reference-environment qualification.
Commands/raw measurements: dashboard/evidence/structure-dense-replay.json.

Full Java regression is currently running; do not claim pass yet. Then run both
native harnesses alone on inherited display, decode/inspect fresh video and
startup/conflict images, finalize source hashes/report and request controller
publication/hosted checks. Previous ignored native folders preserved as
`target/structure-startup-pre-dense-replay` and `target/structure-native-pre-dense-replay`;
never read their private runtime logs. No helper ID was created by either failed
Pro attempt; no proposal or delegated validation was accepted. Independent local
analysis and tests supply the evidence. Whole feature remains unfinished: live
disk-backed history/geometry/roots, city/max admission, full 20-run physical-GPU
and shaped-network 32-client qualification still required. No stable rollout.

## Dense validated replay — final local validation complete

Full final-production Java regression passed: 1,047 tests/209 fresh suites,
zero failures/errors, one optional AssetManager browser-workflow skip. All current
test sources have fresh reports. The 40 focused tests also passed. No production
source changed during either run or either final native workflow.

Strengthened native success fixture to 4,096 alternating bricks/glass depth-4
pieces plus one normal block. Actual Main startup checked every saved piece in
live World; normal close preserved source bytes. Both native Escape cases passed
again: read 200,000 records and partial replay 5,000 version-3 records, no partial
world publication/ready scene or source rewrite. Full native structure workflow
passed with exit0: named capture/anchor/library, import/export/long path, all four
rotations, overlap/holes/player rejection, cancel/atomicity/place/undo/repeat,
32-resolution model dependency/restart, inventory/editor regressions.

Fresh F10 video fully decoded: 25 frames, 44.013 seconds, 466,842 bytes, 960×544,
SHA-256 `5f995190f52e9631ac84296531644b1e20007e9525b88042f57385a659d48b40`.
Inspected new midpoint, occupied-cell conflict screenshot and both startup images.
All 232 production hashes match both 233-file native manifests. Recording matches
isolated profile bytes; no profile temporary files remain. Software FPS 1 remains
functional evidence only. Exact commands/environment/steps/expected/observed,
microbenchmark limitations and current pending gates are in
`dashboard/evidence/structure-dense-replay.json`.

Audit: no removed files/unmerged index/pending Git operation; restored MP3/Hub
files byte-match review base. Intended old test line replacements expect structure
protocol 31 while retaining old-save/packet assertions and adding 29/30 fallback.
Earlier sky-priority functional call grants 10 seconds for cold CI, retains the
fairness assertion and leaves production timing budgets untouched; it is not a
frame/stall qualification result. This turn replaces only decoder calls with the
validated exact-key path and strengthens native dense startup coverage. No
unrelated feature deletion intended. No local staging/commit/push/merge.

Controller must publish pending feature source and sanitized media, then run
exact-source hosted checks. This is a new source handoff, not another wait for
parent 1ed366d9 (all eight parent builds already passed). Continue live disk-backed
region/history/roots/large placement/render/server integration after hosted
results. City/max admission and 20-run reference physical-GPU/shaped 32-client
qualification remain unfinished. Keep stable rollout and supported-limit claims
gated. Do not mark ready.

## Private offline geometry replay — implementation and focused/native startup checks

Inspected parent 8f7ae36eeb9e1f8acee98505aff5227527ba9287: all eight PR-rollup
build checks succeeded; publication skipped. Exact feature run 42395300755 Linux
summary reports 1,047 tests/209 suites, zero failures/errors, one optional skip.
No raw hosted runtime logs read.

Main now prepares offline World geometry on one startup daemon after LocalGame
load and asset intake. It captures an immutable indexed history root, preserves
the live model-library identity, applies the saved stress grid before replay,
and transfers only a complete world after history/model identity and revision
checks. Worker World has no chunks, scheduler, GL objects or file handles. Main
continues drawing progress/handling Escape; cancellation publishes no geometry.
Close is cooperative, nonjoining and does not interrupt the active worker.
Failed/cancelled active preparation closes private state on worker; a discarded
completed heap-only result is collected normally. Multiplayer replay unchanged.
No save format, admission cap or performance qualification changed.

All 36 focused tests passed, including six new worker tests: exact geometry and
model identity, single transfer, history/model fences, mutation during replay,
blocked-worker cancellation without join/interruption, failure cleanup, empty
world and unsupported generic-map rejection. Actual Main/GLFW startup passed:
200,000-record read cancellation; 5,000-record version-3 geometry cancellation
with local loaded and Main.world still null; successful 4,097-record dense replay
with every piece checked. All source bytes unchanged. Updated native manifests
bind this source/harness. Native private folders preserved under ignored
`target/structure-startup-pre-worker-replay` and `target/structure-native-pre-worker-replay`;
never read their private runtime logs.

Full Java regression is currently running. Then run complete native structure
workflow alone, fully decode/inspect new video and images, finalize sanitized
report/source hashes and request controller publication/exact-source checks.
Do not claim those unfinished checks passed. Detailed current report:
`dashboard/evidence/structure-worker-replay.json`. Both Pro attempts failed
before creating IDs; no delegate result or validation accepted. Independent
local reasoning/tests used. No local commit/push/merge or other worktree writes.

This still materializes global history, geometry and indexes. Continue live
disk-backed region/history/durable roots and large placement/render/server
integration. City/max admission and required 20-run reference physical-GPU,
shaped-network 32-client, memory/frame/tick/traffic/stall/cold-load/upload
qualification remain unfinished. Keep supported-limit and stable-rollout gates.

## Private offline geometry replay — final local validation complete

Full final-source Java regression passed: 1,053 tests/210 fresh suites, zero
failures/errors, one optional AssetManager browser-workflow skip. Every current
test source has a fresh report. All 36 focused tests passed. Actual native Main
startup passed all three cases, including the new requirement that cancelled
geometry preparation never publishes Main.world. The source saves remain
byte-exact. Native overlay showed progress 0 at capture; exact applied-prefix
length there was not measured. The blocked-worker unit test finishes one private
apply after close, then observes cancellation and clears private state without
publication. No active-worker interruption or owner join occurred in that test.

Full Main/GLFW structure workflow passed on the same production source: named
capture/anchor/library, import/export/long path, all four rotations, occupied-cell
and player-overlap rejection, holes, cancellation/atomicity, place/undo/repeat,
32-resolution dependency/restart and inventory/editor regressions. All 233
production hashes match both 234-file native manifests and validation sources.
No source changed during the full regression or final native workflows.

Fresh F10 video fully decoded and inspected: 26 frames, 42.926 seconds,
497,335 bytes, 960×544, SHA-256
`5dbc0482718b9117af3164c652a30b6f5b7f6e293c56fdb8871699ccb1e442e9`.
Inspected new midpoint, occupied-cell conflict capture and startup images.
Recording matches isolated profile bytes; no profile temporary files remain.
Software FPS 1 remains functional evidence only. Exact commands, environment,
steps, expected/observed results, source hashes and limits are recorded in
`dashboard/evidence/structure-worker-replay.json`.

Final audit: diff checks passed; no removed files, unmerged entries or pending
Git recovery. All restored MP3/Hub sources, workflows, tests and fixtures still
byte-match review base. This turn replaces offline owner replay with a private
worker and strengthens cancellation assertions; multiplayer owner replay and
all formats/caps stay unchanged. Earlier protocol compatibility replacements and
functional sky-priority test timing are documented in the audit. No unrelated
feature deletion intended. No local staging/commit/push/merge or other-worktree
mutation. Source/report/media await controller publication and exact-source
hosted checks; parent 8f7ae36e checks already passed and need no unchanged wait.

Continue live disk-backed region/history/durable roots and large placement,
renderer and server integration after scoped hosted results. This worker still
materializes global history/geometry/indexes; asset intake, city-sidecar reads
and later GPU setup retain existing owner paths. City/max admission and required
20-run reference physical-GPU/shaped 32-client qualification remain unfinished.
Keep stable rollout and supported-limit claims gated. Do not mark ready.

## One-cell private startup replay — implementation and focused validation

Completed scoped run 42399479148 succeeded for parent 007b01bb. Its Linux summary
uses synthetic merge SHA 651e4e9b335bf63c164191aeefd9ec8e8a799d9c, whose tree
exactly matches assigned parent tree db4538a7ff109efa54552f771b5ef3a5365adc45.
Feature-run Linux summary from 42399474387 explicitly names 007b01bb. Both report
1,053 tests/210 suites, zero failures/errors and one optional skip. No unchanged
hosted wait requested; no full runtime logs read.

Private startup now keeps one mutable 16³ cell while applying consecutive edits.
Coordinate changes flush the canonical frozen tree and final edited-cell image;
interleaved return visits start from the previous private value. Original history
order and per-edit sky/version metadata are retained. Normal World.apply still
uses its original geometry path. The validated finest-grid history helper is
also used by this fully validated private replay. Fresh/loaded/closed/scheduled,
completed and recursive initial replay guards keep it out of live worlds. The
first progress callback is guarded too. Failure/cancel discards private state via
existing OfflineWorldReplay cleanup, before owner/source-fenced publication.
No package/save format, model resolution or admission cap changed.

All 39 focused tests passed after strengthening first-progress reentrancy checks.
New coverage compares interleaved coarse/fine/air, colours and custom models,
exact histories, geometry, edit/sky metadata, dense cells, later ordinary edits,
source isolation, guards, invalid input, callback failure and cancellation.
Existing worker lifecycle/fence/cleanup tests also passed.

Initial dense-only headless diagnostic passed all material/detail/history checks
with five measured runs after one warmup. Raw data preserved as ignored
`target/structure-cell-replay/benchmark-dense-initial.json`. The benchmark now
also includes a coarse 16,384-cell fixture; rerun both serially after full Java
regression finishes. The current draft report's initial benchmark/hash section
must be replaced with those final measurements. Do not claim the new coarse
case was run yet. Full Java regression is running. Then run both native harnesses
alone, decode/inspect fresh media, finalize hashes/report and request controller
publication/exact-source hosted checks. Prior private native folders preserved
as ignored pre-cell-replay names; never read their private runtime logs.

This still materializes global history/geometry/indexes. Live disk-backed
region/history/durable roots and large placement/render/server integration,
city/max admission and full 20-run physical-GPU/shaped 32-client qualification
remain unfinished. Keep rollout and supported-limit gates. No owner answer is
needed for these routine implementation defaults. No local staging/commit/push,
merge, master or other-worktree mutation.

### One-cell private replay: final local validation

The private startup replay cell builder is locally validated. Focused checks passed 39 tests in six suites. Full Java regression passed 1,057 tests in 211 fresh suites with zero failures/errors and one optional browser skip. The serial five-run headless diagnostic measured dense replay median 2345.89 ms before versus 14.95 ms with the builder. Coarse replay measured 31.77 ms versus 32.26 ms and allocated more; this is a documented tradeoff, not a universal speedup or full fixture qualification.

Playtest: `python3 deploy/run_offline_load_playtest.py --display "$DISPLAY"` passed all three startup cases. `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed the full native workflow, including long import paths, conflicts, rotations, cancellation, undo, restart and inventory/editor regression. These runs used isolated synthetic profiles and llvmpipe software rendering. All 233 production source hashes match both native manifests. The fresh F10 video decoded all 25 image frames across 45.695 seconds; its midpoint and conflict screenshot were visually inspected. Video SHA-256: `d60dbf6ef651e523471368f6cd2206dff173b3a6804c94e16f3f592092a707cc`.

The complete diff against review base `71612f09bfec4549456b3317676e76cdced085a4` removes no files. All ten restored MP3/Hub files match that base. No unmerged index or Git operation is pending. New source, reports and media need controller publication and exact-source hosted validation. Parent `007b01bb978cf9c30f33ec8161f5d09eb14ff5cf` hosted checks already succeeded; no unchanged wait is requested.

Full city/maximum admission, live disk-backed integration and required 20-run physical-GPU/shaped 32-client qualification remain unfinished. Supported-limit claims and stable rollout remain gated. Detailed commands, environment, results and source hashes are in `dashboard/evidence/structure-cell-replay.json`.

### 2026-11-09: 32-client TLS observer validation

Resumed substantive reviewer work on parent `662a813379c71b94f78408f354f7aac111a0b9d2`. GitHub master and local origin/master both resolve to review base `71612f09bfec4549456b3317676e76cdced085a4`, already included. No Git merge/fetch/commit/push was performed. All ten MP3/Hub restoration files byte-match that base. MusicPlayer and pom.xml also match. No file deletions or unmerged entries are present. Parent hosted checks already passed; no unchanged wait is requested.

Strengthened `StructureMultiplayerTest` to await both endpoints of each rotated 512-cell road on every client, rather than infer observer delivery from the sender acknowledgement. Verify the rotated far-end dependency geometry on every client. Add optional sanitized JSON measurements only after all assertions, including late join/restart, pass. CREATE_NEW preserves existing reports. Document sample boundaries and limitations in `docs/structures.md`.

Playtest: ran the actual TLS server and 32 synthetic protocol clients using the recorded Maven command in `dashboard/evidence/structure-network-32.json`. Production login limits stayed active, including the 306-second connection-batch wait. One integration test passed with zero failures/errors/skips. Four encoding/request-to-all-client endpoint times were 1480.877057, 1052.204008, 1041.461574 and 882.319348 ms. The bounded 4,096-tick window reported zero failed ticks, work median/p95/worst 0.00601/1.929836/20.599209 ms, and 19.995124856626234 starts/s. This mixed window is not placement-phase qualification. Total workflow was 331739.315759 ms including login waits. First launch was interrupted with exit 130 to correct a test assertion before the final run; no interrupted pass is claimed.

Fresh CLI-result window captured on inherited display/authentication with `deploy/StructureNetworkEvidence.java`; PNG inspected and contains only sanitized synthetic results. Browser playtesting does not apply to this noninteractive TLS integration. All 233 production source hashes stayed unchanged, as did the final test source during execution. Existing exact-parent F10 media was downloaded and byte-verified, but it is historical production-workflow evidence, not the new 32-client capture. No new rendered-client playtest is claimed this turn.

These are 512 instances with 512 sparse dependencies, four orientations, unshaped loopback and nonrendering protocol clients. They are not city/maximum fixtures or 20 full runs. Production admission caps remain unchanged. City/maximum geometry/chunk/history admission, live disk-backed integration, full workflow/resource/traffic/frame/ack/stall budgets and required physical-GPU/shaped 32-rendered-client qualification remain unfinished. Supported-limit claims and stable rollout stay gated. New test/docs/report/image need runner publication and exact-source hosted checks. Continue implementation afterward; do not mark ready.

### Regional offline tail staging: implementation and focused validation

Parent `ec7cce70aee39c473d760c7761618f1ab1dab7f1` feature run `42623014481` completed successfully. No unchanged hosted wait is requested. Continue with the new local source below.

`LocalGame` version-10 loading now calls the existing bounded, worker-owned `RegionReplaySpool`. Replay sections are validated/staged first, followed by drop validation and exact source EOF. Only then does verified spool traversal populate the fresh private history. The spool is deleted before a completed load returns. Callback/cancellation/read/cleanup errors prevent publication. Versions 3–9 retain their existing parsing; no save/package/admission cap changed. This still expands eager global history after validation and is not resident region paging. Valid loads trade temporary disk space and a second pass for source-tail rejection before history expansion.

Spool staging now accepts decoded-record progress callbacks at the existing 128-record cadence. Progress can reach the source record total before later private reconstruction completes. The owner still awaits task completion separately and never joins or interrupts active I/O. Temporary storage uses `java.io.tmpdir`, bounded at 88,000,004 bytes; failure to create/write/clean temporary storage fails the private load.

All 27 focused tests in five suites passed with zero failures/errors/skips. New coverage verifies source EOF before the first history write, exact recurring-region replay order, duplicate-drop replacement/order, malformed/truncated/trailing tails leaving history empty, stage/drain cancellation, callback failure, nonempty-target rejection and cleanup. Existing worker lifecycle, source-byte, spool and offline save regression checks passed. Command/results/hashes are in `dashboard/evidence/structure-regional-load.json`.

Full Java regression is running on frozen production and test source. No full-suite pass is claimed yet. Native harness `deploy/OfflineLoadPlaytest.java` now converts the isolated success fixture to version 10, verifies all 4,097 edits in the loaded World, captures the presented first rendered frame, closes normally and checks source bytes. Version-8 read cancellation and version-3 private replay cancellation remain legacy regression cases. Native checks must run alone after the full suite finishes. Prior native folders were preserved under ignored `target/*-pre-regional-load`; do not read private runtime logs. Then inspect the fresh success image, verify manifests, finalize the report and request controller publication/exact-source checks.

All ten MP3/Hub restoration files still match review base `71612f09bfec4549456b3317676e76cdced085a4`; the proposed diff removes no files. Full city/maximum geometry/chunk/history admission, live disk-backed integration and required 20-run physical-GPU/shaped 32-rendered-client resource/frame/tick/traffic qualification remain unfinished. Keep supported-limit and stable-rollout gates. No owner question or Git mutation is needed for this routine continuation.

### Regional offline tail staging: final local result

Full regression completed with exit 0: 1,062 tests in 212 fresh suites, zero failures/errors and one optional skip; no missing/stale test-source reports. All 27 focused tests also passed. Production and final test source stayed frozen throughout regression.

Playtest: `python3 deploy/run_offline_load_playtest.py --display "$DISPLAY"` passed all three Main/GLFW cases. Version-10 success loaded all 4,097 geometry records, captured a presented game frame, closed normally and kept source bytes exact. Version-8 read cancellation (200,000 records) and version-3 private replay cancellation (5,000 records) also preserved source bytes and prevented partial publication. These cancellation overlays occur at zero displayed records; no nonzero native replay prefix is claimed. Deterministic unit tests cover cancellation during staging and a private drain prefix. No replay spools remained in the native temporary directory. All 234 native manifest hashes, including 233 production files, match the tested source.

Inspected fresh `structure-startup-success.png` and both cancellation overlays. Renderer is llvmpipe (LLVM 15.0.6, 256 bits); this is functional startup evidence, not physical-GPU/frame-time qualification. The success frame demonstrates completed startup; distant fixture geometry is checked by assertions before capture, not visible detail in that image. Detailed commands, source hashes, environment, results and media hash are in `dashboard/evidence/structure-regional-load.json`.

Final diff check passed. No files deleted against the review base, no unmerged index or pending Git operation. New source, tests, reports and startup media require runner publication and exact-source hosted validation. Do not repeat the completed parent check wait. Continue city/maximum admission and remaining live disk-backed integration afterward. All full-fixture, 20-run, physical-GPU, shaped 32-rendered-client and rollout gates remain pending. No ready/review approval is claimed.

### Single-pass decode receipt: implementation and component result

Parent `85c2b0261f48746b6282a6155d8993cad9782cf5` feature run `42629432360` completed successfully. No unchanged wait is requested. New source removes repeated decoded-cell reconstruction/validation and sorting: parser-side material/bounds/model/instance checks precede a private receipt created only after stream CRC/length/EOF validation. Definitions are frozen and geometry is builder-owned. Ordered finalization still rejects duplicate/backtracking keys and copies all published arrays. Public construction/capture validation, formats and caps remain unchanged.

All 37 focused tests in four suites passed on final test source. New tests exercise invalid material/model geometry, bounds, names, ordering, duplicate/empty dependencies, aggregate instance surfaces, both package versions, frozen definitions/geometry, exact canonical roundtrips and builder snapshot/cancellation isolation. First focused launch had one fixture error because SparseVoxelOctree.set rejects encoded LED alpha. The fixture now uses fill; all focused checks were rerun. No initial pass claimed.

`python3 deploy/run_structure_decode_benchmark.py` passed serial parent/current public-API comparisons on identical files, three warmups and 20 measured runs each. Uniform 100,000-cell median decode 40.289218 -> 32.5587575 ms; dense 64-cell/131,072-leaf median 22.7378565 -> 17.5204035 ms. Median thread allocation 8,092,352 -> 4,426,672 bytes and 9,884,896 -> 5,312,256 bytes. Uniform p95 regressed 64.386917 -> 70.356361 ms and allocation p95 rose. Do not claim a universal gain. Benchmark excludes acquisition, fixture construction/export, verification, placement, saves, network, rendering and peak memory. All counts and complete encoded bytes checked after every decode. Parent compiled classes were preserved before compiling changed source; hashes/provenance and raw samples are in the report/ignored target directory.

Full regression is running on frozen production/test source. After its real exit, verify fresh suite reports, then run `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` alone. Its target folder is fresh; previous folders/logs remain preserved unread. Decode and inspect fresh video/images, verify all native/current hashes, finalize `dashboard/evidence/structure-decode-single-pass.json`, audit base deletions/restorations and request runner publication/exact-source checks. No full regression/native pass claimed yet.

Both required full fixtures still exceed unchanged development geometry/chunk/history caps. This component work does not complete city/max admission, live disk-backed world integration or required 20-run physical-GPU/shaped 32-rendered-client/resource/frame/tick/traffic qualification. Keep supported-limit and stable-rollout gates; continue implementation after new checks. No owner answer or local Git mutation is needed.

Single-pass decode full regression completed with exit 0: 1,066 tests in 213 fresh suites, zero failures/errors, one optional skip and no missing/stale test-source reports. Source hashes remain frozen and match. The serial native Main/GLFW structure workflow is now running; no native pass claimed yet. Continue with its actual result, fresh media decode/inspection and exact source manifest audit before publication.

Single-pass decode final native result: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0. Full Main/GLFW workflow passed named capture/anchor/library, long-path import/export, four rotations, conflicts/player rejection, holes, cancellation, atomic/repeated placement, one-operation undo, malformed imports, model dependencies/restart and inventory/editor regression. All 234 production/harness source hashes match current source and the frozen report. The runner receipt digest hashes the canonical sorted source map, not the manifest file bytes; both map digest and every source file were checked. No source fence was weakened.

Fresh F10 video fully decoded: 25 frames, 47.398 seconds, 432,498 bytes, 960x544; SHA-256 `6aa81182296d0d75f306bfa198c3e0f0e12c92c2227359ef3ea15de92e044548`. Midpoint green footprint/anchor/staging and red occupied-conflict image inspected. llvmpipe HUD 1 FPS is functional evidence only. New source, tests, benchmark, report and media need controller publication/exact-source hosted validation; prior parent checks are complete and are not requested again.

Final diff check passed; no deleted files, unmerged index or pending Git operation. All ten restored MP3/Hub files still match the review base. Full regression remains 1,066 tests/213 fresh suites, zero failures/errors, one optional skip; focused final checks 37/4 suites passed. No city/max caps changed. Continue admission and live disk-backed integration after hosted checks; all full-fixture, 20-run, physical-GPU, shaped-network/rendered-client, resource/frame/tick/traffic and rollout gates remain open. Detailed commands, environment, raw benchmark samples, mixed p95 result, source hashes and actual native result are in `dashboard/evidence/structure-decode-single-pass.json`.


### Dense imported model snapshots

Parent fd8a338b hosted platform checks succeeded; no unchanged wait is requested. New source compacts dense imported model dependencies into exact wire bytes plus private direct-sampling arrays. Sparse imports retain persistent nodes. Capture/public construction retains its original shared-node path so it does not duplicate a live library model. Immutable snapshots keep byte-exact identity, redundant branches, colours and per-model limits; mutable copies reconstruct independent trees. Non-sampling queries and world registry/editor copies may reconstruct graphs. Whole-world/placement peak memory is not measured by this component experiment.

Final focused checks passed 43 tests in six suites, including actual TLS multiplayer workflow, material/decode checks, dense 32-resolution samples/mesh, exact bytes, independent edits and cancellation. Separate model/MP3 regression passed 15 tests in four suites. Hub registration CLI passed six tests. The first full regression stopped advancing at completed suite reports and was interrupted with exit 130; no full-suite pass is claimed. Production source was refined during that initial run; focused checks ran after the final source edits. The native Main/GLFW workflow is running against frozen final source; no final native pass is claimed at this checkpoint.

Read-only scans visited every region and dependency in both full fixtures. City: 978288 cells, 968048 geometry leaves, 256 models, 10240 instances, 2173 regions, 1572864 dependency quads, 62914560 instance quads. Maximum: 4000000 cells, 7967232 leaves, 512 models, 16384 instances, 8192 regions, 786432 dependency quads, 25165824 instance quads. These diagnostic readers do not admit a package into production or qualify its performance. Current geometry/surface/chunk/history caps remain unchanged. After native completion, rerun the serial model component benchmark against final compiled source, inspect fresh video and source hashes, finalize structure-model-snapshots.json and hand new source/media to the controller. Continue large-package admission and live region-backed world integration after exact-source checks. Full hardware/shaped-network/32-rendered-client/20-run/resource qualification and stable rollout stay gated. No owner answer is needed.


Final native workflow exited 0. All 234 production/harness source hashes match the current source and canonical receipt digest. Fresh F10 video fully decoded: 23 frames, 38.858 seconds, 484679 bytes. Green footprint/anchor/staging midpoint and red rotated occupied-conflict image were inspected. No physical GPU/frame budget pass is claimed. Final serial model experiments retained all 256 city and 512 maximum dependency identities, with 20 measured first-model decode/snapshot and mesh-count runs per mode plus three warmups. City estimated dependency heap 288276200 -> 77395680 bytes; maximum 72310120 -> 19807944. Snapshot p95 slows in both; maximum mesh p95/worst regress. No universal speedup or supported-limit claim. Final-source full regression retry is running with a 900-second bound. Wait for its actual result; do not read private runtime logs or claim the interrupted first run passed. All model, fixture, command, source and native evidence is in dashboard/evidence/structure-model-snapshots.json.


### Requested quota/service pause — unpublished model snapshot work

Stopped the final full Java regression with Ctrl-C after the explicit pause request; exit 130, no full-suite pass. No local test process remains owned by this turn. All source edits, tests, benchmark, docs and fresh sanitized media are preserved without Git staging/commit/push. Current published parent is fd8a338b9545864b6e4a2d855d85878b90d774a8. Durable exact-source results and resume steps: dashboard/evidence/structure-model-snapshots.json.

Completed: 43 focused Java tests/6 suites; separate model/MP3 15 tests/4 suites; Hub registration 6 CLI tests; full native Main/GLFW workflow; all 234 native source hashes match; fresh 23-frame/38.858-second/484679-byte F10 video decoded and midpoint/conflict inspected; serial model component measurements for city/maximum with 20 samples/mode; full diagnostic fixture scans. No supported limits raised. First and final full regression attempts both remain unpassed (exit 130). Stable rollout and full city/maximum qualification stay gated.

After authoritative resume: inspect Git/source status and this report, finish the full Java regression on frozen source, inspect any real failures, then request publication/exact-source hosted checks for these NEW edits/media. Parent hosted checks already succeeded; never request another unchanged wait. Runner handles latest-master integration; meaningful checks and media must be rerun if source changes. Continue production city/maximum admission, live region-backed geometry/history/persistence and required physical-GPU/shaped-32-rendered-client/20-run resource/frame/tick/traffic qualification. Do not ask the owner for routine controller output or nonexistent desktop access.


### Authorized resume — full regression timing diagnosis

Saved production/native hashes still match; no new production edits. Remote master observed at 71612f09bfec4549456b3317676e76cdced085a4, matching the review base. The resumed Maven attempt was interrupted with exit 130 after report progress paused. A standalone JUnit launcher then showed continued ordinary TLS setup, city simulation and deliberate gateway timeout tests; do not assert a Maven defect. Its 600-second bound expired during gateway waits (exit 124), so no full-suite pass is claimed. The byte-identical sanitized launcher is retained as deploy/SuiteDiagnostic.java; its progress and private test output stay ignored under target/structure-limit-audit.

Current operation: timeout 1200s mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" test, output target/structure-limit-audit/resumed-full-1200.stdout. Allow its real completion; report only fresh XML summary attributes, not runtime logs. Existing 43 focused, 15 model/MP3, six Hub CLI, native/media and component results remain preserved and source-matched. After this required regression, finalize the receipt and request NEW-source controller feature_validation. No unchanged parent CI wait, owner input or qualification waiver is needed. Full city/max admission and live persistence/performance/hardware qualification still require substantive work.


### Final resumed regression result — new publication needed

The 1,200-second-bound Maven run completed with exit 0. Fresh XML reports cover all 214 source test classes: 1,070 tests, zero failures/errors, one optional browser skip, no missing or stale class reports. Earlier Maven interruptions and the diagnostic launcher's 600-second timeout remain incomplete checks; no Maven fault is established. deploy/SuiteDiagnostic.java is a byte-identical retained diagnostic helper that emits only test identifiers and bounded main-thread stack frames; it did not replace the successful Maven check.

Production/test/benchmark hashes still match the frozen receipt. All 234 native production/harness hashes and the canonical source-map digest match; preserved F10 video SHA matches its fully decoded/inspected evidence. No engine source changed during this continuation. All ten restored MP3/Hub files remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. Remote master was observed at that same base; no deleted files or unmerged index. No intended deletions.

Next action is controller feature_validation for the NEW unpublished compact-model implementation, tests, diagnostic helper, docs, report and media. Runner stages/commits/syncs/pushes the assigned feature only and records exact-head checks; this is not review submission or readiness. Do not request another unchanged fd8a338b wait. After actual new-head results, continue production city/max admission, live region-backed geometry/history/persistence and the required physical-GPU/shaped-32-rendered-client/20-run resource/frame/tick/traffic qualification. Supported limits and stable rollout remain gated. Any integration source changes require meaningful checks and fresh media again.


### Direct validated geometry import — implementation and focused result

Parent bd0bc739 hosted checks succeeded. Read AGENTS; worktree started clean. Isolated candidate caps (8388608 leaves, 2097152 dependency quads, 67108864 instance quads) admitted both full fixtures in a one-run smoke with exact decoded-body roundtrip, mid-decode cancellation and CRC rejection under 1 GiB heap. Production controls still rejected them. Maximum smoke decode allocated about 3.46 GB transiently; double-package roundtrip sampled 1071774720 heap bytes with RSS high water 1259614208 bytes. Those analysis-only results do not justify production adoption or game qualification.

New production decoder validates cell size, tags/depth, leaf values/materials, emptiness, remaining leaf budget and cancellation while appending exact geometry bytes to the bounded arena. It no longer creates and reserializes a temporary octree per imported geometry cell. Extended size headers normalize as the previous writer did; redundant branches remain intact. Cell metadata is added only after a complete valid tree, and package publication still waits for full stream CRC/length/EOF verification. Capture/public-constructor validation remains unchanged. No production caps changed.

All 41 focused tests in five suites passed, including four new random differential/malformed/budget/cancellation/snapshot tests. Baseline main classes were copied before edits, with provenance at target/structure-candidate-import/baseline-provenance.json. A serial comparison is running: python3 target/structure-candidate-import/run_comparison.py, output target/structure-candidate-import/comparison.stdout, child reports under comparison-*/report.json and run-*/report.json. It performs 20 measured strict decodes plus one warmup and one full encode/decode byte-body verification and rejection/cancellation checks per fixture/mode; no world is created. Do not edit production or benchmark source/classes during this comparison. Final receipt: dashboard/evidence/structure-geometry-stream-decode.json. Native/media and full Java regression on the new production source are still pending. No readiness or supported-city claim.


Serial isolated candidate comparisons completed with source/tool/class fences intact. Each fixture/build has one warmup and 20 measured strict decodes, one full export/second decode with exact uncompressed body bytes, cancellation and CRC rejection. Current production controls still reject both full fixtures. Maximum p95 16352.208072 -> 12018.719278 ms; median main allocation 3460693592 -> 1101999500 bytes. Maximum double-package roundtrip sampled heap 1049063208 -> 882860304 bytes. City p95 7972.094241 -> 7734.470881 ms; sampled decode heap/RSS peaks regressed, so do not claim universal memory gains. Export observations are single measurements, not export p95. Raw samples/provenance/method/caveats are now in dashboard/evidence/structure-geometry-stream-decode.json.

Fresh native workflow is running: python3 deploy/run_structure_playtest.py --display "$DISPLAY", output ignored target/structure-candidate-import/native.stdout. Do not read runtime-private.txt. After real completion, fully decode and inspect new media, verify source hashes, run final full Maven regression with 1200-second bound (do not prematurely stop ordinary slow tests), and finalize report/audit/publication handoff. No production cap changes, whole-game budget passes or readiness claims.

Final local regression observation: all 215 fresh Surefire suite reports contain 1,074 tests, 0 failures, 0 errors and 1 skip. The process session was unavailable on resumption, so no Maven exit code is claimed. Production and validation source hashes still match the benchmark/native receipt. New source and fresh media need controller publication and exact-head hosted checks. City/maximum live workflow and physical-GPU qualification remain pending; production caps and rollout gates remain unchanged.

Chunk budget admission milestone: added conservative occupied-voxel face bound with unchanged exact fallback and render scanner. 32 focused Java tests passed with exit 0, including dense fallback, fragmented rejection, render bytes, cancellation and multiplayer. Fresh Main/GLFW workflow passed with exit 0; decoded 25-frame/497251-byte video and inspected midpoint/conflict images. Serial 20-call component samples saved in structure-chunk-budget-bound.json. No limits raised. New source/media await controller publication and exact-head hosted checks. Required city/max live, region-backed history/persistence, shaped 32-client and physical-GPU qualification remain unfinished. No deleted files; 10 restored MP3/Hub files still match base.

Hosted startup gap at 6ddd7f6d: push run 42671647314 and PR run 42671654610 both completed failure with zero jobs; push check suite has zero check runs. Workflow triggers cover this source. No build/test result exists. Request bounded controller retry or existing workflow_dispatch pinned to the assigned exact head, with sanitized startup diagnosis if still empty. No repeated unchanged checks wait, spending change or gate waiver. Local edits preserve this diagnostic only; production source remains the tested head.

Startup repair continuation: authorized one manual build.yml dispatch on assigned branch; command exit 0. Run 42715939974 observed in_progress on exact 6ddd7f6d. This is a new active run, not another wait on terminal runs. Published exact-head MP4 fetched and SHA-256 verified against local media. No new hosted pass claimed. Independent audit confirms LocalGame regional spool still drains eagerly into History and offline/server 2,000,000-record limits remain; next live milestone must provide region-backed authoritative state/history before lifting caps.

History capacity milestone: offline/server staging preflights final canonical replay count from octree metadata before retaining replacement history. Full-cell clear/leaf shared key and later capacity release counted exactly. 24 focused tests passed with exit 0, including 100 randomized forward/reverse differential cases, cancellation, stale-state/save atomicity/restart, TLS multiplayer and 2,003,433-record rejection before staging progress. Fresh native workflow passed with exit 0; 234 source hashes verified; all video frames decoded and midpoint/conflict inspected. New media/report await controller publication and new-head hosted checks. No limits raised. Manual run 42715939974 succeeded on prior 6ddd7f6d; no further retry needed. Full region-backed live state/history, city/max support and reference hardware/network qualification remain unfinished.

Live-region table milestone in progress: WorldVoxels multi-cell buckets now use RegionCellTable with primitive local keys, half-full sparse arrays and direct 4096 slots above 1024 occupied entries. Single-cell layout and COW owner protocol unchanged. 52 focused tests passed with exit 0, including full-key differential tests, sparse/dense COW, negative-coordinate WorldVoxels lookup, explicit air, history cursor, streaming and structure/multiplayer regression. Component probe completed 3 warmups and 20 copies per occupancy/mode; full region copy median allocation 163936 to 16448 bytes. Generic entry iteration boxes entries; no whole-world memory/capture budget claim. Native workflow running on frozen production source; private output target/structure-region-table-native.stdout, do not read runtime-private.txt. Pending receipt dashboard/evidence/structure-region-cell-table.json. Finish native result/media/hash verification before publication handoff. Prior 280b3f23 push/PR hosted checks both succeeded. Flat replay History/city/max and physical GPU/network qualification remain pending; no limits raised.

Live-region table milestone complete locally: native Main/GLFW workflow passed with exit 0; 235 production/harness hashes verified, fresh MP4 fully decoded and midpoint/conflict inspected. Report finalized at dashboard/evidence/structure-region-cell-table.json with 52 focused tests and component copy samples. Media uses software rendering at 0-1 FPS and does not qualify frame budgets. Production source frozen throughout tests/native; no limits raised. New source/media await controller publication and exact-head hosted checks. Next substantive work remains region-backed authoritative replay history/persistence, then full city/max workflow and shaped 32-client/physical-GPU qualification. No readiness or stable rollout claim.

Packed-query milestone running: get/uniform walk private packed wire bytes without node reconstruction/cache; model point sampling remains unchanged. Overflowing aligned local coordinates now reject. 43 focused tests passed with exit 0, including differential cell/model queries, noncanonical branches, concurrent readers, copy independence, packed geometry and multiplayer. Serial 20-batch component probe (64 queries/batch) passed checksum comparisons; fragmented point p95 24.393 to 0.442 ms and median allocation 8994304 to 0 bytes. Full-fixture performance remains unqualified. Native workflow running; source frozen. Receipt: dashboard/evidence/structure-packed-cell-queries.json. Next canonical-history migration must cover both LocalGame/server history and World replay metadata/editedCells; replacing only the loader will not remove eager live state. No format/cap changes or support claims.

Packed-query milestone complete locally: 43 focused tests and checksum-verified serial component probe passed; native Main/GLFW workflow passed with exit 0. All 235 native source/harness hashes verified; fresh MP4 fully decoded and midpoint/conflict inspected. Final receipt dashboard/evidence/structure-packed-cell-queries.json. New source/media await controller publication and new-head hosted validation. Point/aligned packed reads now avoid temporary node graphs; region extraction, raycast and mutable-copy paths still decode. Canonical region-backed authoritative history must also cover World replay metadata, not just LocalGame/server loaders. Full city/max fixtures, supported caps and physical-GPU/shaped 32-client qualification remain pending; rollout gates unchanged.


### Installed compact backing cells — focused validation

Parent 89386e34 platform checks were reported successful by the controller. WorldVoxels.prepareCellImage now keeps detached fractional images packed when World publishes whole-cell replacements. Uniform and air cells keep persistent nodes. Backing voxels and editedCells share the same factory-owned frozen image; loaded chunk trees and ordinary WorldVoxels.apply/replaceCell remain unchanged. This avoids retaining an additional decoded graph for that backing image, but staging replayState, flat histories, initial replay and loaded geometry still retain their existing data. It does not complete canonical region-backed authority or admit the city/max fixtures.

52 focused tests passed with exit 0, covering three new packed-installation/COW/replay/cancellation tests, resident/cold cell replacement, patches, snapshots, streaming, structure and TLS multiplayer. Instrumented component probe passed: 3 warmups and 20 samples per preparation/edit mode, sparse and fully fragmented cells, node and prepacked inputs. Fragmented reachable tree footprint was 140448 bytes for the previous decoded image versus 21152 bytes for the packed image. This sums unique reachable tree objects including shared nodes, not whole-world peak heap or marginal savings while source/replay/loaded trees remain alive. Normal-input packing and first mutation cost more. Exact samples and tradeoffs are in dashboard/evidence/structure-installed-packed-cells.json. Probe's initial incorrect Blocks import was corrected before successful execution. Native workflow is running on frozen production source; no final native pass yet.

The next authority migration must replace flat LocalGame/server history AND World replay metadata. World.InitialReplay.flush currently writes ordinary node-backed voxels, duplicates editedCells wrappers, and registers per-chunk History. A loader-only or whole-cell-factory-only change does not remove this retention. Preserve old replay ordering and explicit-air semantics. Keep legacy world formats readable, full-file validation before publication, mutation/revision fences, cancel/atomic undo, restart/late-join equivalence and bounded region activation. Do not raise the 2m-record, geometry, model-surface or 256-chunk caps from these component results. Full city/max 20-run, physical-GPU, shaped 32-client, resource/frame/tick/traffic and stable-rollout gates remain pending.

Canonical authority migration requirements (planned, not implemented):

- Replace retained per-leaf Edit/key objects with immutable region/cell images and a bounded ordered edit overlay. Keep explicit empty overrides distinct from absent seeded terrain. Regional snapshots must retain only immutable roots, and owner revisions must fence asynchronous work.
- LocalGame.stageStructureHistory currently captures History identity/revision, preflights replacementSize, builds World.CellReplacement and remembers each CellReplay. MultiplayerServer has equivalent 2,000,000-record checks. Both need one shared canonical-state transaction rather than independent flat reconstruction. Keep concurrent destination/body revalidation under authority ownership and one large placement in flight.
- World.InitialReplay, editsSnapshot, per-chunk edits and editedCells must consume the same canonical state. Preserve legacy replay order when converting old files. Do not silently replace ordered overlapping edits with an unordered Map. Main directly calls WorldVoxels.remember(local.edits,edit), including missile updates; migrate these writers before removing the compatibility map.
- LocalGame v10 RegionReplay and RegionReplaySpool still bound and spool records, then load them eagerly. A future cell-image format must validate all section lengths, coordinates, dependencies, CRC, aggregate limits and EOF before world publication. Keep v3-v10 reading. Stage save to a private temporary file, durable replacement before commit acknowledgement, and stream cold regions without rebuilding an all-world Edit map. Choose a new format version only with roundtrip/restart/late-join migration tests.
- Baseline differential checks must cover ordered overlap and explicit-air edits, ordinary model/editor/inventory paths, world snapshot forks, all rotations, concurrent destination edit rejection, rollback after save failure, cancellation during decode/stage/save, one-operation undo preserving later unrelated edits, cache-empty late joins, and restart recovery. Add measurements for owner-thread lock/stall time and bounded active-region memory; record failures rather than lifting caps to fit synthetic tests.
- Only after full city and maximum fragmented fixtures run through capture/export/import/preview/place/durable save/cold load/sync should production admission limits be reconsidered. Required physical rendering and shaped 32-client qualification remain independent gates even if CPU component probes pass.


Installed backing-cell milestone complete locally: 52 focused Java tests plus 48 targeted regression tests passed with exit 0 (100 total in 17 suites); 6 Hub registration CLI tests passed. Native Main/GLFW workflow passed with exit 0 on frozen source, all 235 production/harness hashes verified. Fresh F10 MP4: 25 decoded frames, 456409 bytes, 45.486 seconds. Inspected green footprint/anchor/staging midpoint and red 90-degree occupied-conflict screenshot. llvmpipe software rendering at 1 FPS remains functional-only evidence. Instrumented probe and tradeoffs are in structure-installed-packed-cells.json; no whole-world memory or full-fixture performance claim. Parent push run 42729608768 terminal success inspected on exact 89386e34 (all four platforms, publication skipped). New source/media require controller feature_validation, not another wait on the old head. Continue the canonical history migration above after new-head results. Full city/max admission and reference physical-GPU/shaped 32-client qualification remain unfinished; no readiness or rollout claim.

### Replay retention comparison — candidate rejected

Hosted push run 42733243681 succeeded on exact parent acd476734c69f6e2405a5b68f896afee5e74faf3: all four platform builds passed, publication skipped. The production source and native harness remain byte-identical to that head.

Do not replace CellReplay's retained Edit objects with primitive rows in isolation. The existing replay shares immutable Edit objects and its immutable key index with staged LocalGame authority and live World histories. A stage-only measurement hides this sharing. The first all-primitive prototype also moved key/index construction onto installation and regressed fragmented install p95 from 6.839468 to 19.653738 ms in its exploratory component sample. A revised prototype kept worker-prepared keys/index and replaced only retained Edit objects. Its isolated stage graph shrank from 688504 to 524632 bytes, but the combined replay plus two distinct histories grew from 2011752 to 2241192 bytes because each history materialized separate Edit objects. Sparse and uniform model cases also grew. Neither prototype was adopted. No production storage, format or cap changed.

Reproduction: compile the unchanged parent production source, then run `python3 tools/structures/run_replay_retention_probe.py`. The wrapper verifies production source identity, rebuilds the parent WorldVoxels and a candidate only inside separate ignored target directories, checks compiled dependency hashes, and runs three warmups plus 20 samples per fixture/mode. The unapplied diagnostic patch is tools/structures/primitive-cell-replay-candidate.patch. Never apply that patch to the feature source; it is retained only to reproduce the rejected alternative. Final wrapper exited 0. Its first attempt failed because patch used read-only /tmp; temporary files now stay inside the assigned worktree. No sandbox control changed.

Final source tests: 49 tests in eight suites, zero failures/errors/skips, exit 0. Added boundary regressions cover 4097-row fragmented replay, exact ordered values/revisions, source independence, negative cell coordinates, full local addresses, LED RGB extremes and model ID 66559. An interruption expectation belonging only to the rejected prototype failed against the unchanged legacy helper; that unimplemented helper contract is not claimed. Final engine cancellation/atomicity and multiplayer tests passed. No fresh native run is claimed for this noninteractive diagnostic: all 235 production/harness hashes still match the actual executed Main/GLFW workflow and its exact-head published video. Detailed commands, raw results, source/class fences, caveats and actual failures are in dashboard/evidence/structure-replay-retention.json.

The next canonical authority change must retain shared immutable region/cell state across LocalGame, World replay metadata, snapshots and staged transactions, instead of independently materializing each layer. Count the union of retained owners and peak staging memory; a smaller isolated wrapper is insufficient. Flat committed History, eager initial replay, 2m-record persistence, geometry/surface/chunk admission, full city/max 20-run workflows and reference physical-GPU/shaped 32-client qualification remain unfinished. Stable rollout stays gated. New regression/probe/report sources await controller publication and exact-head checks; this is not review submission or readiness.


### Shared bulk history — implementation and focused checks

Parent 98ae000d hosted push run 42736448546 succeeded on all four platforms; publication skipped. New production History now retains ordinary Edit records directly, but whole-cell replacements share CellReplayRows across the worker replay, staged authority and live World histories. Fractional rows use bounded primitive words and a local lookup (at most 4681 short slots); uniform cells share one immutable Edit. SegmentedHistory preserves global insertion order: direct put overrides keep their row position, removals mask bulk slots, later remember operations append after coverage removal, and ordinary rows remain interleaved correctly. Bulk rows and override maps are immutable; cell indexes fork with owner tokens. Copy/cell cursors retain revision fences and read-only views. Existing persistence/protocol formats and record/admission limits are unchanged.

59 focused tests in ten suites passed with exit 0. Three new tests cover 350 seeded mixed bulk/ordinary operations across six snapshot branches against LinkedHashMap, full 4097-row override/removal/reinsertion boundaries, exact spelling of keys, invalid-but-legacy Map.put payload preservation, immutable views and iterator fences. Existing history counts/cursors, prepared replacement, cell replay boundaries, structure and TLS multiplayer passed. An initial compile failed at two remaining private History.cells references in snapshot admission; changed those to the equivalent storage.cellCount and reran successfully. No pass claimed for that attempt.

Serial component comparison: python3 tools/structures/run_shared_history_probe.py. Rebuilt only the exact parent WorldVoxels and nested classes against unchanged dependencies; current source/classes were fenced. Three warmups and 20 samples per sparse/fragmented/model fixture and prepare/install/scan/root-copy mode. Fragmented replay plus two histories: 2011752 -> 60096 reachable bytes; installation p95 6.777065 -> 0.052519 ms. Fragmented scan p95 2.423967 -> 3.956006 ms and median transient allocation 224 -> 557448 bytes. Ordinary 512-cell retained history 228488 -> 232616 bytes (about 1.8% growth); uniform model combined graph 1328 -> 1352 bytes. These mixed results are component observations, not whole-world peak heap/RSS, city/maximum or frame/network qualification. Do not claim universal memory/speed gains. Exact raw samples/class fences are under the unique ignored target/shared-history-probe directory; final public receipt is dashboard/evidence/structure-shared-history.json.

Full Java regression is running with a 1800-second bound: timeout 1800s mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" test (ignored output target/structure-shared-history-full.stdout; start/source fence target/structure-shared-history-full-start.json). Native workflow is running: python3 deploy/run_structure_playtest.py --display "$DISPLAY" (ignored output target/structure-shared-history-native.stdout). Do not read runtime-private.txt. Production source is frozen. No final full/native pass claimed yet. Finish actual checks, decode/inspect fresh media, verify hashes, audit restorations/deletions, then publish new source/media through controller feature_validation.

Remaining architecture: ordinary legacy replay still retains rows; v10 saves and the 2m-record limit remain. World.editsSnapshot eagerly flattens groups for DistantTerrainRenderer and WorldSkyVisibility; MultiplayerServer join-delta traversal also materializes rows. Region-backed authority/persistence and these eager consumers still need migration and measurement. Cell/chunk/model/geometry admission caps are unchanged, so required city/maximum fixtures remain unqualified. Full 20-run, physical-GPU, shaped 32-rendered-client, memory/frame/tick/stall/traffic/upload/cold-load and stable-rollout gates remain open.


### Join audit fix and final-source reruns

The first full regression passed with exit 0: all 222 fresh source suites, 1097 tests, zero failures/errors, one browserWorkflow skip. First native workflow also passed, all 237 production/harness hashes verified; its 25-frame/436708-byte/48.076-second video decoded and midpoint/conflict inspected. That source did not yet contain the join audit fix; do not use those as final-source passes. Preserved first clip under ignored target/structure-shared-history-before-join.mp4; source fences and earlier results remain in the final report as prior observations.

Audit found MultiplayerServer still flattened join snapshots under its authority lock. It now uses History.copyOf to retain compact roots. Audit also found a correctness gap: if undo removed the last fractional leaf while leaving the same clear row, comparing only current rows missed the removal. Delta comparison now visits both current and previous rows and deduplicates affected cells. Ready-phase row scanning remains a performance gate; no lock/frame budget claim.

HistoryJoinDeltaTest passed all three tests with exit 0. Its real TLS workflow places one fractional LED leaf, pauses a second client's real protocol handshake before READY (before it joins the broadcast peer set), undoes that placement through an authenticated actor, completes READY, and verifies the empty-cell STRUCTURE_COMMIT delta clears the stale snapshot. Metadata tests also cover additions/changes/removals and deduplication. Synthetic profiles/TLS/accounts only, existing login/structure limits unchanged, no credentials or runtime logs in evidence. A missing ModelLibrary test import was corrected before the successful run.

Final component comparison reran serially on the final compiled source, before other owned checks started. Final full regression is running with a 1800-second bound, ignored output target/structure-shared-history-final-full.stdout, source/start marker target/structure-shared-history-final-full-start.json. Final native workflow is running, ignored output target/structure-shared-history-final-native.stdout. Do not read runtime-private.txt. Main source is frozen after the join fix. Await actual completion, decode/inspect the new clip, verify source/class fences, finish audit/report, and use controller feature_validation for new source/media. Full city/max, region-backed persistence, ordinary legacy/render/ready-delta consumers and reference hardware/network qualification remain unfinished.

### Final-source local validation complete — new publication needed

Final full Java regression exited 0: all 223 source suites have fresh reports, 1100 tests, zero failures/errors, one AssetManagerIntegrationTest.browserWorkflow skip. No browser-workflow pass claimed. The three-test HistoryJoinDeltaTest includes the real TLS paused-READY/final-leaf-undo regression and passed on this source. Six Hub registration tests also passed with exit 0. Initial pre-join-audit results remain historical; the final report clearly scopes both runs.

Final native Main/GLFW workflow exited 0. All 237 production/harness hashes match frozen source and its canonical receipt digest. Fresh F10 video fully decoded: 27 frames, 456458 bytes, 49.858 seconds; SHA-256 1d00175be566baa074507190f23071ef1c44ca12b3f27dfe28de7f70057664d9. Inspected green footprint/anchor/staging midpoint and red 90-degree occupied-conflict screenshot. Software llvmpipe at 1 FPS is functional evidence only. Final production, validation and compiled dependency hashes match the serial component comparison and final test source. No required physical rendering/frame/network budget passed.

Final report: dashboard/evidence/structure-shared-history.json. New source implements shared compact bulk history, constant-root join snapshots and removed-row join delta detection. Ordinary legacy load, v10 save limits, eager World/render/ready-delta row consumers and region-backed persistence remain work. Geometry/chunk/model admission caps were not raised. Required full city/maximum 20-run and reference physical-GPU/shaped 32-client qualification remain unpassed; stable rollout stays gated.

Final diff check passed. Ten restored MP3/Hub files still match review base 71612f09bfec4549456b3317676e76cdced085a4; no deleted files or unmerged index. Intended code removal only replaces WorldVoxels' private flat History implementation with SegmentedHistory/CellReplayRows while preserving public Map/cursor behavior. No unrelated feature removed. Runner must publish new source/media via feature_validation and record actual exact-head hosted checks. This handoff is not review submission or readiness. If latest-master integration changes source, rerun meaningful checks and media on the resulting source before review. Continue remaining large-world implementation after hosted results; no owner answer needed.

### Shared render-history snapshots — validation in progress

Exact parent 09aa3ac04f9dde501b5e5a623b28389e22bf5da4 hosted run 42745800605 completed successfully: mac-arm, mac-intel, Linux and Windows builds passed; publication skipped. This is not review approval or city/maximum qualification.

World.editsSnapshot now copies per-chunk History roots into a detached ordered map and exposes lazy immutable rows. It no longer eagerly creates a LinkedHashMap entry and materialized replay row for every compact cell leaf. Chunk order and row order, exact key lookup, size and read-only Map behavior remain. Owner capture is still required. Snapshot capture remains O(chunks), and scans/lookups still materialize rows; downstream mesher work, peak memory and frame budgets are unqualified. This does not remove persistence, ordinary replay or city admission limits.

All 25 focused tests in five suites passed, including new concurrent owner-mutation/worker-read, complete fragmented rows, negative coordinates, malformed lookups, read-only views, replacement and close isolation. Preliminary compilation found the World history field uses Map values rather than the concrete History type; the constructor now accepts the declared Map type and History.copyOf retains its generic fallback. No failed attempt is counted as a pass. A probe filename collided with an older diagnostic; that older file was restored byte-for-byte and the new tool is WorldEditSnapshotProbe.java.

Final source is frozen. Full regression is running with a 1800-second bound, output only in ignored target/structure-render-snapshot-full.stdout; native production Main/GLFW workflow is running on inherited DISPLAY, output only in ignored target/structure-render-snapshot-native.stdout. Do not read runtime-private.txt. Initial 20-run snapshot-creation comparison completed, but the extended scan/lookup and ordinary-cell comparison still needs an isolated run after the active checks finish. No final full/native or rendering performance pass claimed yet. Finish checks, inspect fresh media, record source fences and mixed component results, audit the diff, and request publication of new source/media through feature_validation. All city/maximum, physical-GPU, shaped 32-client, memory/frame/tick/stall/traffic/cold-load/durability qualification and stable-rollout gates remain pending.

The initial root-only implementation passed all 224 current suites (1102 tests, zero failures/errors, one browserWorkflow skip) and the native workflow. Fresh-source report matching excluded five historical, separately named XML reports; they were preserved and not counted. The video had 24 decoded frames, 486284 bytes, 46.991 seconds; green staging/anchor and red 90-degree occupied conflict views were inspected. These checks predate the adaptive selection below and must not be reported as final-source passes. The old clip is preserved in ignored target/structure-render-snapshot-before-adaptive.mp4.

Extended component measurements exposed a sparse-history regression: for 512 ordinary cells/chunks, source plus root snapshot reached 504560 bytes versus flat 410208 bytes. For eight fully fragmented cells (32776 rows), roots reached 477424 bytes versus flat 5719120 bytes. Scans/lookups also cost more transient allocation and time. Production now keeps the previous flat algorithm at no more than eight rows per indexed chunk, otherwise shares roots. This conservative internal storage threshold is not an admission cap or a latency guarantee. A boundary regression covers empty, eight and nine rows, lookup/order, later mutation and immutable views. Source changed, so final focused/full/native and both isolated component comparisons must rerun. Earlier results remain scoped to root-only source; no final adaptive pass is claimed yet.

### Adaptive final-source validation complete — publication needed

Final focused checks passed all 26 tests in five suites. Final full regression exited 0: all 224 current source suites have fresh reports, 1103 tests, zero failures/errors, one AssetManagerIntegrationTest.browserWorkflow skip. Five historical, separately named reports were preserved and excluded; no current suite is missing or extra. No browser-workflow pass claimed. Final source hashes remained unchanged.

Playtest: fresh production Main/GLFW workflow exited 0 on inherited DISPLAY with an isolated synthetic offline profile. Capture/name/anchor/library, long-path import/export, four rotations, footprint/conflicts, occupied/player rejection, empty-space preservation, cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution model dependency/restart, inventory/editor regressions passed. All 238 production/harness hashes and canonical receipt digest match. Fresh F10 video decoded completely: 25 frames, 563976 bytes, 44.454 seconds, 960x544; SHA-256 c7e455df7466601ae0a1b89ff146fb7b6aa42e30270d038e5e469c7a05fb10fa. Inspected the final green staging/anchor midpoint and red 90-degree occupied-conflict image. llvmpipe at 1 FPS is functional evidence only.

Final component probes ran serially after both long checks exited: python3 tools/structures/run_world_snapshot_probe.py, then the same command with ordinary. Three warmups, 20 samples per mode. Fragmented source plus snapshot reachable graph: flat 5719120 bytes, adaptive 477424 bytes; creation median allocation 6262128 -> 2192 bytes, p95 8.140080 -> 0.108208 ms. Full scan p95 increased 1.660299 -> 7.089069 ms, median allocation 524512 -> 4983944 bytes. Full lookup p95 increased 11.804194 -> 21.926991 ms, median allocation 32 -> 5505440 bytes. Ordinary 512-cell/chunk graph: 402000 bytes for both; adaptive creation adds 32 median allocated bytes, median time increased while p95 decreased. No universal memory/speed claim. Graph observations exclude geometry and other owners; they are not peak-memory/whole-world or city qualification. Source/classes/validation hashes verified after both probes. Full results: dashboard/evidence/structure-render-snapshot.json.

Final diff check passed; ten MP3/Hub files match review base 71612f09bfec4549456b3317676e76cdced085a4 byte-for-byte. No file deletions or unmerged index. The older WorldSnapshotProbe.java diagnostic was restored byte-for-byte. Intended code removal replaces World.editsSnapshot's eager body with the adaptive factory; the previous flat path remains for sparse histories. No unrelated feature removed. No agent commit/push/merge occurred.

Runner must publish the new source, report and fresh media using feature_validation and obtain actual exact-head hosted results. This is not review submission or readiness. Geometry/instance/chunk/replay admission caps remain unchanged. Ordinary legacy load, v10 persistence, full city/maximum admission, at least 20 reference operation runs, physical GPU, shaped 32-rendered-client synchronization, frame/tick/ack/stall/traffic/peak-memory/upload/cold-load/durability qualification and independent review remain unfinished. Stable rollout stays gated. Continue the region-backed authority/persistence and remaining eager row consumers after hosted results. If latest-master integration changes source, rerun meaningful checks and media before review. No owner answer is needed.

### Bounded exact v10 replay loading — validation running

Exact parent 043b9f9d1beb0c35c475b717157b7e2f6c017e5d: controller reports all platform builds passed. Initial direct run 42752407990 still showed Intel Mac running; separate exact-head run 42752411869 was inspected and completed with all four builds successful and publication skipped. No unchanged wait or new dispatch requested.

RegionReplayLoader carries at most 4097 candidate rows across validated sections. It compacts only an exact depth-zero clear followed by valid, disjoint fractional rows in the same cell. Primitive rows retain original order, positions, types and full colours; geometry is not rebuilt or normalized. Other legacy sequences use ordinary replay. Noncandidate rows bypass buffering. Compact replacement includes the initial clear's prior-row removals in the revision stamp, matching ordinary replay. LocalGame still validates the complete spool, drops and EOF before building private resident history. RegionReplay.read is also private staging; a later failure requires discarding its target. This is not live paging, atomic publication, crash durability or a format change. The 2-million-record and other production caps remain unchanged.

All 32 focused tests in six suites passed: eight new loader cases plus section/spool/history/TLS join and OfflineWorldReplay regressions. Cases cover arbitrary fine-leaf order across section boundaries, recurring cells, mixed depth and redundant air, source/snapshot isolation, exact revisions and re-encoded bytes, overlap/duplicate/nonclear fallback, invalid rows, 9001-row same-cell boundedness and cancellation inside eligibility before group commit with retry. A real v10 LocalGame fixture loads, saves and restarts with byte-exact output. Initial new-test failures incorrectly expected copied-history revision stamps to persist and treated coloured air as valid; those test expectations were fixed. No failed attempt counted as passed.

Serial component comparison rebuilt the exact parent RegionReplay decoder against the unchanged ordinary History paths. Three warmups and 20 runs per canonical/legacy fixture in separate 512 MiB JVMs. Each fixture has eight cells and 32776 rows; exact ordered entries, revision stamps and complete re-encoded bytes match after every read. Canonical retained history 10560744 -> 473208 bytes; read p95 86.718425 -> 18.646868 ms; median read allocation 55935216 -> 5294480 bytes. Re-encode p95 25.462874 -> 15.801066 ms, but median allocation rises 6298112 -> 9968512 bytes. Full keyed lookup p95 16.090541 -> 18.122550 ms, allocation 32 -> 5505440 bytes. Legacy fallback retained history is unchanged at 10560744 bytes; read p95 89.881419 -> 88.573434 ms, median allocation adds 48 bytes. Timing variation/JIT/shared VPS prevents a universal speed claim. Retained graph is one history, not peak RSS or combined LocalGame/live World; live startup still rebuilds ordinary World histories. Disk/tail staging, geometry, capture/placement, frames/ticks and networking are excluded.

Production source is frozen. Full regression is running with a 1800-second bound, output only in ignored target/structure-replay-loader-full.stdout; start/hash fence target/structure-replay-loader-start.json. Native production Main/GLFW workflow is running on inherited DISPLAY, output only in ignored target/structure-replay-loader-native.stdout. Do not read runtime-private.txt. Await actual completion, inspect/decode fresh media, verify final hashes and diff, save a sanitized report, then request feature_validation for new source/media. No final full/native pass claimed yet. Region-backed authority/persistence, live World replay memory, city/max admission and full 20-run physical-GPU/shaped-32-client/frame/tick/ack/stall/traffic/peak-memory/upload/cold-load/durability qualification remain unfinished. Rollout gates stay in place; no owner answer is needed.

The first full loader regression exited 0:225 fresh suites,1111 tests,zero failures/errors,one browserWorkflow skip. First loader native workflow also passed;239 production/harness hashes verified. All23 video frames decoded,504365bytes,43.114seconds; final staging/conflict images inspected. These results precede the sparse eligibility refinement below and are not final-source passes. The clip is preserved in ignored target/structure-replay-loader-before-sparse-opt.mp4.

Additional sparse/threshold component audit found eight two-row cells allocated44512bytes per load with the4096-byte boolean occupancy map, versus19352bytes on the old decoder, despite a smaller retained graph(8056->4984bytes). At33rows/cell, retained history still fell86544->83064bytes. Source now uses allocation-free pairwise local overlap tests for groups<=32rows and a64-long/512-byte occupancy map for larger groups. Each strategy retains exact file order and detects overlap; aligned voxel positions are unchanged. A differential test compares both strategies with the existing WorldVoxels.covered oracle at1,2,8,31,32,33,64leaves, shuffled mixed depths and duplicate rejection. This is an internal validation choice, not an admission-cap change.

Final focused validation is running in target/structure-replay-loader-final-focused.stdout. Rerun the now four-fixture component comparison, then final full/native checks on frozen refined source. Earlier timing/allocation results must stay scoped to the earlier bitmap source until replaced with final measurements. No final refined full/native pass claimed yet. All full-city, physical, network and stable-rollout gates remain pending.

Final refined focused checks passed all33 tests in six suites. Final refined four-fixture component comparison also passed, with20 measured loads per mode/fixture and byte/order/revision checks after each. Dense load p95 88.451316->19.326817ms, median allocation55935216->5265808bytes; retained history10560744->473208bytes. Sparse allocation19352->11616bytes and retained8056->4984bytes; sparse load p95 increases0.256178->0.341853ms. Threshold33-row retained86544->83064bytes. Save/lookup allocation and time tradeoffs are recorded in docs/structures.md and raw refined comparison. No universal speed/peak-memory claim.

Final refined source is frozen. Full regression is now running with a1800-second bound in target/structure-replay-loader-final-full.stdout; marker target/structure-replay-loader-final-start.json. Native workflow is running in target/structure-replay-loader-final-native.stdout. Do not read runtime-private.txt. Refined component pointer target/structure-replay-loader-refined-probe.stdout; final report builder target/structure-replay-loader-build-report.py now requires this final marker/component and33 focused tests. Finish actual long checks, decode/inspect new structure-replay-loader media, build report, verify audit and hand off new source/media through feature_validation. No final refined full/native pass claimed yet. Physical/city/network and rollout gates stay pending.

### Refined v10 loader final validation complete — publication needed

Final full regression exited0: all225 current source suites have fresh reports,1112 tests,zero failures/errors,one AssetManagerIntegrationTest.browserWorkflow skip. Five historical reports were preserved and excluded; no current suite is missing or extra. Final focused checks passed33 tests in six suites. The four-fixture,20-run-per-mode component comparison matches final source and compiled dependency hashes. No browser-workflow pass claimed. Earlier bitmap-source passes remain historical.

Playtest: final refined production Main/GLFW workflow exited0 on inherited DISPLAY with an isolated synthetic offline profile. Capture/name/anchor/library, long-path import/export, four rotations, footprint/conflicts, occupied/player rejection, holes, cancellation, malformed import atomicity, repeated placement, one-operation undo,32-resolution dependency/restart and inventory/editor regressions passed. All239 production/harness hashes and canonical receipt digest match. Fresh F10 clip decoded completely:26frames,499238bytes,47.817seconds,960x544; SHA-256 e0bdd9f5e0a825f5ec0e58570fa465ecf7ce3f09777457abb049058d19dde69c. Inspected final staging/anchor midpoint and red90-degree occupied-conflict image. llvmpipe1FPS is functional evidence only; no physical/frame budget passed.

Report: dashboard/evidence/structure-replay-loader.json. Exact ordered rows, revision stamps and complete re-encoded bytes match every measured load. Dense and sparse retained-history graphs fall; sparse load time and save/lookup allocation can rise. All median,p95,worst/raw samples and scope exclusions are recorded. These are decoder-only synthetic observations, not city/maximum, peak-memory or live World qualification. Startup still expands ordinary live histories and geometry; the2-million-record barrier and other production admission caps remain.

Final audit: diff check passed; ten MP3/Hub restorations match review base71612f09bfec4549456b3317676e76cdced085a4 byte-for-byte. No file deletions or unmerged index. Intended code removal replaces per-row regional drains with the bounded loader and retains ordinary fallback; the private CellReplayRows constructor now takes explicit coordinates. No unrelated feature removed. No agent commit/push/merge occurred.

Runner must publish new source/report/media using feature_validation and obtain actual exact-head hosted results. This is not review submission or readiness. Continue region-backed authority/persistence and live startup replay after hosted results. Full city/maximum admission and at least20 reference operation runs, physical GPU, shaped32-rendered-client synchronization, frame/tick/ack/stall/traffic/peak-memory/upload/cold-load/durability qualification and independent review remain unfinished. Supported limits and stable rollout stay gated. Latest-master integration that changes source requires meaningful reruns and fresh media before review. No owner answer is needed.

### Compact private World startup — implementation in progress

Exact-head 03448e35e9cf340b0b4e8c1ad9b6a7d44a1afabb hosted run 42759290438 passed Linux, Windows, mac-arm and mac-intel builds; publish skipped. Exact-head report/video were fetched and match local SHA-256. This is not readiness or review approval.

World InitialReplay now uses one bounded replay writer per contiguous cell, keeping the existing per-row geometry and noteEdit path. The writer reuses the validated compact loader and flushes before private geometry installation. Disjoint clear-led rows keep their exact order; legacy/overlap rows retain ordinary fallback. The target remains worker-private and must be discarded on failure. This removes one per-row history rebuilding path, not geometry residency, region paging, durable persistence or admission barriers. Public writer validates input and rejects reuse after finish. Focused integration checks are running; no new-source pass claimed yet. Full city/maximum, physical GPU, shaped 32-client and all rollout gates remain pending.

Focused startup validation passed 21 tests in four suites, zero failures/errors/skips. Exact-parent component comparison completed: three fixtures, three warmups and 20 samples per fixture/mode, with exact order/revision/complete-geometry checks each time. Dense retained history 10542256->475424bytes and load p95 93.177193->58.492309ms; sparse retained 10368->7072bytes but p95 0.145147->0.389077ms; legacy retained unchanged and p95 125.481797->155.301425ms. Full results and scope will be in structure-startup-compact-history.json after final checks. No city/peak-memory/frame claim.

Production/test source is frozen at target/structure-startup-compact-start.json. Full regression runs with 1800-second bound in target/structure-startup-compact-full.stdout; native workflow runs on inherited DISPLAY in target/structure-startup-compact-native.stdout. Source compilation remains unchanged; do not read runtime-private.txt. Finish actual checks, decode/inspect new media and verify receipts/source fence before publication handoff. No new-source full/native pass claimed yet.

Playtest: new-source production Main/GLFW workflow exited 0 on inherited DISPLAY with an isolated synthetic offline profile. All 239 production/harness source hashes and canonical receipt digest match. Capture/name/anchor/library, long import path, all four rotations, preview/conflicts, occupied/player rejection, holes, cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution model dependency/restart and inventory/editor regressions passed. All 23 fresh video frames decoded; 504297 bytes, 42.763 seconds, 960x544; SHA-256 d149fd95040b6076cbbc159664c216c1cc958360325f3f52e91bef0a5ce324ac. Inspected green staging/anchor midpoint and red 90-degree occupied-conflict screenshot. llvmpipe at 1 FPS remains functional-only evidence. Full regression is still running; no full-suite pass claimed yet.

### Private startup compact history — local validation complete

Full regression exited 0: 226 fresh source suites, 1115 tests, zero failures/errors, one AssetManagerIntegrationTest.browserWorkflow skip. All current test suites match fresh reports; five historical reports were preserved and excluded. Focused validation passed all 21 tests in four suites. Native workflow and complete video decode passed as recorded above. Source fences, 239 native source/harness hashes and receipt digest match. The exact-parent component comparison records all raw samples and its competing-load caveat; it is not a controlled benchmark or city qualification.

Report: dashboard/evidence/structure-startup-compact-history.json. A report filename initially collided with the older structure-startup-replay.json; that older report was restored byte-for-byte and new results use the distinct name. The source/test/probe edits and fresh media are preserved locally. No agent commit, push, merge or publication occurred.

Final audit: git diff --check passed; no deletions or unmerged index. All ten unrelated MP3/Hub restorations remain byte-exact against review base 71612f09bfec4549456b3317676e76cdced085a4. Intended removal replaces InitialReplay's per-row history insertion with a bounded writer; per-row geometry, counters and sky metadata remain. No unrelated feature removed. Tracked and untracked status inspected.

Request feature_validation to publish this new source/report/media and obtain exact-head hosted results. This is not review submission or readiness. Next work remains region-backed authority/persistence, resident geometry and legacy replay memory, and city/maximum admission barriers. Full city/maximum reference fixtures and at least 20 operation runs, physical GPU, shaped 32-rendered-client synchronization, peak memory/frame/tick/traffic/ack/stall/upload/cold-load/durability qualification, latest-master source integration and independent review remain pending. Supported limits and stable rollout stay gated. No owner answer is needed.

### Packed private startup geometry — implementation in progress

Exact-head 5d0811e0f1d0ebda29ab4b18067fc1de029cc21c run 42762543298 passed all four platform builds; publication skipped. This is not review approval or readiness.

Private InitialReplay flush now prepares a factory-owned immutable CellImage after history drain, checks cancellation, installs it and shares its frozen tree with the edited-cell index. It no longer retains a node-backed canonical tree plus a second wrapper. Mutable replay stays limited to the current cell; returning visits copy the installed image before editing. History row order/revisions, per-row counters/sky metadata and geometry semantics remain. Existing model, geometry, instance, chunk and persistence caps remain.

Focused integration tests are running. Next measure the union of source/captured history, World history, canonical cell geometry and edited-cell index, plus lookup costs. Run this component comparison before full/native checks to avoid competing owned check load. No new-source test or performance pass claimed yet. Region-backed authority/persistence, full city/maximum admission and physical/network/reference qualification remain pending; stable rollout stays gated.

Packed startup focused checks passed all 27 tests in six suites. Exact-parent component comparison completed before full/native checks: three warmups and 20 samples per mode/fixture, with ordered rows, per-history/World revisions, complete cell geometry and query checksums verified. Dense combined retained cell-owner graph 2075376->1120816 bytes; sparse 17904->15984; legacy 20656528->19701968. Dense startup p95 rises 32.666706->35.923935 ms; 256-point query p95 rises 0.154555->1.326647 ms. Current legacy query p95 is 1.455901 ms versus 0.077380 ms. Record the memory/query tradeoff and graph exclusions; no peak-memory, city or frame qualification claim.

Production/test source is frozen in target/structure-startup-image-start.json. Full regression runs with 1800-second bound in target/structure-startup-image-full.stdout; native production workflow runs on inherited DISPLAY in target/structure-startup-image-native.stdout. Component pointer: target/structure-startup-image-probe.stdout. Do not read runtime-private.txt. Finish actual checks, decode/inspect fresh media, build the distinct structure-startup-packed-geometry.json report, verify source fences/audit and request feature_validation for new source/media. No full/native new-source pass claimed yet. All physical/reference/network and rollout gates stay pending.

Playtest: packed-startup production Main/GLFW workflow exited 0 on inherited DISPLAY with an isolated synthetic offline profile. All 239 production/harness hashes and canonical receipt digest match. Capture/name/anchor/library, long-path import/export, all four rotations, preview/conflicts, occupied/player rejection, holes, placement/undo/import/export cancellation, malformed import atomicity, repeated placement, single-operation undo, 32-resolution model dependency/restart and inventory/editor regressions passed. All 25 video frames decoded; 495878 bytes, 48.283 seconds, 960x544; SHA-256 6a49f203c0c722ded30470bde7353f3ebc0df041868ab3a76234362862ea0440. Inspected fresh green staging/anchor midpoint and red 90-degree occupied rejection. llvmpipe at 1 FPS remains functional evidence only. Full regression is still running; no full-suite pass claimed yet.

### Packed private startup geometry — local validation complete

Full regression exited 0: 226 fresh source suites, 1117 tests total, zero failures/errors and one AssetManagerIntegrationTest.browserWorkflow skip. All current suites match fresh XML reports; five historical reports were preserved and excluded. All 27 focused tests in six suites passed. Native Main/GLFW workflow passed, all 239 source/harness hashes and receipt digest match, and all 25 frames decoded and were visually sampled as recorded above. No browser or physical/frame-budget pass is claimed.

Report: dashboard/evidence/structure-startup-packed-geometry.json. Source and compiled hashes match the component comparison; probe/driver hashes match. The comparison ran before owned full/native checks and records all 20 raw samples per fixture/mode, combined owner-graph scope, allocation and query costs. Dense combined retained owners fall 2075376 -> 1120816 bytes, but dense 256-query p95 rises 0.154555 -> 1.326647 ms and startup allocation rises. This is a measured memory/query tradeoff using the existing representation, not peak memory or city qualification.

Final audit: diff check passed; all ten MP3/Hub restorations remain byte-exact against review base 71612f09bfec4549456b3317676e76cdced085a4. Both older startup reports remain byte-exact. No deletions or unmerged index; tracked and untracked status inspected. Intended removal replaces private startup's ordinary geometry store and duplicate wrapper with the existing immutable CellImage. No unrelated feature removed. No agent commit, push, merge or publication occurred.

Request feature_validation for this new source/report/media and obtain exact-head hosted results. This is not review submission or readiness. Next implementation remains region-backed authority/persistence, legacy history/whole-world metadata, and city/maximum admission barriers. Full city/maximum reference fixtures and at least 20 operation runs, physical GPU, shaped 32-rendered-client synchronization, peak memory/frame/tick/traffic/ack/stall/upload/cold-load/durable-save qualification, latest-master integration and independent review remain pending. Supported limits and stable rollout stay gated. No owner answer is needed.

### Legacy offline compact history — implementation in progress

Exact head cd84bc2251448cbd19577b731165dc395fb09f94 hosted run 42765979156 passed all four platform builds; publish skipped. This is not readiness or independent review.

The private v3-v9 LocalGame load loop now feeds the same bounded ReplayWriter used by regional/private World replay, then finishes before final progress. It still decodes each version with its original reader and validates each row. Clear-led disjoint fractional groups retain compact rows in exact file order; nonclear/overlap sequences use the same ordinary fallback. Save formats, model definitions, inventory/drop parsing, progress counts and existing limits remain. No legacy/server put semantics are changed; server legacy loading is a separate path. A later failure discards the private LocalGame.

Focused checks are running for v3-v9 semantics, dense exact v8/v9 bytes and dependencies, repeated/overlapping runs, invalid rows/tails, cancellation and callbacks. No new-source pass claimed yet. Measure actual private LocalGame loads against the exact parent before full/native checks. Region-backed persistence, city/max admission and full reference/physical/network qualification remain pending. Stable rollout stays gated.

Final focused checks passed all 29 tests in five suites, including OfflineWorldReplay integration. The first new compatibility test wrongly expected black for a v5 LED; corrected to its existing white default. A summary selector referenced a nonexistent TerrainVersionTest; final focused selection names actual suites and no unexecuted suite is counted. No failed attempt is counted as passed.

Exact-parent component comparison completed before full/native checks: four real legacy file fixtures, three warmups and 20 samples per mode/fixture. Every load checks exact ordered rows, revision, keyed values, dependency fingerprints and complete saved bytes. v8 dense retained loaded graph 10625432->537896 bytes, load p95 91.006949->20.151027 ms; v9 dense graph 10626704->539208 bytes and p95 67.141269->21.245946 ms. Nonclear graph remains 10625432 bytes; median/worst load time rises. Graph excludes live World/geometry, fixture arrays, path objects and peak staging/process memory. Source/probe/compiled hashes are recorded. No cold-cache or city qualification claim.

Production/test source is frozen in target/structure-legacy-replay-start.json. Full regression runs with 1800-second bound in target/structure-legacy-replay-full.stdout; native workflow runs on inherited DISPLAY in target/structure-legacy-replay-native.stdout. Component pointer: target/structure-legacy-replay-probe.stdout. Do not read runtime-private.txt. Finish actual checks, decode/inspect fresh media, create distinct structure-legacy-offline-load.json, verify fences/audit and request feature_validation for new source/media. No full/native new-source pass claimed yet. Full-scale reference, physical/network and stable-rollout gates remain pending.

Playtest: legacy-load production Main/GLFW workflow exited 0 on inherited DISPLAY with an isolated synthetic offline profile. All 239 production/harness source hashes and canonical receipt digest match. Capture/name/anchor/library, long-path import/export, all four rotations, footprint/conflicts, occupied/player rejection, holes, cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution model dependency/restart and inventory/editor regressions passed. All 25 fresh video frames decoded; 524915 bytes, 45.468 seconds, 960x544; SHA-256 65ecc7bb0aadbb8830c35f738c12dfc1c07436f864f17ea1ebd235b9766d2a6f. Inspected green staging/anchor midpoint and red 90-degree occupied rejection. llvmpipe at 1 FPS remains functional-only evidence. Full regression remains running; no full-suite pass claimed yet.

### Legacy offline compact history — local validation complete

Full regression exited 0: 227 fresh source suites, 1122 tests total, zero failures/errors, one AssetManagerIntegrationTest.browserWorkflow skip. All current suites match fresh reports; five historical reports were preserved and excluded. All 29 focused tests in five actual suites passed. Native workflow passed; all 239 source/harness hashes and receipt digest match. All 25 fresh video frames decoded and representative staging/conflict frames were inspected. No browser or physical/frame-budget pass is claimed.

Report: dashboard/evidence/structure-legacy-offline-load.json. Source and compiled hashes match the actual-file component comparison; probe and driver hashes match. Four fixtures ran three warmups and 20 samples per mode before owned full/native checks, verifying exact ordered history, revisions, dependencies and complete saved bytes. Eligible dense v8/v9 histories retain less memory; nonclear history remains ordinary and timing variation/regressions are recorded. These are warm-cache private-load observations, not cold-region, peak-memory or city qualification.

Final audit: diff check passed; all ten unrelated MP3/Hub restorations remain byte-exact against review base 71612f09bfec4549456b3317676e76cdced085a4. Three older startup reports remain byte-exact. No deletions or unmerged index; tracked/untracked status inspected. Intended removal replaces the legacy offline per-row history call with the existing bounded writer; version-specific decoding and ordinary fallback remain. No unrelated feature removed. No agent commit, push, merge or publication occurred.

Request feature_validation for this new source/report/media and obtain exact-head hosted results. This is not review submission or readiness. Next work remains region-backed authority/persistence, whole-world metadata, nonclear and multiplayer legacy history, and city/maximum admission barriers. Preserve multiplayer legacy keyed-put semantics during any migration. Full city/maximum reference fixtures and at least 20 operation runs, physical GPU, shaped 32-rendered-client synchronization, peak memory/frame/tick/traffic/ack/stall/upload/cold-load/durable-save qualification, latest-master integration and independent review remain pending. Supported limits and stable rollout stay gated. No owner answer is needed.

### Keyed legacy server history and bounded overrides — implementation in progress

Exact head bd80f5b76338ed069f59a533671143a98a3f871d run 42770380617 passed all four platform builds; publish skipped. This is not readiness or review approval.

KeyedReplayLoader buffers at most 4097 rows. Only exact clear-led disjoint groups for previously absent cells can become compact segments. All other cases keep pure Map.put behavior, retaining overlapping rows and old key positions. Legacy server validation still checks each row and model before intake; save formats, player/drop tail and admission caps are unchanged. Reusing the ordinary replay sink here would have pruned old rows, so it is deliberately not used.

Inspection found a separate scalability hazard: repeated updates of a compact segment copied its whole override map. Segment overrides now use immutable copy-on-write pages of at most 32 rows, with directory size bounded by the cell replay size (at most 4097 rows). Each point overwrite clones one page/directory; masked removal clones touched pages and releases empty pages. Frozen snapshots keep the previous arrays. Ordinary non-bulk histories remain unchanged.

Initial integration passed four keyed tests, three real server/TLS tests and existing focused suites. Tests cover complete repeated cells, alternating overwrites, exact independent LinkedHashMap order/revisions, later coverage removal, snapshot isolation, 9001-row buffering bounds, invalid/cancelled input, legacy resave/restart, missing dependencies/bad tail and actual TLS late joins. The first server fixture compile missed its checked IOException declaration; fixed. The empty-page cleanup changed during the first integration run, so final-source focused checks and component comparison must rerun before full/native validation. No final-source full/native pass claimed. Region-backed authority/persistence, city/max and physical/network/reference qualification remain pending; stable rollout stays gated.

Final-source focused checks passed all 30 tests in eight suites. The five-fixture component comparison completed before owned full/native checks: exact parent storage, three warmups and 20 samples per mode/fixture, with ordered rows/revisions/keyed values, old snapshots and later coverage verified. Dense history graph 10560728->473160 bytes; recurring graph 2613488->549848. For 512 compact overwrites, median allocation 12532784->701344 bytes and p95 17.405404->0.435113 ms; retained graph 100368->86936 bytes. Sparse intake p95 rises 0.032727->0.194436 ms; nonclear storage is unchanged. Full raw measurements and exclusions are recorded; no server/city/peak-memory/frame claim.

Production/test source is frozen in target/structure-keyed-replay-start.json. Full regression runs with 1800-second bound in target/structure-keyed-replay-full.stdout; native workflow runs on inherited DISPLAY in target/structure-keyed-replay-native.stdout. Component pointer: target/structure-keyed-replay-probe.stdout. Do not read runtime-private.txt. Finish actual checks, decode/inspect fresh media, create distinct structure-keyed-server-history.json, verify source/compiled fences and audit, then request feature_validation for new source/media. No full/native new-source pass claimed yet. All full-scale qualification and stable-rollout gates remain pending.

Playtest: keyed-history production Main/GLFW workflow exited 0 on inherited DISPLAY with an isolated synthetic offline profile. All 240 production/harness source hashes and canonical receipt digest match. Capture/name/anchor/library, long-path import/export, all four rotations, footprint/conflicts, occupied/player rejection, holes, cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/editor regressions passed. All 26 fresh video frames decoded; 539597 bytes, 47.886 seconds, 960x544; SHA-256 3b3cb702ebe7c0bf317319e25f6aaea0fbcde42b0b491c6ba049c142ca3e9e1f. Inspected green staging/anchor midpoint and red 90-degree occupied rejection. llvmpipe at 1 FPS remains functional-only evidence. Full regression is still running; no full-suite pass claimed yet.

Full regression ended with exit 143 after approximately 710 seconds, before its 1800-second bound. No cause is established and no full-run pass is claimed. Fresh XML covers 147 of 229 source suites: 805 tests, zero failures/errors, one skip. Source fence matches; no Java/timeout test process remained in the assigned worktree. The 82 unfinished suites are running on the same frozen source through a bounded continuation. Receipt/initial XML digests: target/structure-keyed-replay-continuation.json; output only in ignored target/structure-keyed-replay-continuation.stdout. Preserve the interruption and report aggregate suite coverage only after actual continuation success; do not label the interrupted command passed. Exact-head hosted full validation remains required. No owner answer is needed.

### Keyed server history and paged overrides — local suite coverage complete

The bounded continuation exited 0 and completed all 82 unfinished suites on unchanged source. Aggregate fresh coverage now matches all 229 source suites: 1129 tests total, zero failures/errors and one AssetManagerIntegrationTest.browserWorkflow skip. All 147 initial completed XML reports retain their recorded SHA-256. The initial full command still has exit 143; no uninterrupted full-command pass is claimed. Exact-head hosted full validation remains required. All 30 final-source focused tests in eight suites passed separately.

Native production Main/GLFW workflow passed, all 240 source/harness hashes and receipt digest match, and all 26 video frames decoded. Fresh staging/anchor midpoint and red 90-degree occupied rejection were inspected. Clip: 539597 bytes, 47.886 seconds, SHA-256 3b3cb702ebe7c0bf317319e25f6aaea0fbcde42b0b491c6ba049c142ca3e9e1f. Software rendering at 1 FPS remains functional-only evidence. No physical/frame budget or browser-workflow pass claimed.

Report: dashboard/evidence/structure-keyed-server-history.json. Source/compiled/probe/driver fences match the five-fixture component comparison. It records 20 samples per mode, history-only retained graphs, sparse timing regression and overlay snapshot/coverage scope. These are not full-server, peak-memory or city measurements.

Final audit: diff check passed; all ten unrelated MP3/Hub restorations remain byte-exact against review base 71612f09bfec4549456b3317676e76cdced085a4. Four older reports remain byte-exact. No deletions or unmerged index; tracked and untracked status inspected. Intended removal routes legacy server keyed insertion through a bounded pure-put loader and replaces compact whole-map override copies with immutable pages. No unrelated feature removed. No agent commit, push, merge or publication occurred.

Request feature_validation for this new source/report/media and exact-head hosted checks. This is not review submission or readiness. Next work remains region-backed authority/persistence, whole-world metadata, ordinary nonclear history, and city/maximum admission barriers. Full city/maximum reference fixtures and at least 20 operation runs, physical GPU, shaped 32-rendered-client synchronization, peak memory/frame/tick/traffic/ack/stall/upload/cold-load/durable-save qualification, latest-master integration and independent review remain pending. Supported limits and stable rollout stay gated. No owner answer is needed.

### Bounded private server geometry replay — implementation in progress

Exact head 8c581bd121f9237198aceb5abee5825f91107efd passed all four platform builds in run 42776503181, including Maven verify and packaged MP3 decoding; publish skipped. This closes that hosted build wait, not the remaining feature qualification.

Server construction now replays adjacent saved rows into one mutable cell and installs one packed immutable image per contiguous run. Returning to an earlier cell starts from its previously installed image. It keeps original row order, encoding, explicit air and the global geometry change count. Replay is restricted to a fresh unpublished world; any failure or cancellation requires discarding that private world. It is not a live-world transaction. History, save formats and admission caps are unchanged. This removes repeated per-row cell copies during private startup; region-backed persistence and whole-world residency remain unfinished.

The first focused run rejected a random glass test row with a nonzero LED-only colour. The fixture is corrected. The initial native harness compile overlapped Maven class rebuilding and failed to find classes; rerun only after Maven completes. Neither failed attempt is counted as passed. Final focused checks, a bounded synthetic per-row/current component comparison and fresh native workflow/media are pending. Full city/maximum, physical GPU and shaped 32-client qualification remain pending; stable rollout stays gated.

The first component probe exposed a material regression: alternating single-row revisits packed the same cell repeatedly (p95 0.498480->26.317374 ms; worst allocation 196936->6478024 bytes). Single-row runs now use ordinary replacement, preserving their existing geometry representation. Multi-row runs retain packed images. Rerun final-source focused/regression checks, component measurements and native workflow after this change; earlier results do not validate this final source. Retain raw initial measurements in ignored target scratch; do not hide the failed candidate.

Final-source validation exited 0: all 78 tests in 14 selected suites passed with zero failures/errors/skips. Selection includes the four new replay tests, real legacy server save/restart/TLS late join, regional restart, structure/multiplayer/city/recipe regressions, server tick checks, world streaming and private client startup. This is selected affected-path coverage; a full local suite was not rerun. Exact-head hosted full verification remains required for the new source.

The corrected component probe completed three warmups and 20 alternating-order samples per mode/fixture in one 512 MiB JVM after the test run ended. All geometry matches the per-row oracle. Dense two-cell retained geometry root is 281152->42560 bytes; median allocation 3146200->2730296 bytes; p95 7.122046->11.242843 ms. Sparse retained graph is 4632 bytes in both paths; p95 0.525411->0.863695 ms. Alternating retained graph is 24832 bytes in both; p95 0.370908->0.988388 ms and median allocation 196936->209240 bytes. The fallback removes the initial multi-megabyte repacking regression, but does not eliminate all overhead. Full raw samples and exclusions must remain in the report. Graph excludes terrain/history/models; no peak-memory, file/cold-cache, whole-server or city claim. Fresh native workflow is running after Maven/probe completion.

### Private server geometry — local validation complete

Playtest: the final production Main/GLFW workflow exited 0 on the inherited DISPLAY with an isolated synthetic offline profile. Capture/name/anchor/library, long-path import/export, all four rotations, conflicts and player overlap rejection, hole preservation, cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/editor regressions passed. Source manifest and receipt digest match the implementation. All 23 fresh video frames decoded: 478029 bytes, 39.506 seconds, 960x544. Inspected the green staging/anchor midpoint and red 90-degree conflict screenshot. llvmpipe/HUD 1 FPS is functional-only evidence, not a physical or frame-budget pass.

Report: dashboard/evidence/structure-server-startup-geometry.json. It contains the 78-test selected-suite results, raw 20-run component samples, timing/allocation regressions, retained geometry scope, native receipt/media digest, source fences and qualification gates. Actual synthetic server/TLS save/restart and late-join integration exercises the affected noninteractive startup path; browser playtesting does not apply. The separate native client run supplies workflow regression media.

Final audit: diff check passed; no file deletions against review base 71612f09bfec4549456b3317676e76cdced085a4 and no unmerged index. All ten MP3/Hub restorations remain byte-exact to that base. Five older component reports remain byte-exact to HEAD. Only the private server startup per-row geometry loop is replaced; live edit handling remains. Tracked/untracked status inspected. No agent commit, push, merge or publication occurred.

Request feature_validation for the new source/report/media and exact-head full hosted verification. This is not review submission or readiness. Next work remains region-backed authority/persistence and paging, whole-world residency/metadata, ordinary nonclear histories, and city/maximum admission barriers. Do not raise caps from these small warm component results. Full reference city/maximum 20-run budgets, physical GPU, shaped 32-rendered-client synchronization, peak memory/frame/tick/traffic/ack/stall/upload/cold-load/durable-save qualification, latest-master integration and independent review remain pending. Supported limits and stable rollout stay gated. No owner answer is needed.

### Cell-index multiplayer join deltas — implementation in progress

Exact head 8209c39e4bea3a66283f805677a7f681c61a2618 run 42781369302 passed four platform builds; publication skipped. This closes its build wait, not the feature qualification gates.

Multiplayer join catch-up currently scans both histories row by row under the authority lock. Indexed histories now compare cell indexes. Shared COW cell identities prove unchanged values; changed identities still require exact keyed value comparison, with shared immutable bulk segments skipped. A second cell-index pass catches wholly removed cells. Generic maps retain the row-union fallback. The result remains a set of changed cell positions; cell delivery order may differ, while each cell payload retains its own semantics.

Ordinary and bulk value-only put paths now call writable(cell) so a snapshot cannot share an identity with later changed values. Add/remove/replacement paths already detached it. Equal-value writes and reordered rows receive exact fallback comparison and must not create false deltas. First ordinary-cell mutation after a snapshot can copy that cell's key index; this overhead must remain visible. The algorithm still scans resident cell indexes and changed-cell rows; it is not incremental region paging or a full lock-stall solution.

Initial test run rejected an invalid fixture use of SparseVoxelOctree.set with encoded world material. Corrected to the existing world-tree fill fixture path. No failed attempt counts as passed. Final focused checks, exact row-union oracle comparisons, component measurements and fresh native regression/media remain pending. City/maximum, physical GPU and shaped 32-client qualification remain pending; stable rollout stays gated.

Final focused checks passed 23 tests in five suites, including actual TLS paused-READY removal of the final fractional leaf. The five-case component probe completed three warmups and 20 alternating-order samples per mode/fixture before broader regressions. Every changed-cell set matches the exact parent row-union algorithm. Shared dense 32776-row p95 50.891702->0.145536 ms, median allocation 29760000->496 bytes. One changed dense cell p95 28.591457->0.395247 ms, allocation 29760072->158080 bytes. Independent dense histories still require exact scanning (p95 32.647222->24.948121 ms; allocation 29760000->9176304 bytes). Sparse 4096-cell p95 3.596624->2.833929 ms; removed-cell p95 26.273962->0.036027 ms. These are warm comparison costs, not authority-lock duration, server ticks, peak memory or city/network results.

Broader final-source history/COW, offline/regional/server, structure and multiplayer regressions are running. Afterwards measure post-snapshot ordinary/compact overwrite cost against the exact parent, then run fresh native regression/media. Production/test source is frozen in target/structure-cell-delta-fence.json. Logs remain ignored in target; never read runtime-private.txt. No broad/native new-source pass claimed yet.

Broader final-source validation exited 0: 83 tests in 18 suites, zero failures/errors/skips. Includes new exact delta tests, actual TLS paused-READY final-leaf undo, history snapshots/cursors/replacement accounting, offline/regional/legacy server persistence, structures and multiplayer regressions. This is selected affected-path coverage; no full local suite pass claimed. New exact-head hosted full verification remains required.

Post-snapshot write comparison against exact parent 8209c39e completed in serial separate 512 MiB JVMs after broader tests finished. Three warmups and 20 samples per mode/fixture; every old/new keyed value, count and revision matched. Ordinary 4097-row cell, 512 overwrites: p95 1.181101->2.972452 ms; median allocation 636584->862264 bytes. Compact case p95 1.582059->1.438435 ms; allocation 725888->713808 bytes. Separate JVM/JIT variation limits attribution. First ordinary-cell COW detachment copies that cell's key index. Timing/allocation excludes fixture/snapshot construction and verification; no retained/peak-memory or city claim.

Native regression now runs sequentially after Maven/probes on inherited DISPLAY with an isolated synthetic profile. Preserve all gates until actual exit, fresh media decode/inspection and source/audit checks. Distinct report planned: dashboard/evidence/structure-cell-index-join.json. No native new-source pass claimed yet.

### Cell-index join deltas — local validation complete

Playtest: production Main/GLFW workflow exited 0 on inherited DISPLAY with an isolated synthetic offline profile. Capture/name/anchor/library, long-path import/export, all four rotations, empty-space preservation, overlap/player rejection, cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/model editor regressions passed. All production/harness source hashes and receipt digest match. All 24 fresh video frames decoded: 430957 bytes, 43.596 seconds, 960x544. Inspected the green staging/anchor midpoint (0 degrees, world unchanged) and red 90-degree occupied-cell rejection. llvmpipe/HUD 1 FPS is functional-only evidence; no physical or frame-budget pass claimed.

Report: dashboard/evidence/structure-cell-index-join.json. It records 83 tests in 18 selected suites, exact commands and expected/observed workflows, the independent row-union oracle and actual TLS paused-READY final-leaf undo, five comparison fixtures and two exact-parent write fixtures with 20 samples per mode, raw timing/allocation results, environment, source/compiled fences, native receipt/media digest and qualification gates. Browser playtesting does not apply to the native headless server; actual TLS workflows exercise its affected integration, with separate native-client regression media. Full local suite was not rerun; new exact-head hosted full verification remains required.

Final audit: diff check passed; no file deletions against review base 71612f09bfec4549456b3317676e76cdced085a4 and no unmerged index. Ten MP3/Hub restorations remain byte-exact to that base; six older component reports remain byte-exact to HEAD. Tracked/untracked status inspected. Intended removal replaces only indexed-history join comparison; generic maps retain the old row-union path. Value-only puts now detach COW cell indexes while preserving history values/order/revision semantics. No agent commit, push, merge or publication occurred.

Request feature_validation for this new source/report/media and obtain exact-head hosted full verification. This is not review submission or readiness. Region-backed authority/persistence and paging, whole-world residency/metadata and ordinary history remain unfinished. Current provisional geometry/chunk/persistence caps still reject required city/maximum fixtures. Full reference city/maximum admission and 20-run budgets, physical GPU, shaped 32-rendered-client synchronization, peak memory/frame/tick/traffic/ack/stall/upload/cold-load/durable-save qualification, latest-master integration and independent review remain pending. Do not lift caps or publish supported-city claims from warm component results. Stable rollout stays gated. No owner answer is needed.

### Indexed private replay staging — implementation in progress

Exact head 68fd93896ccc3411dfb0639e98539dc2ebe6526b run 42786251214 passed all four platform builds; publication skipped. This is not readiness or review approval.

The existing v10 private spool now writes a separate fixed-width disk index while validating source sections. Each 28-byte entry seals file offset, region, record count and payload checksum; an in-memory CRC seals the index. No resident section directory or global replay rows are retained by the spool. Index bytes are bounded by the existing 2,000,000-record cap: at most 56,000,000 additional disk bytes, with combined data/index bound 144,000,004 bytes. Save/wire formats and admission caps are unchanged.

Full readback stays sequential and buffered, validating the sealed index before delivery and each complete section against it. Selective readback scans index metadata and seeks only matching payloads, preserving recurring region sections in original order. It reuses one bounded section frame. Unselected payload integrity is deferred on selective reads; each delivery remains private staging, not permission to publish a whole world. Owner/busy/close fences remain. Failure and close remove both owned temp files.

An intermediate compile failed after removal of a temporary bounded-stream helper left an anonymous-class brace; corrected. No failed attempt counts as passed. Final focused checks passed 32 tests in six suites, including selective order, immutable sections, index corruption before delivery, sealed payload checksum changes, unselected corruption deferral/full-read rejection, cancellation/retry, ownership/reentrancy, cleanup, legacy offline and regional server persistence. A broader final-source rerun remains required after component measurement.

Exact-parent probe is running before broader/native checks. It measures actual private staging forces, warm full/selected traversal and thread allocation for clustered, fragmented and empty replay. Record added index disk cost and any staging/full-read regressions. This is a private validated readback foundation; LocalGame still drains all rows into resident history and the live World/server still lack region paging. City/maximum admission and all reference/physical/network qualification remain pending. Stable rollout stays gated.

Exact-parent component probe completed before broader/native checks. Actual private-file staging (including forces), full read and selected read: three warmups and 20 samples per mode/fixture, ordered count/hash verification, no leaked files after every run. Clustered 8192 rows: selected p95 8.399568->2.375791 ms, full p95 10.223468->11.286021 ms; temporary bytes 196772->196996. Fragmented 8192 one-row sections: stage p95 14.960719->22.380965 ms, full 10.813502->19.195977 ms, selected 8.092057->10.181894 ms; bytes 360452->589828. Empty replay overhead and complete allocation/timing samples are recorded. Fragmented p95 regressions are explicit; no unconditional speedup claim. Warm private I/O only, not cold/durable/live-world or city qualification.

Final-source broader persistence/integration checks run in target/structure-replay-index-validation.stdout. Source fence: target/structure-replay-index-fence.json. Component pointer: target/structure-replay-index-probe.stdout. Native regression/media must run after Maven completes; never read runtime-private.txt. Distinct report planned: dashboard/evidence/structure-replay-disk-index.json. No broader/native new-source pass claimed yet.

### Indexed private replay staging — local validation complete

Broader final-source validation exited 0: 65 tests in 15 suites, zero failures/errors/skips. The 32 focused tests passed separately. Coverage includes new indexed/selective readback, existing spool/replay/loader behavior, offline load/progress/cancellation, v10 compatibility, private World replay, staged offline saves, regional server persistence, structures and multiplayer regressions. This is selected affected-path coverage; a full local suite was not rerun. New exact-head hosted full verification remains required.

Actual RegionReplaySpoolCheck CLI exited 0 with -Xmx64m after the native run ended. One run each at 100000 and current maximum 2000000 one-row sections verified exact replay order, explicit air/fractional LED colour/stone, counts, encoded length, unread source tail, unchanged source SHA-256 and cleanup of both owned files. At 2000000 records, data bytes 88000004; stage 3446.335162 ms and full read 2533.595268 ms. These are single-run diagnostics, not reference budgets, maximum-structure qualification or retained/peak process-memory measurements. The 64 MiB heap setting is not a process memory measurement. The 2,000,000-record cap remains.

Playtest: final production Main/GLFW workflow exited 0 on inherited DISPLAY with an isolated synthetic offline profile. Capture/name/anchor/library, long-path import/export, all four rotations, holes, overlap/player rejection, cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/model editor regressions passed. Production/harness source hashes and receipt digest match. All 25 fresh video frames decoded: 471528 bytes, 45.661 seconds, 960x544. Inspected green staging/anchor at 0 degrees with world unchanged, and red 90-degree occupied-cell rejection. llvmpipe/HUD 1 FPS is functional-only evidence; no physical or frame-budget pass claimed.

Report: dashboard/evidence/structure-replay-disk-index.json. It records commands, expected/observed results, all component raw samples and regressions, environment, source/compiled fences, maximum-record CLI, native receipt/media digest, logical disk bounds and qualification gates. Native file I/O is noninteractive, so browser playtesting does not apply; actual LocalGame/private I/O integrations and separate native-client regression media were exercised.

Final audit: diff check passed; no deletions against review base 71612f09bfec4549456b3317676e76cdced085a4 or unmerged index. Ten unrelated MP3/Hub restorations remain byte-exact to that base; seven older component reports remain byte-exact to HEAD. Tracked/untracked status inspected. Intended removal replaces spool readback with sealed index validation and the shared strict section parser; save/wire bytes, row order and limits remain unchanged. Both owned temporary files are cleaned. No agent commit, push, merge or publication occurred.

Request feature_validation for this new source/report/media and exact-head full hosted verification. This is not readiness or review submission. The private index is not a durable/live-world region backend: LocalGame still drains all rows and World/server still retain resident geometry/history. Region-backed authority/persistence and paging, whole-world metadata and admission barriers remain unfinished. Existing provisional limits still reject required city/maximum packages. Full reference city/maximum admission and 20-run budgets, physical GPU, shaped 32-rendered-client synchronization, peak memory/frame/tick/traffic/ack/stall/upload/cold-load/durable-save qualification, latest-master integration and independent review remain pending. Preserve supported-limit and stable-rollout gates. No owner answer is needed.

The final artifact audit caught the temporary report generator still targeting the older structure-cell-index-join.json. Restored that older report byte-exact from HEAD, corrected the destination to structure-replay-disk-index.json and reran the full source/compiled/test/media/restoration audit. Seven older reports are verified again after writing the new report. New report is distinct; source, tests and captured media are unaffected. No historical report was lost or published with replacement content.

### Bounded private replay pages — validation in progress

Verified exact parent 6aab649a33d146c42ab55561885e734f0628eeaa run 42792227995: all four platform builds, Maven verify and packaged MP3 decoder checks passed; publication skipped. This does not qualify the new edits or mark readiness. Another parent run still had an Intel job pending when inspected; no repeated wait or dispatch was made.

Added a private detached page builder over selective spool reads. Source-row preflight counts overwritten and explicit-air rows before payload decoding and repeats its limit while consuming the index. Private caps are 4096 source rows and 64 edited cells; callers may tighten them. Cell limits reject before insertion. All selected replay and sealed metadata validates before geometry starts. One cell is built at a time over implicit terrain; immutable history/images return only on success. Returned images survive spool close and fork under later installed-world edits. Model definitions and authority revision fences remain caller-owned. No public limit, format or live loader was changed.

The first compile used a nonexistent model-base test constant; corrected to a valid structure model ID. A later terrain fixture incorrectly assumed y=0 was solid; moved to Terrain.MIN_Y and left a known voxel untouched. Those failed attempts are excluded. Final affected-path Maven command exited 0: 70 tests in 16 suites, no failures/errors/skips. Five new page tests cover independent replay, ordered history, immutable/forked images, 4096/4097 source-row and 64/65-cell boundaries, predecode policy isolation, tightened/empty budgets, fragmented terrain-backed geometry and early/late cancellation/retry. Existing offline/regional load/save, legacy replay and multiplayer regressions passed. Full local suite was not rerun.

Playtest: actual private page CLI passed with synthetic recurring sections, LED colours, glass, explicit air and a custom model reference. It matches independent history/per-voxel replay, checks row/cell rejection, cancellation, retry, missing region, cleanup and post-close independent installation. Three warmups and 20 warm private-file component samples recorded stage median/p95/worst 4.140635/5.186620/10.134185 ms; page 9.850149/15.844318/19.173552 ms. These are not city/reference budgets or memory qualification. A launcher path assertion initially rejected relative target/classes; corrected before Java ran. Fresh CLI PNG captured from the actual result window and inspected. Browser playtesting does not apply to this worker-only file integration.

Fresh production Main/GLFW regression is running after Maven and CLI completed, using inherited DISPLAY and an isolated synthetic profile. Do not read target/structure-native/runtime-private.txt. Report planned at dashboard/evidence/structure-replay-page.json; preserve all older reports. Source fence target/structure-replay-page-fence.json; final tests target/structure-replay-page-validation-final2.stdout; CLI receipt pointer target/structure-replay-page-cli2.stdout. No new native pass is claimed yet.

This private page builder is not wired into live authority or rendering. Whole-world resident history/geometry and flat persistence barriers still remain. Required city/maximum admission and 20-run reference budgets, physical GPU, shaped 32-rendered-client synchronization and memory/frame/tick/traffic/ack/stall/upload/cold-load/durable-save qualification remain pending. Preserve limits and stable-rollout gates. Next work must integrate bounded asynchronous pages with authority revision fences and durable overlays; do not treat component success as city support. No owner answer is needed.

### Bounded private replay pages — local validation complete

Fresh native command `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 after the CLI completed. Production Main/GLFW/X11 input with an isolated synthetic offline profile passed capture/name/anchor/library, long-path import/export, four rotations, holes, occupied/player rejection, placement/undo/export/import cancellation, malformed import atomicity, repeated placement, single-operation undo, 32-resolution dependency/restart and inventory/model editor regressions. All 241 native manifest entries and the receipt digest match; all 475 fenced production/test sources remain unchanged.

Playtest: all 25 fresh F10 video frames decoded with bundled JCodec. Video 45.377 seconds, 536160 bytes, 960x544; SHA-256 b271fafda320b999123f0ffe62ab8b2c6b4f3aa8f671abee995fbf226fd02ccf. Inspected green 0-degree staging/anchor with world unchanged and red 90-degree occupied-cell rejection. llvmpipe LLVM15.0.6 / HUD1FPS is functional-only evidence. No physical GPU or frame-budget pass claimed. Six restored Hub registration tests also passed in 0.057 seconds. Full local Java suite was not rerun; the 70 affected-path tests and CLI results above are final for this source.

Report dashboard/evidence/structure-replay-page.json records exact commands, environment, expected/observed workflows, raw 20-run component samples, source/compiled fences, native receipt/media hashes, resource bounds, failed attempts and remaining qualification gates. Fresh CLI screenshot, midpoint, native screenshots and video await controller publication. This is current implementation evidence, not stock or historical media.

Final audit against review base 71612f09bfec4549456b3317676e76cdced085a4 found no deleted paths or unmerged index. Ten MP3/Hub restorations remain byte-exact to that base. Eight older component reports remain byte-exact to HEAD, including structure-replay-disk-index.json. Diff check passed; tracked/untracked status inspected. No intended path deletion; only selected spool admission changed, with page/test/CLI additions. Existing format, full traversal and public caps remain. No agent commit, push, merge or publication occurred.

Request controller feature_validation for these pending source/report/media edits and new exact-head hosted full verification. This is not readiness or review submission. Region paging remains unconnected; each private page still scans the whole index and rejects regions over64 edited cells. Bounded cell batches, faster lookup, async cache lifetime, authority/model/terrain fences, dirty overlays, durable generations and whole-world residency barriers remain unfinished. Required city/maximum admission and20-run reference/physical/shaped32-client budgets remain pending. Latest-master integration and independent review still required; preserve supported-limit and stable-rollout gates. No owner answer is needed.

### Bounded cell-window replay — implementation and local validation in progress

Exact parent e3b5de0d6f22203e03cfc9c77f111d459290c1e9 run 42797655776 passed all four platform builds, Maven verify and packaged MP3 checks; publication skipped. This is parent validation, not readiness or a pass for pending edits.

RegionReplayPage.loadWindow now accepts explicit half-open local-cell coverage, at most 64 consecutive keys per window. Local keys are x | (z << 4) | (y << 8), including negative world coordinates. Coverage includes implicit unedited terrain, even when cells() is empty. A separate decoded-region-row cap is checked from the sealed index before payload decoding. Every matching-region row still passes CRC and model policy, including excluded cells. Selected source-row admission counts overwritten rows before history insertion. No page returns until all matching-region metadata/payload validation and its selected geometry complete. Failure/cancellation leaves only private discarded state; the spool remains caller-owned and retryable.

Private selected-row cap is 4097 to cover one clear plus all 4096 coloured leaves of a complete 16³ cell. This is a bounded staging adjustment by one row; public structure/model/geometry/instance/chunk/persistence caps remain. New tests preserve all 4097 rows and every voxel; 4098 rows reject even when the last row is an overwrite. Whole-region entry-point caller budgets still work. Geometry/history results remain immutable and independent after close and later installed-world edits.

Initial 13 focused tests and a 75-test broader/component run passed before the complete-cell boundary extension. Its unique component receipt/image remain under ignored target/replay-window-check scratch. Final-source broader command exited 0: 76 tests in 17 suites, zero failures/errors/skips. Six new window tests cover all 4096 cells through 64 disjoint negative-coordinate windows; filtered source order, exact geometry and post-close use; decoded/retained/range limits; implicit coverage; excluded-cell model/CRC rejection; early/late cancellation, corruption/retry and complete 4097-row cell boundaries. Existing replay, offline/server persistence and multiplayer regressions passed. Full local Java suite was not rerun.

Playtest: python3 tools/structures/run_replay_window_check.py passed actual private disk staging, 64-window loading, independent history/per-voxel assembly, row limits, excluded model policy, cancellation/retry, cleanup and post-close independent installation. Final fixture: 8192 source rows, 4096 cells, 90112 leaves, 128 rows/64 cells per window. Second fixture: 4097 rows, 1 cell, 4096 leaves. Three warmups plus 20 samples per fixture: stage median/p95/worst 7.499505/8.568869/8.614243 ms; sum 64 window loads 229.980595/257.652642/271.506669 ms; batch including checks/install 232.702737/259.761855/274.921268 ms. Full-cell stage 5.153640/6.082540/7.894533 ms; load 5.700266/6.360834/7.464834 ms. Actual synthetic CLI image captured and inspected. Warm private I/O only; not reference/city, cold-cache, durability or peak-memory qualification. Browser playtesting does not apply to this worker file integration.

Current native Main/GLFW regression is running after final Maven and CLI completed, on inherited DISPLAY with an isolated synthetic profile. No new native pass claimed yet. Do not read runtime-private.txt. Final source fence: target/structure-replay-window-fence.json. Final Java stdout: target/structure-replay-window-validation-final.stdout. CLI receipt pointer: target/structure-replay-window-cli-final.stdout. Planned distinct report: dashboard/evidence/structure-replay-window.json. Preserve every older report, including structure-replay-page.json.

Live World/server authority and rendering still do not use these page/window APIs; LocalGame still drains the whole replay into resident history. Each window scans the whole disk index and decodes the selected region again; 64-window reads repeat this work 64 times. Per-window source order is exact, but concatenating window histories does not restore original global replay order. Faster cell lookup, archive lifetime, asynchronous requests, cache/pinning, authority/model/terrain revision fences, dirty overlays and durable generations remain unfinished. Required city/maximum packages still exceed provisional admission limits. Full 20-run reference city/maximum, physical GPU, shaped 32-client, peak memory/frame/tick/traffic/ack/stall/upload/cold-load/durable-save budgets remain pending. Keep public limits, supported-city claims and stable rollout gated. No owner answer is needed.

Next implementation order: first add a cell-aware disk lookup that can admit a window from metadata and read its source sections once, with original row ordinals and bounded sort runs. Retain a sealed root and bounded checksum metadata; do not add a resident per-record directory. Test recurring sections, duplicate/overwritten rows, negative coordinates, corruption and cancellation against the current full-scan oracle. Keep the existing scan path as a comparison until measurements justify replacement. Then give the archive a worker-owned lifetime across page requests and integrate asynchronous reads with authority/model/terrain revision fences. Define dirty overlays and atomic durable generation publication before lifting persistence/admission caps. Window histories alone are not a whole-world save stream.

### Bounded cell-window replay — local validation complete

Fresh `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 after final Maven and CLI checks. Production Main/GLFW/X11 input with an isolated synthetic offline profile passed capture/name/anchor/library, long-path import/export, four rotations, holes, occupied/player rejection, placement/undo/export/import cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/model editor regressions. All 241 native source manifest entries and receipt digest match; all 476 fenced production/test sources remain unchanged.

Playtest: every one of the 25 fresh F10 frames decoded with bundled JCodec. Video: 42.521 seconds, 467994 bytes, 960x544; SHA-256 f2661ca9ba7932679ac11ae1a61acc1972beaa9dbee7cacab7e6f92c5bdacfe8. Inspected green 0-degree staging footprint/anchor with world-unchanged status and red 90-degree occupied-cell rejection. LOW/llvmpipe LLVM15.0.6/HUD1FPS shows visible terrain mesh gaps during staging. This is functional-only evidence, not physical GPU, nearby-usable-geometry timing or frame-budget qualification.

Report dashboard/evidence/structure-replay-window.json records exact commands, expected/observed workflows, environment, both 20-run component fixtures with raw samples, source/compiled fences, native receipt/media hashes, private resource bounds and remaining gates. Fresh CLI image, midpoint, native screenshots and video await controller publication. Browser playtesting does not apply to the worker file path; the actual CLI integration and separate current native client workflow were exercised. The 76 affected-path Java tests are final; full local suite was not rerun.

Final audit against review base 71612f09bfec4549456b3317676e76cdced085a4: no deleted paths or unmerged index. Ten MP3/Hub restorations remain byte-exact to base. Nine named older component reports, including structure-replay-page.json, remain byte-exact to HEAD. All other tracked evidence JSON is unchanged except refreshed native source/result receipts. Diff check passed; tracked/untracked status inspected. No intended path deletion. Only the private page builder, its budgets/coverage and tests/CLI were changed; public formats, admission limits and whole-world loading stay in place. No agent commit, push, merge or publication occurred.

Request controller feature_validation for pending source/report/media and new exact-head hosted full verification. This is not readiness or review submission. Live authority/rendering integration, faster lookup, archive lifetime, asynchronous requests, cache/revision fences, dirty overlays, durable generations and whole-world residency remain unfinished. Required city/maximum admission and full reference/physical/shaped-32-client budgets remain pending. Latest-master integration and independent review still required. Preserve public limits, supported-city claims and stable-rollout gates. No owner answer is needed.

### Copied region cell index — local validation in progress

Verified exact parent 58d34acc03f3bb0338c43221752ddfa454b5656c run 42803062842: all four platform builds, Maven verify and packaged MP3 checks passed; publication skipped. This is parent validation, not readiness or a pass for pending edits.

Added worker-private RegionReplayCells and RegionReplayPage.loadIndexed. The fixed 4096-cell key space permits bounded counting/scatter placement instead of external sort runs. Two complete validated source-region traversals build a copied file with original matching-region ordinals. Root arrays retain 81928 primitive bytes; data is 28 bytes per row, at most 56000000 bytes. Selected-window counts reject before payload I/O. Selected copied-cell CRCs pass before policy callbacks; row positions/ordinals are checked and selected rows sorted back into source order. Whole-region model policy is checked during build; reads recheck selected rows only and defer other copied-cell post-build corruption. Original scan semantics stay in place. Model/terrain/authority fences remain caller-owned.

The copied index outlives its source spool but stays on one worker. Build failures/cancellation remove private prefixes; read failures allow retry. Close removes only its own file. It is not a durable/reopenable archive or live authority. Page history/geometry construction is shared with the existing scan path. No public limits or save/wire formats changed; LocalGame and World/server still load resident state.

Focused checks passed. Final affected-path Maven command exited 0: 83 tests in 18 suites, no failures/errors/skips. Seven new tests cover all 4096 region cells after source close, filtered order and geometry, immutable rows/images, model-policy rejection during build, admission before payload I/O, selected CRC/length changes, deferred other-cell corruption, future source-frame corruption during scatter, cancelled writes, ownership/reentrancy, early/late read cancellation, retry, 4097/4098-row boundaries and empty/closed/interrupted handles. Existing replay, offline/server persistence and multiplayer regressions passed. Full local Java suite was not rerun.

Playtest: python3 tools/structures/run_replay_cells_check.py passed actual scan/index page workflows and independent history/per-voxel oracles. Three warmups and 20 samples per fixture, alternating read order. For 4096 cells through 64 windows, p95 scan 234.199173 ms; index build 25.555743 ms; indexed reads 62.905185 ms; per-run build-plus-read total 87.536573 ms. For one full 4097-row coloured cell, p95 scan 6.765341 ms versus build-plus-indexed 22.861884 ms: setup regresses this case. Raw medians/worst/samples and shared-stage times are preserved. Copied files: 229376/114716 bytes; each root has 81928 primitive metadata bytes. Heap setting is not peak memory. Actual synthetic result window captured and inspected. Browser playtesting does not apply to this worker file integration.

Fresh native Main/GLFW regression is running after final Maven and CLI checks, using inherited DISPLAY and an isolated synthetic profile. No new native pass claimed yet. Do not read runtime-private.txt. Final source fence target/structure-replay-cells-fence.json; test output target/structure-replay-cells-validation.stdout; CLI receipt pointer target/structure-replay-cells-cli.stdout. Distinct report planned at dashboard/evidence/structure-replay-cells.json. Preserve older reports.

Live authority/rendering integration, bounded archive/cache lifetime, asynchronous requests, model/terrain/authority revision fences, dirty overlays, durable generations and whole-world residency remain unfinished. Per-region setup performs two source-region traversals, each with two global metadata scans (four scans total). Multiple copied roots add metadata/disk costs; a bounded cache is required before live use. Original global replay order across regions must be retained separately. Required city/maximum admission still exceeds provisional limits. Full reference 20-run city/maximum, physical GPU, shaped 32-client, memory/frame/tick/traffic/ack/stall/upload/cold-load/durable-save qualification remains pending. Preserve supported-limit and stable-rollout gates. No owner answer is needed.

### Copied region cell index — local validation complete

Fresh `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 after final Maven and CLI checks. Production Main/GLFW/X11 input with an isolated synthetic offline profile passed capture/name/anchor/library, long-path import/export, four rotations, holes, occupied/player rejection, placement/undo/export/import cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/model editor regressions. All 242 native manifest entries and its receipt digest match; all 478 fenced production/test sources remain unchanged.

Playtest: all 24 fresh F10 video frames decoded with bundled JCodec. Video 43.848 seconds, 415364 bytes, 960x544; SHA-256 082690ee5349f2a75bf5ab89bfd3efa070470f1a828fb568af0a66125186151b. Inspected green 0-degree staging footprint/anchor with world-unchanged status and red 90-degree occupied-cell rejection. LOW/llvmpipe LLVM15.0.6/HUD1FPS is functional-only evidence, not physical GPU, nearby-usable-geometry timing or frame-budget qualification.

Report dashboard/evidence/structure-replay-cells.json records exact commands, expected/observed workflows, environment, both alternating-order 20-run scan/index comparisons with setup costs and regressions, raw samples, source/compiled fences, native receipt/media hashes, resource bounds and validation scope. Fresh CLI image, midpoint, native screenshots and video await controller publication. The 83 affected-path Java tests are final; full local Java suite was not rerun. No unexecuted qualification is marked passed.

Final audit against review base 71612f09bfec4549456b3317676e76cdced085a4: no deleted paths or unmerged index. Ten MP3/Hub restorations remain byte-exact to base. Ten named older component reports, including structure-replay-window.json, remain byte-exact to HEAD. All other tracked evidence JSON is unchanged except refreshed native source/result receipts. Diff check passed; tracked/untracked status inspected. No intended path deletion. The optional copied-index path and shared private page assembly do not change public formats, admission limits or whole-world loading. No agent commit, push, merge or publication occurred.

Request controller feature_validation for pending source/report/media and new exact-head hosted full verification. This is not readiness or review submission. Next work is a bounded worker-owned archive/cache with asynchronous page requests, progress/cancellation, whole-world metadata/tail validation and model/terrain/authority revision fences, followed by dirty overlays and atomic durable generations. A copied region alone never validates an original world tail or other regions and cannot authorize publication. Live authority/rendering integration, whole-world residency, required city/maximum admission and full reference/physical/shaped-32-client budgets remain unfinished. Latest-master integration and independent review still required. Keep public limits, supported-city claims and stable rollout gated. No owner answer is needed.

### Private copied-index cache — validation in progress

Verified exact head 43867e8d135665bf7496002dc4a426977a5c83d2 run 42808582176: all four platform builds, Maven verify and packaged MP3 checks passed; publication skipped. This is parent validation, not a pass for pending source or readiness.

Added package-private synchronous RegionReplayCache. Explicit count cap 1–4 and logical copied-byte cap at most 56000000 include in-flight builds through conservative maxRegionRows*28 reservation before eviction/build. LRU roots never escape; detached pages do. Source spool stays caller-owned. Failed deletion keeps ownership/accounting for retry; failed builds clean their prefix; failed reads may retain valid roots. Empty indexes count against the slot cap. At most four root metadata arrays total 327712 primitive bytes, excluding objects, buffers, source files and returned pages. No peak-memory or timing qualification follows.

Affected-path Maven exited 0, including five new cache tests. CLI integration passed six synthetic regions/ten LRU requests, policy and row rejection/retry, bounds during callbacks, cached read after source close, rejected miss after close, cleanup and independent history/geometry checks. Inspected its actual captured result window. The first CLI attempt rejected a fixture encoded under a wrong fixed region; switched that fixture to production RegionReplay.write and reran successfully. No production format or admission rule changed.

Playtest: fresh production Main/GLFW regression is running on inherited DISPLAY with an isolated synthetic offline profile after final Maven and CLI. No new native pass claimed yet. Do not read runtime-private.txt. Source fence target/structure-replay-cache-fence.json; Maven stdout target/structure-replay-cache-validation.stdout; CLI receipt pointer target/structure-replay-cache-cli.stdout. Planned distinct report dashboard/evidence/structure-replay-cache.json. Preserve all older component reports.

Next: integrate a worker-owned archive with asynchronous requests, whole-world metadata/tail validation and model/terrain/authority revision fences. The synchronous cache is not live authority, a durable archive or permission to publish world state. Define dirty overlays, original global ordering and durable atomic generations before raising persistence/admission caps. Live World/server/rendering integration, resident world state, required city/maximum admission and full 20-run reference/physical/shaped-32-client qualification remain pending. Keep supported-limit and stable-rollout gates. No owner answer needed.

### Private copied-index cache — local validation complete

Fresh `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 after Maven and CLI checks. Production Main/GLFW/X11 input with an isolated synthetic offline profile passed capture/name/anchor/library, long-path import/export, all four rotations, holes, occupied/player rejection, placement/undo/export/import cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/model editor regressions. All 243 native source-manifest entries and its receipt digest match. All 480 fenced production/test sources remain unchanged.

Playtest: all 24 fresh F10 video frames decoded with bundled JCodec. Video 42.529 seconds, 520200 bytes, 960x544; SHA-256 a92f8106ad812ccbbe35c06ec9aea82fef7d99070cbdf1a4c4d8351df57a9a15. Inspected green 0-degree staging bounds/anchor with world-unchanged status and red 90-degree occupied-cell rejection. LOW/llvmpipe LLVM 15.0.6/HUD 1 FPS is functional evidence only. Physical GPU, nearby-usable-geometry timing and frame budgets remain unqualified.

Final affected-path Maven: 88 tests in 19 fresh suites, no failures/errors/skips. Full local Java suite was not rerun. CLI passed actual cache lifecycle/bounds workflows and independent history/voxel oracles; captured result window inspected. Its first fixed-region fixture encoding failure was corrected with production RegionReplay.write and rerun. Distinct report dashboard/evidence/structure-replay-cache.json records exact commands, environment, source/compiled fences, expected/observed results, native receipt/media hashes, logical cache bounds and pending gates. No new timing or peak-memory claim.

Final audit against review base 71612f09bfec4549456b3317676e76cdced085a4: no deleted paths or unmerged index. Ten MP3/Hub restorations remain byte-exact to base. Eleven named older component reports, including structure-replay-cells.json, remain byte-exact to HEAD. All other tracked evidence JSON is unchanged except refreshed native source/result receipts. Diff check passed; tracked/untracked status inspected. No intended file deletions. No agent commit, push, merge or publication occurred.

Request controller feature_validation for source/report/media publication and new exact-head hosted verification. This is not readiness or review submission. Live archive/authority/rendering integration, asynchronous requests, whole-world validation, revision fences, dirty overlays, global order and durable generations remain unfinished. Required city/maximum admission still exceeds provisional limits. Full reference 20-run city/maximum, physical GPU and shaped 32-client qualification, latest-master integration and independent review remain pending. Keep public limits, supported-city claims and stable rollout gated. No owner answer needed.

### Private asynchronous replay worker — validation in progress

Verified exact parent fba6cf2b616b21340526dd3974eed7ce3c7ae781 run 42813946137: all four platform builds, Maven verify and packaged MP3 checks passed; publication skipped. This is parent validation only.

Added RegionReplayWorker: one worker owns source/cache and performs every file/policy/page operation. Owner submit/poll/result/close do not wait for worker I/O or run callbacks. Queue cap is explicit 1–8, plus one active request. Full queues reject; queued cancellation frees a slot; running cancellation blocks late publication. Results remain out of progress snapshots and require owner-side extraction with matching supplied revision tags. Actual live object identity/revision checks remain the installer’s responsibility. Source factory must validate the complete metadata/model prefix and tail before returning. Failed opening must clean its unpublished resources.

Close signals only. Cleanup stays on the owning worker; deletion failure retains ownership, reports CLEANUP_FAILED and waits for an explicit later close before retrying. No automatic retry loop or joining blocked I/O. A shared LocalGame.readRegionalTail factors existing drop/EOF validation for the resident loader and private factories without changing formats or limits.

Final affected-path Maven exited 0: 95 tests in 20 fresh suites, no failures/errors/skips. Seven new worker tests cover queues, thread/owner fences, progress, queued/running cancellation, late results, policy failure/retry, source/extraction revision tags, invalid tails, fatal failures and cleanup retry. Full local Java suite was not rerun. Actual worker CLI passed queue/cancellation/revision/policy workflows, three detached history/voxel oracles, shared production tail rejection and owned cleanup; captured result window inspected. No new timing or peak-memory claim.

Playtest: fresh Main/GLFW regression is running on inherited DISPLAY with an isolated synthetic profile after final Maven and CLI. No new native pass claimed yet. Do not read runtime-private.txt. Source fence target/structure-replay-worker-fence.json; Maven stdout target/structure-replay-worker-validation.stdout; CLI receipt pointer target/structure-replay-worker-cli.stdout. Distinct report planned at dashboard/evidence/structure-replay-worker.json. Preserve all older reports.

Next implementation: open actual offline regional sources with the complete current world metadata/model parser, retain their immutable source identity, and schedule detached page requests from the owner with real live revision/identity fences. The helper's revision tags are not a substitute for those checks. Define dirty overlays, original global order and durable atomic generations before replacing resident state or raising admission limits. Live World/server/rendering integration, required city/maximum admission and full reference/physical/shaped-32-client qualification remain pending. Keep supported-limit and stable-rollout gates. No owner answer needed.

### Private asynchronous replay worker — local validation complete

Fresh `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 after final Maven and CLI checks. Production Main/GLFW/X11 input with an isolated synthetic offline profile passed capture/name/anchor/library, long-path import/export, all four rotations, empty-space preservation, occupied/player rejection, placement/undo/export/import cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/model editor regressions. All 244 native source-manifest entries and the receipt digest match. All 482 fenced production/test sources remain unchanged.

Playtest: all 25 fresh F10 video frames decoded with bundled JCodec. Video 46.458 seconds, 509133 bytes, 960x544; SHA-256 2d4d55823cf83afb1698ca9da6ca455379b00af8aebe0718b348ecc3075d91fc. Inspected green 0-degree staging bounds/anchor with world-unchanged status and red 90-degree occupied-cell rejection. LOW/llvmpipe LLVM 15.0.6/HUD 1 FPS is functional-only evidence. Physical GPU, usable-geometry timing and frame budgets remain unqualified.

Final affected-path Maven: 95 tests in 20 fresh suites, no failures/errors/skips. Full local Java suite was not rerun. Worker CLI passed actual queue/cancellation/revision-tag/policy workflows, three detached history/voxel oracles, shared regional-tail rejection and resource cleanup. Captured result window inspected. Distinct report dashboard/evidence/structure-replay-worker.json records exact commands, environment, source/compiled fences, expected/observed results, native source/media hashes and logical worker/cache bounds. No timing, peak-memory or 100 ms acknowledgement/stall claim.

Final audit against review base 71612f09bfec4549456b3317676e76cdced085a4: no deleted paths or unmerged index. Ten MP3/Hub restorations remain byte-exact to base. Twelve named older component reports, including structure-replay-cache.json, remain byte-exact to HEAD. All other tracked evidence JSON is unchanged except refreshed native source/result receipts. Diff check passed; tracked/untracked status inspected. No intended file deletions. The resident loader retains its prior tail-validation order through the shared helper. Public formats, admission caps and whole-world loading are unchanged. No agent commit, push, merge or publication occurred.

Request controller feature_validation for source/report/media publication and exact-head hosted verification. This is not readiness or review submission. Next, build actual offline source opening with full version/seed/generator/model/inventory/health validation before replay, then validate the tail. Bind that immutable source to real live geometry/model/terrain identities and revisions. Live installation must distinguish unloaded cells from implicit empty/terrain state before edits or collisions are allowed. Supplied revision tags alone do not provide those guarantees. Dirty overlays, original global replay order, durable generations and live World/server/rendering integration remain unfinished. Required city/maximum admission and full reference 20-run, physical-GPU and shaped-32-client qualification remain pending. Latest-master integration and independent review remain required. Keep public supported-limit claims and stable rollout gated. No owner answer needed.

### Full saved-world readback opener — validation in progress

Verified exact parent 57d4d84fbeee66ebe144611c066f12044953d9d3 run 42819574050: all four platform builds, Maven verify and packaged MP3 checks passed; publication skipped. Parent validation only.

Added LocalGame.openRegionalReadback for existing v10 files. Shared prefix parsing preserves the resident loader's version/seed/generator/model/inventory/health checks and legacy path. New readback checks inventory/replay/every serialized drop model reference, stages replay without resident history, validates tail/EOF and returns loaded private definitions, terrain and SHA-256 of all consumed source bytes. Worker exposes the fixed fingerprint only after a successful source return. Definitions are frozen; the fingerprint does not follow later path replacement. Neither the hash nor supplied revision tags replace live generation/identity fences.

Final affected-path Maven passed 101 tests in 21 fresh suites, no failures/errors/skips. Six new source tests cover production regional writing, model/fractional preservation, exact hash/order/geometry, snapshot/reopen behavior, malformed prefix and body, missing references including overwritten drops, declared limits, cancellation, callback failure, cleanup and legacy compatibility. An initial test setup used null instead of a valid spool for the fingerprint-format assertion; fixed the setup and reran. Full local Java suite was not rerun.

Playtest: actual CLI saved-file writer/opener/worker/cache/page and resident-loader integration passed. A 32-resolution dependency and coloured fractional cell matched ordered history/per-voxel oracles. Reads left source bytes unchanged. Path replacement retained the old staged snapshot; reopen gave the new validated fingerprint. Malformed full-source tail returned no page or identity and cleaned owned files. Captured synthetic result window inspected. No timing or peak-memory claim.

Fresh Main/GLFW regression is running on inherited DISPLAY with an isolated synthetic profile after final Maven and CLI. No new native pass claimed yet. Do not read runtime-private.txt. Source fence target/structure-readback-source-fence.json; Maven stdout target/structure-readback-source-validation.stdout; CLI receipt pointer target/structure-readback-source-cli.stdout. Distinct report planned at dashboard/evidence/structure-readback-source.json. Preserve older reports.

Next: transfer validated metadata to the owner, bind a stable save generation and actual live geometry/model/terrain identities/revisions, and define unloaded-cell behavior before live reads, collisions or edits. The opener does not replace resident LocalGame state. Dirty overlays, global replay order, durable generations and live World/server/rendering integration remain unfinished. Required city/maximum admission and full 20-run reference, physical-GPU and shaped-32-client qualification remain pending. Keep supported-limit and stable-rollout gates. No owner answer needed.

Integration guard for the next phase: do not present metadata-only LocalGame state as fully loaded. Ordinary save paths currently serialize resident history; using them before all replay is resident could overwrite a world with an incomplete history. Owner metadata transfer therefore needs an explicit partial/resident state and save gate, followed by a source-plus-dirty-overlay save path. Movement, collision, raycasts, capture and editing also need explicit unknown/unloaded-cell handling before live paging is enabled. The current opener and worker do not construct or install such partial LocalGame state.

### Full saved-world readback opener — local validation complete

Fresh `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 after final Maven and CLI checks. Production Main/GLFW/X11 input with an isolated synthetic offline profile passed capture/name/anchor/library, long-path import/export, all four rotations, empty-space preservation, occupied/player rejection, placement/undo/export/import cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/model editor regressions. All 244 native source-manifest entries and receipt digest match; all 483 fenced production/test sources remain unchanged.

Playtest: all 25 fresh F10 video frames decoded with bundled JCodec. Video 45.048 seconds, 475121 bytes, 960x544; SHA-256 36e1f9d0b6ff78058d1f7cc042ed33587ef3f6ea644ee1eaa6560c360b9c576e. Inspected green 0-degree staging bounds/anchor with world-unchanged status and red 90-degree occupied-cell rejection. LOW/llvmpipe LLVM 15.0.6/HUD 1 FPS is functional-only evidence, not physical GPU, usable-geometry timing or frame-budget qualification.

Final affected-path Maven: 101 tests in 21 fresh suites, no failures/errors/skips. Full local Java suite was not rerun. Actual saved-file writer/opener/worker/cache/page and resident-loader CLI passed, including full-byte hashes, source read-only behavior, 32-resolution dependency/fractional history/voxel oracles, snapshot/reopen behavior and invalid-source rejection/cleanup. Captured result window inspected. Distinct report dashboard/evidence/structure-readback-source.json records exact commands, environment, source/compiled fences, expected/observed results, native source/media hashes and remaining gates. No timing, peak-memory or 100 ms acknowledgement/stall claim.

Final audit against review base 71612f09bfec4549456b3317676e76cdced085a4: no deleted paths or unmerged index. Ten MP3/Hub restorations remain byte-exact to base. Thirteen named older component reports, including structure-replay-worker.json, remain byte-exact to HEAD. All other tracked evidence JSON is unchanged except refreshed native source/result receipts. Diff check passed; tracked/untracked status inspected. No intended file deletions. Inline resident prefix parsing moved into a shared helper with the same fields/order; version-3 default models remain intact. Stricter reference checks belong to the new private opener. Public formats, admission caps and whole-world resident loading remain unchanged. No agent commit, push, merge or publication occurred.

Request controller feature_validation for source/report/media publication and exact-head hosted verification. This is not readiness or review submission. The full v10 private opener now exists. Owner metadata transfer, stable save-generation binding, actual live identity/revision fences, explicit partial-state/save gates, unloaded-cell behavior, dirty overlays, global replay order, durable generations and live World/server/rendering integration remain unfinished. Required city/maximum admission and full reference 20-run, physical-GPU and shaped-32-client qualification remain pending. Latest-master integration and independent review remain required. Keep public supported-limit claims and stable rollout gated. No owner answer needed.

### Owner metadata transfer — validation in progress

Verified exact parent 8ea7fd07ea91a27c211e78c94313fb31c33bd4c9 run 42824935688: all four platform builds, Maven verify and packaged MP3 checks passed; publication skipped. Parent only.

Validated sources prepare one independent owner metadata snapshot on the worker. Model maps, inventory and drops are copied there; immutable model definitions may be shared. Worker.metadataResult checks owner/running state/revision tags, transfers once and drops mutable references. Its private source model policy remains separate. The returned LocalGame has metadata and a source fingerprint but no resident replay/city and a private final replayComplete=false flag.

Partial save, city-start, edits, craft/drop/tick, model creation, staged structure history/save and structure authority all reject before callbacks or file access. Public field mutation/history replacement cannot promote the object. OfflineGameLoad.result and OfflineWorldReplay reject incomplete state; geometry startup rejects before its world factory. Full constructors and normal load/save remain complete. No live promotion path added.

Final affected-path Maven passed 109 tests in 23 fresh suites, no failures/errors/skips. Seven new metadata tests and one root startup test cover transfer values/ownership/tags/single use/lifetime, all operation and save guards, independent owner/source state, populated-history bypass, failed/cancelled opening and full startup fences. An initial test fixture encoded data inside an IOException-only factory while its helper declared Exception; moved fixture encoding outside that lambda, then reran. Full local Java suite was not rerun.

Playtest: actual saved-file opener/worker/owner CLI passed one-time transfer, 32-resolution model/metadata preservation, partial operation guards, source/sidecar preservation, independent owner mutation, worker page reads, reopen, history-assignment bypass rejection, full loader rejection, full load/save regression and cleanup. Captured result window inspected. No timing or peak-memory claim.

Fresh native Main/GLFW regression is running on inherited DISPLAY with an isolated synthetic profile after final Maven and CLI. No new native pass claimed yet. Do not read runtime-private.txt. Source fence target/structure-readback-metadata-fence.json; Maven stdout target/structure-readback-metadata-validation.stdout; CLI receipt pointer target/structure-readback-metadata-cli.stdout. Distinct report planned at dashboard/evidence/structure-readback-metadata.json. Preserve older reports.

Next: bind stable source generations and real live identities/revisions; define unloaded-cell rules, page installation/scheduling and source-plus-dirty-overlay saves with original global order and atomic generations. Keep metadata-only state out of existing full-world startup/save paths. Live World/server/rendering integration, city/maximum admission and full reference/physical-GPU/shaped-32-client qualification remain pending. Keep supported-limit and stable-rollout gates. No owner answer needed.

### Owner metadata transfer — local validation complete

Fresh `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 after final Maven and CLI checks. Production Main/GLFW/X11 input with an isolated synthetic offline profile passed capture/name/anchor/library, long-path import/export, all four rotations, empty-space preservation, occupied/player rejection, placement/undo/export/import cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/model editor regressions. All 244 native source-manifest entries and receipt digest match; all 485 fenced production/test sources remain unchanged.

Playtest: all 25 fresh F10 video frames decoded with bundled JCodec. Video 46.219 seconds, 478468 bytes, 960x544; SHA-256 93c88464661b40cf1072b3131f460c61457ec5345d18abf54db5b3725c3645d3. Inspected green 0-degree staging bounds/anchor with world-unchanged status and red 90-degree occupied-cell rejection. LOW/llvmpipe LLVM 15.0.6/HUD 1 FPS is functional-only evidence, not physical GPU, usable-geometry timing or frame-budget qualification.

Final affected-path Maven: 109 tests in 23 fresh suites, no failures/errors/skips. Full local Java suite was not rerun. Actual owner metadata CLI passed transfer values, 32-resolution dependency, one-time/tag guards, partial operation/save guards, source/sidecar preservation, independent owner changes, worker reads, reopen, populated-history bypass rejection, full loader rejection, normal full load/save and cleanup. Root geometry-startup rejection before its world factory passed in Java integration. Captured result window inspected. Distinct report dashboard/evidence/structure-readback-metadata.json records exact commands, environment, source/compiled fences, expected/observed results, native hashes and remaining gates. No timing, peak-memory or 100 ms acknowledgement/stall claim.

Final audit against review base 71612f09bfec4549456b3317676e76cdced085a4: no deleted paths or unmerged index. Ten MP3/Hub restorations remain byte-exact to base. Fourteen named older component reports, including structure-readback-source.json, remain byte-exact to HEAD. All other tracked evidence JSON is unchanged except refreshed native source/result receipts. Diff check passed; tracked/untracked status inspected. No intended file deletions. Normal constructors still initialize complete state and drops; incomplete construction is private and guarded. Public disk/wire formats, admission caps and normal resident loading remain unchanged. No agent commit, push, merge or publication occurred.

Request controller feature_validation for source/report/media publication and exact-head hosted verification. This is not readiness or review submission. Owner metadata transfer and partial-state guards now exist. Keep those guards while implementing stable source generations, actual live identity/revision fences, unloaded-cell rules, page scheduling/installation, dirty overlays, global replay order and atomic durable generations. No complete-state promotion path was added. Live World/server/rendering paging, required city/maximum admission and full reference 20-run, physical-GPU and shaped-32-client qualification remain pending. Latest-master integration and independent review remain required. Keep public supported-limit claims and stable rollout gated. No owner answer needed.

### Readback file-change guard — validation in progress

Verified exact head cfd209c65ea4e0a125cd47a39e77bdc555eb451a run 42830971423: Linux, Windows, macOS ARM and Intel builds succeeded; publication skipped. Published metadata report, image and native video matched local bytes before this phase.

The private v10 opener now checks regular-file attributes before decoding and after full prefix/replay/tail/EOF validation. Observed size, modified time, creation time or file-key changes reject and clean staged replay before metadata/source publication. Directories reject before staging. This is a conservative file-change guard, not an immutable-generation lock: timestamps may be coarse or restored, file keys may be unavailable, and a path may change after the check. Normal resident loading, save formats, public limits and live startup are unchanged.

Added actual production-file tests and a CLI for same-size in-place modification with changed timestamp, identical-byte atomic replacement preserving mtime, deletion, cleanup and unchanged-source/detached-snapshot regressions. The initial fixture declared one drop while writing zero; corrected the fixture count before final rerun. No failed initial run is counted as passed. Full native regression and final affected-path checks remain pending for these edits.

Next: retain these conservative checks while implementing immutable source generations, actual live identity/revision fences, unloaded-cell rules, scheduling/installation, source-plus-dirty-overlay persistence and original global order. City/maximum admission and required 20-run reference, physical-GPU and shaped-32-client qualification remain pending. No supported-limit or stable-rollout gate changed.

### Readback file-change guard — local validation complete

Final affected-path Maven command exited 0: 114 tests in 24 fresh suites, no failures/errors/skips. Five new production-file tests cover observed in-place modification, identical-byte replacement preserving mtime, deletion, non-regular input, cleanup and unchanged-source/detached-snapshot regression. All 486 fenced Java sources match. Full local Java suite was not rerun.

Playtest: `python3 tools/structures/run_readback_file_change_check.py` passed actual production writer/opener source races, cleanup and snapshot regression. Its captured synthetic result window was inspected. Browser playtesting does not apply to this file integration. `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 with production Main/GLFW/X11 and an isolated synthetic offline profile. Capture/name/anchor/library, long-path import/export, four rotations, holes, occupied/player rejection, cancellation, malformed atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/editor regressions passed. All 244 native manifest entries and receipt digest match.

All 25 fresh F10 frames decoded with bundled JCodec: 42.947 seconds, 501873 bytes, 960x544; SHA-256 591ed660e7886dae67fcc3f7922d04bbc1d904cec74133fb2e2dc22b8ac52e62. Inspected green 0-degree staging bounds/anchor and red 90-degree occupied-cell rejection. The staging frame has visible terrain/mesh gaps. LOW/llvmpipe LLVM 15.0.6/HUD 1 FPS is functional-only evidence. Nearby usable geometry, frame budgets and physical GPU remain unqualified.

Distinct report dashboard/evidence/structure-readback-file-change.json records commands, environment, source/compiled hashes, expected/observed results, failed-fixture correction, native media and limitations. All 4,262 previously tracked historical JSON reports remained byte-exact to HEAD except the two standard native receipts. Fifteen named component reports were checked again by the final report builder. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No intended file deletions or unmerged index. Diff check and final status inspected. No agent commit/push/merge/publication.

Request controller feature_validation to publish these source/report/media edits and run exact-head hosted checks. This is not readiness or review submission. The attribute guard detects observed changes only; it is not an immutable-generation guarantee. Preserve partial-state guards. Continue immutable generation and real installation fences, unloaded-cell handling, source-plus-dirty-overlay atomic saves with original global order, and live World/server/rendering integration. Required city/maximum admission and 20-run reference, physical-GPU and shaped-32-client qualification remain pending. Latest-master integration and independent review remain required. Keep supported-limit and stable-rollout gates. No owner answer needed.

### Windows replacement fixture — validation in progress

Exact head 611c5f74df7ecadecdfbef167d85c14ae423cf1e run 42836223216 failed on Windows. Scoped job 129899018255 identifies ReadbackFileChangeTest.atomicReplacementWithIdenticalBytesAndTimesRejects: the filesystem denied replacement of the open source with AccessDeniedException, wrapped by the fixture callback. The fixture incorrectly required the move to succeed. The full Windows test run reported 1,190 tests, one failure and one skip; it did not pass. Other platform jobs in this run were cancelled; publication skipped.

The fixture now checks both safe outcomes without platform-name branches or skips. If replacement succeeds, the opener must reject the changed source and clean its spool. If AccessDeniedException prevents replacement, the opener must return an unchanged valid source; bytes, SHA-256, metadata and replay must match, resources must close, and replacement/reopen must succeed after reader close. Other I/O failures still fail. A separate explicit-denial fixture exercises this branch locally; that is not actual Windows execution. Production Java and native harness source are unchanged. New exact-head Windows validation remains required.

### Windows replacement fixture — local validation complete

Final scoped command exited 0: `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" -Dtest=ReadbackFileChangeTest,RegionalReadbackSourceTest,ReadbackMetadataTest,ReadbackStartupFenceTest,WorldSavePublicationTest test`. All 24 tests in five fresh suites passed, no failures/errors/skips. The replacement suite now has six tests. Real Linux replacement and explicit injected denial both exercised; no local Windows execution claim. The full local suite was not rerun.

Playtest: `python3 tools/structures/run_readback_replacement_check.py` passed actual integration fixtures for real replacement rejection, explicit AccessDeniedException, source-byte/fingerprint/metadata/replay preservation, retained replacement, cleanup, retry/reopen after close, and unchanged-source/detached-snapshot regression. Inspected its fresh synthetic result capture at dashboard/evidence/structure-readback-replacement-cli.png. Browser playtesting does not apply to this test/file integration. Distinct report dashboard/evidence/structure-readback-replacement.json records commands, environment, source/compiled hashes, scoped hosted failure, expected/observed results and qualification exclusions.

No production Java or native harness changed. All 486 Java source hashes match final validation. All 244 retained native source-manifest entries and its digest still match; the existing 501873-byte video SHA-256 remains 591ed660e7886dae67fcc3f7922d04bbc1d904cec74133fb2e2dc22b8ac52e62. Native execution was not repeated for this test-only fix. Its prior functional validation applies to identical production source; visible terrain/mesh gaps, 1 FPS llvmpipe and all physical/performance gates remain unqualified. No prior report/media was overwritten.

Final audit: ten MP3/Hub restorations byte-exact to review base; preexisting tracked evidence unchanged; no deleted paths or unmerged index; diff check passed and tracked/untracked status inspected. No intended deletions, production changes, skips, platform-name branches, agent commit/push/merge/publication or weakened qualification gates.

Request controller feature_validation for exact-source publication and new hosted checks. Previous Windows failure is not a pass and cancelled platforms remain unvalidated for that run. Fresh Windows execution is required; local injection is not a substitute. This is not readiness/review submission. Immutable generations, live identity/revision fences, unloaded-cell rules, source-plus-dirty-overlay persistence/global order, live paging, city/maximum admission, 20-run reference and physical-GPU/shaped-32-client qualification remain pending. Latest-master integration and independent review remain required. Keep supported-limit claims and stable rollout gated. No owner answer needed.

### Replay target-world fence — validation in progress

Verified exact head 69b31d3fdd22766ab0ea5e7c90611536a57f9905 run 42838610627: Windows, Linux, macOS Intel and ARM builds succeeded; publication skipped. The replacement fixture's hosted Windows failure is fixed on this source. This is scoped hosted validation, not full city/maximum qualification or review approval.

Added World.ReplayFence: owner-only capture of actual World identity, editsVersion, resident geometry epoch, model-library identity/revision, terrain identity/revision and open state. Capture and validation copy no geometry or definitions and perform no file I/O. RegionReplayWorker.submitFenced captures it on the owner; result(tags,world) requires the actual unchanged target and an active worker. Tag-only extraction rejects fenced requests; a world argument cannot imply validation for an unfenced request. Existing unfenced behavior remains for private callers. Results stay detached: this does not establish source generation, source-to-target model compatibility, unloaded-cell coverage or installation permission.

Five new integration tests exercise same-target no-mutation extraction, tag-only bypass, a replacement World with the same numbers/library, edits, model/terrain changes, resident geometry, close, replacement model/terrain identities, wrong owner and legacy unfenced behavior. Initial fixture checks used a nonexistent World.type getter, then a loaded-chunk getter on a deliberately nonresident world. Corrected to World.sample and encoded voxel values before final affected-path validation. No initial failed run is counted as passed.

Next: source-generation/dependency binding and unloaded-cell rules before scheduling or installation. Preserve partial-state guards. Dirty overlays, original global order, atomic durable generations, live paging and city/maximum admission remain unfinished. Keep 20-run reference, physical-GPU, shaped-32-client and stable-rollout gates.

Next implementation order (engineering defaults, not additional owner scope): bind each private target session to the validated source fingerprint and the exact worker-prepared owner model-library identity/revision before scheduling pages. A matching target fence alone cannot establish compatible dependency IDs. Keep source-generation compare-and-publish separate from current-path attribute observations; a paged save must not overwrite a newer generation. All source-plus-dirty-overlay writers need a shared publication protocol before paged saves can be enabled.

For unloaded coverage, begin with fixed 64-cell windows matching current private page requests. Track a window as usable only after all of its cells are installed and owner validation succeeds. Cancellation, stale results or partial installation must not mark coverage ready. Movement/collision/raycast/capture/edit paths must distinguish unknown coverage from empty/implicit terrain. This is a planned implementation order, not implemented coverage or measured performance. Retain original cross-region order separately from per-region ordinals before adding dirty-overlay persistence. Do not lift current admission caps to compensate for unfinished storage or scheduling.

### Replay target-world fence — local validation complete

Final affected-path Maven exited 0: 134 tests in 28 fresh suites, zero failures/errors/skips. Five new actual World/worker tests cover same-target extraction without mutation, wrong World with matching numeric revisions/library, edit/model/terrain/resident geometry changes, replacement model/terrain identities, owner/close checks, tag-only bypass and legacy unfenced behavior. Existing readback, replay/cache, load/save, multiplayer, startup and structure snapshot regressions passed. All 487 fenced Java sources match. Full local suite was not rerun.

Playtest: `python3 tools/structures/run_replay_world_fence_check.py` passed actual World/replay-worker integration and cleanup. Its fresh synthetic result window was inspected. Browser playtesting does not apply to this owner/worker integration. `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 with production Main/GLFW/X11 and an isolated synthetic offline profile. Capture/name/anchor/library, long-path import/export, all four rotations, holes, occupied/player rejection, cancellation, malformed atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/editor regressions passed. All 244 native source-manifest entries and the receipt digest match.

All 25 fresh F10 frames decoded: 45.448 seconds, 552500 bytes, 960x544; SHA-256 b9b7ae79b4e7ee23cb12456b2a761feb0b8964d19a6bd9a7dd483ac646639fe7. Inspected green 0-degree staging bounds/anchor and red 90-degree occupied-cell rejection. Dark gaps remain visible in the staging view. LOW/llvmpipe LLVM 15.0.6/HUD 1 FPS remains functional evidence only. Physical GPU, nearby usable geometry and frame budgets remain unqualified.

Distinct report dashboard/evidence/structure-replay-world-fence.json records exact commands, environment, source/compiled hashes, expected/observed results, fixture corrections, native media and limitations. Seventeen older component reports remain byte-exact to HEAD. All other tracked evidence JSON is unchanged except standard native receipts. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No intended deletions or unmerged index; diff check and final status inspected. No agent commit/push/merge/publication.

Request controller feature_validation for these source/report/media edits and exact-head hosted validation. This is not readiness/review submission. Actual target-world fences now exist for the private request API; immutable source/dependency binding, unloaded-cell rules, scheduling/installation, dirty overlays/global order, atomic durable generations and live World/server/rendering paging remain unfinished. Required city/maximum admission and 20-run reference, physical-GPU/shaped-32-client qualification remain pending. Latest-master integration and independent review remain required. Keep supported-limit claims and stable rollout gated. No owner answer needed.

### Replay source/dependency binding — validation in progress

Verified exact head 26a5dffa52b66a21a0d4c706a9fa12a2b79c2e92 run 42845736495: all four platform builds succeeded; publication skipped. Scoped hosted validation only.

The worker now records its exact transferred LocalGame and model-library identity/revision. A private binding admits one fresh target with that exact library, matching seed/generator and default terrain. It seals the validated byte fingerprint and source revision. Submissions/results validate current target state, worker lifetime and owner metadata/library identity/revision; requests carry the binding identity. Legacy extraction APIs cannot bypass a bound request. Source constructors and decode boundaries also reject terrain that differs from whole-file metadata. All owner checks copy no model/history/geometry and do no file I/O.

This binds a detached validated snapshot, not the current path's live generation. No loaded coverage, page installation, state promotion or save permission is granted. Metadata-only guards remain. Path deletion after staging does not invalidate owned source bytes; it does not authorize saving over a newer generation. Dirty overlays/global order, atomic publication and live paging remain separate requirements.

Eight new integration tests use actual v10 files and workers: 32-resolution dependency/ordered replay, fresh/mismatched targets, same-hash foreign metadata/requests, model changes before/after binding, path deletion, source terrain mismatch/decode race, owner/cancel/close and no mutation/promotion. The first fixture compile called an Exception-declaring directory helper inside an IOException-only factory; setup moved outside before final rerun. No initial failed run counted as passed. Final checks and fresh native regression remain pending.

### Replay source/dependency binding — local validation complete

Final affected-path Maven exited 0: 142 tests in 29 fresh suites, no failures/errors/skips. Eight new actual saved-file/worker tests exercise source/model/target binding, 32-resolution dependency and ordered replay, foreign metadata/requests, target freshness and identity, model mutation, source path deletion, metadata/terrain mismatch, decode-time terrain change, owner/cancel/close and no mutation/promotion. Existing replay/readback/load/save/multiplayer/startup/snapshot regressions passed. All 488 fenced Java sources match. Full local suite was not rerun.

Playtest: `python3 tools/structures/run_replay_source_binding_check.py` passed actual v10 writer/opener, metadata/dependency matching, request binding, decode race, cancellation/lifetime, snapshot retention and partial-save rejection workflows. Its fresh synthetic result window was inspected. Browser playtesting does not apply to this file/owner integration. `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 with production Main/GLFW/X11 and an isolated synthetic offline profile. Capture/name/anchor/library, long-path import/export, four rotations, holes, occupied/player rejection, cancellation, malformed atomicity, repeated placement, one-operation undo, 32-resolution dependency/restart and inventory/editor regressions passed. All 244 native source-manifest entries and the receipt digest match.

All 25 fresh F10 frames decoded: 44.578 seconds, 432010 bytes, 960x544; SHA-256 44ee712169e025327e4e7a54117e5f87486ce6b926b8c1191fa6418062b2fcc0. Inspected green 0-degree staging bounds/anchor and red 90-degree occupied-cell rejection. Dark squares remain visible within the staging footprint. LOW/llvmpipe LLVM 15.0.6/HUD 1 FPS remains functional evidence only; physical GPU, nearby usable geometry and frame budgets remain unqualified.

Distinct report dashboard/evidence/structure-replay-source-binding.json records exact commands, environment, source/compiled hashes, expected/observed results, fixture correction, native media and limitations. Eighteen older component reports remain byte-exact to HEAD. All other tracked evidence JSON remains unchanged except standard refreshed native receipts. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No intended deletions or unmerged index; diff check and final status inspected. No agent commit/push/merge/publication.

Request controller feature_validation for these source/report/media edits and exact-head hosted validation. This is not readiness/review submission. Private validated-snapshot/model binding now exists, but city/other source sidecars, live origin-generation compare-and-publish, unknown/loaded coverage, scheduling/installation, dirty overlays/global order, atomic durable generations and live World/server/rendering paging remain unfinished. No partial-state promotion or paged saving enabled. City/maximum admission and required 20-run reference, physical-GPU and shaped-32-client qualification remain pending. Latest-master integration and independent review remain required. Keep supported-limit claims and stable rollout gated. No owner answer needed.

### Read-only replay data coverage — validation in progress

Verified exact head 0470ea1ee1172bfd6822a87c56a11bf9ccabfca3 run 42853058079: all four platform builds succeeded; publication skipped. Scoped validation only.

Added explicit private read-only World data mode. World sample/getBlock/cell/value/material/region access routes through validated replay windows. Missing coverage raises World.UnloadedReplayCellException instead of falling back to default terrain. replayDataReady is separate from resident/mesh isLoaded and nearbyReady; a data window never claims usable meshes. Known unedited cells use matching default terrain only after their complete window is admitted.

A binding opens its view before the first request. It retains 1–4 complete aligned 64-cell windows; new publications evict the oldest published window, and evicted cells become unknown again. Only validated bound requests may publish; partial, foreign, failed or cancelled results cannot alter coverage. Model/target/source lifetime checks remain on reads. Closing world/worker releases view-owned page references. Caller-retained detached requests/pages remain outside this cache bound.

Mutation, initial replay, history export, structure capture and streaming paths reject read-only mode before callbacks. Snapshot plans captured before attachment also reject later use. No geometry/history root is installed, metadata state is not promoted, and Main cannot start this partial client. Live movement/collision/raycast scheduling, meshes, dirty overlays and atomic saves remain unfinished. This is actual World sampling integration, not completed gameplay paging.

Nine focused tests passed before adding the old snapshot-plan transition check. Final affected-path checks include that added guard plus normal-world regression. No failed check is recorded as passed. Fresh CLI/native/media validation remains pending.

### Read-only replay data coverage — local validation complete

Final affected-path Maven exited 0: 151 tests in 31 fresh suites, no failures/errors/skips. Nine new integration tests exercise actual World samplers, typed unknown without terrain fallback, known natural terrain/fractional colour/model32, negative coordinates/FIFO eviction, partial/foreign/cancelled admission, read-only guards, pre-existing snapshot plans, bootstrap rejection, owner/model/lifetime invalidation and ordinary-world compatibility. Existing replay/readback/save/load/multiplayer/snapshot regressions passed. All 490 fenced Java source hashes still match. Full local suite was not rerun.

Playtest: `python3 tools/structures/run_replay_read_view_check.py` exited 0 using actual v10 writer/opener, private worker and World sampler integration. Fresh synthetic result image inspected. Browser playtesting does not apply to this file/owner integration. `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` exited 0 using production Main/GLFW/X11 and an isolated synthetic offline profile. Capture/name/anchor/library, long-path import/export, four rotations, holes, occupied/player rejection, cancellation, malformed atomicity, repeated placement, one-operation undo, model32/restart and inventory/editor regressions passed. All 244 native manifest entries and receipt digest match the tested source.

Decoded all 26 fresh F10 frames: 46.851 seconds, 502192 bytes, 960x544; SHA-256 cee2aeccf5dd75188359e19def716244b3e517ca48716af0fe892c609d0ffc45. Inspected green 0-degree staging bounds/anchor and world-unchanged status, plus red 90-degree occupied-cell rejection. Dark square gaps remain inside the staging footprint. LOW/llvmpipe LLVM 15.0.6/HUD 1 FPS remains functional-only evidence; physical GPU, nearby usable geometry and frame budgets remain unqualified.

Distinct report `dashboard/evidence/structure-replay-read-view.json` records exact commands, environment, source/compiled hashes, expected/observed results, visual inspection, limits and pending work. Nineteen older component reports remain byte-exact to HEAD. Other tracked evidence JSON is unchanged except standard refreshed native receipts. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No intended deletions or unmerged index; diff check and final status inspected. No agent commit/push/merge/publication.

Request controller feature_validation for source/report/media edits and exact-head hosted validation. This is not readiness or review submission. Private World data sampling now handles known/unknown windows. Main startup remains blocked for incomplete metadata. Live movement/collision/raycast scheduling, meshes, dirty overlays/global order, city/other sidecars, live source-generation compare-and-publish, atomic durable generations and server/rendering paging remain unfinished. City/maximum admission and required 20-run reference, physical-GPU and shaped-32-client qualification remain pending. Latest-master integration and independent exact-head review remain required. Keep supported-limit claims and stable rollout gated. No owner answer needed.

### Bounded replay directory snapshot — validation in progress

Verified exact head 238d293860f3e7a01fafd891ba646d86b1cd1eb0 run 42863472934: all four platform builds succeeded; publication skipped. Previously published report, CLI image and video match local bytes.

Selected spool visits now retain up to 4096 matching section records in a 114688-byte primitive buffer while validating the whole sealed index. No policy or payload delivery occurs until validation finishes. Payload sections still match the sealed entries and CRCs. Larger selections retain the existing streaming second pass, without a new rejection limit. Recurring sections stay in file order. Full traversal remains unchanged. This halves global metadata passes for bounded selected visits (four to two per two-pass cell-index build); the first global scan per visit remains. This is not a disk region directory or a measured performance/peak-heap claim.

The retained directory is a per-visit validated snapshot. Changes to the original index during delivery cannot change retained entries; the next visit revalidates the original. No extra temporary files, persistent cache or publication authority are added. Focused tests passed, including threshold/fallback, order, limits before policy and checksum rejection. Final broader checks, CLI and native regression/media remain pending. No admission caps raised or rollout gates removed.

### Bounded replay directory snapshot — local validation complete

Final affected-path Maven exited 0: 154 tests in 32 fresh suites, zero failures/errors/skips. Three new tests cover 4096-record snapshot and 4097-record streaming fallback, recurring-section order, row limits before policy, original-index mutation during delivery, next-visit revalidation, selected payload/global index corruption, empty selection and cleanup. Existing cancellation/ownership/reentrancy, cache, worker/read-view, persistence/load and multiplayer regressions passed. All 491 fenced Java sources match. Full local suite was not rerun.

Playtest: `python3 tools/structures/run_replay_directory_check.py` exited 0 against actual staged files and the three integration workflows. Expected threshold/fallback, order, row preflight, sealed snapshot, payload checksum and cleanup behavior was observed. Fresh synthetic result image inspected. Browser playtesting does not apply to this private file integration. Main/native workflow was not rerun for this noninteractive index change; the prior video remains historical at parent 238d2938, without a new native-pass claim.

Distinct report `dashboard/evidence/structure-replay-directory.json` records exact command, source/compiled hashes, environment, expected/observed results, visual inspection and pending work. Twenty older component reports remain byte-exact to HEAD. All other tracked evidence, including native video/receipts, is unchanged. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No intended deletions or unmerged index; diff check and final status inspected. No agent commit/push/merge/publication.

Request controller feature_validation for these source/report/CLI-media edits and exact-source hosted validation. This is not readiness/review submission. The 114688-byte primitive snapshot removes one global metadata pass for bounded selections, but first global scans and larger-selection fallback remain. No timing/peak-heap qualification or cap increases. Live paging, authoritative dirty overlays/global order, immutable source generations/sidecars, atomic durable persistence and scalable disk region lookup remain unfinished. City/maximum admission and required 20-run reference, physical-GPU and shaped-32-client qualification remain pending. Latest-master integration and independent review remain required. Supported-limit claims and stable rollout stay gated. No owner answer needed.

### Private replay request pump — validation in progress

Verified exact head 18ff82c06c8016212a2907875bfd28e2a18972e8 run 42867985952: Windows, mac-arm, mac-intel and Linux builds succeeded; publication skipped. Prior published directory report/image match local bytes.

Added a private owner-driven pump over bound read-only windows. A desired list is limited to view capacity (1–4); duplicate windows share one owned request. Each tick observes each pending result once, then tries bounded submissions without waiting. A full shared queue returns backpressure rather than failure or a blocking retry. Desired-window changes cancel and discard obsolete owned requests. Complete results publish through the existing source/model/World fences. Failures remain visible until explicit retry; repeated ticks or an unchanged desired list do not retry them. Close cancels owned work and releases the view without joining or closing the worker.

Six focused integration tests passed using actual v10 files, workers and World samplers: deduplication/publication without promotion, obsolete decode cancellation, shared-queue saturation, failed decode/explicit recovery, input/owner/model fences and closing during blocked decode. Main remains unable to start partial metadata, and capture/mutation/save/stream guards remain. Final broader tests, actual CLI and fresh result media remain pending. No timing, gameplay, cap or qualification claims.

### Private replay request pump — local validation complete

Final affected-path Maven exited 0: 160 tests in 33 fresh suites, no failures/errors/skips. Six new integration workflows cover actual saved-file/worker/World publication, request deduplication, desired-window change with blocked-decode cancellation, externally saturated shared queue, failed decode with explicit recovery, input/owner/model fences and closing without waiting for blocked decode. Existing replay/directory/cache/worker/read-view, persistence/load and multiplayer regressions passed. All 492 fenced Java source hashes match. Full local suite was not rerun.

Playtest: `python3 tools/structures/run_replay_pump_check.py` exited 0 and exercised those six actual integration workflows. Desired-window change/cancellation and queue saturation provide edge cases; unchanged source, blocked partial saves and existing normal paths provide regressions. Fresh synthetic result image inspected. Browser playtesting does not apply to this private file/owner integration. Main/native workflow was not rerun; previous video remains historical at 238d2938 and is not a new native pass.

Distinct report `dashboard/evidence/structure-replay-pump.json` records exact command, environment, source/compiled hashes, expected/observed behavior, visual inspection and limits. Twenty-one older component reports and all other tracked evidence, including native receipts/video, remain byte-exact to HEAD. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No intended path deletions or unmerged index; diff check and final status inspected. No agent commit/push/merge/publication.

Request controller feature_validation for source/report/CLI-media edits and exact-source hosted validation. This is not readiness or review submission. Pump ticks perform bounded owner work and never wait for completion or spin on queue saturation; no timing/heap/frame claim. Main partial startup stays disabled. Live movement/collision/raycast and mesh scheduling, dirty overlays/global order, immutable source generations/sidecars, atomic durable persistence, server/rendering paging and scalable disk region lookup remain unfinished. City/maximum admission and required 20-run reference, physical-GPU and shaped-32-client qualification remain pending. Latest-master integration and independent review remain required. Supported-limit claims and stable rollout stay gated. No owner answer needed.

### Single source-pass replay cell build — validation in progress

Verified exact head f1f7112c9cf57c0a7f083f89d3ff40b6a6c2d82b run 42872110479: all four platform builds succeeded; publication skipped.

Cell-index builds now make one selected source traversal into a bounded counting scratch, then scatter the copied rows. Scratch rows retain original matching-region ordinals and are sealed with an in-memory CRC. Size/order/validity/region/counts and CRC must pass before the final root transfers. Per-cell CRCs and selected-page policy checks remain. Source-policy checks now occur once during the source pass; cache/worker test counts reflect that change. The future-frame corruption test injects during the first pass instead of waiting for a removed second pass.

The raw scratch adds at most 56000000 logical disk bytes at the existing 2-million-row ceiling. Source owns at most two scratch paths (raw and candidate); failed deletion retains ownership and blocks new raw builds. Successful candidate transfer is explicit. Cache logical copied-byte limits remain unchanged. No timing/peak-memory claim or admission-limit increase. Four new focused tests passed for raw corruption/truncation/tail/order, retained validated snapshots after source change, failed cleanup and worker-only ownership/transfer. Broader tests and fresh CLI/media remain pending.

### Single source-pass replay cell build — local validation complete

Final affected-path Maven exited 0: 164 tests in 34 fresh suites, no failures/errors/skips. Four new actual spool/cell-index workflows cover valid-row CRC corruption, truncation/tail/order, validated copy retention after source change, next-build source revalidation, failed scratch deletion/new-build blocking, worker-only ownership and explicit candidate transfer. Existing cancellation, future source-frame corruption, cache/worker/pump/read-view, persistence/load and multiplayer regressions passed. All 493 fenced Java sources match. Full local suite was not rerun.

Playtest: `python3 tools/structures/run_replay_counting_copy_check.py` exited 0. Actual file workflows exercised single-source-copy behavior, raw-corruption and failed-cleanup edges, and detached-root/source integrity regressions. Fresh synthetic result image inspected. Browser playtesting does not apply to private file integration. Main/native workflow was not rerun; prior video is historical at 238d2938 and is not a new native pass.

Distinct report `dashboard/evidence/structure-replay-counting-copy.json` records exact command, environment, source/compiled hashes, expected/observed behavior, test-count adjustments, visual inspection and resource bounds. Twenty-two older component reports and all other tracked evidence, including native receipts/video, remain byte-exact to HEAD. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No path deletions or unmerged index; diff check and final status inspected. The old two-source-pass build was replaced intentionally. No agent commit/push/merge/publication.

Request controller feature_validation for source/report/CLI-media edits and exact-source hosted validation. This is not readiness or review submission. Source pass count is reduced, but extra scratch I/O/disk is explicit and no speed/peak-heap/cold-load budget is claimed. Failed scratch cleanup stays owned. Main partial startup, live movement/collision/raycast/meshes, dirty overlays/global order, immutable source generations/sidecars, atomic durable persistence, server/rendering paging and disk region directory remain unfinished. City/maximum admission and required 20-run reference, physical-GPU and shaped-32-client qualification remain pending. Latest-master integration and independent review remain required. Supported-limit claims and stable rollout stay gated. No owner answer needed.

### Replay-build comparison — local diagnostic complete

Verified exact head 3227e84e02a73062133d26acfb71457c9e4c7e6b run 42876779410: all four platform builds succeeded; publication skipped. No new wait or dispatch required for that head.

Playtest: `python3 tools/structures/run_replay_build_comparison.py` exited 0 against actual prior/candidate spool/cell-index implementations. Separate JVMs used the same byte-identical 65536-row/8192-section fixture, 32768 selected rows in 4096 cells, two warmups and twenty measured builds each. First/last window boundaries, repeated source rows/order, policy pass counts and cleanup passed every run. Fresh actual result image inspected. Browser playtesting does not apply to this CPU/file integration. Main/native workflow was not rerun. The initial attempt stopped before Java execution because /usr/bin/time was absent; direct Linux os.wait4 resource collection and a 240-second child timer removed that dependency. No failed attempt counted as passed.

Build median/p95/worst ms: prior112.167/125.916/128.984; candidate91.205/131.189/161.158. First-window median/p95/worst ms: prior1.037/1.367/1.695; candidate0.901/1.075/1.166. Whole-process max RSS: prior147636KiB, candidate159320KiB. Candidate median improved, but build p95/worst and RSS worsened. No overall speedup, timing-budget or qualification pass claimed. Environment, raw twenty-run samples, heap-pool peaks, method caveats and all source/class/helper hashes are recorded in dashboard/evidence/structure-replay-build-comparison.json. Fixed run order and warm-cache/JIT/GC/OS effects limit conclusions.

Production sources and compiled application/test classes remain unchanged and match prior validated hashes; no full Maven rerun this phase. Twenty-three older reports and all other tracked evidence remain byte-exact to HEAD. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No path deletions or unmerged index; diff check and final status inspected. No agent commit/push/merge/publication.

Request controller feature_validation for benchmark tooling/docs/report/image edits and exact-source hosted validation. This is not readiness or review submission. The diagnostic is not the required city/maximum/reference/cold-cache fixture. Live paging, dirtied overlays/global order, immutable generations/sidecars, atomic persistence, scalable region lookup and city/maximum admission remain unfinished. Required city/maximum 20-run, physical-GPU and shaped-32-client qualification remain pending. Latest-master integration and independent review remain required. Supported-limit claims and stable rollout stay gated. No owner answer needed.

### Bounded replay scatter pages — validation in progress

Verified exact head f937294db8789d0bca7cdfb584354a0de89be3b7 run 42881673467: all four platform builds succeeded; publication skipped.

Replaced one positional 28-byte write per copied row with four reusable 64 KiB write-back pages (262144 primitive array bytes). Candidate file is pre-sized within its unchanged 56000000-byte limit. Pages load prior contents on miss, flush on eviction and reuse the evicted array; records may cross two pages. Remaining pages flush only after raw size/order/region/count/CRC validation, before force and ownership transfer. Worker-only I/O, cancellation checks, scratch cleanup ownership and per-cell CRC rules remain. No admission cap or Main startup change.

Focused tests passed: all 64 windows over 32768 rows match original order across splits/evictions/reuse; cancellation after a real dirty-page flush removes both files and allows retry. Existing raw/source corruption, cleanup, cache/worker/pump tests passed. Broader tests, actual CLI/media and same-fixture twenty-run measurement remain pending. No gain or qualification claimed yet.

### Bounded replay scatter pages — local validation complete

Final affected-path Maven exited 0: 166 tests in 35 fresh suites, no failures/errors/skips. All 64 windows over 32768 rows preserve source order across record splits, page eviction and buffer reuse. Cancellation after actual dirty-page flush leaves no transferred root, cleans both scratch files and permits retry. Existing source/raw corruption, cleanup/ownership, cache/worker/pump/read-view, persistence/load and multiplayer regressions passed. All 494 fenced Java source hashes match. Full local suite was not rerun.

Playtest: `python3 tools/structures/run_replay_scatter_check.py` exited 0 against actual source/cell files. Fresh result image inspected. `python3 tools/structures/run_replay_scatter_comparison.py` exited 0 against the prior one-pass writer and new paged writer, using the same byte-identical fixture, two warmups and twenty measured builds/version. Source pass counts, first/last windows and cleanup passed each run. Fresh comparison image inspected. Browser playtesting does not apply to this private file/worker integration. Main/native workflow was not rerun; earlier video remains historical, without a new native-pass claim.

Build median/p95/worst ms: prior99.813/141.523/144.418; candidate54.977/82.498/97.867. Window median/p95/worst ms: prior0.874/1.106/1.130; candidate0.940/1.181/1.478. Whole-process max RSS KiB: prior158988, candidate170192. Build improved in this warm-cache CPU/file diagnostic, while window p95/worst and RSS worsened. No city/cold/GPU/network/heap/frame or overall qualification pass claimed. Fixed run order and OS/JIT/cache/GC noise limit conclusions.

Distinct reports `dashboard/evidence/structure-replay-scatter.json` and `structure-replay-scatter-comparison.json` record exact commands, environment, source/class/helper hashes, raw samples, expected/observed behavior, image inspection and tradeoffs. Twenty-four older reports and all other tracked evidence, including prior measurements and native video/receipts, remain byte-exact to HEAD. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No path deletions or unmerged index; diff check and final status inspected. No agent commit/push/merge/publication.

Request controller feature_validation for source/tooling/docs/report/media edits and exact-source hosted validation. This is not readiness or review submission. Four reusable pages bound primitive arrays to 262144 bytes; candidate/scratch byte caps remain unchanged. Main partial startup remains disabled. Live gameplay/meshes, dirty overlays/global order, immutable generations/sidecars, atomic persistence, server/rendering paging, scalable region lookup and city/maximum admission remain unfinished. Required city/maximum 20-run reference, physical-GPU and shaped-32-client qualification remain pending. Latest-master integration and independent review remain required. Supported-limit claims and stable rollout stay gated. No owner answer needed.

### Private replay metadata buckets — validation in progress

Verified exact head 3ad9cddc042fa4700641a8d855de08dc58ff07e1 run 42886578188: all four platform builds succeeded; publication skipped.

Cell builds now use a worker-private copied metadata lookup. Initial preparation validates the whole sealed original index before publishing at most 32 hash-bucket files. They preserve original section-offset order and hold at most 56000000 copied metadata bytes in total. Preparation opens at most 32 output streams with 4096-byte buffers. Later lookup validates the chosen bucket's full CRC, size, row totals, hash membership and offset order before payload/policy delivery. The existing 4096-record bounded snapshot/large-selection fallback and row preflight apply within that bucket. Payload sections still match the sealed metadata CRC. Direct visitRegion/full traversal keep original global-index semantics.

This is a private copied snapshot. Other-bucket corruption is deferred until read; later original-index changes do not rewrite copied metadata. Hash collisions can still degrade to a full-size metadata scan. No constant/logarithmic lookup, performance or city qualification claim. No public format, source-row/admission cap or startup/save authority change. Failed/cancelled preparation cleans prefixes and allows retry; failed deletion retains paths for source close/retry. Source working disk adds at most 56000000 metadata bytes. Five new focused workflows and existing regressions passed. Broader tests and actual CLI/media remain pending.


### Private replay metadata buckets — local validation complete

Final affected-path Maven exited 0: 171 tests in 36 fresh suites, no failures/errors/skips. All 495 Java source hashes match the validation fence. Five new workflows cover recurring region order and hash collisions, selected-bucket CRC rejection before policy, deferred other-bucket corruption, preparation cancellation/cleanup/retry, 4097-section fallback and row preflight, failed deletion ownership and wrong-thread rejection. Existing offline persistence, multiplayer and replay regressions passed. Full local suite was not rerun.

Playtest: `python3 tools/structures/run_replay_buckets_check.py` exited 0 against actual private spool/cell files. Captured and inspected `dashboard/evidence/structure-replay-buckets-cli.png` on the inherited display. Browser playtesting does not apply to this private file integration. Main/native workflow was not rerun; previous video remains historical. No timing or peak-memory measurement was made this phase.

The distinct report `dashboard/evidence/structure-replay-buckets.json` records exact commands, environment, source/class/tool hashes, expected/observed behavior, resource bounds and image inspection. All 26 older reports remain byte-exact to HEAD. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No path deletions or unmerged index; diff check passed. No agent commit/push/merge/publication.

Request controller feature_validation for these source/tooling/docs/report/image edits. This is not readiness or review submission. Initial global validation remains; later reads scan one private copied bucket. Hash collisions can still require a full metadata scan. Main partial startup remains disabled. Live gameplay/meshes, dirty overlays/global order, immutable generations/sidecars, atomic persistence, server/rendering paging and city/maximum admission remain unfinished. Required city/maximum 20-run reference, physical-GPU and shaped-network 32-client qualification remain pending. Latest-master integration and independent exact-head review remain required. Supported-limit claims and stable rollout stay gated. No owner answer needed.


### Private replay lookup diagnostic — local validation complete

Verified actual exact-head run 42893116436 for ceece1af0b1f3ad5decb794d1068238f3c9700c4: Linux, Windows, mac-arm and mac-intel succeeded; publication skipped. Production/test Java sources and compiled classes remain byte-exact to the 171-test bucket validation. No new production change or full local Maven rerun this phase.

Playtest: `python3 tools/structures/run_replay_lookup_diagnostic.py` exited 0 against actual private spool files. The fixture has 96 recurring regions, 49152 rows, 6144 sections and 512 rows per region. Every pass checks all 96 regions plus an empty region, exact source order and policy counts. Two warmups and twenty measured passes per method alternate method order and rotate region order. Lookup leaves the owned file set unchanged; source close removes all files. Copied metadata totals 172032 bytes across 31 files. Fresh measured-result image captured on inherited display and inspected. Browser playtesting does not apply to private CPU/file integration. Main/native workflow was not rerun; prior video remains historical.

Direct pass median/p95/worst: 178.195/199.614/201.215 ms. Bucket pass median/p95/worst: 51.097/67.554/70.311 ms. Initial bucket preparation plus one lookup: 111.614 ms. Whole-process maximum RSS: 155600 KiB, including fixture setup, both methods, warmups and all runs; it cannot be assigned to either method. Initial preparation runs on the worker. This warm CPU/file diagnostic shares a JVM/source/cache and includes equality/policy checks and allocations. It does not measure cold-load, independent-process memory, main-thread stalls, usable geometry, client/server, frames, ticks, GPU or network. Hash collisions can still degrade to a full metadata scan. No city/maximum budget or supported-limit claim follows.

The first attempt completed lookup computation but failed before image capture because the reused renderer had an incompatible argument contract (`IllegalArgumentException: Fresh image required`). Added a dedicated measured-text renderer and reran the entire diagnostic. Only the successful attempt supplies reported samples. Distinct report/image `dashboard/evidence/structure-replay-lookup-diagnostic.json` and `.png` preserve exact commands, environment, raw samples and source/class/tool hashes. All 27 older reports remain byte-exact to HEAD; ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No path deletions or unmerged index. Diff check passed. No agent commit/push/merge/publication.

Request controller feature_validation for diagnostic tooling/docs/report/image edits. Not readiness or review submission. Main partial startup remains disabled. Live gameplay/meshes, dirty overlays/global order, immutable generations/sidecars, atomic persistence, server/rendering paging and city/maximum admission remain unfinished. Required city/maximum twenty-run reference, physical-GPU and shaped-network 32-client qualification remain pending. Latest-master integration and independent exact-head review remain required. Supported-limit claims and stable rollout stay gated. No owner answer needed.


### Explicit ray loading boundary — validation in progress

Verified actual fa39e073261cf97cbf12d7dea85c5008901dcb97 run42896845625: all four platform builds succeeded; publication skipped. Existing collision queries already block unresident chunks, and legacy raycasts stop at the first unresident cell. The new opt-in `BlockRaycaster.trace` returns that stopping cell plus its replay-data readiness, without treating it as a hit or known air. Data-ready but unmeshed cells remain stopping boundaries. Legacy `cast` keeps null-on-boundary behavior and does not allocate a trace or call data readiness. Normal ray normalization, traversal and hit selection remain unchanged.

This is a query API for later owner-side demand wiring. It performs no I/O, page request, geometry publication, movement, mutation or startup activation. Main partial startup remains disabled. Four focused synthetic World/ray/collision workflows and existing ray, physics, model/fractional and streaming tests passed. Broader tests and actual CLI/image remain pending. No performance or qualification claim.


### Explicit ray loading boundary — local validation complete

Affected-path Maven exited 0: 224 tests in 42 fresh suites, no failures/errors/skips. All 496 Java source hashes match the validation fence. New unknown-replay and data-ready-but-unmeshed cases stop without geometry sampling; negative cell coordinates, resident hit/miss, first missing chunk, invalid inputs, legacy no-readiness-query behavior and solid collision boundaries passed. Existing ray, fractional/model, physics, streaming, replay, offline persistence and multiplayer regressions passed. Full local suite was not rerun.

Playtest: `python3 tools/structures/run_replay_ray_boundary_check.py` exited 0 against actual synthetic World/ray/collision fixtures. Fresh `dashboard/evidence/structure-replay-ray-boundary-cli.png` captured on inherited display and inspected. Browser playtesting does not apply to this internal query API. Main/native workflow was not rerun; old video remains historical. No performance claim. The distinct report `dashboard/evidence/structure-replay-ray-boundary.json` records actual commands, environment, source/class/tool hashes, expected/observed results, image inspection and limits.

All 28 older reports and other tracked evidence remain byte-exact to HEAD. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No path deletions or unmerged index; diff check passed. The only implementation replacement moves the existing ray body into a private callback overload; legacy consumers still use `cast`. No agent commit/push/merge/publication.

Request controller feature_validation for source/test/tool/docs/report/image edits. Not readiness or review submission. The opt-in trace performs no I/O, page request or publication. Main partial startup remains disabled. Owner request wiring, live gameplay/meshes, dirty overlays/global order, immutable generations/sidecars, atomic persistence, server/rendering paging and city/maximum admission remain unfinished. Required city/maximum twenty-run reference, physical-GPU and shaped-network 32-client qualification remain pending. Latest-master integration and independent exact-head review remain required. Supported-limit claims and stable rollout stay gated. No owner answer needed.


### Bounded ray-to-worker demand — validation in progress

Verified actual exact-head ef1277247ec6f4f8b27a71b2373523818a9f04b2 run 42900679809: Linux, Windows, mac-arm and mac-intel succeeded; publication skipped. The private owner pump now accepts at most 64 ray boundary cell hints, maps valid world coordinates to aligned 64-cell region windows and deduplicates before applying the unchanged 1–4-window view budget. Invalid/null/over-cap input rejects before existing desired/pending/failure state changes. Coordinate mapping uses floor-divided regions and local cell bits, including negative coordinates. Reported readiness is ignored: a captured flag can be stale or from another view. Current retained-frame state still determines worker requests.

Demand preflight performs no I/O, geometry sampling or submission; existing explicit ticks retain bounded queue/backpressure, cancellation and owner/source/model/World fences. Main partial startup remains disabled. Four focused actual saved-world/worker/ray workflows passed: data loading without mesh/save promotion, negative mapping, bounded preflight preserving blocked work and stale-ready reload after eviction. Broader tests and CLI/image remain pending. No performance or qualification claim.


### Bounded ray-to-worker demand — local validation complete

Affected-path Maven exited 0: 228 tests in 43 fresh suites, no failures/errors/skips. All 497 Java source hashes match the validation fence. Four new workflows use actual synthetic saved-world files, worker and bound World: ray demand to data readiness without mesh/save promotion, negative coordinate mapping, invalid/null/over-cap demand preserving blocked work and stale readiness after eviction requiring reload. Existing pump cancellation/backpressure/retry, read-view/source integrity, ray/physics/model/fractional/streaming, offline persistence and multiplayer regressions passed. Full local suite was not rerun.

Playtest: `python3 tools/structures/run_replay_demand_check.py` exited 0. Fresh `dashboard/evidence/structure-replay-demand-cli.png` captured on inherited display and inspected. Data loads through the worker; rays and collision still block unmeshed cells. Source save bytes remain unchanged, and partial save remains rejected. Browser playtesting does not apply to this internal demand API. Main/native workflow was not rerun; older video remains historical. No performance claim. The distinct report `dashboard/evidence/structure-replay-demand.json` records actual commands, environment, source/class/tool hashes, expected/observed results and limits.

All 29 older reports and other tracked evidence remain byte-exact to HEAD. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No path deletions or unmerged index; diff check passed. No agent commit/push/merge/publication.

Request controller feature_validation for source/test/tool/docs/report/image edits. Not readiness or review submission. The private adapter accepts at most 64 hints and the unchanged 1–4 desired-window capacity. Captured readiness is not authority; current binding and retained-frame state still control worker requests. Preflight performs no I/O, geometry sampling or submission; explicit ticks remain bounded. Main partial startup remains disabled. Main request wiring/live movement, usable meshes, dirty overlays/global order, immutable generations/sidecars, atomic persistence, server/rendering paging and city/maximum admission remain unfinished. Required city/maximum twenty-run reference, physical-GPU and shaped-network 32-client qualification remain pending. Latest-master integration and independent exact-head review remain required. Supported-limit claims and stable rollout stay gated. No owner answer needed.


### Current-source native structure workflow — validation in progress

The controller reports exact-head 38d547cbe15df7a3cedfbc4f9c87a2f6443c45b7 builds succeeded. The inspected run 42904953910 currently has Linux, Windows and mac-arm succeeded while mac-intel remains in progress. No full-build pass is claimed from that observation.

Added optional `--evidence-prefix` to the native harness. Validated prefixes contain 1–64 lowercase letters/digits/hyphens with an alphanumeric first character. Prefix mode stages sanitized output in ignored scratch, rejects existing prefix destinations and copies fresh artifacts with exclusive creation only after successful playtest and source-fence validation. Default canonical naming remains available. The native source manifest now includes the Python driver. Existing reports/video/screenshots remain unchanged.

CLI help and traversal/case/empty/overlong prefix rejection passed before native launch/evidence changes. Source and compiled hashes match the prior 228-test demand validation. `python3 deploy/run_structure_playtest.py --display "$DISPLAY" --evidence-prefix structure-demand-native` is running against production Main/GLFW on the inherited role display with a new isolated synthetic profile. It explicitly uses software rendering for functional validation. Runtime logs remain private and unread. Current-source functional result/media inspection remain pending; no new native or performance pass yet. City/maximum, physical-GPU and shaped-network 32-client qualification remain pending.


### Current-source native structure workflow — local validation complete

`python3 deploy/run_structure_playtest.py --display "$DISPLAY" --evidence-prefix structure-demand-native` exited 0 on production Main/GLFW/X11 with an isolated synthetic LOW profile and inherited role display. Renderer: llvmpipe (LLVM 15.0.6, 256 bits). Named capture/anchor/library, self-contained export/import including the >240-character import path, footprint and all four rotations, relative geometry/model orientation/colours, empty-space preservation, occupied/player overlap rejection without mutation, one-operation undo, placement/undo/export/import cancellation, malformed import atomicity, repeated placement, 32-resolution dependency/restart recovery, inventory and model editor checks passed.

Playtest: current-source named capture, clear 0-degree bounds/anchor preview, player-overlap rejection, blocked 90-degree preview and placed component screenshots inspected. Fresh F10 MP4 is 534524 bytes, 45.44 seconds, 25 H264 frames, 960×544. Every frame decoded with bundled JCodec; decoded count matches metadata, first/last frames differ and artifact is below 6 MB. Representative decoded video frame and placed-geometry screenshot inspected. `ffprobe` was unavailable; no missing-tool attempt counted as passed. JCodec supplied full decode instead. This software-rendered run shows HUD 1 FPS and is functional evidence only. No physical-GPU, 30 FPS, frame/tick, cold-load, city/maximum or network qualification is claimed.

Current compiled MusicTrack separately decoded restored CBR stereo and VBR mono fixtures to 44100 Hz, 6.060 seconds PCM each. This is not a new packaged-JAR smoke result. `TMPDIR="$PWD/target/reviewer-tmp" python3 -m unittest deploy.tests.test_hub_preview_registration` exited 0: six synthetic API workflow registration tests passed. Prefix help/invalid input and existing-prefix rejection passed. Reusing the prefix rejects before clearing scratch; source world and generated artifacts remain byte-exact. Native runtime logs remained private and unread.

All 497 production/test Java source hashes and compiled hashes match the prior 228-test/43-suite validation. The native source manifest also pins the Java harness and changed Python driver. No new Maven run was needed for these harness-only edits. All 8267 tracked historical evidence files remain byte-exact; all 30 prior phase reports and ten MP3/Hub restorations are preserved. No path deletions or unmerged index; diff check passed. Actual final run42904953910 jobs are now all successful on Linux, Windows, mac-arm and mac-intel; publication skipped. Earlier Intel Mac pending observation was retained honestly.

Seventeen fresh prefixed native artifacts and `dashboard/evidence/structure-native-current-validation.json` are local evidence pending controller publication. No existing native report, screenshot or video was overwritten. Request controller feature_validation for harness/docs/report/media edits and exact-source hosted checks. No agent commit/push/merge/publication. This is not readiness or review submission. Main partial startup, usable meshes/live request wiring, dirty overlays/global order, immutable generations/sidecars, atomic persistence, server/rendering paging and city/maximum admission remain unfinished. Required city/maximum twenty-run reference, physical-GPU and shaped-network 32-client qualification remain pending. Latest-master integration and independent exact-head review remain required. Supported-limit claims and stable rollout stay gated. No owner answer needed.


### Independent ordinary-save attempts — validation in progress

Verified actual exact-head c4ff170092357de6dba761fcc258cbdde089041e run 42909614021: Linux, Windows, mac-arm and mac-intel succeeded; publication skipped. Inspection found ordinary saves shared `world.dat.tmp`. With A blocked after opening/buffering its header, B could truncate/write/move the same inode; when A then failed, its buffered close rewrote B's published target. The controlled prior-source probe reproduced changed published bytes and lost legacy scratch. The same probe against the fix preserved both.

Ordinary LocalGame saves now create a private `.world-save-*.tmp` attempt in the destination directory. The attempt is registered before open and its open/write/flush/force/publication all sit under its cleanup scope. The existing atomic-required/compatibility-fallback policy and serialization remain. Formats, row limits, partial-save guard and last-writer behavior remain. This does not add source-generation CAS or atomic city/sidecar persistence.

Three new actual file integration tests passed: failed overlapping writer preserves B's published bytes and unowned scratch, successful overlaps leave complete readable files, denied publication preserves target contents and cleans its own attempt. One existing limit/retry test initially failed because it expected successful retry to consume the legacy shared `.tmp` sentinel. Updated it to require that sentinel's byte preservation and no remaining owned attempt files; the focused retry passed. Broad tests and CLI/image remain pending. No missing or failed check is counted as passed.


### Independent ordinary-save attempts — local validation complete

Affected-path Maven exited 0: 237 tests in 46 fresh suites, no failures/errors/skips. All 498 Java source hashes match the validation fence. Three new actual file workflows passed: failed overlapping writer preserves published B bytes and unowned legacy scratch, successful overlaps reopen as complete expected states, denied publication preserves target contents and cleans its own attempt. Existing normal-save limits, atomic publication policy, staged/structure save, source/readback/replay, ray/physics/model/fractional/streaming, offline persistence and multiplayer regressions passed. Full local suite was not rerun.

Playtest: `python3 tools/structures/run_save_isolation_check.py` exited 0 against actual synthetic LocalGame files and interleaved writer threads. Fresh `dashboard/evidence/structure-save-isolation-cli.png` captured on inherited display and inspected. Browser playtesting does not apply to this noninteractive persistence integration. The isolated prior/candidate probe compiled the parent LocalGame into a separate ignored class path: parent changed published bytes and consumed legacy scratch after A failed; candidate preserved both. The failed writer outcome was verified in both probes. No Main/native rerun this phase; the c4ff1700 native video remains historical for this production save change. No performance or crash-durability claim.

The distinct report `dashboard/evidence/structure-save-isolation.json` records commands, environment, source/class/tool hashes, controlled parent/candidate outcomes, expected/observed results, test-contract correction and image inspection. All 8285 tracked historical evidence files and all 31 prior phase reports remain byte-exact to HEAD. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No path deletions or unmerged index; diff check passed. No agent commit/push/merge/publication.

Request controller feature_validation for source/test/tool/docs/report/image edits. Not readiness or review submission. Unique save attempts prevent shared-inode corruption; complete concurrent saves retain existing last-writer behavior. No generation lock/CAS, city-sidecar atomicity, directory-sync/power-loss guarantee or admission-limit increase was added. Main partial startup, usable meshes/live request wiring, dirty overlays/global order, immutable generations, atomic persistence, server/rendering paging and city/maximum admission remain unfinished. Required city/maximum twenty-run reference, physical-GPU and shaped-network 32-client qualification remain pending. Latest-master integration and independent exact-head review remain required. Supported-limit claims and stable rollout stay gated. No owner answer needed.


### Windows CI cumulative-counter fixture repair — validation in progress

Actual dcfc6bf017be4bda4a7767525b3d8e83488e4056 run42914381831 failed Windows Maven: 1244 tests, one failure, one skip. All three LocalGameSaveIsolationTest cases passed there. The sole failure was SkyVisibilitySchedulerTest.disjointArrivalsDoNotRestartAnInProgressMergeAndNewerTilesStillPublish at old line287: expected lifetime partialPublications1, observed2. Linux and both Mac jobs were cancelled by the failed matrix, not passed; publication skipped.

Sky startup can legitimately publish a partial atlas before this controlled merge. Changed the fixture to capture its current counter and require exactly one new partial publication, not an absolute lifetime total. The controlled merge and complete follow-up now run twice; the second explicitly asserts a nonzero baseline. Cursor visit count, original capture generation, non-null partial result and full expected follow-up geometry assertions remain. The injected empty packingParts fixture also resets packingInts to zero. Production renderer and save code remain unchanged. No OS branch, test skip or timing-budget relaxation.

Focused 19-test SkyVisibilitySchedulerTest execution passed before the final broader run. Broader regressions and actual World/sky-worker CLI/image remain pending. Prior hosted failure remains a failure until exact-source hosted checks pass. Current city/maximum, physical-GPU and shaped-network 32-client qualification gates remain.


### Windows CI cumulative-counter fixture repair — local validation complete

Affected-path Maven exited 0: 256 tests in 47 fresh suites, no failures/errors/skips. All 498 Java source hashes match the validation fence. Includes all 19 sky scheduler cases and prior 237 affected save/replay/physics/model/streaming/offline/multiplayer regressions. Production Java is byte-exact to dcfc6bf017be4bda4a7767525b3d8e83488e4056; no renderer or save behavior changed this phase.

Playtest: `python3 tools/structures/run_sky_merge_counter_check.py` exited 0. Five actual synthetic World/sky-worker fixtures each executed two controlled partial merges and complete follow-up atlas comparisons. The second explicitly requires a nonzero baseline. Exact counter increment, cursor reuse, original capture generation, non-null partial result and full expected world geometry assertions passed. Fresh `dashboard/evidence/structure-sky-merge-counter-cli.png` captured on inherited display and inspected. Browser playtesting does not apply to this noninteractive fixture repair. No Main/native rerun; no frame/GPU/city/network qualification claim.

The distinct report `dashboard/evidence/structure-sky-merge-counter.json` records actual commands, environment, source/class/tool hashes, hosted failure diagnosis, unchanged production source, expected/observed results and image inspection. All 8287 tracked historical evidence files and all 32 prior phase reports remain byte-exact to HEAD. Ten MP3/Hub restorations remain byte-exact to review base 71612f09bfec4549456b3317676e76cdced085a4. No path deletions or unmerged index; diff check passed. The only assertion replacement checks the exact scenario increment rather than lifetime total; the scenario now runs twice with all original generation/cursor/geometry checks retained. No OS branch, test skip or timing-budget relaxation.

Request controller feature_validation for fixture/tool/docs/report/image edits. The previous hosted Windows run remains failed; Linux and both Mac jobs were cancelled, not passed. Fresh exact-source hosted checks on all platforms are required. No agent commit/push/merge/publication. This is not readiness or review submission. Main partial startup, usable meshes/live request wiring, dirty overlays/global order, immutable generations, atomic persistence, server/rendering paging and city/maximum admission remain unfinished. Required city/maximum twenty-run reference, physical-GPU and shaped-network 32-client qualification remain pending. Latest-master integration and independent exact-head review remain required. Supported-limit claims and stable rollout stay gated. No owner answer needed.


### Legacy save row integrity — implementation

Legacy v8/v9 ordinary saves now require the serialized row count to match the declared history count and reject null or invalid edits before publication. Regional v10 already enforces these checks. A malformed history must not replace an existing valid world. Unique temporary-attempt cleanup and publication policy remain unchanged. This is not a source-generation snapshot or CAS; same-count concurrent value changes remain outside this check. Formats, admission limits and city sidecars are unchanged.

New file integration checks cover too few/many rows, null and invalid coordinates in legacy/component formats, exact published-byte preservation, readable prior health, unowned scratch preservation, attempt cleanup and valid retry. Validation is in progress. Main/native and city/maximum qualification are not claimed.

### Legacy save row integrity — local validation complete

Affected Maven command with offline worktree cache and worktree temporary directory passed: LocalGameSaveRowsTest, LocalGameSaveIsolationTest, LocalGameSaveLimitTest, StructureTest and StructureMultiplayerTest; 32 tests in five suites, zero failures/errors/skips. No full-suite pass claimed. Playtest: `python3 tools/structures/run_save_rows_check.py` exited 0, exercising eight malformed-row cases across v8/v9 plus successful retries on actual synthetic files. The fresh CLI image was inspected. Browser playtesting does not apply to noninteractive persistence; Main/native was not rerun.

`dashboard/evidence/structure-save-rows.json` records commands, environment, source/class/tool hashes and actual results. Prior tracked evidence remains unchanged; ten MP3/Hub restorations remain byte-exact to review base. No deleted paths or unmerged index. Delegate calls failed without task IDs; independent local analysis and actual checks were used. No delegated validation claimed.

Controller reports all four parent d17d1fa9 platform builds successful, publication skipped; scoped local observation preceded the final Intel result. New source needs exact-source hosted checks. Request feature_validation, not readiness. Same-count concurrent value changes, immutable source generations/CAS, city sidecars, Main paging/meshes/global order, city/maximum admission and twenty-run reference, physical-GPU and shaped-network 32-client qualification remain pending. Supported-limit claims and stable rollout remain gated. Latest-master integration and independent exact-head review remain required. No owner answer needed.


### Exact-source native workflow after save integrity changes — in progress

Verified run42919675144 for 0e360f04aab84765e3503e773512d1b974b81a31: Linux, Windows, mac-arm and mac-intel builds succeeded; publication skipped. Fresh `python3 deploy/run_structure_playtest.py --display "$DISPLAY" --evidence-prefix structure-save-rows-native` runs production Main with an isolated synthetic LOW profile on the inherited role display. Existing evidence remains unchanged; private runtime logs are not read. Native results/media are not yet passed.

Critical-path audit: Main.awaitOfflineWorld still loads complete resident history, and awaitOfflineReplay prepares complete resident geometry before play. RegionReplayWorker's private read view only supplies bounded data windows; data readiness is not usable chunk/mesh readiness. No live paging switch is authorized by a successful data-window fixture. Next integration must preserve unknown-cell ray/collision rejection, build/publish usable meshes under source and model fences, and keep dirty edits and global replay order through eviction. Persistence then needs an immutable generation plus dirty overlay and atomic world/sidecar publication; ordinary unique-temp saves and row checks do not provide this. Server authority, late joins and synchronization need the same source generation. Only then can city/maximum admission and all required 20-run budgets qualify increased supported limits. Existing caps and rollout gates remain.


### Exact-source native workflow after save integrity changes — local validation complete

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY" --evidence-prefix structure-save-rows-native` exited 0 on production Main/GLFW/X11, inherited role display and a new isolated synthetic LOW profile. Named capture/library/anchor, self-contained export/import including >240-character path, all four rotations, preserved holes, occupied/player rejection without mutation, placement/undo/export/import cancellation, malformed import atomicity, repeated placement, one-operation undo, 32-resolution model restart, inventory and model editor checks passed. Renderer llvmpipe (LLVM 15.0.6, 256 bits); HUD 1 FPS is functional evidence only. No physical-GPU or frame budget pass. Browser playtesting does not apply to this native desktop workflow.

Fresh F10 MP4: 492081 bytes, 42.292 seconds, 25 H264 frames, 960×544. Every frame decoded with bundled JCodec; count matches metadata, first/last frames differ, below 6 MB. Inspected named capture, green bounds/anchor, red occupied conflict, placed component and decoded video frame. Existing-prefix reuse rejected before clearing scratch; synthetic world and all copied artifacts stayed byte-exact.

All 499 Java source and compiled hashes match the prior 32-test save/structure/multiplayer validation; no new Maven run for unchanged source. All 8291 tracked historical evidence files stayed byte-exact; ten restored MP3/Hub files remain byte-exact to review base. No deletions or unmerged index; diff check passed. Private runtime logs remained unread. Delegate failed without task ID; independent source audit and local execution were used, no delegated result claimed.

Seventeen fresh prefixed artifacts plus `dashboard/evidence/structure-save-rows-native-validation.json` are pending controller publication. No production source or harness changed this phase. Request feature_validation for publication of concrete evidence/checkpoint/progress edits, not readiness or review submission. Live paging/usable meshes/dirty overlays/global ordering, immutable generations/CAS and atomic sidecar persistence, city/maximum admission, required 20-run reference and physical-GPU/shaped-network 32-client qualification remain unfinished. Existing limits and stable rollout gates remain. Latest-master integration and independent exact-head review remain required. No owner answer needed.


### Detached complete replay chunk geometry — local validation complete

Verified exact-head 6fbf67bd run42924042102: Linux, Windows, mac-arm and mac-intel succeeded; publication skipped. Added private RegionReplayChunk.load over the existing worker-owned disk replay cache. It assembles all 4096 local cells in key order from one indexed single-cell page at a time, preserving implicit terrain and folded source-order geometry within each cell. It freezes the complete 256-resolution world-chunk root only after success. Model IDs remain references; definitions and source/model/World publication fences remain caller-owned. This is physical world geometry, not a larger voxel-model asset; per-model limits remain.

At most one 4097-source-row page/current cell and one detached root are retained. Non-air cell leaves are summed before install and capped at a caller budget no greater than 131072; this conservative bound does not discount merging between cells. Cache disk budgets remain independent. Cancellation is checked per cell and by underlying row decoding. Terrain even-generation checks precede and follow cell work and complete assembly. Invalid/outside-world regions, invalid budgets, terrain changes and foreign cache ownership reject without a returned partial result. No resource, mesh-readiness, source-generation authority or performance claim beyond these logical bounds.

Affected Maven command passed 51 tests in eight suites, zero failures/errors/skips: RegionReplayChunkTest, RegionReplayCacheTest, RegionReplayPageTest, RegionReplayDemandTest, RegionReplayReadViewTest, LocalGameSaveRowsTest, StructureTest and StructureMultiplayerTest. No full-suite pass claimed. Playtest: `python3 tools/structures/run_replay_chunk_check.py` exited 0 against actual synthetic spool/cache data. All 4096 negative-coordinate cells matched independent WorldVoxels replay, including implicit terrain, fractional glass, LED colour and model references. Result remained frozen after cache/spool cleanup. Geometry/cancellation/terrain/owner rejection and valid retry passed. Fresh CLI image inspected. Browser does not apply to this noninteractive worker geometry path. No Main/native rerun; the prior native video is historical for this new private path.

`dashboard/evidence/structure-replay-chunk.json` records commands, environment, 501 source/class/tool hashes, expected/observed behavior and unchanged gates. Existing tracked evidence and ten restored MP3/Hub paths remain unchanged; no deleted paths or unmerged index; diff check passed. Delegate failed without task ID; independent local analysis and actual execution were used. No delegate validation claimed.

Request controller feature_validation for new source/test/tool/report/image and checkpoint/progress edits. Not readiness or review submission. Next: bounded worker chunk requests with source/model/World fences, usable meshes and live paging; dirty overlays/global replay ordering; immutable generations and atomic world/sidecar persistence. Main partial startup remains disabled. City/maximum admission and required twenty-run reference, physical-GPU and shaped-network 32-client qualification remain unfinished. Structure limits and stable rollout gates remain. Latest-master integration and independent exact-head review remain required. No owner answer needed.


### Asynchronous detached replay chunk requests — local validation complete

Verified exact-head 15bdaab9 run42927235431: Linux, Windows, mac-arm and mac-intel builds succeeded; publication skipped. Chunk requests now share the existing RegionReplayWorker page queue, capacity1–8 plus one active request. Existing terminal-state CAS governs late cancellation and completion. The worker dispatches page or bounded chunk assembly through its owned cache; no second worker or independent queue was added. Existing page extraction remains; separate chunk extraction rejects wrong result type. Chunk extraction requires actual World fencing, and source-bound requests must use their originating Binding, which checks actual transferred metadata/model identity and target revision before and after extraction. Worker source-terrain metadata checks still surround loading.

The result remains frozen detached CPU geometry. Phase.READY means the request completed, not live data/mesh readiness. No World chunk is installed, no read-view coverage is published, and partial LocalGame state cannot save. Tests verify unchanged loaded chunks/edit revision/save bytes and retained per-model definitions. Unbound fenced helper calls retain existing private caller responsibility for source-generation provenance; source-bound calls require the exact source binding. No geometry/model/package limits or save formats changed.

Actual Maven tests:65 tests in nine matched suites passed in the first command. Its RegionReplayWorldFenceTest selector was a typo and matched no suite; the report audit detected this. A second command ran the correct RegionReplayFenceTest:5 tests passed. Total70 tests across ten actual suites, zero failures/errors/skips; no full-suite pass claimed. The actual command strings and correction are in the report.

Playtest: `python3 tools/structures/run_replay_chunk_worker_check.py` exited0 against actual saved-world metadata and asynchronous disk replay. Four workflows passed: complete4096-cell geometry/source binding without promotion; mixed page/chunk queue bounds and cancellation; cancelled late results, geometry rejection and page/chunk retry; foreign bindings, actual model/terrain/World changes and owner/tag/result-type rejection. The fresh capture was inspected. Browser does not apply to noninteractive worker integration. Main/native was not rerun; prior native video is historical for this new private path.

`dashboard/evidence/structure-replay-chunk-worker.json` records environment, commands,502 source hashes plus class/tool hashes and actual results. Historical tracked evidence and ten MP3/Hub restorations remain unchanged; no deleted paths/unmerged index; diff check passed. Delegate failed without task ID; independent local inspection/execution used, no delegated validation claimed.

Request controller feature_validation for worker/test/tool/docs/report/image edits. Not readiness or review submission. Next: bounded chunk demand/pump and live progress, CPU mesh construction, actual World/Main publication under source/model/World fences, dirty overlays/global ordering, immutable generations and atomic world/sidecar persistence. Main partial startup remains disabled. City/maximum admission and required20-run reference, physical-GPU and shaped-network32-client qualification remain pending. Existing limits and stable rollout gates remain. Latest-master integration and independent exact-head review remain required. No owner answer needed.


### Detached replay chunk cell progress — local validation complete

Verified 5e4e76e3 run42930634379: Linux, Windows, mac-arm and mac-intel succeeded; publication skipped. Added worker-local progress callback after each staged cell, including implicit terrain with no edit rows. Existing load signature retains a no-op callback; Request.completedCells exposes a separate atomic 0–4096 diagnostic count. Page requests leave it at zero. Final cancellation and terrain checks remain after the last callback, so 4096 is not READY or a valid source receipt. Progress may advance until the worker observes cancellation; it never grants live data/mesh readiness or save authority. No UI wiring, queue/cache/geometry limits or package formats changed.

Affected Maven command passed 73 tests across eleven suites, zero failures/errors/skips; no full-suite pass claimed. Playtest: `python3 tools/structures/run_replay_chunk_progress_check.py` exited 0 against actual spool/cache and asynchronous worker workflows. Implicit cells advanced from 0 to4096 without rows; final callback terrain mutation rejected after4096. Controlled callback cancellation stopped at128 and retry began0. An actual worker blocked decoding local key128 while the owner observed128 completed cells and LOADING, with no early result. Cancellation won; retry reached4096 and returned frozen geometry while the live World remained unloaded. Fresh CLI capture inspected. Browser does not apply to this noninteractive worker integration; no Main/native rerun.

`dashboard/evidence/structure-replay-chunk-progress.json` records actual command/environment/results and503 source hashes plus class/tool hashes. Historical evidence and ten restored MP3/Hub paths remain unchanged. No deleted paths/unmerged index; diff check passed. Delegate failed without task ID; independent local analysis/execution used. No delegated validation claimed.

Request controller feature_validation for source/test/tool/report/image and checkpoint/progress edits. Not readiness or review submission. Chunk demand/pump/UI integration, CPU meshes and live World/Main paging, dirty overlays/global order, immutable generations/CAS and atomic world/sidecar persistence remain unfinished. City/maximum admission and required20-run reference, physical-GPU and shaped-network32-client qualification remain pending. Existing limits and stable rollout gates remain. Latest-master integration and independent exact-head review remain required. No owner answer needed.


### Bounded detached chunk demand scheduler — local validation complete

Actual final 1f265865 run42933581845: Linux, Windows, mac-arm and mac-intel builds succeeded; publication skipped. Initial scoped observation had Intel Mac still running; final observation after independent implementation confirmed completion. Added a private source-bound chunk pump with capacity 1–2 desired regions. Owner ticks observe each pending request once and never wait or perform file I/O. Demand preflight reads at most two indexed entries before cancelling obsolete work. Duplicate regions submit once. Ready geometry and errors are retained only for current desired regions. Failures require explicit retry; a full shared worker queue reports backpressure. Close cancels owned work and drops retained roots without joining or closing the worker. Worker shutdown also closes its pump.

A binding opens either a chunk pump or its existing read view before requests, not both. Every pump operation checks source/model/World fences. Status aggregates staged-cell diagnostics; ready means detached CPU result only. At most two desired regions are tracked across pending/ready/failure maps. The shared worker may hold an obsolete active candidate until it observes cancellation; independent cache/queue budgets remain. Geometry caps and package/model/world admission limits are unchanged. No measured heap or owner-tick latency claim.

Initial affected Maven run: 79 tests, four failures in new assertions copied from the read-view fixture. A fresh World without a read view reports implicit terrain data-ready even before pump work; that is the existing World.replayDataReady contract. Corrected new assertions preserve this baseline and still require no loaded chunks, unchanged edit revision, unchanged source bytes and partial-save rejection. Production readiness behavior was not changed. Final affected Maven run passed all 79 tests across twelve suites, zero failures/errors/skips; no full-suite pass claimed.

Playtest: `python3 tools/structures/run_replay_chunk_pump_check.py` exited 0. Six actual saved-world/worker workflows passed: bounded dedupe, moving demand and stale-result cancellation, shared-queue backpressure, corrupt-source rejection and explicit retry, input/owner/model fencing, and nonblocking owned-work close. The fresh capture was inspected. Browser does not apply to noninteractive scheduling. Main/native was not rerun; prior native video is historical for this new private path.

`dashboard/evidence/structure-replay-chunk-pump.json` records actual commands/environment/results, the initial failed assertion diagnosis and final pass, plus 504 source hashes and class/tool hashes. Historical evidence and ten MP3/Hub restorations remain unchanged; no deleted paths/unmerged index; diff check passed. Delegate failed without task ID; independent local analysis and execution used. No delegated validation claimed.

Request controller feature_validation for worker/test/tool/report/image and checkpoint/progress edits. Not readiness or review submission. Next: CPU meshes, read-only coverage gate, exact-source geometry/mesh publication and Main demand/movement wiring. Dirty overlays/global order, immutable generations/CAS and atomic world/sidecar persistence remain unfinished. City/maximum admission and required 20-run reference, physical-GPU and shaped-network 32-client qualification remain pending. Existing limits and stable rollout gates remain. Latest-master integration and independent exact-head review remain required. No owner answer needed.


### Detached ordinary CPU mesh preparation — local validation complete

Verified exact-head 75309bc8 run42936738046: Linux, Windows, mac-arm and mac-intel builds succeeded; publication skipped. Added worker-only RegionReplayMesh over a constant-root persistent geometry copy. Its detached Chunk sampler uses the frozen root and checks cancellation while sampling. The unchanged MeshDataGenerator creates ordinary CPU vertices, indices and surface data under existing render-array caps. Worker chunk requests prepare this mesh before READY, then recheck cancellation and source terrain generation. The geometry-only loader/constructor remain available. No GL objects, live chunks or readiness are published.

Unknown neighbours are conservatively air. Live neighbour/source/mesh fencing and publication remain required. Custom model IDs remain in geometry; existing ordinary meshing excludes model surfaces, so model draw/dependency staging and aggregate model surface admission remain separate unfinished work. Geometry is frozen; MeshData retains the existing mutable CPU-array ownership convention. It is not immutable authority. A completed-cell count of 4096 can still be LOADING during mesh work. Existing geometry/cache/queue/package/model limits remain. No measured heap, timing, frame or GPU qualification.

Initial affected Maven run: 89 tests, one failure. The new cancellation test caught CancellationException being wrapped as IOException because it extends IllegalStateException. Added an explicit cancellation catch/rethrow before mesh-error wrapping; budget failures remain IOException. Final affected Maven run passed all 89 tests across fourteen suites, zero failures/errors/skips; no full-suite pass claimed.

Playtest: `python3 tools/structures/run_replay_mesh_check.py` exited 0 with a 256 MiB heap cap. Four actual disk replay/worker workflows passed. CPU vertices, indices and surface arrays exactly matched independently reconstructed chunks with fractional glass, LED colour, water and model IDs. Cancellation and clean retry, existing fragmented mesh rejection, persistent capture without freezing the caller, unchanged saved bytes/partial-save guard and no loaded chunks were verified. Result geometry/mesh survived source-worker cleanup. The fresh capture was inspected. Browser does not apply to noninteractive worker preparation. Main/native was not rerun; prior native video is historical for the new private path.

`dashboard/evidence/structure-replay-mesh.json` records commands, environment, initial failure/fix/final results and 506 source hashes plus class/tool hashes. Historical tracked evidence and ten MP3/Hub restorations remain unchanged; no deleted paths/unmerged index; diff check passed. Delegate failed without task ID; independent local analysis/execution used. No delegated validation claimed.

Request controller feature_validation for source/test/tool/report/image and checkpoint/progress edits. Not readiness or review submission. Next: custom model draw staging, live neighbour/source/mesh fences and read-only coverage gate, actual World/Main demand and rendering integration. Dirty overlays/global order, immutable generations/CAS and atomic world/sidecar persistence remain unfinished. City/maximum admission and required 20-run reference, physical-GPU and shaped-network 32-client qualification remain pending. Existing limits and stable rollout gates remain. Latest-master integration and independent exact-head review remain required. No owner answer needed.


### Detached custom-model CPU draw staging — local validation complete

Verified 90674f0c run42939646629: Linux, Windows, mac-arm and mac-intel builds succeeded; publication skipped. ReadbackMetadata now retains the original parsed model-library reference already used by its row policy. Owner metadata still transfers an independent library copy; clearing transfer fields does not clear worker draw definitions. Added worker model batching over frozen chunk geometry: at most 4096 local cell keys and 512 types, frozen persistent definition copies, count-only admission before model render arrays, then one CPU model geometry per type. Existing per-model resolution and 8192-quad limits remain. Weighted instance quads reject above 262144, matching existing structure chunk admission. These remain provisional staging bounds, not qualified supported-city limits.

Worker chunk requests stage model batches before ordinary CPU mesh preparation and preserve them in the detached result. Batches keep model IDs, definitions and local x/z/y cell keys; voxel geometry, colour and physical cell positions remain. CPU arrays retain existing renderer ownership conventions; only definitions/geometry roots are frozen. Source/model/World extraction fences remain. No GPU resources, owner model updates, live chunk installation or save authority are published.

Affected Maven run passed 106 tests across seventeen suites, zero failures/errors/skips; no full-suite pass claimed. Playtest: `python3 tools/structures/run_replay_model_draws_check.py` exited 0 with a 256 MiB heap cap. Four actual workflows passed: shared model batch/local keys and exact geometry/colour, aggregate surface admission and bounded retry, cancellation/missing dependencies/independent frozen capture, and actual bound-worker model restoration after metadata transfer. The checker fixture has 1536 quads: 170 instances at 261120 weighted quads pass, 171 at 262656 reject under the existing 262144 cap. Tested 8- and 32-resolution definitions. Owner model-map changes invalidate extraction, while saved bytes and partial-save guards remain unchanged. The fresh capture was inspected. Browser does not apply to noninteractive staging. Main/native was not rerun; earlier native video is historical for the new private path.

`dashboard/evidence/structure-replay-model-draws.json` records actual commands/environment/results, ownership and staging bounds, plus 508 source hashes and class/tool hashes. Historical tracked evidence and ten restored MP3/Hub paths remain unchanged; no deleted paths/unmerged index; diff check passed. Delegate failed without task ID; independent local analysis and execution used. No delegated validation claimed.

Request controller feature_validation for source/test/tool/report/image and checkpoint/progress edits. Not readiness or review submission. Next: live neighbour/source/geometry/mesh publication fences, read-only coverage gate, actual World/Main demand/rendering and GPU model-batch upload. Dirty overlays/global order, immutable generations/CAS and atomic world/sidecar persistence remain unfinished. City/maximum admission and required 20-run reference, physical-GPU and shaped-network 32-client qualification remain pending. Existing limits and stable rollout gates remain. Latest-master integration and independent exact-head review remain required. No owner answer needed.

### Detached mesh face neighbours — local validation

Exact parent `e0c8a818bccfe289e42f18a3ad21498e9a8c056b` has four successful platform builds in run `42943373359`; publication was skipped. This validates that parent, not the edits below.

`RegionReplayMesh.prepare` now accepts at most six detached face-neighbour snapshots. It captures persistent frozen roots without freezing caller wrappers. It rejects duplicate, nonadjacent, wrong-resolution and mismatched terrain-revision inputs before emitting a result. Full opaque neighbour cells cull shared faces. Unknown neighbours remain air for conservative exterior meshing; partial geometry, models and the existing water visibility rule retain their prior treatment. The old overload and worker still use unknown neighbours.

Local command: `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" -Dtest=RegionReplayMeshNeighboursTest,RegionReplayMeshTest,RegionReplayModelDrawsTest,RegionReplayChunkWorkerTest,RegionReplayChunkPumpTest,StructureTest,StructureMultiplayerTest test`. Result: 47 tests across seven suites; no failures, errors or skips.

Playtest: `python3 tools/structures/run_replay_neighbours_check.py` exercises the actual CPU mesh builder through three integration fixtures and captures its CLI result on the inherited display. It covers six sequential shared-face removals, negative chunk coordinates, unknown/partial/model/water neighbours, invalid input rejection, cancellation, persistent capture and retry. Browser playtesting does not apply to this private noninteractive API. This is not a Main/GLFW or GPU test.

This phase does not wire neighbours into the asynchronous worker or publish live geometry. Live neighbour/source/geometry/mesh fences, read-only coverage, Main demand and GPU installation remain required. Dirty overlays, global history order, source generations, CAS and atomic world/sidecar persistence remain unfinished. City/maximum admission and all 20-run physical-GPU/32-client/frame/tick/traffic qualification remain pending. Existing admission limits and rollout gates are unchanged. No unrelated deletion is intended.

### Read-only World sampling from staged chunks

Parent `9d21313eaba92d98b4541ef9296d5e215dbb9d0d` has four successful platform builds in run `42945472281`, with publication skipped. The duplicate run still showed Intel Mac running when inspected; it is not used as a completed result.

`Binding.openChunkReadPump` attaches an owner-only `World.ReplayReadView` before any requests. It uses the existing pump's at-most-two ready chunk roots; it adds no geometry cache. Each World data query validates the source/model/World binding. Only complete ready results grant coverage. Loading, failed, cancelled and evicted chunks cannot fall back to implicit terrain. Returned cell wrappers are frozen persistent subregions. Demand changes remove old coverage immediately. Close cancels pending work and drops retained roots without joining the worker. The original detached chunk pump and 64-cell read view remain separate APIs.

Attaching this view changes the World view identity and refreshes the binding baseline. Old World fences cannot authorize publication. Wrong-owner reads reject before clearing owner state; model/source invalidation clears pump results and rejects further reads. World resident mutation, capture/history/streaming, live mesh readiness and LocalGame save authority retain their existing incomplete-replay guards. This does not enable Main partial startup, live GPU installation or interactive movement.

Validation command: `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" -Dtest=RegionReplayChunkReadViewTest,RegionReplayChunkPumpTest,RegionReplayReadViewTest,RegionReplayMeshNeighboursTest,RegionReplayMeshTest,RegionReplayModelDrawsTest,RegionReplayBindingTest,RegionReplayFenceTest,RegionReplayDemandTest,StructureTest,StructureMultiplayerTest test`. The first run found an incorrect new-test expectation comparing raw RGB with encoded ARGB. The fixture's custom model ID is also derived from the independent reference rather than hard-coded. The corrected 71-test run passed. A final failure/retry fixture was then added; the final report records its executed result.

Playtest: `python3 tools/structures/run_replay_chunk_read_view_check.py` exercises actual World queries and the asynchronous worker with isolated synthetic saved worlds. The five fixtures cover all 4,096 source cells, fractional LED data, custom models, implicit terrain, negative coordinates, eviction, loading/cancel/close, decode failure and explicit retry, owner/model/view fences, frozen snapshots and unchanged save bytes. It captures the actual CLI result on the inherited display. Browser playtesting does not apply to this private noninteractive World data API. This is not live rendering or physical qualification.

Next: live neighbour/source/geometry/mesh fences and GPU installation; Main read-only startup/demand; dirty overlays and global history order; immutable source generations, CAS and atomic world/sidecar persistence; city/maximum admission and the required 20-run physical-GPU/shaped-network 32-client qualification. Limits and rollout gates stay unchanged. No unrelated deletion is intended.

Final local result for this phase: 72 tests across 11 suites passed with zero failures, errors or skips. The CLI's five fixtures also passed under `-Xmx256m`; its capture was inspected. Source/class/tool/driver/image hashes match the receipt. `dashboard/evidence/structure-replay-chunk-read-view.json` records the commands, results, environment and remaining gates; the companion PNG is pending controller publication. `git diff --check` passed, ten restored paths remain byte-exact to the review base, no tracked deletion or unmerged entry exists, and all 8,323 prior tracked evidence files remain unchanged.

### Streamed unchanged-source v10 save staging — local validation complete

Exact parent `064bffbf764dff8528d40ec3b7285f506934ed64` has four successful platform builds in run `42947992277`; publication was skipped. The other duplicate run still showed Intel Mac running when inspected and is not counted as complete.

`StagedOfflineSave.stageRegions` now has a worker-owned replay-spool overload. It writes the existing v10 prefix directly from fully revalidated spool sections in global file/row order. Regions may recur, overwritten records remain ordered, and explicit air remains present. It does not build a resident `History`. It buffers one serialized section of at most 1,024 records / 24,576 bytes plus the existing bounded decoder payload/section and output buffer. This is a logical bound, not a measured memory or performance result. The 2,000,000-row persistence limit is unchanged.

The existing header/model/survival-prefix writer is shared with the resident path. Publication still patches survival, appends drops, forces the private file and replaces the target through the existing publication helper. Late CRC failure, policy rejection, cancellation, progress failure and abandoned stages clean only their own temporary prefix and preserve the old destination. Source spool ownership, section CRCs, row counts and cancellation checks remain enforced. This is private unchanged-source staging, not permission for a partial LocalGame to save. No source-generation/CAS, dirty-overlay merge or world/city-sidecar atomicity is claimed.

Executed checks:
- `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" -Dtest=StagedReplaySourceSaveTest,RegionReplayTest,RegionReplaySpoolTest,LocalGameSaveRowsTest,LocalGameSaveIsolationTest,StructureTest,StructureMultiplayerTest test`: 46 passed.
- `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" -Dtest=StructureOfflineSaveTest test`: six passed, retaining v8/v9 serialization, model-write cancellation and publication failure coverage.
- Playtest: `python3 tools/structures/run_replay_source_save_check.py`: four actual streamed-writer/normal-LocalGame-opener fixtures passed under `-Xmx256m`, on isolated synthetic files. Byte equivalence with the resident writer includes a custom 32-resolution model, fractional LED data, inventory, health and drops. Recurring-region overwrite/air order, rejection, cleanup, retry and empty-source load passed. The CLI image was inspected on the inherited display. Browser playtesting does not apply to this noninteractive persistence integration; no live rendering or physical qualification was run.

Source/class/tool/driver/image hashes match the CLI receipt. The report `dashboard/evidence/structure-replay-source-save.json` and companion PNG await controller publication. `git diff --check` passed. Ten restored paths remain byte-exact to the review base. All 8,325 historical tracked evidence files remain unchanged. No deletion or unmerged entry exists; no unrelated deletion is intended.

Still required: live neighbour/source/geometry/mesh fences and Main/GPU integration; dirty overlays, global history, immutable generations, CAS and atomic world/sidecar persistence; city/maximum admission and all required 20-run physical-GPU/shaped-network 32-client/frame/tick/traffic qualification. Limits and rollout gates remain unchanged. Latest-master integration and independent review remain controller/reviewer work.

### Ordered replay overlays in streamed v10 staging

Parent `24262efa8f74d2e5337057d8b42a1a6b4396b15c` has four successful platform builds in run `42950210864`; publication was skipped.

`RegionReplayOverlay` captures an immutable ordered list of valid `Protocol.Edit` values, with per-row cancellation and input-size checks. Its private provisional cap is 65,536 rows, chosen to bound one snapshot while the live dirty-state integration remains unfinished. This is not a benchmark-established supported structure or city limit. Capture does not freeze or retain the caller's list. Edit records are immutable. The caller still needs real owner/source-generation/commit fences; a same-size concurrent input change is not detected or claimed safe.

The new streamed-save overload writes the complete validated base source first, then overlay rows in their captured order, including duplicate keys and explicit air. Appended sections use the existing per-section 1,024-row / 24,576-byte limits and CRCs. It does not sort, compact or canonicalize the global replay. Before creating a destination prefix, it checks base plus overlay against the unchanged 2,000,000-row persistence limit. A canonical result that would fit after compaction can still be rejected when the raw source plus overlay exceeds that limit; compaction remains future work. The no-overlay overload retains its byte-equivalent path.

Cancellation, policy rejection and progress failures remain private staged-prefix failures. They cannot publish a partial overlay or alter the old destination. This adds no World mutation, owner dirty-state capture, partial-world save authority, source generation/CAS or world/sidecar transaction. Those gates remain required.

Validation commands and final results are recorded in the phase report. Playtest uses `python3 tools/structures/run_replay_overlay_save_check.py` with isolated synthetic saved worlds and a generated streaming 2,000,000-row spool. It exercises actual normal LocalGame loads, raw source/overlay order, fractional LED/air/model geometry, immutable capture, malformed/cancelled input, overlay cancellation/policy rejection, cleanup/retry and combined-limit admission. Browser playtesting does not apply to this private noninteractive persistence API. No city, frame, physical-GPU or shaped-network/32-client performance result is claimed.

Executed command: `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" -Dtest=StagedReplayOverlaySaveTest,StagedReplaySourceSaveTest,RegionReplayTest,RegionReplaySpoolTest,StructureOfflineSaveTest,LocalGameSaveIsolationTest,LocalGameSaveRowsTest,StructureTest,StructureMultiplayerTest test`. Result: 56 tests across nine suites passed; no failures, errors or skips. The CLI's four fixtures also passed under `-Xmx256m`, and the captured image was inspected. Source/class/tool/driver/image hashes match the receipt. The report `dashboard/evidence/structure-replay-overlay-save.json` and companion PNG await controller publication.

`git diff --check` passed. Ten restored paths remain byte-exact to the review base. All 8,327 historical tracked evidence files remain unchanged. No deletion or unmerged entry exists; no unrelated deletion is intended. Live World/Main/GPU integration, source generations/CAS, atomic sidecars, city/maximum admission, all required 20-run physical-GPU/32-client/frame/tick/traffic qualification, latest-master integration and independent review remain pending. Limits and rollout gates are unchanged.

### Cooperating-JVM save publication generation guards — local validation complete

Exact parent `5329629ea7507e01c82d266af80545ab5f492b8e` has four successful platform builds in run `42952543594`; publication was skipped.

`WorldSavePublication` now serializes its publications through 64 fixed lock stripes keyed by normalized absolute destination path. Each successful publication advances that stripe's revision. A generation capture records the revision, regular-file attributes and SHA-256 bytes using a 64 KiB buffer, with cancellation and a caller-selected byte cap no greater than 512 MiB. Attributes are checked before and after hashing. Missing destinations have an explicit absent state. A stripe collision may conservatively invalidate a token for another path; the table does not grow with path count.

`replaceIfUnchanged` checks the target, revision and freshly observed destination under the same stripe, then uses strict atomic replacement. The optional `StagedOfflineSave.publish` generation overload uses that guard and cancels/cleans its own private prefix on failure. Old publication overloads retain their last-writer and atomic-fallback policies; they participate in locking and token invalidation but do not acquire an optimistic expected generation themselves. An old unguarded pending writer can still publish later. Main has not been converted to guarded source/owner transactions.

This is a cooperating-JVM destination guard, not cross-process or alias-proof CAS. Foreign writers can race after validation. Normalized absolute paths do not resolve all aliases. The destination token does not prove that a spool/overlay matches the source or current owner state. Hashing and lock acquisition may block; the guarded API belongs on a worker. No latency budget is claimed. Immutable source generations, inter-process fencing, directory durability and atomic world/city-sidecar publication remain required.

Executed checks:
- `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" -Dtest=WorldSaveGenerationTest,WorldSavePublicationTest,StagedReplayOverlaySaveTest,StagedReplaySourceSaveTest,StructureOfflineSaveTest,LocalGameSaveIsolationTest,LocalGameSaveRowsTest,StructureTest,StructureMultiplayerTest test`: 52 passed.
- `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" -Dtest=StagedWorldSaveTest,ServerRegionSaveTest,LocalGameSaveLimitTest test`: eight passed. Total: 60 tests across twelve suites, with no failures, errors or skips.
- Playtest: `python3 tools/structures/run_save_generation_check.py`: four actual publication/staged-save/normal-LocalGame-load fixtures passed under `-Xmx256m`, using isolated synthetic files. Two concurrent same-token writers produced exactly one complete winner. Identical-byte ordinary publication invalidated the old token. Wrong-target and stale staged commits rejected; the newer save stayed intact and private prefixes were removed. Fresh-token retry, absent destination, bounded/cancelled hashing and completed foreign byte changes with restored mtime passed. The CLI image was inspected on the inherited display. Browser playtesting does not apply to this noninteractive persistence API.

Source/class/tool/driver/image hashes match the receipt. `dashboard/evidence/structure-save-generation.json` and its companion PNG await controller publication. `git diff --check` passed. Ten restored paths remain byte-exact to the review base. All 8,329 historical evidence files remain unchanged. No deletion or unmerged entry exists; no unrelated deletion is intended.

Source/owner generation integration, cross-process fencing, immutable world/sidecar commits, Main/GPU integration and live mesh fences, city/maximum admission and all required 20-run physical-GPU/shaped-network 32-client/frame/tick/traffic qualification remain pending. Limits and rollout gates stay unchanged. Latest-master integration and independent review remain required.

### Readback digest bound to destination generation

Parent `b791c054585a5e7dd144d121c98a4b01ff1068a8` has four successful platform builds in run `42955484566`; publication was skipped.

After the complete v10 source, model references, tail and EOF validate, `LocalGame.openRegionalReadback` now calls a bounded `WorldSavePublication.captureMatching` using the SHA-256 of decoded bytes. It requires the current destination to exist and match that digest. The immutable destination generation is retained in worker-prepared metadata and transferred to the partial LocalGame through a package-private getter. Resident LocalGame loads carry no readback token. A streamed staged save can use the source's token rather than capturing an unrelated destination generation later.

This adds a second full source-file hash read, capped at 512 MiB with a 64 KiB buffer. It runs in the existing worker opener. No startup, I/O, memory or latency budget is claimed. A same-size in-place rewrite with restored timestamps that escaped the original attribute checks now rejects when current bytes differ from decoded bytes. The returned spool is still private and immutable only by protocol; foreign writers and aliases are not excluded by this JVM guard. A future owner transaction must validate its World/source binding and dirty overlay at commit. Cross-process fencing, immutable source manifests and atomic world/city-sidecar commits remain required.

Partial LocalGame save/mutation guards remain unchanged. This does not enable Main partial startup or live GPU installation. The destination token does not promote incomplete replay or grant authority to save arbitrary owner changes.

The initial Maven attempt caught a new-test resource error: `RegionReplayWorker.Source` is not AutoCloseable. The fixtures were corrected to close their owned spool. No failed compile was counted as test execution. Final executed checks and environment are recorded in the phase report.

Playtest: `python3 tools/structures/run_readback_save_generation_check.py` uses isolated synthetic v10 worlds, actual readback, guarded streamed-overlay publication and normal LocalGame reload. It checks token transfer, continued partial-save rejection, stale rejection after ordinary save, wrong/missing/malformed digest rejection, buffered same-size rewrite with restored mtime, cleanup and retry. Browser playtesting does not apply to this private noninteractive persistence integration. No city, physical-GPU or shaped-network/32-client qualification is claimed.

Executed command: `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" -Dtest=ReadbackSaveGenerationTest,ReadbackMetadataTest,RegionalReadbackSourceTest,ReadbackFileChangeTest,WorldSaveGenerationTest,WorldSavePublicationTest,StagedReplaySourceSaveTest,StagedReplayOverlaySaveTest,RegionReplayBindingTest,RegionReplayChunkReadViewTest,StructureTest,StructureMultiplayerTest test`. Corrected result: 78 tests across twelve suites passed, with no failures, errors or skips. The CLI's four fixtures passed under `-Xmx256m`; the image was inspected. Source/class/tool/driver/image hashes match the receipt. `dashboard/evidence/structure-readback-save-generation.json` and its companion PNG await controller publication.

`git diff --check` passed. Ten restored paths remain byte-exact to the review base. All 8,331 historical tracked evidence files remain unchanged. No deletion or unmerged entry exists; no unrelated deletion is intended. Owner/World commit integration, inter-process fencing, immutable world/sidecar transactions, Main/GPU integration and live mesh fences, city/maximum admission and all required 20-run physical-GPU/shaped-network 32-client/frame/tick/traffic qualification remain pending. Limits and rollout gates stay unchanged. Latest-master integration and independent review remain required.

### Fresh native workflow on readback-generation source — passed, qualification pending

Tested source: `fc5e88be3d51e51e057cd3ad8163ff58f0d2cf05`, review base `71612f09bfec4549456b3317676e76cdced085a4`. Hosted run `42958493764` has four successful platform builds and skipped publication. The duplicate run still showed Intel Mac running when inspected and is not counted as complete.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY" --evidence-prefix structure-readback-generation-native` exited 0. It ran production Main with real GLFW/X11 input, the inherited assigned role display, an isolated synthetic profile, LOW/detail32/horizon512/scale0.5 and shadows/AA/dynamic resolution off. Renderer: `llvmpipe (LLVM 15.0.6, 256 bits)`. It captured a named house and anchor, browsed the library, exported/imported self-contained dependencies through a path longer than 240 characters, previewed all four rotations, preserved terrain under empty source cells and rejected occupied/player overlap without mutation. Placement, undo, export and import cancellation, malformed import atomicity, repeated placement, 32-resolution model/restart recovery, inventory and model-editor regressions passed. The tested ordinary save/load workflow includes the current publication helper. This does not run partial paging/Main/GPU integration or the private guarded-save path as a gameplay feature; separate CLI evidence covers those private APIs.

Expected: every workflow assertion succeeds; failed/cancelled operations preserve geometry/history/save bytes; one-operation undo and detailed dependencies survive the normal save/restart path. Observed: the harness reported passed, and the new clear/blocked previews, anchor, placed copies, close-up and decoded middle video frame were inspected. The software-rendered HUD shows 0–1 FPS. This is functional regression evidence only and does not satisfy the required ≥30 FPS, physical-GPU, city/max, server-tick, memory or shaped-network/32-client budgets. Those gates remain pending.

The production F10 clip is 433,320 bytes, H.264, 960×544 and 45.812 seconds. `StructureCurrentMediaCheck` was compiled from `target/structure-native-regressions/StructureCurrentMediaCheck.java` into ignored scratch and run against the new clip. Every one of its 25 frames decoded; frame count matches MP4 metadata; first and last frames differ; the clip is below 6 MB. The exact compile/decode commands are in `dashboard/evidence/structure-readback-generation-native-validation.json`. All 17 harness artifacts and the decoded frame are fresh, sanitized and under the artifact cap. They await controller publication under the `structure-readback-generation-native-` prefix.

The runtime manifest's 249 production/harness source hashes match current files. All 515 source and 1,026 class hashes from the prior 78-test validation remain unchanged; those tests were not rerun for this media-only phase. The full diff inventory contains 460 non-evidence paths. The audit rechecked all deleted-path/unmerged entries and inspected existing deployment, compatibility, Main and Protocol changes. No files were deleted. Replaced lines implement structure/replay/history/mesh paths and the protocol 30→31 bump; city command/frame IDs and old save fixtures remain. Ten MP3/Hub restoration paths remain byte-exact to the review base. All 8,333 prior tracked evidence artifacts remain unchanged. No unrelated deletion is intended; `git diff --check` passed.

Implementation is still incomplete: owner/World commit integration, inter-process fencing and immutable world/sidecar transactions, partial Main paging/GPU integration and live mesh fences, city/max admission and all required 20-run reference qualification remain pending. Limits and rollout gates are unchanged. Latest-master integration and independent review remain required. These new local artifacts need controller publication; no review or readiness is claimed.

### Private readback owner save snapshots — local validation complete

Exact parent `749d9bf3b2b6a994e6661c5ef3bcdd186cccb165` has four successful platform builds in run `42961828044`; publication was skipped.

`ReadbackSaveSnapshot` captures on the caller's contractual owner thread: the partial LocalGame's readback generation/fingerprint, a World/model fence, an immutable ordered overlay, an independent ModelLibrary map snapshot sharing frozen definitions, inventory, health and a bounded map of immutable drop records. Overlay dependencies and drop identities/values are checked. Limits stay at 65,536 overlay rows and the existing 100,000-drop cap; model and combined replay limits are unchanged. Inventory/health/drops are preserved world-save state, not added structure-package contents.

Staging runs on the source spool's owning worker thread and reads only captured values. It requires the original metadata generation object, not only an equal fingerprint tag, and validates the worker terrain before and after staging. A different source with identical bytes cannot supply this snapshot's prefix. The private Prepared wrapper ties the prefix to its capture. Commit runs on the capture thread, rechecks the World/model fence and exact survival values, then uses the captured destination generation guard. Rejection or cancellation closes only that prefix. Wrong-thread commit rejects before closing it, allowing the real capture thread to proceed. Successful helper publication does not mutate the private World or promote partial LocalGame save authority.

This is a private blocking reference integration. The World does not itself enforce a creator thread; capture assumes the existing owner-thread contract. Foreign mutation of live state is not excluded. Commit hashes/forces/moves on its caller and is not connected to Main; no acknowledgement, stall or frame-time budget is claimed. A nonblocking owner lease/worker commit protocol is still required. Inter-process/alias fencing, immutable source manifests, atomic World geometry/save and city-sidecar commits remain unfinished. No required qualification gate is relaxed.

Executed command: `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" -Dtest=ReadbackSaveSnapshotTest,ReadbackSaveGenerationTest,ReadbackMetadataTest,WorldSaveGenerationTest,WorldSavePublicationTest,StagedReplaySourceSaveTest,StagedReplayOverlaySaveTest,RegionReplayBindingTest,RegionReplayChunkReadViewTest,StructureTest,StructureMultiplayerTest test`. Result: 70 tests across eleven suites passed; no failures, errors or skips.

Playtest: `python3 tools/structures/run_readback_owner_save_check.py` passed four fixtures under `-Xmx256m`, with actual executor-owned source spools and isolated synthetic worlds. Capture→worker staging→owner commit preserved saved model definitions, inventory, health and drops; normal LocalGame reload matched the overlay. Changed health, inventory, drops, model identity and World edits each rejected publication and retained old bytes. Identical-digest foreign metadata, changed source terrain, wrong owner, cancellation, cleanup and retry passed. The captured CLI image was inspected on the inherited display. Browser playtesting does not apply to this private noninteractive persistence helper. Main has not been wired to it, so this is not live graphical/GPU qualification.

Source/class/tool/driver/image hashes match the receipt. `dashboard/evidence/structure-readback-owner-save.json` and the companion PNG await controller publication. `git diff --check` passed. Ten MP3/Hub restoration paths remain byte-exact to the review base. All 8,352 historical tracked evidence files remain unchanged. No deletion or unmerged entry exists; no unrelated deletion is intended.

Still required: nonblocking owner/worker commit integration, cross-process fencing and immutable World/sidecar transactions; Main partial paging/GPU installation and live mesh fences; city/maximum admission and all required 20-run physical-GPU/shaped-network 32-client/memory/frame/tick/traffic qualification; latest-master integration and independent review. Limits and rollout gates stay unchanged.

### Staged-save dropped-item count validation — local checks passed

Parent `f3df254a0a5e3277430b128437d71bc424138da5` has four successful platform builds in run `42964681088`; publication was skipped. `StagedOfflineSave` now bounds the declared dropped-item count by the existing 100,000 cap and verifies every appended row against that count. Negative/over-limit counts, extra/missing rows and null rows reject publication and close only the owned prefix. No format, limit or authority change is intended.

The scoped Maven command and source/class hashes are recorded in `dashboard/evidence/structure-save-drop-count.json`. Result: 43 tests across seven suites passed, with no failures, errors or skips. The initial test compile failed because the fixture passed a Map where History was required; the corrected fixture passed on retry.

Playtest: `python3 tools/structures/run_save_drop_count_check.py` exercised actual staged v10 publication and ordinary LocalGame reload under `-Xmx256m`, using an isolated synthetic save. Expected and observed: six invalid cases preserve original save bytes and remove private staged prefixes; a valid retry restores dropped items, seed and health. The fresh CLI capture was inspected on the inherited display. Browser playtesting does not apply to this noninteractive persistence helper. No Main, GPU, city or network qualification is claimed. Image and sanitized report await controller publication.

No deleted or unmerged paths exist. Ten MP3/Hub restoration paths remain byte-exact to the review base; `git diff --check` passed. No unrelated deletion is intended. Nonblocking owner/worker commits, immutable World/sidecar transactions, Main paging/GPU/live mesh fences, city/max admission and all required 20-run reference qualification remain unfinished. Limits and rollout gates remain unchanged. Latest-master integration and independent review remain required.

### Same-source worker neighbor meshing — local validation complete

Parent `dac7c0b2327d37582d3e2069d90f4cbf08a6bfd5` has four successful platform builds in run `42967098250`; publication was skipped.

The private replay worker now accepts an opt-in six-bit face mask through `submitChunkWithNeighboursFenced` and the source Binding equivalent. Bits select -X,+X,-Y,+Y,-Z,+Z. Nonzero masks validate the center and selected neighbor coordinates before queueing. Selected face chunks load sequentially from the same worker-owned source/cache and policy. CPU meshing consumes those frozen roots; no caller-supplied geometry, foreign source, live World queries or neighbor render arrays enter the worker. Existing page requests, chunk requests and ChunkPump keep their zero-mask behavior. Unknown mesh faces remain exposed; collision/data coverage does not change.

The request retains at most six extra frozen roots, each under the existing 131,072 staged-cell-leaf cap, for at most 917,504 staged cell leaves including the center. These are logical admission bounds, not measured heap/time support. Disk-cache, queued/active request and center model/mesh limits remain unchanged. `completedCells` still counts only the center (0..4096); it is not completion or readiness. The phase stays LOADING through neighbor assembly and final source/terrain checks. Cancellation and failure cannot expose the center as a completed result. Bound extraction still validates source identity and actual owner World/model/terrain fences. No live mesh/GPU installation or World/save authority is granted.

Executed the scoped Maven command recorded in `dashboard/evidence/structure-worker-neighbours.json`: 67 tests across eleven suites passed, with no failures, errors or skips. This includes existing mesh, model batching, queue, chunk/pump/read-view and offline/multiplayer structure regressions.

Playtest: `python3 tools/structures/run_worker_neighbours_check.py` passed five integration fixtures under `-Xmx256m` using actual v10 readback spools, worker threads and CPU meshes. Expected and observed: default unknown faces yield 216 center indices; each selected face yields 210; all six yield 180, with unchanged center geometry at negative chunk coordinates. Invalid masks/world edges reject before queueing; shared queue pressure and queued cancellation work. Cancellation during neighbor read, policy failure and unchanged retry pass. A changed owner World rejects extraction. A terrain change during neighbor loading rejects the fully staged center and subsequent requests against its stale source metadata. Save bytes and live World geometry remain unchanged. The fresh CLI capture was inspected on the inherited display. Browser playtesting does not apply to this private noninteractive CPU worker. No Main, GL/GPU, city or network qualification is claimed.

Source/class/tool/driver/image hashes are pinned in the sanitized report. Fresh PNG/report await controller publication. Ten MP3/Hub restoration paths remain byte-exact to the review base. No deleted/unmerged paths exist; previous tracked evidence is unchanged; `git diff --check` passed. No unrelated deletion is intended.

Still required: actual Main paging and GPU installation with live neighbor/source/geometry/mesh freshness and coverage fences; nonblocking owner/worker commit and immutable World/sidecar transactions; city/max admission and all required 20-run physical-GPU/shaped-network 32-client memory/frame/tick/traffic qualification; latest-master integration and independent review. Limits and rollout gates remain unchanged.

### Resident mesh dependency fences — local validation complete

Parent `4770c9d422abb97d62966b9f86c889964565163b` has four successful platform builds in run `42969584369`; publication was skipped. The other run still showed Intel running when inspected and is not counted as complete.

`World.ReplayFence.captureMeshDependencies` captures seven fixed dependency positions: center plus six face neighbors. It records absent entries or exact resident Chunk references with geometry, mesh-invalidation and attachment revisions. Validation checks the baseline World fence before and after these bounded dependency checks. Chunk attachment now increments a constant-size identity epoch, so unloading and reattaching the same wrapper cannot revive a stale receipt. No geometry is copied. This uses the existing capture-thread contract; it does not exclude illegal foreign mutation of owner-owned World state.

Every fenced replay chunk request captures this receipt before queueing and validates it before/after owner-side result extraction. Cell-page requests retain their existing checks. Adjacent natural residency or mesh-only invalidation can now reject a stale chunk result even when the global replay fence is unchanged. Distant natural residency remains valid when baseline geometry/model/terrain state does not change. Worker code does not read the World. This is extraction validation, not source identity, live installation, coverage, collision or GPU readiness. Publication-time checks and actual Main paging remain required.

Final executed Maven command and hashes are recorded in `dashboard/evidence/structure-replay-mesh-fence.json`. Result: 84 tests across fourteen suites passed, with no failures, errors or skips. The run includes existing World streaming/startup history, replay queue/binding/pump/read-view, neighbor mesh and offline/multiplayer structure regressions. Earlier scoped runs passed; inspection then found the same-wrapper reattachment case, so the final run includes its fix and regression assertion.

Playtest: `python3 tools/structures/run_replay_mesh_fence_check.py` passed six fixtures under `-Xmx256m` with isolated synthetic Worlds and real v10 readback workers. Expected and observed: natural loads at each of seven dependency positions invalidate old mesh receipts; mesh-only dirtying, unload/replacement and same-wrapper reattachment reject stale receipts; distant natural changes remain valid. Wrong thread/World, edits, terrain/model changes and close retain baseline rejection. Ready and queued chunk results reject adjacent changes; pages remain valid for residency-only changes; a fresh unchanged chunk retry succeeds. No worker result changes live membership/history. The fresh CLI image was inspected on the inherited display. Browser playtesting does not apply to this noninteractive receipt/extraction helper. No Main, GL/GPU, city or network qualification is claimed.

Sanitized PNG/report await controller publication. Source/class/tool/driver/image hashes match the receipt. Ten MP3/Hub restoration paths remain byte-exact to the review base; no deleted/unmerged paths exist; previous tracked evidence remains unchanged; `git diff --check` passed. No unrelated deletion is intended.

Still required: Main paging/GPU installation and publication-time source/mesh/coverage checks; nonblocking owner/worker commits and immutable World/sidecar transactions; city/max admission and all required 20-run physical-GPU/shaped-network 32-client memory/frame/tick/traffic qualification; latest-master integration and independent review. Limits and rollout gates remain unchanged.

### Worker-prepared detached Chunk wrappers — local validation complete

Parent `30c7df167936f662aa56576cbaa3da81ea59f22a` has four successful platform builds in run `42972487366`; publication was skipped.

`Chunk.prepareReplayGeometry` captures a persistent root copy, checks 256-resolution world geometry and the existing 131,072-leaf cap with cancellation during leaf admission, then scans 4,096 cells to build its model-cell map and fractional-cell count. Invalid material IDs reject preparation. It does not attach a World, allocate GL resources, replay individual edits or dirty owner geometry. The root copy remains independently mutable under the existing copy-on-write rules. The per-chunk cap now has one constant in Chunk, with the existing replay constant aliasing it; its value did not increase.

After CPU mesh/model batches and selected same-source neighbor preparation, the replay worker creates this detached Chunk before final terrain/source checks and READY publication. Existing five/six/seven-field Snapshot constructors remain compatible. A detached receipt records geometry, mesh and attachment revisions. Owner extraction rejects reuse after geometry mutation, attachment or premature mesh installation/cleaning. Chunk position and GL mesh remain null at transfer. The receipt does not hash caller-owned CPU mesh/model arrays, which retain their existing mutable ownership semantics. This is not live World installation, source authority, GPU readiness, cell coverage or partial-save promotion.

Executed the scoped Maven command recorded in `dashboard/evidence/structure-detached-chunk.json`: 72 tests across thirteen suites passed, with no failures, errors or skips. The initial test compile used nonexistent `Blocks.fractional`; correcting it to `Blocks.piece` produced the passing retry. Existing World startup/streaming, source/binding/chunk/neighbor/pump/read-view/model and offline/multiplayer structure regressions passed.

Playtest: `python3 tools/structures/run_detached_chunk_check.py` passed five fixtures under `-Xmx256m` with isolated synthetic Worlds and real v10 worker/spool reads. Expected and observed: all 4,096 cells, fractional geometry, colored LED values and model-cell maps match ordinary replay; input and prepared mutations remain independent. Cancellation, invalid resolution/material and leaf-budget rejection preserve the input; empty zero-budget preparation and unchanged retries pass. Bound worker results carry matching detached geometry; owner mutation, attachment and premature mesh installation invalidate reuse. Live World membership/history, source save bytes and partial LocalGame authority remain unchanged. The fresh CLI capture was inspected on the inherited display. Browser playtesting does not apply to this noninteractive CPU preparation helper. No Main, GL/GPU, city or network qualification is claimed.

Sanitized PNG/report await controller publication. Source/class/tool/driver/image hashes match the receipt. Ten MP3/Hub restoration paths remain byte-exact to the review base; no deleted/unmerged paths exist; prior tracked evidence remains unchanged; `git diff --check` passed. No unrelated deletion is intended.

Still required: actual Main paging/GPU upload and live publication/coverage checks; nonblocking owner/worker commits and immutable World/sidecar transactions; city/max admission and all required 20-run physical-GPU/shaped-network 32-client memory/frame/tick/traffic qualification; latest-master integration and independent review. Limits and rollout gates remain unchanged.

### Private ordinary replay mesh GL upload — local native validation complete

Parent `89ce75a15f864a672055624a3c8b673786046dbe` has four successful platform builds in run `42975612792`; publication was skipped.

`RegionReplayMeshUpload` validates a bound worker result and detached Chunk without allocating GL resources in its constructor. Owner-side `pump` lazily allocates the existing Mesh.Upload, accepts at most 256 KiB per call and checks deadline/cancellation plus source/World/resident-neighbor/detached receipts around work. Zero-byte and expired-deadline pumps allocate nothing. Empty ordinary meshes need no GL resource. Failed or cancelled owner operations close owned upload resources. Wrong-thread calls reject before consuming them. Successful `take` transfers one ordinary Mesh handle and frees upload scratch; caller owns the handle afterward. This stage never calls Chunk.installMesh or changes World membership/readiness/save authority. Model meshes/instances remain separate unfinished work.

The byte cap is an engineering staging bound, not a measured frame-time claim. GL allocation and driver calls may still block. Caller-owned CPU arrays must remain unchanged during staging; this helper does not copy or hash them. Actual publication-time integration and coverage checks remain required.

Regression command is recorded in `dashboard/evidence/structure-replay-gpu-upload.json`: 72 tests across thirteen suites passed, with no failures, errors or skips. The native helper is exercised by the scoped GL harness rather than those headless regressions.

Playtest: `python3 tools/structures/run_replay_gpu_upload_check.py --display "$DISPLAY"` passed against a real GLFW/OpenGL context on the inherited assigned display, isolated synthetic v10 save, source worker and private World. Vendor `Mesa/X.org`; renderer `llvmpipe (LLVM 15.0.6, 256 bits)`; GL `4.5 (Core Profile) Mesa 22.3.6`. Expected and observed: four-byte partial uploads and 64-byte slices respect byte caps; zero/expired budgets allocate nothing; cancellation, stale adjacent residency and detached mutation reject and restore Mesh byte accounting. Wrong-owner pump/close leave resources for the owner. Successful ownership transfer leaves a live buffer until caller close. Vertex, index and surface GPU readback exactly matches the CPU arrays. Production Mesh.render draws the uploaded ordinary cube into the captured framebuffer; no GL errors remain. Empty mesh transfer returns null without allocation. World publication/history, source bytes and partial save authority remain unchanged. This is a focused native GL integration check, so browser playtesting does not apply. It is not Main gameplay, model rendering, physical-GPU, city or network qualification.

The final checked framebuffer was inspected. The first pilot framebuffer is preserved separately and labelled in the report; the final run adds the empty-mesh check and pins the final tool. Fresh sanitized captures/report await controller publication. Source/class/tool/driver/image hashes match the final receipt. Ten MP3/Hub restoration paths remain byte-exact to the review base; no deleted/unmerged paths exist; prior tracked evidence remains unchanged; `git diff --check` passed. No unrelated deletion is intended.

Still required: model GPU/instance staging and actual Main/World paging, publication and coverage integration; nonblocking owner/worker commits and immutable World/sidecar transactions; city/max admission and all required 20-run physical-GPU/shaped-network 32-client memory/frame/tick/traffic qualification; latest-master integration and independent review. Limits and rollout gates remain unchanged.

### Private model and instance GPU staging — local native validation complete

Parent `5c2af0138bc946c18f9d803bdeaceab557f9c6ce` has four successful platform builds in run `42979142140`; publication was skipped.

`RegionReplayModelUpload` serializes source-validated model batches through one Mesh.Upload and then one Mesh.InstanceUpload at a time. Constructor validation copies at most 4,096 unique local cell positions into at most 48 KiB of xyz data and checks the existing 512-type limit. Existing CPU geometry/weighted surface admission remains unchanged. Owner pumping accepts at most 256 KiB per call and validates source/World/resident-mesh/detached receipts around work. Completed model handles remain private alongside partial work until one Resources transfer; cancellation/staleness closes both partial and completed handles. Wrong-owner calls do not consume owner resources. No World/Chunk installation, coverage or partial-save authority changes.

Mesh.InstanceUpload allocates bounded instance storage, copies positions in slices and marks prepared instances drawable only after finish. Mesh.renderPreparedInstances draws that buffer without another stream upload. Existing dynamic instance/colored-box methods invalidate the prepared count. A Mesh owns the GL buffer; upload close frees scratch. Transferred Resources own their meshes and enforce owner-thread access/close. CPU geometry arrays retain the existing trusted owner-only lifetime and must not change during staging. GL allocations may block; no stall/frame-time qualification is claimed.

The scoped Maven command recorded in `dashboard/evidence/structure-model-gpu-upload.json` passed 72 tests across thirteen suites, with no failures, errors or skips. Native GL behavior is exercised by the dedicated harness, not those headless tests.

Playtest: `python3 tools/structures/run_model_gpu_upload_check.py --display "$DISPLAY"` passed on the inherited assigned display with a real GLFW context, isolated synthetic v10 save/source worker and private World. Vendor `Mesa/X.org`; renderer `llvmpipe (LLVM 15.0.6, 256 bits)`; GL `4.5 (Core Profile) Mesa 22.3.6`. Expected and observed: two definitions at resolutions 8 and 32 render four instances with retained dimensions and colors. GPU vertex/index/instance bytes exactly match the CPU geometry and local cell keys. Four-byte/64-byte slices obey byte budgets. Cancellation during first mesh, first instance and after a complete batch restores Mesh byte accounting. Stale neighbors, wrong-owner stage/resource calls, transfer lifetime and empty batches pass; no GL errors remain. Source bytes, replay history and partial authority remain unchanged; the only resident addition is the explicit synthetic neighbor used to test invalidation.

The ordinary replay GL harness was also rerun against this Mesh implementation with fresh ignored fixtures and a fresh framebuffer; its exact command is recorded in the report. Vertex/index/surface readback, rendering, partial cancellation, staleness, ownership and empty upload regressions passed. Both model and ordinary framebuffer captures were inspected. These are focused native GL integrations; browser playtesting does not apply. They are not Main gameplay, physical-GPU, city or network qualification.

Fresh sanitized captures/report await controller publication. Source/class/tool/driver/image hashes match. Ten MP3/Hub restoration paths remain byte-exact to the review base; no deleted/unmerged paths exist; prior tracked evidence remains unchanged; `git diff --check` passed. No unrelated deletion is intended.

Still required: actual Main/World paging, atomic ordinary/model resource publication, coverage and eviction integration; nonblocking owner/worker saves and immutable World/sidecar transactions; city/max admission and all required 20-run physical-GPU/shaped-network 32-client memory/frame/tick/traffic qualification; latest-master integration and independent review. Limits and rollout gates remain unchanged.

### Combined ordinary/model GPU resource transfer — local native validation complete

Parent `24697476a95643e4620ba17e753ba5fd4181666c` has four successful platform builds in run `42982901010`; publication was skipped.

`RegionReplayChunkUpload` owns ordinary and model/instance upload stages for one exact bound CPU snapshot. It pumps one lane per call, sharing the existing 256 KiB cap rather than doubling it. Neither handle escapes until both stages are ready. Transfer validates source/World/resident-neighbor/detached receipts, takes both substages and returns one owner-only Resources owner. Any failure or cancellation, including between the two substage takes or after both, closes every acquired handle and remaining stage. Cleanup attempts both owners and retains suppressed cleanup failures. Wrong-thread methods reject before consuming owner resources.

The result validates its source before borrowed access and owns ordinary and model cleanup together. Borrowed handles must not be independently closed or mutated. This is atomic resource ownership transfer, not World geometry/GPU/model publication: it does not call Chunk.installMesh, attach a chunk, declare collision/coverage ready or promote save authority. Driver allocation may block; CPU arrays retain trusted owner-only lifetime without a hash fence. No frame-time or physical-GPU claim is made.

The scoped Maven command recorded in `dashboard/evidence/structure-chunk-gpu-upload.json` passed 72 tests across thirteen suites, with no failures, errors or skips. The combined GL stage is exercised by its native harness rather than those headless regressions.

Playtest: `python3 tools/structures/run_chunk_gpu_upload_check.py --display "$DISPLAY"` passed using real GLFW/OpenGL on the inherited assigned display, isolated synthetic v10 save/source worker and private World. Vendor `Mesa/X.org`; renderer `llvmpipe (LLVM 15.0.6, 256 bits)`; GL `4.5 (Core Profile) Mesa 22.3.6`. Expected and observed: an ordinary mesh plus two 8-/32-resolution model definitions/four instances transfer together and render. Ordinary vertex and model vertex/index/instance GPU bytes match CPU data. Shared four-/64-byte pumping respects caps. Cancellation after ordinary completion, between substage transfers and after both substage transfers restores Mesh byte accounting. Stale stages/resources, wrong-owner stage/resource calls, single transfer, lifetime/cleanup and empty combined transfer pass with no GL errors. Source bytes, history and partial authority remain unchanged; resident changes are explicit synthetic invalidation fixtures.

The first native run failed a too-strict exact-gray capture assertion for tinted stone. Correcting its RGB tolerance retained separate ordinary/red/blue pixel checks and exact GPU byte comparisons; the fresh retry passed without a production upload change. The final framebuffer was inspected. Browser playtesting does not apply to this focused native integration. This is not Main gameplay, physical-GPU, city or network qualification.

Fresh sanitized framebuffer/report await controller publication. Source/class/tool/driver/image hashes match. Ten MP3/Hub restoration paths remain byte-exact to the review base; no deleted/unmerged paths exist; prior tracked evidence remains unchanged; `git diff --check` passed. No unrelated deletion is intended.

Still required: actual Main/World paging and atomic geometry/GPU/model publication with coverage and eviction; nonblocking owner/worker saves and immutable World/sidecar transactions; city/max admission and all required 20-run physical-GPU/shaped-network 32-client memory/frame/tick/traffic qualification; latest-master integration and independent review. Limits and rollout gates remain unchanged.

## Private replay scene publication (2026-11-11)

Parent `1aceeed2a7948565d9b3ef5a8db5318bc1261d8c` has four successful hosted platform builds in run `42986441640`; publication was skipped. This is build evidence, not feature approval.

A private owner-thread scene now retains the existing at-most-two CPU chunk results and their requests. Combined ordinary/model GPU resources transfer into an immutable entry map only after source, World, dependency and snapshot identity checks. Cancellation or rejection leaves resource ownership with the caller. Data-only replay views still default to no mesh readiness. `World.isLoaded` consults the scene's complete GPU entry, while `replayDataReady` and cell sampling remain separate. Desired-region eviction removes CPU and GPU readiness together and retires the scene-owned resources. Cleanup failure blocks new work until explicit owner retry; source invalidation fails closed and requires explicit owner cleanup. Borrowed GPU handles must not be mutated or closed. No caps were raised.

The exact scoped Maven command and fourteen suite results are recorded in `dashboard/evidence/structure-replay-scene.json`: 73 tests passed, no failures, errors or skips. The new headless scene test checks pending/data-only readiness, exact sampling, capacity rejection, wrong-owner close, eviction and unchanged save authority/source bytes. GPU publication is checked by the native harness.

Playtest: `python3 tools/structures/run_replay_scene_check.py --display "$DISPLAY"` passed on the inherited role display with an isolated synthetic v10 save and real GLFW/OpenGL. Renderer `llvmpipe (LLVM 15.0.6, 256 bits)`; GL `4.5 (Core Profile) Mesa 22.3.6`. Expected and observed: CPU data alone stops rays at an unavailable mesh boundary; complete GPU publication permits a stone hit and renders one ordinary mesh plus two 8-/32-resolution model definitions/four instances. Cancelled publication, eviction before publication, wrong-owner calls and duplicate publication are rejected without lost ownership. Eviction clears both readiness forms and releases GPU allocations. Closing a populated World also releases the scene resources. Model GPU bytes match CPU data; source bytes and partial save authority remain unchanged. No GL errors occurred. The final framebuffer was inspected. Browser playtesting does not apply to this focused native integration; this is not a fresh Main gameplay run.

Fresh pilot and final framebuffers plus the source/class/tool/driver/image-pinned report await controller publication. The final image is `structure-replay-scene-verified.png`. The ten MP3/Hub restoration paths remain byte-exact to the review base. No deleted or unmerged paths exist, prior tracked evidence is unchanged, and `git diff --check` passed. No unrelated deletion is intended.

Main renderer/paging integration, full coverage and movement, nonblocking saves, immutable World/sidecar transactions, city/max admission and all required 20-run physical-GPU/shaped-network 32-client memory/frame/tick/traffic qualification remain unfinished. Driver allocation may block. The private scene does not install resident chunks/history, enable Main partial startup, promote save authority, or establish supported city limits. Rollout gates, latest-master integration and independent review remain required.


## Replay scene draw bridge (2026-11-11)

Parent `93d8c8f80fa9d5c9747b568cdd8436f366207f8e` was published. Its scene image/report were fetched from that exact source and match local bytes and SHA-256. Hosted run `42990198301` was observed with all four platform builds in progress; no new build pass is claimed here.

World now delegates ordinary and model replay draws to the scene that owns their GPU resources. The scene validates every published entry before the first draw, uses chunk translation with local model offsets, culls both lanes by chunk frustum, and restores instancing state. Its publication/eviction revision feeds World scene revision and reflection invalidation. Main's ordinary pass, shadows, reflection probes and planar reflection capture call the same ordinary bridge. VoxelModelRenderer calls the prepared-instance bridge without rebuilding private scene model meshes or per-frame instance buffers. Resident worlds keep their existing draw path. Replay draws currently bypass resident Hi-Z/GPU-driven chunk caches and retain the two-chunk cap.

This does not enable Main partial startup, a moving paging session, edits or partial save authority. Water surface selection/draws and lighting/coverage integration still rely on resident chunks and require further work before replay-backed Main startup. No supported city limits or frame-time claim follows from this bridge.

The scoped Maven selector in `dashboard/evidence/structure-replay-render.json` passed 73 tests across fourteen suites with no failures, errors or skips. Separate restored compatibility checks passed: 4 `MusicMp3Test` cases and 6 `test_hub_preview_registration.py` cases. These commands exercised the affected JVM integration and registration CLI; browser testing does not apply to them.

Playtest: `python3 tools/structures/run_replay_render_check.py --display "$DISPLAY"` passed using real GLFW/OpenGL and an isolated synthetic v10 source. Renderer `llvmpipe (LLVM 15.0.6, 256 bits)`; GL `4.5 (Core Profile) Mesa 22.3.6`. Expected and observed: the World ordinary bridge and production VoxelModelRenderer issue one ordinary draw and two model-instance draws, translated to chunk `(0,10,0)`. Both model resolutions and ordinary stone are visible in the inspected framebuffer. Frustum-culling, CPU-only, evicted and stale-source paths issue zero draws. Wrong-owner draw calls reject before touching GL; instancing is restored to zero. GPU publication and eviction each advance the render revision; CPU-only data does not. Existing resident model rendering still draws both types. Cancellation, ray readiness, ownership and populated World-close cleanup regressions pass with no GL errors. GPU data and source bytes remain unchanged. The harness uses a small diagnostic shader through the production ShaderProgram API, not the complete Main lighting/shadow/reflection pipeline.

Fresh image/report have source, compiled-class, tool, driver and image hashes. All hashes still match after compatibility checks. The ten MP3/Hub paths match the review base byte-for-byte; no deleted or unmerged paths exist; prior tracked evidence is unchanged. No unrelated deletion is intended. Full Main regression results are recorded below after execution; they do not qualify the private scene's incomplete lighting/water integration or city performance.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY" --evidence-prefix structure-replay-render-native` exited 0. Production Main ran with real GLFW/X11 input, an isolated synthetic offline profile and LOW/detail32/horizon512/scale0.5, shadows/AA/dynamic resolution OFF. Capture, named library/anchor, export/import including the long import path, all four rotations, occupied and player-overlap rejection, empty-space preservation, one-operation undo, placement/undo/export/import cancellation, malformed import atomicity, repeated placement, 32-resolution dependency restart recovery, inventory and model editor regression checks passed. Source stayed unchanged throughout; all 254 production/harness manifest entries match afterward.

The fresh H.264 recording is 461,078 bytes, 25 frames, 46.051 seconds, 960×544. Every frame decoded; first and last differ. The decoded 65-percent frame, blocked preview and placed-world screenshots were inspected. HUD shows 1 FPS on llvmpipe. This is functional evidence, not the required >=30 FPS, physical-GPU, city or network qualification. The fresh native artifacts and outer report have hashes and remain pending controller publication. The actual decode command and helper hash are recorded in `structure-replay-render.json`. Browser playtesting does not apply to this native game workflow.

Next work remains implementation: moving Main paging/session, full coverage/lighting/water and save authority, nonblocking durable transactions, and city/max admission. Required 20-run physical-GPU and shaped-network 32-client qualification remains pending. Existing resource and rollout gates stay unchanged. New-source hosted validation, latest-master integration and independent review are still required before readiness.

## Paused at safe boundary — replay water work (2026-11-11)

Published parent: `94e2c16499e0bb7543ac5edd9c3ab8055aca2b49`; review base remains `71612f09bfec4549456b3317676e76cdced085a4`. Hosted runs `42992132921` and `42992127501` were observed pending. No hosted pass is claimed. No Git operation or publication was performed locally.

Unpublished edits are preserved in World, RegionReplayChunk, RegionReplayMesh, RegionReplayScene, WaterRenderer and RegionReplayMeshTest, plus new `tools/structures/ReplayWaterCheck.java` and `run_replay_water_check.py`. Worker mesh preparation now computes a water-height scalar using the existing Chunk surface convention, carries it through detached staging, and checks cancellation during the scan. Scene-only water selection/draw methods reject CPU-only coverage, reuse owned ordinary geometry and feed WaterRenderer selection/rendering. Partial startup/save authority and all caps remain unchanged. These edits are not yet validated sufficiently for publication or readiness.

Executed regression command: `mvn --batch-mode -q -o -Dmaven.repo.local="$PWD/target/structure-m2" -DargLine="-Djava.io.tmpdir=$PWD/target/reviewer-tmp" -Dtest=RegionReplayMeshTest,RegionReplaySceneTest,ReplayChunkPreparationTest,RegionReplayDetachedChunkTest,ReplayMeshFenceTest,RegionReplayWorkerMeshFenceTest,RegionReplayWorkerNeighboursTest,RegionReplayChunkWorkerTest,RegionReplayChunkPumpTest,RegionReplayChunkReadViewTest,RegionReplayModelDrawsTest,WorldStreamingTest,WorldInitialReplayTest,StructureTest,StructureMultiplayerTest test` exited 0: 78 tests across fifteen suites, no failures, errors or skips. New assertions cover water metadata, detached preservation, dry geometry and cancellation.

Playtest: the already-running `python3 tools/structures/run_replay_water_check.py --display "$DISPLAY"` finished with exit 1. It used the inherited role display, real GLFW/OpenGL and a synthetic v10 source. Failure: `java.lang.IllegalStateException: Mesh buffer allocation failed`, from `Mesh$Upload.<init>(Mesh.java:87)` through `RegionReplayMeshUpload.pump`, `RegionReplayChunkUpload.pump` and `ReplayWaterCheck.finish`, at harness main line 60. A 6,063-byte `dashboard/evidence/structure-replay-water-checked.png` was created before failure; it is an unqualified partial-run capture, not passed playtest evidence. No completed native receipt exists. Do not reuse its filename or overwrite prior evidence. Bounded diagnostics are in ignored `target/structure-replay-water-native.stdout`; never read `target/structure-native/runtime-private.txt`.

Resume only when service/quota authorization returns. First diagnose the native failure, including possible GL error left by the new unlit WaterRenderer call/sampler bindings before the later upload. Do not assume the allocation failure proves resource exhaustion. Then rerun with fresh evidence names, inspect media and pin source/class/tool hashes. Complete Main regression as appropriate after source fixes. No further implementation or tests were started after the pause request. `git diff --check` passed; all unpublished edits remain unstaged. Main paging/lighting/save integration, city/max admission, 20-run physical-GPU/shaped-network 32-client qualification, exact-source hosted checks, latest-master integration and independent review remain required.

## Replay water validation resumed (2026-11-11)

The preserved water edits are now locally validated. Parent remains `94e2c16499e0bb7543ac5edd9c3ab8055aca2b49`. Run `42992132921` was observed with all four platform builds in progress; no parent build pass is claimed. Its published replay-render PNG was fetched at the exact parent and matches local bytes. The prior Main workflow belongs to that parent; it is not a fresh Main run for these water edits.

The failed native run was traced to an earlier water draw, not established memory exhaustion. An immediate post-draw assertion reproduced GL error 1282. The unlit WaterRenderer branch left active sampler3D, isampler3D and isamplerBuffer uniforms on unit 0 alongside a samplerCube, despite runtime branches skipping lighting. Assigning distinct units 3/4/5/6 to those lighting samplers fixes the unlit draw. The supplied lighting callback path is unchanged. No GL error was cleared to hide a failure.

Worker mesh preparation scans the existing water-top surface convention with cancellation and records one height scalar. Detached preparation and both model-preparation snapshot copies preserve it. GPU-published scene entries supply immutable nearest-water descriptors and owned water draws to WaterRenderer; CPU-only, dry and evicted regions do not supply water. Selection remains bounded by the existing two-chunk scene cap. No mutable Mesh/Chunk handles cross this API. Geometry, instance, package, replay and dependency caps remain unchanged; partial Main startup/edit/save authority stays disabled.

The scoped Maven command recorded in `dashboard/evidence/structure-replay-water.json` passed 78 tests across fifteen suites with no failures, errors or skips. It includes water-height generation, dry geometry, cancellation, detached preservation and model-preparation preservation assertions. Browser playtesting does not apply to these JVM integration checks.

Playtest: `python3 tools/structures/run_replay_water_check.py --display "$DISPLAY"` exited 0 on the inherited role display, isolated synthetic v10 source and real GLFW/OpenGL. Renderer `llvmpipe (LLVM 15.0.6, 256 bits)`; GL `4.5 (Core Profile) Mesa 22.3.6`. Expected and observed: published local height 1 maps to world height 161; CPU-only coverage cannot select/draw water; production WaterRenderer finds and draws the scene with optics disabled. The final framebuffer combines diagnostic opaque geometry and the actual production unlit water shader, with a separate water-pixel assertion; it was inspected. Water, ordinary and model frustum culling, wrong-owner calls, stale-source zero draws, eviction, invalid eye rejection, ray readiness, cancellation, resident model rendering and populated-World cleanup regressions pass. Immediate post-water GL checks and final cleanup report no GL errors. Source bytes and save authority remain unchanged.

`structure-replay-water-checked.png` is the preserved unqualified partial failed-run capture. `structure-replay-water-verified.png` is a successful pilot before the model-preparation preservation fix. `structure-replay-water-final.png` is the final successful source's capture. The outer report distinguishes them and pins current Java, class, tool, driver and final image hashes. All hashes match. The ten restored MP3/Hub paths remain byte-exact to the review base; no deleted/unmerged paths exist; prior tracked evidence is unchanged; `git diff --check` passed. No unrelated deletion is intended.

New-source hosted checks require controller publication. Planar optics and complete Main paging/lighting integration, nonblocking World/sidecar saves, city/max admission, all required 20-run physical-GPU/shaped-network 32-client memory/frame/tick/traffic qualification, latest-master integration and independent review remain pending. The software renderer checks establish no frame-time or supported-city claim. Rollout gates remain intact.

## Bounded automatic scene GPU pump (2026-11-11)

Inspected actual hosted run `42993699904` for parent `30ab89e0693df34f656b0e7372423e0a9389b7b2`: Linux, Windows, Intel Mac and ARM Mac builds succeeded; publication skipped. This is parent build evidence, not review approval or new-source validation.

The private scene now owns one automatic chunk upload at a time. `pumpGpu(bytes,deadline,cancel)` uses one existing upload lane per call and the shared maximum 256 KiB budget. Zero/expired budgets do not start staging. It chooses the first desired CPU-ready entry without GPU resources, preserves desired ordering, and publishes only a complete combined resource owner. Other published entries remain owned when staging fails or is cancelled. Removing the staged chunk from the desired set aborts its private upload before CPU eviction. Scene close attempts active-upload and published-resource cleanup. Cleanup failures remain retained and block work until explicit retry; cleanup-failure injection has not been run. Mutating scene calls during an automatic pump callback reject reentry. Manual uploads remain caller-owned and cannot begin while an automatic upload is active.

No Main session is enabled by this method. The two-chunk CPU/GPU cap and all structure/replay limits stay unchanged. Driver allocation may still block; the byte/deadline interface is not a measured frame-time guarantee. Full paging, coverage, lighting and save authority remain separate unfinished work.

The scoped Maven command in `dashboard/evidence/structure-replay-scene-pump.json` passed 78 tests across fifteen suites, with no failures, errors or skips. Scene regressions include no-GL zero/expired budgets, invalid budgets, cancellation and retained CPU data. Browser playtesting does not apply to these JVM integration checks.

Playtest: `python3 tools/structures/run_replay_scene_pump_check.py --display "$DISPLAY"` exited 0 using real GLFW/OpenGL on the inherited assigned display and isolated synthetic v10 sources. Renderer `llvmpipe (LLVM 15.0.6, 256 bits)`; GL `4.5 (Core Profile) Mesa 22.3.6`. Expected and observed: two CPU chunks publish through at most one owned upload; four-/64-byte slices respect the shared cap. Zero/expired/invalid budgets and wrong-owner calls are checked. Cancellation closes a staged upload while retaining CPU data and another published GPU chunk. Desired eviction aborts an upload, retries succeed, and a reentrant desired-set mutation rejects without losing published resources. Complete scenes allocate no additional Mesh memory. Evicting one published chunk keeps the other usable. Source invalidation closes active staging and fails readiness closed; World close releases the remaining published entry. A separate fresh source confirms scene close releases an active upload. Source bytes and partial save authority remain unchanged; no GL errors occurred.

The inspected final frame renders ordinary geometry, prepared model instances and real unlit production water. `structure-replay-scene-pump.png` is the successful pilot before the active-upload close check; `structure-replay-scene-pump-final.png` is the final successful run. The report pins current source/class/tool/driver/image hashes, all verified. Ten MP3/Hub restoration paths remain byte-exact to review base `71612f09bfec4549456b3317676e76cdced085a4`; no deleted or unmerged paths exist; prior tracked evidence is unchanged; `git diff --check` passed. No unrelated deletion is intended.

New edits and sanitized media await controller publication and exact-source checks. Main paging/lighting, planar optics, nonblocking durable World/sidecar transactions, city/max admission, 20-run physical-GPU/shaped-network 32-client memory/frame/tick/traffic qualification, latest-master integration and independent review remain required. No city support, stable rollout or readiness claim is made.

## Replay World close preflight (2026-11-11)

Actual hosted run `42997952202` for parent `b7a697141b16a6900335de7cb4bac339aa688db4` passed all four platform builds; publication skipped. New edits still require exact-source hosted validation.

World.close previously marked the World closed and changed its sky journal before a replay view could reject a foreign-thread or pump-reentrant close. It now calls a non-mutating replay-view preflight first. All three real replay views check the owner; the GPU scene also checks pump reentry. Rejection leaves World state and resources intact. Accepted closes retain the existing CloseSequence cleanup path and owner-side cleanup retry behavior. The default hook remains empty for compatibility; this does not make ordinary resident World close generally thread-safe. No caps or partial startup/save authority changed.

The command in `dashboard/evidence/structure-replay-world-close.json` passed 86 tests across seventeen suites with no failures, errors or skips. The new test exercises data-window, data-chunk and GPU-scene read views. Foreign World.close rejects before changing isClosed, skyStamp or sceneRevision; owner sampling remains available. Legitimate owner close and repeated close still work. Source bytes remain unchanged. Browser playtesting does not apply to these JVM integration checks.

Playtest: `python3 tools/structures/run_replay_world_close_check.py --display "$DISPLAY"` exited 0 with real GLFW/OpenGL on the inherited assigned display and isolated synthetic v10 sources. Renderer `llvmpipe (LLVM 15.0.6, 256 bits)`; GL `4.5 (Core Profile) Mesa 22.3.6`. Expected and observed: a World.close attempted inside an upload-pump cancellation callback rejects before mutation; a foreign-thread World.close with a published GPU chunk and active upload also rejects. The World stays open, readiness and the active upload remain intact, and Mesh memory accounting is unchanged. Owner World.close releases active uploads and published resources, including repeated-close checks. Two-chunk pump, cancellation, eviction, source invalidation, water/model rendering and save-authority regressions pass without GL errors. The fresh framebuffer was inspected. This focused native check is not a new Main UI or city-performance qualification.

Source/class/tool/driver/image hashes match the fresh report. Ten MP3/Hub restoration paths remain byte-exact to review base `71612f09bfec4549456b3317676e76cdced085a4`; no deleted or unmerged paths exist; prior tracked evidence is unchanged; git diff --check passed. No unrelated deletion is intended. New edits and media await controller publication. Main paging/lighting, planar optics, durable World/sidecar saves, city/max admission, physical-GPU/shaped-network 32-client qualification, latest-master integration and independent review remain unfinished. Rollout gates stay in place.

## Cooperating-process save publication lease (2026-11-11)

Inspected actual run `43001020636` for parent `70fb36ce3e991116a79f4f5e2795c584fe2a6055`: all four platform builds succeeded; publication skipped. New source still needs hosted validation.

Generation capture and save replacement now hold an OS FileLock inside the existing JVM stripe. The lease uses tryLock rather than waiting for another process. A busy capture, ordinary publication or guarded publication rejects before replacement. The stable adjacent `.voxel-publication-<SHA256 filename>.lock` file stays in place after release, preventing lock-inode replacement races among cooperating publishers. Its bounded hashed name does not extend the save filename. Existing invalid/non-regular lock metadata rejects before publication. These APIs need permission to create/open the metadata sidecar; it contains no world payload. Strict atomic replacement and ordinary-save fallback behavior remain unchanged.

This serializes cooperating publishers using the same normalized path. It does not exclude path aliases or noncooperating writers, create a persistent generation manifest, make World/sidecar commits atomic, or remove synchronous hashing, filesystem I/O or same-JVM stripe waits. Generation checks retain their existing bounded digest, attributes and JVM revision. No caps or Main partial startup/save authority were changed.

The exact Maven command in `dashboard/evidence/structure-world-save-process.json` passed 81 tests across sixteen suites, with no failures, errors or skips. A real child JVM holds the actual publication move critical section. Competing capture, strict/ordinary replacement and guarded replacement fail busy while target and staged bytes remain unchanged. Releasing the child completes its atomic replacement; the old generation then rejects and a fresh capture/retry succeeds. A non-regular lock path rejects unchanged files. Existing strict/fallback, staged-save, readback, source-change, structure and multiplayer regressions pass.

Playtest: `python3 tools/structures/run_world_save_process_check.py --display "$DISPLAY"` exited 0. It reran the actual child-process integration cases, then exercised real GLFW/OpenGL save/readback, two-chunk GPU pumping, cancellation, eviction, source invalidation, water/model rendering and World-close cleanup using isolated synthetic v10 profiles and the inherited role display. Renderer `llvmpipe (LLVM 15.0.6, 256 bits)`; GL `4.5 (Core Profile) Mesa 22.3.6`. Source bytes, model data and partial authority remain unchanged; no GL errors occurred. The fresh replay regression frame was inspected. It is not a lock-status UI or a new Main workflow run. Browser playtesting does not apply to save-publication coordination; the real subprocess CLI is the affected workflow.

Current source/class/tool/driver/image hashes match. Ten MP3/Hub restoration paths remain byte-exact to review base `71612f09bfec4549456b3317676e76cdced085a4`. No deleted or unmerged paths exist; prior tracked evidence is unchanged; git diff --check passed. No unrelated deletion is intended. New source and sanitized evidence await controller publication. Path-alias fencing, nonblocking owner saves, immutable generation manifests, joint World/sidecar transactions, Main paging/lighting, planar optics, city/max admission, all physical-GPU/shaped-network 32-client performance qualification, latest-master integration and independent review remain required. No readiness, supported-city or frame-time claim is made.

## Persistent save publication epochs (2026-11-11)

Controller scoped observation reports four platform builds succeeded for parent `94fda65259249ce97026c25b0d69838bdcee06af`; publication skipped. New source requires hosted validation.

The persistent publication lock now holds a fixed 32-byte version, random epoch ID and sequence. Capture records that epoch. Cooperating current-protocol publishers advance and force the sequence before replacement, so even identical-byte publication by another JVM invalidates stale tokens. Failed moves conservatively invalidate old tokens; fresh capture allows retry. Empty legacy metadata initializes under the lock. Invalid size/version, negative sequence and exhausted sequence reject before replacement. Metadata that aliases the destination rejects before metadata writes. These changes do not provide a joint World/sidecar transaction, alias fencing, crash qualification or nonblocking frame I/O. Corrupt metadata fails closed rather than silently resetting. Older-protocol and noncooperating writers remain outside epoch fencing.

The command recorded in `dashboard/evidence/structure-world-save-epoch.json` passed 85 tests across 17 suites, with no failures, errors or skips. New cases exercise identical-byte child-JVM publication, empty legacy upgrade, malformed records, exhausted sequence, failed move invalidation and fresh retry. Existing save, readback, structure and multiplayer regressions passed.

Playtest: `python3 tools/structures/run_world_save_epoch_check.py --display "$DISPLAY"` exited 0. It executed the new epoch cases and existing real subprocess lease cases, then real GLFW/OpenGL replay save/readback, two-chunk pumping, cancellation, eviction, source invalidation, water/model rendering and close regressions. Isolated synthetic v10 profiles; inherited display; llvmpipe LLVM 15.0.6 and GL 4.5 Mesa 22.3.6. The fresh replay frame was inspected; it shows the render regression, not an epoch-status UI. Browser playtesting does not apply to publication coordination; the subprocess CLI exercises that workflow.

Source/class/tool/driver/image hashes match. Ten MP3/Hub restoration paths remain byte-exact to the recorded review base. No deleted or unmerged paths exist; prior tracked evidence is unchanged; git diff --check passed. No unrelated deletion is intended. New source and evidence await controller publication. Main paging/lighting, planar optics, nonblocking saves, joint World/sidecar transactions, city/max admission, required physical-GPU and shaped-network 32-client qualification, latest-master integration and independent review remain pending. No readiness or supported-city claim is made.

## Reject busy JVM save stripes without waiting (2026-11-11)

Inspected exact-head run `43009076792` for parent `a0e3fed451ca4a3a2cedb01a9cda8ba9c12f0e6c`: four platform builds succeeded; publication skipped. Duplicate run `43009069832` had an Intel Mac `MobileGatewayTest.receiptTimeoutReportsOnlyFixedSyntheticStage` JUnit temp-directory cleanup error; Ubuntu passed and the other platforms were cancelled. The successful run is not readiness or qualification.

The fixed 64 JVM save stripes now use ReentrantLock.tryLock. A competing thread or same-thread callback reentry gets `Save publication busy` before acquiring the OS lease or changing its epoch. Resource scope releases the stripe on lease failure, move failure and success. Hash collisions can conservatively reject an unrelated target for retry. Existing OS lease, persistent epoch, digest checks and atomic/fallback policy remain intact. This removes waiting on the JVM monitor, not filesystem stalls: hashing, forcing, opening and moving still perform synchronous I/O. No 100 ms latency or frame-time guarantee is claimed.

The recorded Maven command passed 87 tests across eighteen suites, with no failures, errors or skips. A synthetic held writer remains blocked on a latch until competing capture, ordinary publication and guarded publication return busy. Target and staged files remain unchanged during rejection. After release, a stale token rejects and fresh capture/retry succeeds. Callback reentry rejects, and a deliberately failed move releases the stripe for retry. Existing epoch, child-process lock, save/readback, structure and multiplayer regressions passed.

Playtest: `python3 tools/structures/run_world_save_stripe_check.py --display "$DISPLAY"` exited 0. The real CLI thread/process cases ran, then real GLFW/OpenGL replay save/readback, two-chunk pumping, cancellation, eviction, source invalidation, water/model rendering and cleanup regressions ran with isolated synthetic v10 profiles. Inherited display; llvmpipe LLVM 15.0.6; GL 4.5 Mesa 22.3.6. The freshly captured replay regression frame was inspected. It does not show a lock-status UI or a new Main session. Browser playtesting does not apply to publication coordination; CLI thread/process integration exercises the affected workflow.

Source/class/tool/driver/image hashes match. Ten MP3/Hub restoration paths match the recorded review base exactly. No deleted or unmerged paths exist; prior tracked evidence is unchanged; git diff --check passed. No unrelated deletion is intended. New source and sanitized evidence await controller publication. Main paging/lighting, planar optics, nonblocking saves, World/sidecar transactions, city/max admission, required physical-GPU and shaped-network 32-client qualification, latest-master integration and independent review remain pending. The attempted delegate call failed without a task ID; this change was inspected and validated locally. No outstanding delegate task exists.

## Fix concurrent readback admission failure (2026-11-11)

Exact parent `d714b5b91e1652267ea1e48886d5d1d0d0be74d8` failed hosted validation. Run `43012827540` failed Intel Mac `RegionReplayBindingTest.foreignMetadataAndRequestsRejectEvenWithIdenticalSourceHashAndTags`; the other three builds succeeded. Duplicate `43012820484` failed Ubuntu `RegionReplayChunkWorkerTest.identicalTagsCannotSubstituteForeignBindingAndModelMutationInvalidatesReadyChunk`, cancelling other jobs. Both failed while awaiting metadata from two readers opening the same file. Fail-busy generation capture can reject one reader during another reader's capture; the test formerly waited for an unreachable RUNNING state.

Readback admission now retries only a typed publication BusyException on its existing worker. It uses a one-second contention budget and 1–32 ms backoff. It does not retry corrupt metadata, digest mismatch or other I/O failures. Cancellation and thread interruption follow the existing CancellationException policy, retaining the interrupt flag. Every successful attempt still validates the decoded digest against the destination. Publication APIs remain fail-busy without retry. This is not a filesystem deadline, frame-thread API or performance qualification. The test readiness helper now fails when admission terminates, retaining the original RUNNING assertion and ten-second opening deadline.

Final Maven command in `dashboard/evidence/structure-world-save-readback.json` passed 110 tests across 22 suites with no failures, errors or skips. Initial run had one new test expectation failure: interrupt was expected as InterruptedIOException instead of the existing CancellationException policy. The assertion was corrected, a changed-digest-after-contention case added, and the full selector rerun. New tests cover retry then successful readback, immediate publication rejection while busy, cancellation, contention timeout, malformed metadata, interrupt preservation and source digest rejection after a competing replacement. Failure/cancellation leave target and staged bytes intact except the explicitly successful competing writer.

Playtest: `python3 tools/structures/run_world_save_readback_check.py --display "$DISPLAY"` exited 0. It exercised the new CLI cases, then repeated each hosted failing two-reader workflow 20 times, retaining all foreign binding/model fence assertions. It also reran publication thread/process/epoch cases and real GLFW/OpenGL save/readback, two-chunk GPU pumping, cancellation, eviction, source invalidation, water/model rendering and cleanup regressions. Isolated synthetic v10 profiles; inherited display; llvmpipe LLVM 15.0.6; GL 4.5 Mesa 22.3.6. The new frame was inspected; it shows replay rendering after the CLI cases, not lock status or a new Main session. Browser playtesting does not apply to worker admission. These 20 correctness repetitions are not city/max benchmark runs.

Source/class/tool/driver/image hashes match. Ten MP3/Hub restoration paths remain byte-exact to the recorded review base. No deleted or unmerged paths exist; prior tracked evidence is unchanged; git diff --check passed. No unrelated deletion is intended. New source and sanitized evidence need controller publication and hosted validation. Main paging/lighting, planar optics, nonblocking saves, World/sidecar transactions, city/max admission and all required physical-GPU/shaped-network 32-client performance qualification remain unfinished. Latest-master integration and independent review remain required. A delegate attempt failed without a task ID; work was independently implemented and executed locally. No outstanding delegate task exists.

## Close readback input before publication admission (2026-11-11)

Exact parent `12532b78b325cfe1eb36af8d610fdae326643e6a` failed Windows validation. In run `43016199122`, `WorldSaveReadbackRetryTest.heldLeaseRetriesReadbackButPublicationStaysFailBusy` hit AccessDeniedException replacing `world.dat`; Ubuntu succeeded and both Mac jobs were cancelled. The reader retained its input handle while waiting for the writer's publication lease. Windows can deny replacement of that open file.

Regional readback now closes its decoded input before generation admission retries. The validated bounded spool, decoded metadata and digest remain available for the final generation check. A close failure rejects admission and cleans the spool. No package, persistence or model caps changed. Publication remains fail-busy; readback retries remain worker-only and bounded. A package-private input opener permits deterministic lifecycle and close-failure tests; the default still opens the save through Files.newInputStream.

The regression test now holds the competing writer until its reader reports the input closed, then releases the writer. This removes timing dependence and verifies the required close order on Linux as well as Windows. An injected close failure leaves world bytes unchanged and removes the private spool. The recorded Maven selector passed 111 tests across 22 suites, with no failures, errors or skips.

Playtest: `python3 tools/structures/run_world_readback_close_check.py --display "$DISPLAY"` exited 0. It exercised the input-close ordering/failure cases, retry/cancel/timeout/interrupt/digest cases, and 20 repetitions each of the two concurrent-readback binding workflows. It also ran the existing publication thread/process/epoch checks and real GLFW/OpenGL replay save/readback, two-chunk pumping, cancellation, eviction, source invalidation, water/model rendering and cleanup regressions. Synthetic v10 profiles; inherited display; llvmpipe LLVM 15.0.6 and GL 4.5 Mesa 22.3.6. Fresh replay frame inspected. It shows rendering after the CLI checks, not lock status or a new Main session. Browser playtesting does not apply to file-handle admission. These correctness repetitions are not city/max performance qualification. Native Windows validation of this fix remains pending.

Source/class/tool/driver/image hashes match. Ten MP3/Hub restoration paths match the recorded review base exactly. No deleted or unmerged paths exist; prior tracked evidence is unchanged; git diff --check passed. No unrelated deletion is intended. New source and sanitized evidence need controller publication and hosted validation. Main paging/lighting, planar optics, nonblocking saves, World/sidecar transactions, city/max admission, required physical-GPU/shaped-network 32-client performance qualification, latest-master integration and independent review remain unfinished. A delegate attempt failed without a task ID; this fix was independently inspected and executed locally. No outstanding delegate task exists.
