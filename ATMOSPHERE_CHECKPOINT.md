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
override. The supplied review base is `bcdcab56c6ae1c183621aaa1b4a7e3d0e1f0b3ba`; the full name-status diff was audited against it with no deleted files. After further master integration, repeat the diff audit and rerun checks/media on final source.

## Hosted Mac failure and retry

The exact-source Mac run for `bd8366626d044e0f9ea15863950852c0571e9aad` built the native client, but XCTest failed the sunlight transmittance bound at WorldRulesTests.swift:15. The trusted report has environmentReady=true and clientChecked=false. The only returned image is the fresh simulator home screen. It was inspected and copied as explicitly labeled environment evidence, not gameplay.

Fixed a double-negated exponent: the old helper computed exp(-a), while sunlight and view radiance passed negative optical depth. The new attenuation(opticalDepth) helper takes positive depth, and both callers use that contract. Native tests now check exp(0), exp(-1), exp(-2) and Earth RGB transmission ordering. Linux iOS harness checks passed 53 tests; the new Swift checks have not yet run. Request another scoped exact-source remote_ios_test. See dashboard/evidence/atmosphere-ios-retry.json. Full implementation work listed above remains open.

## Hosted Mac success and current continuation

Inspected trusted run https://github.com/jaymsDooku/voxel-one/actions/runs/37964789992
for d4b9f4e5f19d52bf978df3817d91016b81d72943. All six real native client XCTest cases
passed on macOS 15.7.9, Xcode 16.4, iOS 18.5, iPhone SE (3rd generation) Simulator.
Verified media hashes and inspected the actual client image. Copied sanitized images as
atmosphere-ios-client-fallback.png and atmosphere-ios-passed-preflight.png. The client
image shows fallback gameplay, not enabled atmospheric rendering. No physical-device
performance result is claimed. See atmosphere-ios-passed.json.

Current local source adds an original compact isotropic multiple-scattering closure,
packed transmittance/multiple atlas, and amortized row-budget builds. Complete initialized
output stays active while resources rebuild. Physical-parameter keys exclude coordinates,
sun and altitude; view-only changes reuse static textures. The GL playtest passed with
multiple-table comparison (six texels / 18 RGB components; normalized error 0.0342457
against the same 16-direction closure at 256 view/sun samples, tolerance 0.6), static reuse,
rapid cancellation, retained output, row limits and deleted textures. This comparison does
not establish full multiple-scattering numerical accuracy. Main captures are refreshed.
Java AtmosphereConfigTest (4) and AtmosphereReferenceTest (6) passed, including vacuum,
finite multiple radiance and albedo response. Linux mocked iOS checks passed 53 tests.
The delegate closure-design request failed transiently and was resolved self; all delegates
are closed. Implementation and checks were independently performed locally.

Native Sky opt-in now persists as a local UserDefaults choice. Missing preference remains
false. The normal-control native test enables/disables it, enables it again, saves a
horizon-facing pose, cold restarts and checks the accessible On value and game controls.
This Swift change has NOT run on a Mac yet. Request remote_ios_test against the controller's
published exact source. Require all six native tests and actual enabled client media.

Full acceptance still requires sunlight/irradiance agreement, complete water/cloud and
transparent composition, room/fractional/tiny-model/HUD/held-item/player camera regressions,
transition recording, higher-angular-resolution numerical comparisons, hardware performance
budgets, full suite and final master integration/source audit. Do not mark this work ready.
The current review-base name-status diff again contains no deleted files; git diff --check
passed. The controller retains all Git/publication responsibility.

The existing engine RenderingSmoke regression reran on this source with the assigned display
and isolated synthetic profile; exit 0. Refreshed reports/media copied to
dashboard/evidence/atmosphere-regression-*. This is partial regression coverage, not full acceptance.

## Native enabled evidence and direct sunlight phase

