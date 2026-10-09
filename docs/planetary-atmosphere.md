# Planetary atmosphere prototype

This feature branch contains the configuration contract, double-precision reference integrator,
GL 3.3 transmittance/sky-view prototype and an opt-in native iOS adapter. It is not yet ready
for release. The remaining work is listed below; current screenshots show the prototype.

All authoritative lengths are metres. Optical coefficients are inverse metres. One default
block is one metre. The Earth approximation uses a 6,360,000 m radius, a 100,000 m shell,
8,000 m molecular and 1,200 m aerosol scale heights. Ozone-like absorption has a triangular
profile. RGB irradiance and scattering are artistic approximations, not spectral photometry,
climate or breathable-air simulation. Disabled/airless profiles have unit transmittance and
zero atmospheric scattering. Clouds remain a separate medium.

`AtmosphereConfig` is immutable and validates all scalar/vector values, density scales,
extinction >= scattering, phase anisotropy and albedo ranges. Earth, thin, hazy and airless
presets are available. Games may construct custom profiles. A surface anchor at
`origin + (0, seaLevel, 0)` maps to the local tangent plane. World X/Z become orthonormal
east/north directions around `up`; the virtual planet centre is one radius below that
anchor. CPU coordinates subtract the origin before metre conversion and use doubles.
The GPU uses kilometres and matching inverse-kilometre coefficients. Sky rays use inverse
projection and inverse view rotation separately so high-altitude camera translations do
not quantize ray directions. Gameplay observers below their reference surface use a
sky-only minimum radius of radius + 1 m; local voxel geometry still controls visibility.
The CPU diagnostic/reference integrator clips the actual planetary surface.

## Configuration and compatibility

New server worlds accept `--atmosphere earth|thin|hazy|airless|off`,
`--atmosphere-haze MULTIPLIER` (0..20), and `--atmosphere-thickness MULTIPLIER` (0.1..5).
Haze multiplies the chosen preset's aerosols; thickness scales its shell and density
heights. Saved world settings win on restart. Desktop quality is local:
`-Dvoxel.atmosphereQuality=LOW|MEDIUM|HIGH`.

City snapshot version 17 adds atmosphere payload version 1 after the existing clock.
`GameConfig.write(out)` and `read(in)` retain the original 18-byte clock layout.
Only their versioned overloads add/read the fixed 277-byte atmosphere payload.
City saves use magic `0x43495441`; old versions 1..16 retain their readers and receive
the Earth default without changing their clock. Wire protocol 28 carries snapshot 17;
older unsupported peers fail the existing version handshake instead of consuming a
misaligned stream. Legacy supported client connections retain their versioned readers.
Mobile HTTP snapshot schema 1 adds optional `atmosphere` and `sun` fields. The profile
is `{version:1, enabled:BOOLEAN, values:[34 DOUBLES]}` in the same order as binary
encoding. Swift validates it; absent legacy fields receive the same Earth profile.

## Current rendering

Transmittance uses raster integration with 64 samples and denser angular coordinates
near the horizon. A compact isotropic multiple-scattering closure integrates 128 equal-solid-angle directions,
32/64/128 view samples for Low/Medium/High and diffuse ground reflection. Its feedback is capped at 0.95. The square
table is packed below transmittance in the same atlas, preserving the 16 texture-unit limit.
Sky view uses bounded scattering with this closure, with a larger horizon
sample count and a bounded exterior-camera path. All LUT textures use RGBA16F.
Quality sizes and peak LUT allocation (two complete sets plus a temporary multiple table):

| Quality | Transmittance | Multiple | Sky | Samples | Peak LUT bytes |
| --- | --- | --- | --- | --- | --- |
| Low | 96x32 | 8x8 | 96x48 | 16 | 135,680 |
| Medium | 256x64 | 16x16 | 192x108 | 32 | 661,504 |
| High | 384x96 | 32x32 | 256x144 | 48 | 1,384,448 |

