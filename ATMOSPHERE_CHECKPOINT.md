# Atmosphere continuation checkpoint

Feature branch: `feature/queue-35336561653866652d643166312d343564312d623236382d346161333036333437363761`.
Baseline: `bcdcab56c6ae1c183621aaa1b4a7e3d0e1f0b3ba`. No commit, push, PR, merge or deployment performed.
Only this assigned worktree was edited. No pending rebase/unmerged index was present.

Implemented a foundation and desktop prototype, not the full release acceptance.
See `docs/planetary-atmosphere.md` for the units, versioned encoding, quality dimensions,
renderer contracts, references and remaining work. Two developer helper tasks were closed
with `self`; one was taken over to avoid blocking configuration integration, one failed
transiently. All applied code and checks were inspected/executed locally.

Current checked source includes:

- Immutable metre-based profile/presets/tangent transform and a bounded double CPU reference.
- Versioned city snapshot/save 17, wire protocol 28, legacy clock/default migration and
  bounded mobile JSON profile plus server sun direction.
- GL 3.3 raster transmittance and sky-view tables, shared environment cube, altitude-stable
  ray reconstruction and one detailed/distant single-scattering haze contract.
- Native profile decoding/CPU cube preview/SceneKit low fog adapter and normal `Sky` control.
  Native preview is off by default pending a physical-device budget.
- Native model and normal-control tests, added to the exact-source simulator receipt checks.

Executed local checks are recorded in `dashboard/evidence/atmosphere-validation.json`.
The last scoped Java run passed 38 tests. Linux iOS harness tests passed 53 tests.
The actual GL sky workflow passed on llvmpipe with fixed exposure and synthetic profiles.
Its 16 sampled transmittance texels had maximum RGB absolute error about 0.000952 against
4096-sample CPU reference integration. Existing engine renderer regression also passed.
Fresh prototype media are in `dashboard/evidence/atmosphere-*.png` and must be published
by the controller. These captures are not old/stock media. No runtime logs are evidence.

Next controller action: scoped `remote_ios_test` against this exact published source.
Run client mode through `ios/check-simulator.sh`; require all six native XCTest cases,
including `testAtmosphereProfileMigrationAndNumericalBounds` and
`testPlanetAtmosphereNativeToggleAndLegacyWorld`. Return the trusted report, narrow compiler
diagnostics on failure, and actual client screenshots/video. A simulator preflight alone
does not validate this client. Inspect returned media before recording native success.

Continue implementation after that handoff. Required unfinished work includes multiple
scattering, amortized update scheduling/fallback/resource tests, sunlight/irradiance
agreement without legacy attenuation, complete water/cloud/transparency composition,
camera/room/fractional/tiny-model/HUD/held-item regressions, transition recording, full suite
and hardware performance budgets. Existing GL scenes cover only part of those workflows.
The broader Maven run was interrupted after a stall; no full-suite pass or release package
is claimed. New save-header assertions retain the old fixture writers and old-format readers.

No deleted files appear in the current diff. Intended code removals replace the old CPU
analytic environment cube and two different fog formulas and remove isometric's flat-sky
override. The controller has not yet supplied the synchronized review base. After integrating
master, audit the whole diff, preserve merged features and rerun checks/media on final source.
