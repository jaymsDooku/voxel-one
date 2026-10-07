# Rendering algorithms

The renderer retains an OpenGL 3.3 core path. Window creation first tries OpenGL 4.3, then falls back to 3.3. `-Dvoxel.gl33=true` forces the baseline. Compute shaders and indirect commands are created only when the context supports OpenGL 4.3. The baseline uses indexed draws and transform-feedback particles.

World rendering uses HDR color and depth targets. Screen effects run before the UI. Held items render after temporal reconstruction so their separate depth buffer does not erase world depth. History is rejected after camera cuts, geometry disocclusion and input-size changes.

| Feature | Implementation |
| --- | --- |
| Greedy meshing | Full-block face masks merge rectangles with identical encoded material values. LED RGB values, neighbor visibility and material boundaries remain distinct. Fractional blocks keep their sparse greedy model mesher. |
| Screen-space LOD | `ScreenError` projects geometric error into viewport pixels. Distant terrain refines a bounded quadtree with parent fallback while children build. Orthographic views use their actual projection scale. |
| Frustum culling | CPU AABB tests cover chunks, distant tiles, models and shadow/probe views. The optional compute path also tests chunk bounds. |
| Hi-Z occlusion | Five maximum-depth reductions preserve conservative coverage. Chunk bounds test the previous hierarchy only when the view matrix is identical. World edits and dirty chunks invalidate it. Bounds expand for jitter. |
| TAA | Halton projection jitter, depth reprojection, disocclusion rejection, neighborhood clipping and motion-dependent accumulation. |
| Voxel AO / screen AO | Coarse voxel distance samples estimate local occlusion. A four-direction screen-space horizon pass adds visible contact detail. |
| Cascaded shadow maps | Three depth-array layers cover camera-centered extents of 32, 96 and 256 world units. Texel snapping and edge blending reduce transitions. |
| PCF / PCSS | Blocker search estimates penumbra width. Sixteen disk samples filter the shadow, with a bounded 1–8 texel radius. |
| PBR materials | GGX normal distribution, Smith masking, Schlick Fresnel and energy-conserving diffuse/specular terms. Materials carry roughness and emission. |
| Physically based sky | Analytic single Rayleigh/Mie scattering uses wavelength-dependent coefficients, optical depth and aerosol phase. Environment lighting uses the same atmosphere model. This is a single-scattering approximation. |
| Height fog / aerial perspective | Exponential height-density integration attenuates distant surfaces. A separate volumetric integration adds in-scattered light. |
| Bloom | HDR mip levels supply a multi-scale bright-pass blur before tone mapping. |
| ACES | The fitted ACES curve maps exposed HDR color before display gamma. |
| Auto exposure | Actual HDR luminance from the final mip drives bounded, time-smoothed exposure. |
| Contact shadows | Short world-space rays test reconstructed screen depth. Offscreen blockers fall back to geometry shadows. |
| Screen-space reflections | World-space reflection rays test screen depth with distance-based thickness and edge fading. Missing hits retain environment/probe reflections. |
| Reflection probes | One local six-face cubemap captures nearby world/model geometry over successive frames, then generates roughness mips. Box projection corrects parallax. Sky remains the fallback until capture completes. |
| Decals | `RenderPipeline.addDecal(Decal)` projects up to 16 bounded, normal-filtered tint decals onto reconstructed surfaces. `clearDecals()` removes them. They do not change collision or saved voxels. |
| Clustered lighting | A 12×16×12 world-space cluster grid stores bounded lists of emissive lights. Each fragment visits its own list, capped at 32 lights. The nearby source cap is 256. |
| Volumetric fog / sunlight | Twelve ray samples integrate height-density extinction and in-scattering. Screen-depth visibility modulates sunlight, with a forward-scattering phase for rays. Offscreen visibility remains approximate. |
| Screen-space GI | Nearby reconstructed surfaces gather visible reflected radiance. Sparse voxel lighting supplies the offscreen fallback. |
| Dynamic emissive lighting | RGB LED edits update clustered direct light, sparse transport, GI radiance and particle emitters. |
| Colour grading / LUTs | Saturation and contrast precede trilinear 3D LUT sampling. `ColourLut.load(Path)` reads bounded unit-domain `.cube` LUTs; `setColourLut` uploads one. The default LUT is identity. |
| GPU particles | OpenGL 3.3 transform feedback integrates bounded LED particle positions, age and velocity. Emitters refresh on scene edits. Additive point sprites render into HDR with depth testing. |
| Triplanar mapping | Normal-weighted world-space material projections blend three axes. |
| Detail normal mapping | Screen derivatives of material height build a cotangent-frame detail normal. |
| Probe-based GI | Validity-weighted diffuse integration samples a lattice of angular radiance probes. Buried probes are excluded. |
| Radiance cascades | Three nested ray intervals use 16, 64 and 256 angular samples as spatial density drops. Transparent near intervals merge far radiance from the next cascade. |
| Voxel cone tracing | Five diffuse cones sample mipmapped premultiplied voxel radiance and opacity with expanding footprints. |
| Sparse voxel GI | The existing adaptive fine-light octree retains exact fractional/model transport. Coarse radiance/probes augment this sparse transport. |
| Volumetric clouds | A height-bounded noise-density layer uses 16 view samples and four sunlight samples per step. Wind moves the density field. |
| Temporal upscaling | HDR reconstruction writes at display resolution from a smaller input with depth-reprojected history. |
| Dynamic resolution | Nonblocking GPU timer queries drive a bounded 0.5–1.0 scale. Hysteresis changes scale by at most 0.05 every 30 timing samples. |
| Planar reflections | The nearest visible water-bearing chunk defines one reflection plane. Mirrored view rendering clips geometry below the plane and reverses winding. |
| Water refraction / caustics | Water samples the opaque color/depth resolve, rejects foreground distortion, applies wavelength-dependent absorption and blends Fresnel reflections. Animated procedural caustics modulate submerged color. |
| SDF shadows / AO | A worker builds a signed coarse distance estimate from voxel-center Manhattan distances. Bounded sphere traces estimate soft light visibility and AO. Fractional cells use conservative coarse occupancy here; their detailed transport remains in the sparse octree. |
| GPU-driven indirect rendering | The optional 4.3 compute path writes each chunk's indexed indirect command after testing its bounds. CPU still submits chunk dispatch/draw pairs; this is not a multi-draw scene submission system. |
| Dithered LOD transitions | A shared screen-space mask gives complementary parent/child coverage while refinement fades in. |

