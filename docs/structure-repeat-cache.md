# Repeated-read chunk cache admission

The immutable chunk root reduced snapshot copying, but its sampled lookup cache allocated records during random reads. `WorldVoxels` now admits a cache record when consecutive cold misses name the same chunk. Cache hits still require the exact immutable root and all three coordinates. Every admitted record uses the current lookup result. The scalar admission hints may race between readers; a false match only changes allocation and hit ratio. They hold no root or bucket and cannot authorize a cache hit. Root replacement and clear reset the cache and admission hint. Concurrent mutation of a live world still requires its existing authority lock.

This change preserves formats, limits, geometry, collision checks, undo and dependency policies. It does not remove the trie traversal cost or complete the live region backend.

The actual `WorldChunkLookupProbe` used 8,192 stored chunks, 65,536 reads per run, three warmups and 20 measured runs. Parent `469209c890eaec620a10e7a17d463ef74a4514b8` WorldVoxels was compiled into an isolated first classpath entry with unchanged dependencies and identical helper bytecode. Versions ran in separate JVM cohorts with `-Xmx512m` on the shared Linux VPS. Other tenants were uncontrolled. All 80 timing and 80 allocation samples and summary statistics were independently checked.

| Query batch | Parent median / p95 / worst ms | Current median / p95 / worst ms | Parent / current median allocated bytes |
| --- | ---: | ---: | ---: |
| Coherent, repeat 16 | 1.715371 / 1.991705 / 2.209396 | 1.633887 / 1.698109 / 2.201355 | 131,040 / 131,040 |
| Mixed, 75% hits | 5.861037 / 6.533396 / 6.769785 | 5.859137 / 6.218205 / 6.542566 | 524,224 / 416 |

These diagnostics show lower mixed-read allocation in this fixture. They do not establish a game speedup, a retained-memory bound or city/maximum qualification. General cold trie reads remain more costly than the earlier linked-map baseline. Full placement, meshing, GPU, durable saves and network traffic are excluded. Exact commands, raw samples and source hashes are in `dashboard/evidence/structure-repeat-cache.json`.

Regression tests mix alternating and repeated accesses across four frozen-world readers. A further test changes each coordinate independently and checks repeated absent-bucket reads through source edits, snapshots and clear. Full Java regression passed: 1,026 tests across 204 fresh current suites, zero failures/errors and one optional `AssetManagerIntegrationTest.browserWorkflow` skip. Every current test source has a fresh report. All 31 backend integrations passed in 272.416 seconds. The first native run hit its unchanged 600-second subprocess guard while full Java regression ran concurrently; it is not a pass. Its ignored synthetic run is retained. The serial Main/GLFW retry passed with the same 600-second guard.

Both full city/maximum fixtures still exceed unchanged geometry/chunk/history admission caps. Live region/history/durable-root/renderer/server integration and physical-GPU/shaped 32-client/20-run game qualification remain unfinished. Stable rollout stays gated. No unrelated deletion is intended.


Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed in production Main/GLFW with real X11 input, inherited display/authentication and an isolated synthetic offline profile. Capture/name/anchor/browse, self-contained import/export, all four rotations, conflicts/holes, rejection without mutation, placement/one-operation undo, cancellation, malformed import atomicity, repeated placement, model dependency/restart recovery and inventory/editor regressions passed. All 229 production/harness hashes match benchmark, native and current source; no temporary profile files remain.

The fresh recording matches the synthetic-profile output and fully decoded: 24 frames, 42.244 seconds, 460,506 bytes, 960×544, SHA-256 `c6fd243b093abf463501ed63231f35a25a6b26ae8c76a8a5d86fd249edc3cf27`. The midpoint shows green bounds/anchor, orientation 0 and staging 10% without world mutation. The inspected cancelled-undo view retains the house with a red blocked footprint. Software llvmpipe at 1 FPS is functional evidence only.

No tracked deletions or unmerged entries exist against review base `71612f09bfec4549456b3317676e76cdced085a4`. Restored MP3 fixtures/tests/packaged smoke sources and Hub workflows/registration tests match that base. `git diff --check` passed. Source, sanitized report and fresh media await controller publication and exact-source hosted checks. This scoped result does not finish the work item or qualify stable rollout.
