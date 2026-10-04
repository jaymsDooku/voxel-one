# Shop-stock review correction checkpoint

Meal selection and purchase now require sufficient local portions before moving money/inventory/hunger. Removed clamping; sales deduct exact validated portions. Prior ownership/CITY8/protocol and preservation fixes retained; 91 capital assets unchanged and no base files deleted.

Added low-stock and shared-company simulation regressions plus selecting a one-portion suitable meal when cheaper four portions exceed local stock. Supporting economy/materials/business/agriculture/manufacturing suites: 44 tests passed. Corrected MarketEconomyTest rerun: 13 tests passed. Initial focused run had one assertion failure because it overlooked normal restocking adding a portion; corrected only the assertion and reran affected market suite. No production changes after supporting run. Actual commands, results and source hashes in market-shop-stock-tests.json. Prior 253-test full-build report is historical relative to this fix; socket classes require CI.

All edits preserved. Controller handles staging, commit/publication and independent exact-head review. Developer performed no staging, rebase continuation, commit, push, merge or deployment. No owner answer needed.