Static transmittance/multiple tables change only with physical parameters/quality.
Coordinate-origin, sun and altitude changes reuse them. Low submits up to four rows every
two frames; Medium eight rows per frame; High sixteen rows per frame. Multiple-scattering
updates submit only one costly angular row per update. Initial Low output
is initialized synchronously before first use. Later builds keep initialized output and
swap only complete bundles. Rapid profile changes cancel unpublished resources.
View tables change with sun or
altitude thresholds. New textures replace old complete textures; no blocking GPU readback occurs
in production. Auto exposure uses three 16-byte pixel-pack buffers and zero-timeout fences;
it reads only completed 1x1 mip copies. Adaptation uses elapsed time and a 0.02 minimum
exposure so bright daylight remains readable after night/space transitions. The reflection environment samples the same sky table. Detailed and
distant surface shaders use the same bounded atmospheric integration in linear HDR.
Their direct sunlight uses the profile's linear top-of-shell RGB irradiance times shared
solar transmittance, planetary shadow, material BRDF and existing local occlusion. The
legacy clock strength is not multiplied into that direct term. Distant diffuse uses the
Lambertian 1/pi factor. The fixed sandbox clock now uses its configured hour, like cycling
worlds; mobile snapshots use the same solar-direction formula. Local-probe cameras use
the same planet anchor rather than interpreting probe-relative positions as player-relative.
The pipeline bake separates local RGB from exact outdoor visibility. It removes fixed blue
sky RGB, fixed-direction sun-bounce seeds and non-emissive solid glow. LEDs and fractional
visibility remain in voxel transport. A blurred shared environment approximation provides
sky diffuse colour, gated by the existing visibility. Without accepted visibility it stays
dark. Local probe reflections keep their own light independent of outdoor visibility.
Scene aerial samples use the same voxel sky visibility and local solar shadow maps. The
old post-pass height medium is suppressed in this path, so light shafts/air compose once.
Orthographic aerial paths use per-pixel origins on the camera plane; local overview strength
is `-Dvoxel.overviewHaze=0..1`, default 1, and does not change the world profile.
Cloud illumination uses shared solar transmittance and sampled sky, with foreground air
retained once in the cloud blend. Water captures use the same voxel scene shader and shade
the physical surface-to-reflector leg. The water surface applies the foreground air to the
reflected leg; refraction reuses the opaque HDR scene and preserves foreground scattering
rather than applying a second fog layer. Native low-quality fog and underwater refraction
remain approximations, not complete participating-water transport.
Held objects skip it. Isometric sky rays reconstruct per-pixel origins and parallel
directions. Profile/table changes reset temporal history.

iOS has a bounded 16x16 cube per face, precomputed by its CPU reference approximation,
and a documented SceneKit built-in fog stage. `Sky` enables/disables this native preview.
Its local choice persists across app restarts; a missing choice stays off pending
physical-device performance measurements. UIKit HUD is
outside the fog pass. This path is not desktop aerial-perspective parity.

## Evidence and remaining work

`python3 deploy/run_atmosphere_playtest.py --display "$DISPLAY"` runs the actual engine
renderer in a synthetic profile on the assigned display. It fixes exposure, records
noon/dawn/dusk/night, 8 km/90 km/120 km altitude, airless and orthographic captures,
exercises quality changes and compares 16 GPU transmittance texels with a 4096-sample
double reference. See `dashboard/evidence/atmosphere-playtest.txt`. Software rendering
checks correctness; it does not establish the 1080p desktop GPU target or an iPhone budget.

Current closure comparisons cover nine GPU texels against a 256-direction double
reference, including sunset near 28 km. The maximum normalized error was 0.1298811
with tolerance 0.25 and denominator floor 0.01. Rendered glass admits light; the
1/16 opaque wall and 1/32 model wall remain dark. Fresh production controls passed 79
frames and produced a 51.92-second, 41-frame, 1,136,385-byte F10 clip. The asynchronous exposure path keeps
the inspected overview readable.

Mac run 37990783149 passed seven native cases for
ac7f2ce230cc85c1f1e4cf69f8e73f2eed762749. Exact-source receipt and media hashes
were checked. The inspected client image shows a solar disc against a blue sky,
with a clear HUD. Offline/online touch workflows, profile/save compatibility,
roof/window/glass visibility and preview restart passed. Native
preview remains opt-in. Its bounded five-ray whole-cell visibility approximates
open windows and glass; fractional/tiny geometry and local LED transport remain
native parity gaps. Global ambient/environment intensity is zero in preview so
unmasked outdoor diffuse light cannot enter a closed room.

Custom profiles require height and density widths at least max(1 m, radius*1e-6),
and local-origin components within +/-1e12 blocks. This rejects scales below the
float shader precision contract. Reflection captures use their own ray origin
while retaining the stable world-to-planet anchor.

Still required before review/release:

- Current-source sky/reference and reflection regressions pass; collect fresh final-source media after synchronization.
- Low/Medium measurements are in atmosphere-performance.txt; the all-quality run
  timed out at 900 seconds. Focused High completed both modes with exit 0; see
  atmosphere-performance-high.txt (full GPU EMA 2792.8525 ms airless, 4303.0312 ms enabled;
  peak LUT allocation 1,384,448 bytes).
  GPU values are query EMAs after 15 sampled steady frames and can retain rebuild
  history. They do not establish the <=2 ms hardware target.
- A declared reference GPU and physical-device performance budget. Simulator results
  cannot establish an iPhone budget; mobile remains disabled by default.
