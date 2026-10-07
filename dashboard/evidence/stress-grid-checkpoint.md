# Stress grid: ready for independent review

All implementation edits remain in the assigned feature worktree. Runner alone handles commit, push, master integration, PR submission and publication. No owner answer is pending.

The offline City saves menu now installs Stress Test Grid once without overwriting an existing name. Its 1000 x 1000 grid has one million vacant 16 x 16 plots, exact centered 40/20/20/20 zoning shares, and continuous paved two-lane roads between every row and column. Stable plot IDs, compact format-15 persistence, map navigation and distance-bounded routes support the larger layout. Ordinary saves retain format 14. Protocol 26 carries the new snapshot tail. The lighting worker also uses the grid terrain, avoiding shadows from hidden original hills.

This is a vacant fixed-layout zoning benchmark. It has no starting residents or completed buildings and is not a million-resident or million-building performance test. Geometry editing is disabled in this preset. These limits are documented in docs/stress-test-grid.md.

Executed checks: 116 tests across 23 selected suites passed. After adding running CitySimulation route assertions, all 7 StressGridTest tests passed again; game source did not change. Native Main playtest passed automatic installation, all four rings, far plot #1000000, duplicate rejection, save/reload, outdoor industrial lighting, Original city restoration and F6. Python smoke/media CLI checks and git diff --check passed.

Playtest: Linux X11, inherited assigned DISPLAY/XAUTHORITY, Java 25.0.3, Mesa llvmpipe, isolated synthetic profile. 301 frames rendered. Each ring capture waited 44 rendered frames for terrain streaming. The industrial framebuffer check found 269885 lit-grass pixels among 396720 inspected pixels. The production F10 camera-pan clip has 12 decoded H264 frames, duration 4.62 seconds and size 1488880 bytes. Screenshots and the decoded middle frame were visually inspected. Browser playtesting does not apply to this native Java application.

Commands, suite results, environment, source/media hashes, expected/observed workflow results, scope limits and diff audit are in stress-grid-tests.json. Detailed Playtest results are in stress-grid-playtest.txt. The media decoder report is stress-grid-media.json. Eight fresh PNGs and stress-grid-main.mp4 are below 6 MB each. They remain pending controller publication from this feature branch.

The diff has no unrelated file deletions. Normal Point coordinate limits remain intact; an internal factory permits generated grid vertices. Existing save, road, terrain, lighting, market, regional, railway, aviation and protocol behavior passed the selected regressions. No full-suite or CI pass is claimed. Helper failure was resolved with self; no delegated execution was used as proof and no helper remains pending.

Controller must integrate latest master before review. If that changes game source, resume for checks and fresh media on the integrated source. The authoritative post-sync review base has not yet been supplied. Current source hashes and base HEAD are recorded in the validation report.

## Rebase recovery

Resolved dashboard/progress.json in working files. Preserved all 63 unrelated HEAD entries and top-level metadata except the later updatedAt timestamp. Controller must stage and continue the rebase. No Git staging or continuation performed here.

Audit against 3c9162971fecf6afcc2b59cf413c6e91b505522d: no deleted files or unrelated deletions found. Intended line replacements add grid-aware bounds, lazy zoning and road lookup, compact format 15 / protocol 26, lighting terrain propagation and visible-zone rendering. Seven compatibility assertions follow protocol 26; legacy formats remain covered. Unrelated historical evidence matches the review base byte for byte.

Recovery checks: JSON parsing and unrelated-entry equality passed; git diff --check passed. Playtest: not rerun during recovery. Existing test reports and media predate integration; rerun meaningful checks and native workflow/media after controller continuation.

## Current rebase handoff

Resolved dashboard/progress.json in working files. Preserved 64 unrelated HEAD task entries and all top-level metadata except the later timestamp. JSON and equality checks passed; git diff --check passed. No files deleted against review base 4de70d245c8af27fd2c76b86807daa34f0271d17. Intended replacements in the current patch add grid geometry, compact snapshots, protocol version support, camera and rendering bounds, road queries and lighting propagation.

The current replayed patch is the earlier grid implementation. Development-enabling follow-up changes and their development test must remain in the controller recovery sequence; this intermediate source is not ready for review. Controller must stage and continue Git. No staging, continuation or publication performed here. Playtest: not rerun on intermediate recovery source. Earlier tests and media are historical until final-source checks and fresh media complete.
