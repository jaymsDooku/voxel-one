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
