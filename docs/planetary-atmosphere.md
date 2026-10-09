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
near the horizon. A compact isotropic multiple-scattering closure integrates 16 equal-solid-angle directions,
32 view samples and diffuse ground reflection. Its feedback is capped at 0.95. The square
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
two frames; Medium eight rows per frame; High sixteen rows per frame. Initial Low output
is initialized synchronously before first use. Later builds keep initialized output and
swap only complete bundles. Rapid profile changes cancel unpublished resources.
View tables change with sun or
altitude thresholds. New textures replace old complete textures; no GPU readback occurs
in production. The reflection environment samples the same sky table. Detailed and
distant surface shaders use the same bounded atmospheric integration in linear HDR.
Their direct sunlight uses the profile's linear top-of-shell RGB irradiance times shared
solar transmittance, planetary shadow, material BRDF and existing local occlusion. The
legacy clock strength is not multiplied into that direct term. Distant diffuse uses the
Lambertian 1/pi factor. The fixed sandbox clock now uses its configured hour, like cycling
worlds; mobile snapshots use the same solar-direction formula. Local-probe cameras use
the same planet anchor rather than interpreting probe-relative positions as player-relative.
Legacy indirect GI and cloud lighting still need profile agreement.
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

Still required before review/release:

- Higher-angular-resolution multiple-scattering comparisons and measured fallback/update costs.
- Full sunlight/irradiance agreement without duplicate legacy daylight attenuation.
- Water, local reflection, cloud depth and transparent-layer integration audit.
- Sealed-room/window/glass/fractional/tiny-model/LED regression scenes and all player
  cameras, full-world isometric fit, resize/context lifetime and temporal transitions.
- High-sample radiance comparisons at sunset/horizon and diagnostic spherical limb scenes.
- Recording-disabled CPU/GPU/frame/rebuild measurements and a declared hardware budget.
- Hosted Mac run 37964789992 passed all six client tests for d4b9f4e5f19d52bf978df3817d91016b81d72943.
  Run 37967934841 then passed all six tests for 05977d7d70f960c68fc503b7760280ab82a98c0d,
  including saved opt-in/restart. Its inspected enabled image has a blue sky and clear HUD,
  but a dark virtual-ground band above the local patch. The native cube now shades the
  virtual diffuse ground with profile albedo, transmitted sunlight and view transmission.
  This background adds no collision or voxels. Its visual fix needs another exact-source run.
  Physical-device performance/signing
  evidence remains unavailable. Simulator preflight cannot replace a client playtest.
- Full existing test suite, controller master synchronization, final diff audit against
  the supplied review base, fresh final-source media and independent review.

## References

The current code is original bounded RGB scattering with an isotropic multiple-scattering
closure; it does not claim to implement
the full Hillaire or Bruneton algorithms and copies no reference code. The intended compact
table design is informed by [Hillaire's paper](https://onlinelibrary.wiley.com/doi/10.1111/cgf.14050)
and [author implementation](https://github.com/sebh/UnrealEngineSkyAtmosphere).
[Bruneton's reference and tests](https://ebruneton.github.io/precomputed_atmospheric_scattering/index.html)
describe dimensional consistency and numerical reference validation. Any later adapted
source must retain its attribution and license.