Verified trusted report and both media hashes for head 05977d7d70f960c68fc503b7760280ab82a98c0d,
run https://github.com/jaymsDooku/voxel-one/actions/runs/37967934841. All six native client
tests passed, including saved Sky opt-in/restart. Inspected client.png shows actual enabled
blue sky and clear touch HUD, plus a dark virtual-ground band above the local terrain.
Copied fresh media as atmosphere-ios-enabled.png and atmosphere-ios-enabled-preflight.png;
see atmosphere-ios-enabled-report.json. These are simulator results, not physical iPhone.

New local source shades the native virtual ground with profile albedo, solar transmission,
planet shadow and view attenuation. Native numerical tests check positive daylight ground,
albedo response and zero night-side ground. This new Swift code is unexecuted on Mac.
Request another exact-source remote_ios_test with all six client cases and actual client media.

Fixed sandbox time now honors configured hour. Mobile snapshots share that solar formula.
Detailed/distant terrain direct light now uses profile solar irradiance and RGB transmission
without legacy clock attenuation. Existing voxel/local shadow and skylight visibility remain.
Held/unbound previews retain their existing shader lighting path. Local reflection capture
uses the main atmosphere coordinate anchor independently of its own camera. Profile irradiance
18 is linear HDR, not 0.18; brightness changes are intentional physical-profile agreement.
Legacy sky/indirect GI, clouds and complete water composition remain unfinished.

Scoped Java run passed 41 tests (Daylight, atmosphere, restart/latejoin, migration,
rendering algorithms, lighting/occlusion). Linux mocked iOS harness passed 53 tests.
Direct-light reviewer helper findings were independently checked and accepted; no delegated
execution claimed. All helper tasks are resolved and concurrency is zero.

The added mobile snapshot solar-clock parity test passed. DaylightTest now has 3 passing
cases; 42 unique scoped Java cases passed across the two commands. Main GL workflow reran
on the direct-light source and passed fixed-sandbox noon/midnight surface checks and all
prior numerical/lifecycle cases. Fresh orthographic image inspected. See
dashboard/evidence/atmosphere-direct-validation.json.

Existing real engine rendering regression also reran on the latest direct-light source:
exit 0, GL_NO_ERROR. Sanitized regression reports/media refreshed. git diff --check passed;
no deleted files against the supplied review base. Full final diff audit remains before review.
Next controller action: remote_ios_test for new native ground background/tests; preserve
in-progress status and continue the listed remaining implementation after that result.

## Sky/GI/composition continuation

Trusted Mac run 37969915051 for 5f6e84ddc1cef572a60f3502f59ca1015f746983 passed all six
native cases. Verified receipt and media hashes; inspected enabled client image confirms
virtual ground closes the dark band. Fresh images/report copied as atmosphere-ios-ground*.
No physical-device result. Native code is unchanged since that passed source.

New desktop mode separates local RGB/LEDs from voxel sky visibility. Legacy bake overloads
stay compatible. Pipeline bakes fixed visibility independent of clock, removes fixed blue
sky/sun-bounce and normal-solid glow, and uses shared environment diffuse approximation.
Mode participates in asynchronous cache acceptance. Exact fractional-leaf visibility is
also available through sampleLighting; sample still returns legacy RGB. Material aerial
samples now use voxel visibility/local sun shadows. A real sealed-room test found duplicate
post height fog (mean 11.03); that legacy layer is suppressed where atmosphere is composed.
Rerun passed closed noon mean 0, open window 77.71, sealed LED night 167.59. New GPU shader
shares exact irradiance lookup with water. Orthographic aerial paths start per pixel.
Water uses full scene capture with physical reflector-to-scene air and foreground air on
reflection. Refraction reuses opaque HDR and preserves foreground air instead of adding fog.
Cloud illumination/foreground medium now use the same physical profile. Virtual ground is
also in the desktop sky table/environment. Combined GL workflow passed with clouds/water.

