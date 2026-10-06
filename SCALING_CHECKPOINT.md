# Regional population: controller rebase handoff

Current base HEAD: `2b50f4c4be6d47a865906ec0af04585a6db203b0`.
The rebase remains paused. The controller must stage resolved files and continue it. No staging, continuation, abort/reset, commit, push, merge or deployment was performed here.

Resolved `dashboard/progress.json` by retaining all 49 base entries unchanged and adding this task's entry. All 28 base road source/test/tool/evidence files are byte-identical to the base, including block IDs 187–189, color handling, lane choices and road tests. No base files were deleted. Unrelated historical conflict markers inside unchanged base queue evidence remain untouched.

Regional saves now use format 11 (`0x4349543B`) and protocol 21. Formats 1–10 load without a regional tail; format 10 retains paved-road type bytes. Formats below 11 reject nonempty regional data before writing. Base-generated format-10 empty and paved-road fixtures, their writer provenance, a reproducible generator and four compatibility tests were added.

Pre-handoff checks: 45 tests across eight suites passed, including format-10 migration, format-11 roundtrip, truncated save rejection, block validation/color handling, paved lane placement/persistence, regional scaling and real TLS late join/restart. Commands, preservation hashes and fixture provenance: `dashboard/evidence/regional-scale-recovery-tests.json`.

MANDATORY AFTER CONTROLLER CONTINUES REBASE:
- Resume development for final-head tests and native playtesting on the inherited assigned display.
- Refresh regional PNG/MP4/report evidence; existing regional media describes the prior submitted head and must not validate this corrected head.
- Exercise paved-road placement alongside regional workflow, starvation and local-only regressions. Preserve prior base road media; give new captures distinct names.
- Verify source/fixture/preservation hashes and publish only through the controller before independent review.

The developer handoff is `recover`, not `ready`. No owner answer is needed. No full-suite or final-head native pass is claimed in this checkpoint.
