# Orbital entry adapter

The default Canvas path and opt-in WebGL2 preview share a version 1 planet profile,
terrain generator version 1, SI atmosphere contract, fixed sun/moon directions,
cloud seed and landing anchor. Existing seed galleries use the unchanged Planet
terrain generator. No art assets are downloaded.

The Game owns the 12-second crash/descent, 1.2-second blackout and 3-second waking
states. Fine terrain arriving late can hold the approach for at most 1 second, with coarse parent data retained. `skipDescent()` and normal descent call `completeEntry()` once. Combat
coordinates and hitboxes stay in the original 480 × 570 coordinate space. The
renderer samples the story clock; pause, blur and hidden tabs freeze the camera,
clouds and exposure. ALT displays altitude in metres above the landing reference.
Reduced motion removes impact shake and heat streaks. Touch pause and skip remain
available. Restart cancels worker generation and rejects old epochs.

`planet-profile.mjs` freezes the completed terrain configuration at entry. Later
lab generation during descent is rejected with a status message. The lab unlocks
when level 3 combat starts. Macro fields use unit sphere coordinates to wrap
longitude and poles. A tangent patch maps local coordinates in metres to exact
Planet samples, with a narrow smooth border. Local surface and exposed spans use
the existing terrain-scene data. The final camera is retained through waking;
the classic external fighter view resumes with combat after waking.

`atmosphere.mjs` ports AtmosphereReference's stable sphere roots, shell/solid
clipping, exponential densities, RGB extinction, solar shadow and midpoint single
scattering. `atmosphere-reference.json` contains 30 Java double-reference results:
vacuum, grazing, both sides of shell entry, shadow and solid clipping for earth, alien,
thin, hazy and airless. Regenerate with:

```sh
mkdir -p .tmp/reference
javac -d .tmp/reference src/main/java/dev/jayms/net/atmosphere/AtmosphereConfig.java src/main/java/dev/jayms/render/AtmosphereReference.java deploy/atmosphere-reference/Generate.java
java -cp .tmp/reference Generate > games/space-invaders/atmosphere-reference.json
node --test games/space-invaders/*.test.mjs
```

The WebGL2 shader uses a fullscreen world pass, a coarse 32 × 16 RGBA8 spherical atlas refined to 128 × 64 by the bounded Worker and an
RGBA8 local height/palette patch. It needs no float render targets or float
filtering extensions. It uses linear lighting, colored solar transmittance,
planetary shadow, bounded cloud shells and one tone map. The GL backend initializes only when selected, so shader compilation cannot stall the default combat route. Low/Medium/High use
8/16/32 view samples. World resolution is capped at 640 × 400; the Canvas HUD
retains its separate DPR cap of 2. Shader/link/context failures keep Canvas usable.
Context loss intentionally remains on Canvas for that session.

Canvas uses one bounded module Worker to precompute up to 49 small world images
at 0.25-second intervals. It interpolates cached frames and draws the same local
terrain with the same camera. Medium cache storage is about 1.2 MB, plus canvases. Prewarming uses the current viewport aspect; resize keeps the same scene point while regenerating reduced images. It uses
Medium uses 4 view samples and 2 solar samples per cached pixel at 96 × 64; Low uses 3/2 at 64 × 40; High uses 8/4 at 128 × 96. No GPU readback or per-pixel
integration runs on the animation thread. Cold/failed workers retain a coarse
sphere and reference-sampled sky. This emergency path has weaker limb geometry
and cloud detail. The cache is reduced quality and can blur fine orbital features.

Release limits: the browser ports the original single-scattering model and
capped isotropic multiple-scattering closure. It is not a full spectral solver. Local face aerial perspective uses a reduced directional table of the shared reference, with 16 distance bins. Cloud occlusion over these detailed faces remains a reduced approximation.
Physical Safari iPhone 30 FPS and desktop hardware 60 FPS are not established by
headless software browsers. Advanced rendering stays opt-in pending those gates.

## Shader dependency audit

The original GLSL 330 `atmosphere-common.glsl` uses engine includes, world-to-planet
matrices, visibility bindings and an RGBA16F transmittance/multiple-scattering
atlas on texture unit 15. Sky data binds on unit 14. The browser pass inlines the
stable roots, shell clipping, density, RGB extinction, phase functions and solar
shadow equations as GLSL ES 300 with explicit highp precision. Browser terrain
textures bind on units 0 and 1; planet-relative metre coordinates remove the
engine matrix and block conversion dependencies. RGBA8 terrain/cache data avoids
float-FBO/filter requirements, while float capabilities are detected explicitly.
The original Java multiple-scattering closure is ported and validated against 15
additional double-reference samples. The worker prepares a small altitude/sun
cosine table (8 × 8 Low, 8 × 16 Medium, 16 × 16 High). Texture unit 2 stores
RGBA32F CPU-generated data with NEAREST filtering and explicit shader bilinear
fetches. WebGL2 core float texture sampling is used; no float render target or
float linear-filter extension is assumed. Engine visibility/reflection bindings
and the engine sky atlas remain outside this browser adapter.

Cloud seed drives fixed world-space weather. The 4,500 m and 1,800 m layers use
600 m and 450 m bounded bands, respectively. Their ray intervals clip against
the visible solid surface and fade continuously at band boundaries.

The default alien preset changes only validated RGB molecular scattering to
[5.8e-6, 22e-6, 18e-6] per metre for teal air. Earth remains a reference control;
thin, hazy and airless retain the original Java preset definitions.
