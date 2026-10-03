# Camera orbit: terrain-base resolution ready for controller

Current rebase HEAD/base: 127c1d22d72d746a412962086964a769689051d9. Controller must stage resolutions and continue the existing rebase. No reset, abort, commit, push, merge or deployment performed.

513 unrelated base files hash-identical; every one of 40 base progress entries retained verbatim. Geography, terrain source/tests/tools/evidence, Protocol 17 and LocalGame version-7 save compatibility retained. Main diff contains only camera changes; --production-config preserved.

58 tests passed, including TerrainGenerationTest saveVersionAndLegacyMigration. Fresh actual GLFW/X11/Mesa run passed 400-degree orbit, release, inventory modal, view changes, focus loss/restore and fresh drag. Fresh F10 clip independently decoded, below 6 MB. Commands/results: dashboard/evidence/free-isometric-orbit-rebase-tests.json.

MANDATORY CONTROLLER CHECK AFTER REBASE, BEFORE RESUBMISSION:
python3 deploy/verify_isometric_orbit_tree.py --revision HEAD
This must pass. It checks the tested engine/test/tools/config fingerprint, all unrelated base blobs and each base progress entry. Manifest: dashboard/evidence/free-isometric-orbit-tested-tree.json. Default working-tree verification passes; negative check against current base-only HEAD correctly fails. If the post-rebase check fails, do not submit; preserve state and return for correction. Earlier submitted heads and old-base reports do not establish correctness of a new head.

The index still marks dashboard/progress.json unmerged until controller stages the resolved file. All edits preserved; independent exact-head review remains required.