- Full Java suite now passes (see atmosphere-full-java-tests.json). Controller master
  synchronization, any changed-source checks and independent review remain mandatory.

## References

The current code is original bounded RGB scattering with an isotropic multiple-scattering
closure; it does not claim to implement
the full Hillaire or Bruneton algorithms and copies no reference code. The intended compact
table design is informed by [Hillaire's paper](https://onlinelibrary.wiley.com/doi/10.1111/cgf.14050)
and [author implementation](https://github.com/sebh/UnrealEngineSkyAtmosphere).
[Bruneton's reference and tests](https://ebruneton.github.io/precomputed_atmospheric_scattering/index.html)
describe dimensional consistency and numerical reference validation. Any later adapted
source must retain its attribution and license.

Spherical diagnostics use the actual sky shader and its virtual diffuse ground,
with tangent cameras at 95/100/105/120/1,000 km. Inspected images show a curved
blue atmospheric limb. The 95–105 km mean brightness difference is 2.05565625/255
(tolerance20/255); this is not pixelwise equivalence. Airless renders without the
blue rim. No gameplay terrain, collision or atmospheric voxels are added. Run
`python3 deploy/run_atmosphere_playtest.py --display "$DISPLAY" --limb`.

The LUT byte counts are declared logical allocations, excluding driver padding,
programs, the reflection environment (786,420 logical RGB16F bytes including mips),
48 exposure-buffer bytes and timestamp-query metadata. They are not total VRAM measurements.

### Exterior sunlight review fix

Sun rays from outside the atmospheric shell now integrate extinction over the clipped shell interval with 128 midpoint samples. Vacuum intervals contribute no extinction. Planet shadow is checked before both the LUT and fallback paths. The top `min(1 km, 2% of shell height)` blends smoothly from the interior LUT to this bounded path to avoid a LUT seam during shell entry. This fallback adds work near and outside the shell; it does not establish the desktop hardware performance target.

The executable `--sunlight` playtest probes the production common shader in a 1×1 floating-point framebuffer against the double-precision 4096-sample CPU integrator. It covers 99,999/100,000/100,001 m, the reported 120 km dense crossing, grazing hit/miss, outward/missing shell rays, planet shadow and airless. Readback is for correctness only. See `dashboard/evidence/atmosphere-exterior-sunlight.txt` for actual RGB values and tolerance.

### Outdoor coverage beyond local GI

The 96×128×96 local light volume remains bounded. Detailed geometry outside it now samples a separate sparse vertical sky-visibility lookup built from loaded voxel leaves and unloaded edited roofs. The lookup retains exact fractional X/Z footprints and expands repeated tiny voxel models. Touching rectangles merge only when their height and transmission match; actual gaps remain. Opaque roofs block outdoor light. Glass and water use a low-cost 0.7 transmission approximation outside local GI. Inside the volume, existing fractional transport, windows and local LEDs remain active. Roof visibility above the volume seeds its top boundary rather than assuming open sky.

The lookup shares the existing integer 3D fine-root atlas, requiring no extra texture units or compute shaders. It rebuilds on scene geometry changes and preserves the fine-light buffer addressing. Storage is capped at 8,000,000 integers (32 MB before atlas padding); traversal is bounded to 32 binary-search steps and 128 segments per column. More complex columns conservatively block below their highest occluder and remain open above it. Unknown columns are conservative. Local RGB light transport remains limited to the local GI volume; this coverage path supplies outdoor visibility, not global LED propagation or global bounced GI.

`python3 deploy/run_atmosphere_playtest.py --display "$DISPLAY" --coverage` exercises actual outdoor stone patches across the volume boundary, elevated terrain, an elevated sealed room and a full synthetic overview. It also probes the common irradiance shader at both sides of the boundary. See `dashboard/evidence/atmosphere-coverage-playtest.txt` and the matching fresh images. Software-renderer correctness does not validate the 2 ms reference GPU target.

### Model roofs while streaming

Loaded chunks and unloaded authoritative edits use the same model leaf expansion for sky visibility. A partial model roof blocks only its occupied footprint. Its gaps remain open when the roof chunk unloads and reloads. Missing models and fractional model cells retain the existing conservative fallback. Streaming uses `World.unloadChunk`, which releases resident buffers and preserves edits for replay.

`WorldSkyVisibilityTest.modelRoofGapsSurviveUnloadAndReload` checks a quarter roof in all three residency states. `deploy/run_atmosphere_playtest.py --display "$DISPLAY" --coverage` also checks the production GPU visibility at the gap `(70.8,170,45.8)`, beneath occupied geometry, and above the roof. The tolerance is 0.001. These are software-renderer correctness checks, not hardware performance measurements.
