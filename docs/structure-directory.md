# Private persistent chunk directory experiment

`PersistentChunkDirectoryPrototype` tests immutable directory roots before any replacement of the production chunk hash map. It uses a balanced AVL tree ordered by the three signed chunk coordinates. Each insertion, replacement or deletion creates a bounded tree path; retaining a root requires no map traversal or node copy. Values must be immutable or separately protected by the existing copy-on-write ownership protocol. The directory does not copy, freeze or authorize its values.

The constructor accepts a private entry budget from 1 through 1,048,576. Adding a new key at capacity rejects before insertion-path allocation; replacing an existing key still works. Null keys/values reject. Iteration is sorted, stable for that root and read-only, with a stack bounded by tree height. Clear returns an empty root without changing the old root. Callers must bound retained roots; these fixture bounds are not supported game limits or a whole-process memory bound.

The CLI checks sorted insertion/reverse deletion, exact sizes/balance/traversal, deliberate hash collisions, extreme signed coordinates, missing deletes, invalid input, read-only entries/iterators, worker forks and independent historical roots. It compares 25,000 mixed operations against a separate `TreeMap` oracle while retaining at most eight old roots. Real `WorldVoxels.CellImage` values are compared to world cells, then checked after caller and world edits. All checks passed, including 11 rejection cases. Three tooling integration tests passed in 14.837 seconds. Their common Java source list compiles this experiment for hosted checks.

The benchmark uses 8,192 entries, three warmups and 20 runs. Each run separately measures a `HashMap` snapshot copy, 256 existing-key replacements and 65,536 mixed hit/miss reads. Tree/hash update and query order rotate. Final maps and checksums must match. Setup, validation and oracle checks are excluded from component timings. No root-assignment timing or main-thread timing guarantee is claimed.

Painted-window cohort, milliseconds:

| Component | Median | p95 | Worst |
| --- | ---: | ---: | ---: |
| Hash snapshot copy | 0.650191 | 2.345100 | 2.990649 |
| Hash updates | 0.020007 | 0.031895 | 0.039104 |
| Tree updates | 0.273976 | 0.365975 | 0.375269 |
| Hash reads | 2.770961 | 5.164133 | 5.386477 |
| Tree reads | 12.897979 | 15.133934 | 15.731321 |

The tree height was 14. Whole Java child peak RSS was 162,340 KiB with `-Xmx256m`, including fixture checks, benchmark and Swing. This is not per-phase or retained memory. All three diagnostic cohorts and all 300 raw timing samples are retained in the report, with independently recomputed median/p95/worst values. The first UI cohort overlapped the three integration checks; the first CLI and painted UI cohorts had no other long-running own check during timing. Other shared-VPS work remains uncontrolled.

Keep the production hash map. Stable roots remove one whole-map snapshot cost, but the AVL query cost is too high to adopt without a different measured query/cache design. No production geometry, renderer, history, persistence, package format or admission limit changed. The next backend work still needs cheap live queries, worker-owned staging, explicit pending/ready/failed region states and authoritative root/history/save publication. This directory is not a placement transaction or game backend.

Playtest: actual CLI checks and all benchmark paths completed. Its own Swing result window was captured on inherited X11/authentication and inspected. The first capture was blank before paint; it was replaced by the inspected painted capture. Browser playtesting does not apply to this CLI-only experiment. All 228 production/harness hashes still match the completed Main/GLFW workflow. The published workflow at parent `eb34fb67669d6bd3dbda06ae56bdda2adae673ad` was downloaded and its SHA-256 matched the local video. No new Maven/native game pass is claimed. All eight hosted parent builds passed; publication jobs skipped.

Commands, environment, expected/observed results and raw measurements: `dashboard/evidence/structure-directory.json`. Inspected image: `dashboard/evidence/structure-directory.png`. These new files and tooling await controller publication and exact-source hosted checks. No tracked deletions against review base `71612f09bfec4549456b3317676e76cdced085a4`; restored MP3 fixtures/tests and Hub workflows remain intact. No unrelated deletions intended.

Both full city/maximum fixtures still exceed production admission caps. Live region/root/history, renderer/physics/server/persistence work is unfinished. Physical GPU, shaped 32-client and required 20-run full game/memory/frame/tick/traffic qualification remain pending. Stable rollout stays gated.
