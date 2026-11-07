# Bounded disk-region read cache

The private `DiskRegionCachePrototype` connects the disk package index to frozen cell reads. It is an integration prerequisite, not a game save backend or importer. Production admission limits remain unchanged. City/maximum placement and all physical-GPU/shaped 32-client qualification remain pending. Stable rollout stays gated.

The cache belongs to one reader thread. Its source index owns an immutable decoded file and must remain open and unchanged for the cache lifetime. The cache does not close the index. It indexes spatial section numbers and reads one section on a miss. Regions contain at most 4,096 cells. Geometry becomes immutable packed cell data; model instances retain dependency indices. Missing cells remain absent, so a caller must apply the world terrain and overlap policy explicitly.

A page becomes visible only after the complete region passes CRC, order, material and cancellation checks. A failed read does not evict an existing page or expose a partial result. Successful reads evict least recently used pages until both configured limits hold. Diagnostic limits are 1–128 pages and 1 byte–8 MiB of encoded region data. A region larger than the byte allowance rejects before reading. These counters do not measure Java heap, object overhead, temporary decoded trees, dependency models or references retained by callers. Staging a new page temporarily retains that page alongside the old cache. Returned frozen geometry remains valid after eviction or cache close.

`DiskRegionCacheCheck` exercises the actual CLI path. Its synthetic package crosses three sections and contains fractional coloured geometry, solid materials, source holes and a custom model. Tests cover LRU reloads, absent cells/sections, frozen results, cancellation during a cold read, foreign-thread access, closed handles, over-budget pages and corrupt cache data. Loaded geometry stages through the existing detached `WorldVoxels.CellPatch`. Actual world queries verify exact geometry/material/colour and dependency remapping, source-hole preservation, unrelated concurrent edits, cancelled staging and rejection after an affected-chunk edit. This is not the production placement authority, renderer, multiplayer or durable save path.

Compile and exercise it after compiling production classes:

```sh
javac -encoding UTF-8 --release 17 -cp target/classes -d target/structure-region-cache tools/structures/DiskPackageIndexPrototype.java tools/structures/DiskRegionCachePrototype.java tools/structures/DiskRegionCacheCheck.java
java -Xmx256m -cp target/structure-region-cache:target/classes DiskRegionCacheCheck
python3 -m unittest discover -s tools/structures -p 'test_*.py' -v
```

An optional package filename runs a single full read-only traversal with a two-page, 1 MiB encoded-data cache. Each cell is compared to an independent index visit. It reports counts and timings, not performance qualification. `--evidence` runs the synthetic check and displays its actual result in a temporary Swing window for capture on the inherited assigned display. Browser and native game playtesting do not apply to this tooling-only change; production and native harness source hashes remain bound to the prior game playtest.

The next integration must add a world-owned region/root backend with bounded startup and asynchronous reads, not treat a missing or unloaded page as empty. It must preserve explicit air and legacy saves, stage dependency and root changes, publish them atomically with one-operation undo, retain authoritative conflict fences and propagate I/O failures without partial placement. Renderer/physics reads must never perform blocking disk I/O on the owner thread. Only then can full fixtures be admitted and the required 20-run game/network qualification begin.

## Observed diagnostic runs

One traversal per full fixture passed exact cell equality against separate index reads. Three-region synthetic checks passed 14 rejection cases. All 58 tooling tests passed in 128.054 seconds before four additional budget-edge cases; the final selected integration rerun passed in 4.208 seconds. The synthetic CLI also exited 0 with the evidence window enabled. Its screenshot was inspected.

| Fixture | Cells | Regions / loads | Peak pages | Peak encoded bytes | Index / read-compare seconds | Whole-process peak RSS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| City | 978,288 | 2,173 | 2 | 43,140 | 7.219240 / 1.487032 | 180,512 KiB |
| Maximum | 4,000,000 | 8,192 | 2 | 76,800 | 14.918111 / 41.961276 | 191,052 KiB |

Linux `os.wait4` measured each Java child with `-Xmx256m`. RSS includes fixture indexing, validation and transient geometry, not just cached pages. Environment: Intel Core Processor (Haswell, no TSX), Debian 12, Linux 6.1.0-50-cloud-amd64, Java 25.0.3+9-LTS-195. The maximum run overlapped the small synthetic evidence child. Shared host activity is uncontrolled. These are single diagnostic runs, not isolated benchmarks, the required 20-run measurements or game budget passes. No GPU/driver performance was measured.

Playtest: the real CLI synthetic workflow staged disk-loaded geometry into a detached world patch, queried the resulting candidate, preserved holes/unrelated edits, restored its custom model and rejected affected edits. Cancellation, corruption, budgets and cache cleanup passed. Only the CLI's own Swing result window was captured using inherited display/authentication. No production or native harness source changed; all 228 hashes still match the completed native workflow at `096aa171ed270b40fb818ec5d5d6c36d79bb3325`. That head's eight hosted platform builds succeeded and its publication jobs skipped. No new Java-suite or native game run is claimed for this tooling-only change.

Sanitized commands, fixture hashes, results and limits are in `dashboard/evidence/structure-region-cache.json`. New source/tests/docs/media await controller publication and exact-source checks. There are no tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`; no unrelated deletions are intended. Restored MP3 tests/fixtures and Hub workflows still match the base.