Scoped Java run passed 44 tests after two new sky/LED tests. Their first attempt had an
incorrect test assumption that sample returned alpha; fixed by explicit sampleLighting.
Shared shader extraction first missed uAmbient in water; fixed before successful GL run.
Developer helper failed transiently and was resolved self; all delegate tasks closed.

Production Main harness added actual F5/F6/F10, synthetic clock and 120 km diagnostic view.
First attempt hit classes removed during concurrent compilation; runner now copies stable
compiled classes before launch. Second attempt lacked capture/focus at F5; harness now
waits for normal menu/cursor controls and actual key callbacks. A rerun is required.
Timestamp counters added inside FrameBudget for table/sky cost, without nested elapsed
queries or blocking reads. 1080p recording/readback-free software benchmark is prepared
but unexecuted. Full Maven suite is running under a 600-second timeout; no full pass claimed.

Remaining: production controls/media, full suite completion, numerical angular comparisons,
fractional/tiny/glass/held/HUD full regression coverage, measured 1080p software budget and
hardware target evidence, final base/source audit and controller master integration.

## Current continuation: native sunlight and production altitude

Observed Mac run 37969915051 (source 5f6e84ddc1cef572a60f3502f59ca1015f746983)
passed six client cases; inspected actual enabled client and copied ground report/media.
New native code is not covered by that run: shared RGB solar irradiance/tangent basis,
512x512 directional shadows, angular-radius solar geometry with depth occlusion,
no duplicate disc in the coarse cube or fog colour, planetary-interior path guards.
Native profile tests add zero-irradiance, rotated-frame and below-ground checks.
Need exact-source Mac compilation and actual simulator playtest. Default remains off.

90 cases in 19 selected Java suites passed: config/reference/sky lighting, Daylight,
legacy lighting/occlusion, server late join, renderer algorithms and all changed city
save/protocol compatibility suites. Commands recorded in continuation report. New
sky visibility test covers supported 1/16 and 1/32 opaque/glass detail. Initial test
missed an import, then tried unsupported detail resolution 64; both corrected.
53 Linux iOS harness contract tests passed; bash -n ios/check-simulator.sh passed.
Full Maven suite timed out after 600 seconds; no full-suite pass. First 1080p
benchmark timed out after 900 seconds; no measured report or hardware target claim.
A shorter benchmark now saves each completed mode; it has not run yet.

Production altitude fixture first mutated a copy, then collision returned the player
from unloaded chunks. It now changes actual player position and loads 27 empty
diagnostic chunks around the camera. Production Main passed 73 frames: actual
W/F5/F6/F10, three cameras, night, asserted 120 km altitude/return, resize and menu.
Fresh space/overview images inspected; planet-interior sky band gone. Overview
after night/space is too bright; exposure transition still needs work. F10 MP4
50.07 seconds, 41 frames, 1121923 bytes; decoded actual frames for inspection.
Higher-angle CPU report compares 16/32/64/128 directions with 256; maximum error
for existing 16-direction closure is 98.16 percent, especially at 30 km sunset.
128 directions stay within 7.61 percent for these six samples. Improve the GPU
angular integration and compare its updated atlas before accepting numerical parity.

Developer helper failed and was resolved self after independent source inspection.
All helper tasks are closed; concurrency zero. No Git publication performed.
Remaining acceptance: angular fix, exposure transitions/nonblocking exposure path,
final water/cloud/transparency/fractional/tiny rendered regressions, budget metrics,
physical reference GPU/device limits, full relevant source audit, controller master
integration and fresh final-source checks/media; no ready/release claim.

Current-source GL atmosphere workflow and existing rendering regression both completed
with exit 0. Refreshed sanitized regression media/reports. Next controller action:
remote_ios_test for new native sunlight/shadow/disc code. Remain in progress; after
Mac result continue angular integration and exposure gaps, budgets and final checks.


