# Private shared hash directory experiment

`SharedHashChunkDirectoryPrototype` tests 64 linked hash shards without changing the live engine. A snapshot copies 64 shard references and changes the source wrapper's ownership token. The first later write to a shared shard copies that shard's entries. Each mutable wrapper has one owner; snapshot/transfer need external authority synchronization. Values must be immutable or separately copy-on-write. Sharing a directory does not freeze mutable chunk buckets.

Puts, replacements, removals and clear remain independent on both sides of a snapshot. Iterators capture stable shard references and expose immutable entries. Iteration stays linked within each shard, but does not preserve global insertion order. Any live adoption must audit replay/save/cursor order as well as the existing bucket ownership protocol. The private constructor accepts 1 through 1,048,576 entries; additions at capacity reject before cloning. This fixture bound is not a supported world limit.

The full-coordinate mix distributes colliding `ChunkPos.hashCode` values. It does not guarantee a maximum shard size. A diagnostic deliberately places 256 distinct keys in one shard, snapshots it, changes one value and verifies that all 256 entries were copied while the old root stayed exact. No fixed write-latency or retained-memory bound follows from 64 shards.

Tests compare 25,000 mixed operations against an independent `HashMap` oracle, retaining at most eight old roots. They cover both-side writes, stable iteration through clear, capacity/null/iterator rejection, colliding hashes, simultaneous extreme signed coordinates and deletion, worker-fork independence, and real frozen `WorldVoxels.CellImage` values after caller and world edits. Eleven rejection cases pass. Three affected tooling integrations passed in 15.962 seconds. After stronger extreme-coordinate assertions, the affected integration passed again in 5.388 seconds. An initial fixture used an invalid non-ARGB colour; it was corrected to an encoded material before passing checks. No implementation gate changed.

Final benchmark, milliseconds, with 8,192 entries, three warmups and 20 runs:

| Component | Median | p95 | Worst |
| --- | ---: | ---: | ---: |
| Linked snapshot | 0.221440 | 0.372760 | 6.505812 |
| Shared snapshot | 0.002364 | 0.007153 | 0.011495 |
| Linked updates | 0.018766 | 0.052788 | 0.129931 |
| Shared updates | 0.427530 | 2.602306 | 2.695548 |
| Linked reads | 3.910196 | 4.907952 | 10.053622 |
| Shared reads | 5.091559 | 5.892563 | 12.055441 |

The baseline uses `LinkedHashMap`, matching the current production container type. Each run separately measures snapshot capture, 256 existing-key replacement calls and 65,536 mixed hit/miss reads, rotating path order. Final maps and read checksums match. The distributed writes copied all 8,192 entries across the 64 shards; the largest original shard held 183 entries. Small root capture defers copying rather than removing it from this placement-shaped update workload. Keep the production linked hash map. This candidate may help read-only capture, but does not solve bounded commit publication.

Setup and oracle/invariant checks are outside component timings. Whole final Java child peak RSS was 163,940 KiB with `-Xmx256m`, including checks, benchmark and Swing. All 120 raw timing samples and independently recomputed median/p95/worst values are in `dashboard/evidence/structure-shared-directory.json`. No other long-running own test overlapped final timing; other shared-VPS work is uncontrolled. Preliminary cohorts used earlier checker revisions and remain ignored diagnostics. No full placement, main-thread, memory or game performance qualification is claimed.

Playtest: actual CLI checks and all component paths completed. Its own painted Swing window was captured on inherited display/authentication and inspected. Expected exact old/current roots and independent frozen world images; observed pass. Browser playtesting does not apply to this tooling-only change. All 228 production/harness hashes remain identical to the completed Main/GLFW implementation. No new Maven/native pass is claimed. Controller exact-head observation for parent `cd45357c23f726463bb7054c9fe8f2e2bd19e373` reports all four platform builds succeeded and publication skipped. No further wait for that completed observation is requested.

Commands, source hashes, environment and results are recorded in the report. The inspected image is `dashboard/evidence/structure-shared-directory.png`. New tooling enters the common hosted Java compile list; new source/report/media await controller publication and exact-source checks. No unrelated deletions intended.

Production geometry, package formats and all admission caps stay unchanged. Both full city/maximum fixtures still exceed production caps. Live region/root/history, renderer/physics/server/persistence integration remains unfinished. Physical GPU, shaped 32-client and required 20-run full game/memory/frame/tick/traffic qualification remain pending. Stable rollout stays gated.
