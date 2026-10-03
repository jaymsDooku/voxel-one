Special building permits — ready for independent review

The Special toolbar tab opens a catalog for administration, primary school, secondary school, university and police station permits. Select levels 1–3, ownership category, and an existing citizen/company owner, then click a site. Administration levels are parish hall, town hall and city hall. Buildings reserve a 6 × 9 site including entrance strips; their front faces north and requires an adjacent road. All placement checks run on the authoritative simulation before edits are applied. Existing zones, roads, buildings, owned plots, occupied players, obstructions and bounds are protected. Later roads and zones also respect the reserved site.

Ownership and level persist and are visible through Inspect. These permits create civic structures immediately; education/policing service simulation and construction finance are outside this feature. Service-coloured signs, level lights and furnishings distinguish the buildings.

Compatibility: network protocol advances from 14 to 15 for SPECIAL commands with explicit owner kind/ID fields. Existing command encoding is unchanged. City snapshot layout remains version 6: types 4–18 encode catalog kind/level; only these types use zone as negative ownership kind and stock as owner ID. This is documented in SpecialBuildings and validated by CityFrame. Civic buildings are excluded from private property adoption, automatic business operation/job assignment, and business metrics. Existing saves retain their old records.

Actual checks and limitations are recorded in special-buildings-tests.json. The latest final run passed all 19 selected tests. Other relevant city suites also passed. Broad tests passed 162/163 before fixing the boundary-picking regression, with subsequent passing coverage for that regression. Multiplayer sockets and visual recording remain unverified in this sandbox.

No commits, pushes, PRs, merges or deployments were performed. Runner owns submission and independent review. Maven cache and command output were moved from .build-tools to ignored target/special-build-tools after testing; reruns can use that local repository and temp directory.

Preserved rebase recovery

Resolved CityCommand, CitySimulation and dashboard progress conflicts in working files. Demolition keeps command ID 4; special permits use ID 5. Both simulation handlers and both milestones remain. Full-height overhead validation and the atomic-preservation regression have been restored from the saved revision. Special-building demolition now uses the civic blueprint so service furnishings and level lights are removed. A combined-feature regression checks adjacent demolition/permit wire round trips and actual civic block removal.

Broad Maven suite excluding MultiplayerTest, CityMultiplayerTest and UpdaterTest exited 0: 169 tests passed, zero failures/errors. Commands and sanitized suite totals are in special-buildings-recovery-tests.json. Conflict marker scan and git diff --check pass. Graphical playthrough and socket tests remain unverified.

Controller action required: stage the resolved files and continue the pending rebase. Git still reports the three paths as unmerged because this worker does not stage or continue rebases. The reviewer overhead fix is already included in these resolutions; preserve it if a later replayed revision overlaps. No git reset/abort, commit, push, merge or deployment was performed.

Metric-history review revision

The current preserved rebase applies special buildings onto reviewed base 8c524a18d18d7f685cdabb6a16c7ebbf57fb77f3, which contains metric history. Verified 28 source/integration/test/tool/evidence files byte-for-byte against that base, including MetricHistory, MetricTrends, Main history observation, MayorDashboard and BusinessDashboard Trends controls. The metric progress entry remains unchanged. Resolved the timestamp conflict while retaining both milestones. Full-height placement protection, ownership persistence, demolition ID 4 / special ID 5 and civic demolition remain.

Combined chart/placement/demolition/dashboard verification: 23 tests passed. Eight fresh changing-chart OpenGL captures passed using the existing smoke tool. Two new real engine/OpenGL captures show the actual catalog and a City hall placed through the CityTools click handler and authoritative simulation. Static synthetic fixtures, not live gameplay: special-buildings-menu.png, special-buildings-placement.png and special-buildings-rendering.json. Reproducible tooling: deploy/SpecialBuildingsSmoke.java and deploy/run_special_buildings_smoke.py. Full evidence: special-buildings-metric-integration-tests.json.

Controller must stage resolutions and continue the preserved rebase; this worker did not stage, continue, commit, push, merge, deploy or change sandbox controls. Preserve the concurrent chart feature during any further replay. Graphical live playthrough/F10 and multiplayer sockets remain unverified.

Review revision: overhead preservation

The placement check now spans grade+1 through Terrain.MAX_Y for every cell that level() touches, including entrance strips. Obstructions anywhere in that volume reject the permit before world edits or city mutations. The regression reproduced acceptance on the prior code and passes after the fix. Nine combinations cover three heights (including grade+10 and the world ceiling) and three site positions; each asserts zero edit batches, preservation of all blocks and equality of the complete city snapshot. SpecialBuildingsTest and CityToolsTest pass all 9 tests after this revision. Actual commands and results: special-buildings-overhead-tests.json. Changes remain local for runner submission and independent review.

Overhead revision replay recovery

Resolved the second preserved conflict in working files. Retained the overhead preservation test and the demolition/permit compatibility test, retained full-height validation, and combined checkpoint histories. Command IDs remain demolition=4 and special=5. Civic demolition still uses its matching blueprint. Source changes relative to the preceding verified recovery are comments and formatting only. Targeted verification passed all 13 SpecialBuildingsTest, CityToolsTest and DemolitionTest tests; the earlier broad 169-test passing evidence remains in special-buildings-recovery-tests.json. Current commands/results: special-buildings-replay-recovery-tests.json.

Controller must stage current resolutions and continue the rebase. Unmerged index entries are intentionally left for the controller. No staging, rebase continuation, abort/reset, commit, push, merge or deployment performed by this worker. Graphical playthrough and socket tests remain unverified.

Metric integration final replay checkpoint

Resolved the replay metadata conflicts by retaining both histories and the current metric-preservation progress. Kept the full-height source validation and all chart, placement and demolition integration. Reverified 28 metric-feature files against reviewed base and retained the metric milestone and OpenGL building screenshots. Combined checks again passed all 23 tests; evidence: special-buildings-metric-replay-tests.json. Source replay was comments/formatting only; existing rendering evidence remains applicable.

Controller stages resolutions and continues the preserved rebase. This worker did not stage/continue, abort/reset, commit, push, merge or deploy. Live gameplay/F10 and socket tests remain unverified.