## Controls and limits

`RenderPipeline.settings` exposes effect switches and quality values to engine callers. `-Dvoxel.noTaa=true` disables TAA. `-Dvoxel.renderScale=0.65` selects a fixed smaller input. `-Dvoxel.dynamicResolution=true` enables the GPU-time controller. Dynamic resolution is off by default. `-Dvoxel.msaa=4` exercises the multisample resolve.

GI and light transport remain bounded to the existing nearby 96×128×96 voxel volume. Screen effects cannot see hidden or offscreen surfaces. Reflections use one rolling local probe and one water plane, not an unlimited scene-wide capture system. The SDF is a coarse voxel distance approximation, not an exact Euclidean mesh distance field. These limits are explicit; the implementation does not imply cinematic offline accuracy.

## Native checks

Build with `mvn -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" package` after creating `target/tmp`. Run `python3 deploy/run_rendering_playtest.py --display "$DISPLAY" --gl33` for the strict 3.3 fixture, omit `--gl33` for the 4.3 path, and run `python3 deploy/run_rendering_main_playtest.py --display "$DISPLAY" --gl33` for the production game workflow. These harnesses use isolated synthetic homes and the inherited display. They never start an X server or access account data.

Executed results and fresh media are recorded separately in `dashboard/evidence`. Harness source is not proof that a check ran.

Clustered LED shadows traverse the finite segment from the biased receiver to the light. They exclude the target emitter cell and use exact cell crossings in the existing coarse transport opacity grid. Other opaque cells before the light block it; cells beyond the light do not. The coarse grid cannot resolve separate fractional shapes within the emitter cell. Directional sunlight keeps the bounded distance-field shadow march.

Local reflection captures share the main scene lighting binder, including cascades, irradiance, voxel transport and clustered LEDs. Capture starts after valid shadow maps and an accepted lighting bake. Light edits and accepted GI updates invalidate the six-face capture. A face is captured each frame. Initial or invalidated probes use the sky fallback until all six are ready; periodic refresh keeps the rolling contents. The captured cube is never sampled recursively.