## Continuation: quality and native visibility
Observed Mac run 37978076544 passed six cases at f252571ae22c70fbbf5642526c46a3af8b158f08. Client screenshot inspected and copied with bounded report to dashboard/evidence/atmosphere-ios-solar*.
Current scoped Maven command passed 29 cases with zero failures/errors. Linux iOS mocks passed 53 cases; bash syntax, Python compile and git diff --check passed. No deleted tracked paths against bcdcab56c6ae1c183621aaa1b4a7e3d0e1f0b3ba; no unmerged index.
Desktop changes: 128-direction closure (32/64/128 path samples), one costly row per update; PBO/fence exposure with no blocking readback; reflection-specific ray origins; profile precision/origin bounds. Prior current-turn GL closure/glass/fine/tiny run passed before final reflection changes. Main passed 81 frames; F10 actual 41.128 seconds, 41 frames, 1145703 bytes. Decoded frames 1/4 inspected; HUD/held item clear and space stars visible.
1080p all-quality software benchmark timed out at 900 seconds after Low/Medium; partial EMA results retained in atmosphere-performance.txt. HIGH unexecuted. Per-quality flag added. Do not claim hardware target or full-suite pass.
New skyReferenceCheck first run aborted exit -6, free(): corrupted unsorted chunks. Texture dimension guards added; rerun in progress. Preserve failure; do not report this check passed without inspecting rerun.
New native source needs exact-source Mac: five-ray whole-cell roof/window/glass visibility, gated diffuse emission with global ambient disabled in preview, seventh unit case, profile precision bounds, normal-control Sun framing. Mobile remains opt-in; native fine/model geometry and LED transport parity gaps remain. check-results.py now requires seven cases. No physical iPhone budget claimed.
Next: finish GL rerun and reflection regression; focused HIGH benchmark; native Mac seven-case/media inspection; fresh final Main/media; controller master sync and full proposed diff audit; independent review. No Git/publication performed by developer.

Guarded current-source GL rerun completed exit 0: sky max noon 0.0296122872, dawn 0.0070034346, dusk 0.0071296082, night 0; tolerance 0.25. All scene/quality/lifecycle cases passed. First native abort root cause not proven. RenderingSmoke regression now running.

RenderingSmoke current-source regression passed exit 0; probe occluder shadows 1506 pixels, clustered LED 3448, LED edits 4096; fresh six faces, water/TAA/exposure/upscaling/resize/streaming Hi-Z all pass. Reports and water image copied to atmosphere-current-* evidence. HIGH performance and final master/source checks remain. Request next seven-case native Mac check.

Mac run 37984720498 at 2c5a248b102ee7a783cbea91e2fbbf96b7dabbb7 failed Sun-look pose in native workflow; bounded diagnostics inspected. App built; clientChecked=false. Preflight copied as environment-only. Revised normal-drag closed loop 16 attempts/minimum12points/HUD update wait5s; pose tolerance unchanged0.03rad. 53 Linux mocks and syntax/compile checks pass. Corrected Mac check required. Focused HIGH performance currently running.

Focused HIGH benchmark completed exit0:1920x1080 recording/readback off, llvmpipe; airless/enabled fullGPUEMA2792.8525/4303.0312ms; CPU2329.9158694/4942.7330031ms; LUT1384448bytes. Report atmosphere-performance-high.txt. No hardwaretargetclaim. Native gesture correction additionally checks pose after final16thdrag. All delegates resolved self; no live helpers. Next action exact-source Mac retry; then inspect client/Sun media, finish final-source/full-suite/master audit.

Mac37987207495 at fa6cbf2a45e1e5e7d7eb5091bd116b508227bb83 failed Look gesture HUD-update wait; app built, clientCheckedfalse. Inspected bounded diagnostics and preflight; environment-only image copied. World gestures moved from SCNView sibling to parent; delegate excludes controls/scroll ancestors and menu/paused. Normal look/planning over HUD labels now receives touches; buttons retain input. Pose check unchanged0.03rad; failure prints synthetic before/after pose. 53 Linux mocks pass; syntax/compile/diff checks pass. Exact-source seven-case Mac needed before any native pass claim.
