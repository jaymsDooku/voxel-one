# Shop-stock review correction checkpoint

Meal selection and purchase now require sufficient local portions before moving money/inventory/hunger. Removed clamping; sales deduct exact validated portions. Prior ownership/CITY8/protocol and preservation fixes retained; 91 capital assets unchanged and no base files deleted.

Added low-stock and shared-company simulation regressions plus selecting a one-portion suitable meal when cheaper four portions exceed local stock. Supporting economy/materials/business/agriculture/manufacturing suites: 44 tests passed. Corrected MarketEconomyTest rerun: 13 tests passed. Initial focused run had one assertion failure because it overlooked normal restocking adding a portion; corrected only the assertion and reran affected market suite. No production changes after supporting run. Actual commands, results and source hashes in market-shop-stock-tests.json. Prior 253-test full-build report is historical relative to this fix; socket classes require CI.

All edits preserved. Controller handles staging, commit/publication and independent exact-head review. Developer performed no staging, rebase continuation, commit, push, merge or deployment. No owner answer needed.

## Controller pause — 2026-10-04

Confirmed HEAD 2eef1351beca21bf227fd9c8e187859b2cc5c0bd and clean checkout before this checkpoint update. Implementation retained unchanged. Paused while the controller installs independent-review checkout recovery; no new implementation or tests started. Controller must resume review after recovery.

New running-application playtest requirements remain outstanding: exercise market purchasing, a stock edge case and a regression; record actual environment, steps and observations; capture current-implementation image/video and attach a durable authorized HTTPS artifact URL. No such playtest or media verification was executed in this turn. Existing 13 market and 44 supporting test results are historical recorded evidence, not new execution. Confirm the authorized artifact publication path on resumption without accessing credentials or runner configuration.

## Playtest evidence handoff — 2026-10-04

Inspected existing graphical smoke tooling and documented artifact uploader. Xvfb :129 -screen 0 1280x720x24 -nolisten tcp remained running for three seconds and was terminated normally; this establishes display startup only, not game or market verification. No market playtest or current media captured. The documented deploy/update_progress.py --artifact --publish path writes the development-progress branch, while this queue reserves publication for the runner. Need the controller-authorized media handoff/publication mechanism and resulting durable HTTPS URL before satisfying new evidence requirements. Implementation unchanged; checkpoint edits preserved.

## Current native market validation — 2026-10-05

Implementation remains at 2eef1351beca21bf227fd9c8e187859b2cc5c0bd. Local master 93a32e40aae2720502270f89cedc6d752a881068 is an ancestor; no master-only source differences. Added reproducible deploy/MarketPlaytest.java and deploy/run_market_playtest.py. Actual synthetic CitySimulation advances and BusinessDashboard filter/row clicks passed on Linux Java 25.0.3 with Mesa EGL 1.5. Stocked shop sold four portions, stock 19 became 15, aggregate stock reconciled to 16. Low-stock shop sold zero portions, with two portions after normal restocking. Cheapest suitable meals/material suppliers, partial fills, cash conservation and shared-company stock assertions passed. Current screenshots were captured and visually inspected; all are below 6 MB. This is an automated native component playtest, not a full Main client walkthrough.

Current 13 market tests and 36 supporting tests passed without failures/errors/skips. Combined Maven run exited 143 during AgricultureTest; that suite was rerun separately. See market-current-playtest.json for commands, environment, expected/observed results, failures and media hashes. Xvfb lock-file creation was unavailable in the sandbox; EGL recovered native rendering. No production source changes, publication, commit, push, merge or deployment performed. Media URLs are pending controller publication on this feature branch. Independent review and published-head/media verification remain controller duties.

AgricultureTest separate rerun completed successfully: 8 tests, zero failures/errors/skips, 164.5 seconds. Total current completed tests: 57. Playtest and fresh media ready for controller publication and independent review.

## Exchange labour review correction — 2026-10-05

Preserved base HEAD a5d3b61f8e0cb46193c64296e19265b5144da72a. Local master 93a32e40aae2720502270f89cedc6d752a881068 and reviewed base a3c1513221c227435ee1cbddc2162d16e916b9d4 are both ancestors. No remote fetch, commit, push, merge or deployment performed.

Exchange analyst/support payroll now uses role-specific market quotes instead of fixed transaction wages. The treasury remains the payer. Funded offers, skill-role limits and worker comparisons govern hiring. Paid exchange workers can take better offers; unpaid staff can seek funded private vacancies. Existing farm protections remain. A graduated support worker cannot trigger a fifth office hire.

Current focused checks passed 39 tests, including six new exchange labour regressions. Manufacturing passed 7 tests. Economy/materials/business passed 29 tests. Agriculture is running separately at this checkpoint; no agriculture result claimed yet. The initial supporting batch exited 143 before completing a suite; smaller completed batches replace it. Initial fixture/compile failures are recorded in market-exchange-review-fix.json.

Playtest: python3 deploy/run_market_playtest.py dashboard/evidence target/market-build-cache/m2 passed on Linux amd64, Java 25.0.3, Mesa EGL 1.5, native 1280x720 UI. The actual CitySimulation, inspector clicks and market dashboard workflow ran with synthetic city data. Market wages and treasury debits reconciled; qualified funded staff opened trading; treasury zero closed trading; qualified hiring, private job choice, graduating support capacity and prior goods-purchasing regressions passed. Current market-exchange-paid.png and market-exchange-unfunded.png were captured and visually inspected. Captions are test harness annotations showing live simulation values. This is a native component playtest, not a full Main client walkthrough; browser playtesting does not apply. Media publication and exact-head independent review remain controller duties.

Final correction validation: AgricultureTest passed all 8 tests in 167.5 seconds, with zero failures/errors/skips. Current total: 83 tests passed (39 focused plus 44 supporting). The final native playtest and fresh exchange images passed and were inspected. All edits remain local for runner publication and independent review. No owner answer is needed.
