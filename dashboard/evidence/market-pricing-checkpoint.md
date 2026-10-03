# Capital preservation recovery checkpoint

Base: `a3c1513221c227435ee1cbddc2162d16e916b9d4`. Progress conflict resolved retaining every base record plus market entry. 91 ownership/capital source/test/tool/evidence assets match base; no base deletions. Ownership, stock exchange, capital commands/dashboards, protocol 18 and CITY8 serialization retained. Earlier concurrent features and review corrections retained.

Fixed private job review displacing civic exchange staff; existing normal-commuting regression now passes. CITY7 regression explicitly writes version 7. Added base-generated CITY8 fixture with synthetic ownership, open order and exchange building, generated using isolated reviewed-base CitySimulation/CityEconomy/CityMaterials and unchanged base dependencies/helpers. New regression loads it, retains capital/buildings and re-saves CITY8 unchanged. Fixture provenance and generator source recorded in market-capital-combined-tests.json and BaseCity8Fixture.java.

Targeted capital/market checks passed 44 tests after correcting one commuting failure. Fresh full combined offline Maven verify passed: 253 tests, zero failures/errors/skips; packages built. Command, suites, preservation and source/build-input hashes recorded in market-capital-combined-tests.json. Socket-dependent tests require CI. Restored visual evidence remains historical.

Controller must stage working-tree resolutions/evidence/fixture, continue preserved rebase and submit resulting exact head for independent review. No developer staging, continuation, commit, push, merge or deployment. All edits retained. If later replay changes source/build inputs, rerun affected checks.
