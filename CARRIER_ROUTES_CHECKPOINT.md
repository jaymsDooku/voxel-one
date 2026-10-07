# Container carrier routes: obstacle fixes ready for review

Actual edited-world clearance now uses World.sample across water y14 and air y15..23. Cache identity includes World plus editsVersion and ports; obstacles, removal and replay invalidate routes. Partial-block obstructions are covered. Implicit WorldVoxels.type avoids unnecessary octree allocation with equivalent generator values.

Full Maven verify passed 411 tests, zero skips/failures/errors, with worktree-local temp files. Six route tests cover water/hull/mast obstructions, partial blocks, removal, independent worlds, replay and implicit sampling. Vehicle audio and road work remain intact: 42 protected base files match hashes and all 62 base progress entries are unchanged. git diff --check passed.

Playtest: production Main on inherited role X11/Mesa with isolated synthetic profiles. Real stone insertion disables cached/fresh routes and sailing ships; removal restores service. Outbound arrival at 60.00000089406967 s, return departure at 68.40000101923943 s, saved elapsed 68.50000102072954 s. Production Main was restarted and rendered the restored ship from the saved profile. Return wait is now bounded at 240 sec rather than 40 sec; no simulation acceleration. Fresh obstacle, return and restart images captured.

Playtest: fresh road regression passed budget rejection/retry, chain placement, editing/deletion, direct diagonal and shallow headings for all four types. First extra road process exited 143; one retry passed. Ship F10 video: 73 frames, 1988687 bytes, 32.883 sec. Road F10 video: 179 frames, 4316239 bytes, 99.281 sec. All frames decoded; both clips below 6 MB. Fresh images and decoded samples inspected.

Exact commands, expected/observed results, source/media hashes and base preservation are in dashboard/evidence/carrier-obstacle-validation.json. Controller owns staging/commit/push and review submission. No owner answer pending; no developer Git mutation or deployment performed. All new HTTPS artifacts remain pending controller publication.
