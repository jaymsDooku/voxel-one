# Immutable voxel chunk roots

`WorldVoxels` now retains an immutable `ChunkDirectory` root when taking a whole-world geometry snapshot. It no longer copies every stored chunk-map entry. Existing bucket ownership tokens still change at sharing boundaries, so later source, snapshot and staging edits clone their mutable buckets independently. Selected-region snapshots and patches retain their existing validation and authority requirements.

The directory uses compact 32-way bitmap branches over a 32-bit coordinate hash. Each edit copies its lookup path, with at most seven branch levels. Full-hash collisions use immutable balanced coordinate trees rather than a linear list. Values still require immutable or copy-on-write protection. This is not a transaction, a material validator or a wall-time/memory guarantee. The world uses an `Integer.MAX_VALUE` entry allowance to preserve its prior global storage capacity; package/world/model resource policies remain outside this container.

Iteration is stable and deterministic for a root, but does not preserve global insertion order. Flat edit-history order and all protocol/package/save formats remain unchanged. Primitive-coordinate lookup avoids constructing ordinary temporary lookup keys. A single immutable cache entry holds the root, three coordinates and bucket; it is published atomically. Hits check every field. Root replacement/clear discards the cache. Every fourth cold miss updates it. A racy miss counter can affect hit ratio only; it cannot authorize a hit or placement. Four concurrent frozen-world readers are covered by regression tests.

The initial coordinate XOR mix clustered mirrored coordinates. Sequential coordinate mixing and an 8,192-entry signed-grid distribution check address that measured case. Forced full-hash collision tests still exercise the fallback independently. Earlier cache variants are retained as rejected diagnostics in the report; they are not final-source measurements.

All 139 focused tests passed in 23 fresh suites. Eight new tests cover old-root independence, random edits, forced collisions, signed/deep paths, deletion collapse, budgets, grid distribution, worker transfer, concurrent reads and cache behavior across edits/snapshots/clear. Full Java regression passed: 1,025 tests in 204 fresh suites, zero failures/errors and one optional browser workflow skip. The complete region-backend integration suite passed all 31 tests in 155.717 seconds. Four selected tooling integrations had also passed in 21.845 seconds.

The parent feature Mac Intel build failed `ServerRegionSaveTest.startupAndOrdinaryResavePreserveRegionalHistoryModelsAndPlayerTail`, despite the controller success summary. A bounded Maven-summary artifact identified the failure. Reproduction using three actual ticks moved its unsupported fixture drop from y=180.0 to 179.7 and changed the save bytes. The fixture now supports the drop with a stone block and waits for three actual ticks during both opens. Exact-byte assertions remain. Reproduction now stays at y=180.0 and byte-identical. The affected tests and full suite pass. Production server physics is unchanged; new exact-source hosted checks remain required.

Final component diagnostics use three warmups and 20 runs. Parent `WorldVoxels.java` is compiled into an isolated first classpath entry with unchanged dependencies and identical helper bytecode. Versions run in separate JVMs. These are shared-VPS diagnostics, not the supplied city/maximum fixture qualification.

| Actual voxel snapshot | Parent p95 ms | Current p95 ms | Parent/current median allocated bytes |
| --- | ---: | ---: | ---: |
| 100,000 cells, 416 compact chunks | 0.541914 | 0.001779 | 20,912 / 104 |
| 100,000 cells, 100,000 scattered chunks | 39.358482 | 0.002082 | 5,048,720 / 104 |

First edits still copy the affected bucket. In the compact fixture, first-edit p95 was 0.192262 ms for the parent and 0.250616 ms for current source. Root sharing does not eliminate bucket work.

| Actual 65,536-query batch, 8,192 stored chunks | Parent median/p95/worst ms | Current median/p95/worst ms |
| --- | ---: | ---: |
| Coherent, repeated 16 times | 1.404121 / 1.792261 / 1.989930 | 1.835849 / 1.907833 / 1.920709 |
| Mixed, 75% hits | 2.778043 / 3.157529 / 3.255343 | 6.216141 / 7.120453 / 7.238490 |

Reads remain costlier. Current median allocated bytes per query batch are 131,040 coherent and 524,224 mixed, versus zero measured for the parent after JIT optimization. Do not claim a general speedup. The immutable root is a storage prerequisite; cold lookup cost and the remaining live region/query design still need qualification.

Whole child peak RSS for scattered snapshots was 222,076 KiB parent and 110,324 KiB current; lookup children used 70,988 and 78,172 KiB respectively, with `-Xmx512m`. These include setup, warmups and verification. Retained-heap deltas are single GC-sensitive samples. No general retained/peak-memory bound follows. All 420 final timing samples and 320 allocation samples were independently checked; raw samples, process receipts, exact commands and environment are in `dashboard/evidence/structure-hash-trie.json`. Other tenants are uncontrolled. Full placement, meshes/GPU, saves, traffic and 32-client synchronization are excluded.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed in production Main/GLFW using inherited X11/authentication and an isolated synthetic profile. Named capture/browse, self-contained export/import, four rotations, bounds/anchor/conflicts, empty-space preservation, terrain/player rejection, placement/undo, cancellation, malformed import atomicity, repeated placement, dependency restoration, restart, inventory and model editor regressions passed. All 229 production/harness hashes match benchmark, native and current source. No temporary synthetic-profile files remain.

The fresh F10 clip fully decoded: 27 frames, 50.138 seconds, 488,581 bytes, 960×544, SHA-256 `6483bab7bfb6b4d7c47ec3e8d1e8de15af2ef0687887948056a214f684d3aad9`. Midpoint and cancelled-undo views were inspected. The midpoint shows a green footprint and staging without world mutation; cancelled undo retains the house with a red blocked footprint. The actual component CLI window was also captured and inspected. The 1 FPS llvmpipe run is functional evidence only.

No tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4` are intended. Source, sanitized reports and fresh media await controller publication and exact-source hosted checks. No ordinary CI/runner handoff requires an owner answer.

Geometry/chunk/history caps remain unchanged. Both full city/maximum fixtures still exceed production admission. Live region/history/durable-root/renderer/server integration remains unfinished. Required physical GPU, shaped 32-client and 20-run full-game/memory/frame/tick/traffic qualification remain pending. Stable rollout stays gated.
