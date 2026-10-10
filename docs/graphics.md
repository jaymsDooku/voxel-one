# Graphics and performance capture

Desktop Sandbox and Voxel City One use the same local Graphics screen. Open Escape, then Graphics (G). Controls and city Saves remain in the menu. Tab changes pages; arrows select/edit rows; F1 opens full help. The mouse selects rows, tabs and buttons; the wheel scrolls. Small windows keep the same keyboard controls. Graphics uses GLFW content scale for layout, text and hit targets; HUD targets remain at native framebuffer resolution. A 200% layout can be exercised in the desktop fixture; actual Windows scaling still needs its packaged-client check. Apply creates renderer resources once. Editing a row changes only the draft; Cancel discards it. Reset changes the draft to Balanced and touches graphics only.

## Presets and capabilities

The table is provisional. No preset promises 60 FPS, 16.7 ms, a Windows result or a physical iPhone budget. Detailed distance stays 112 blocks and distant horizon stays 2048 blocks at every desktop preset. Explicit distance edits change rendering only; loading radius, simulation, physics, edits and multiplayer are unchanged.

| Preset | Shadow map / cascades | AO / contact / GI / reflections | Water optics / clouds / bloom | Atmosphere | Anisotropy | LOD error |
| --- | --- | --- | --- | --- | --- | --- |
| Low | 512 / 1 | Off | Off | Low | 2 | 12 px |
| Balanced | 2048 / 3 | On | On | Medium | 8 | 12 px |
| High | 2048 / 3 | On | On | High | 8 | 12 px |
| Ultra | 4096 / 3 | On | On | High | 16 | 6 px |

All presets use TAA, native scale, VSync, uncapped CPU pacing, smooth mipmapped voxel textures, auto exposure and 256-block shadow distance. Selecting a preset replaces all constituent preferences. A manual edit selects Custom. Requested preferences are retained; effective values respect texture/renderbuffer/sample limits, anisotropy support and enumerated monitor modes. Unsupported fullscreen requests use windowed mode. MSAA uses one temporal sample instead when fractional scale or dynamic resolution is active. The renderer never combines TAA with multisample targets.

## Control mapping

| Page / control | Actual work |
| --- | --- |
| Display mode, resolution / refresh | GLFW monitor/window configuration. Fullscreen modes must appear in the device list. Borderless uses the monitor's desktop mode. Windowed refresh is a preference for fullscreen, not an OS refresh switch. |
| VSync / cap | Swap interval; independent CPU frame pacing after swap. Cap 0 disables CPU pacing. |
| Scale / dynamic target / minimum / maximum | Scene targets resize; HUD and text retain framebuffer resolution. Completed GPU frame timing drives a 0.05 scale step at most every 30 frames, with 12% decrease / 20% increase hysteresis. Requested range is validated. Target time is not an FPS guarantee. |
| Detailed distance / horizon / LOD error | Detailed upload priority and drawing, distant visual bounds and screen-error tile selection. Existing parent coverage stays until children are ready. Orthographic offscreen terrain uses coarse parents within the same horizon. |
| Shadow quality / distance | Allocated map size, 1/2/3 cascades and rendered coverage. Off skips shadow drawing. Stationary geometry, unchanged sun and unchanged texel-snapped camera reuse shadow maps. Edits, chunk mesh swaps, membership, model revisions and sun changes invalidate them. |
| AO / contact / screen GI | Both voxel transport loops and temporal post shader work are gated. Lighting occupancy and wall blocking are retained. |
| Reflections | Local voxel reflection work, screen reflection and rolling probe capture are skipped when off. Existing six-face probes update one face per frame; complete renewal needs six eligible frames. |
| Water optics | Off preserves water surfaces with flat shading and aerial perspective; skips planar capture, scene copies and refraction/reflection sampling. |
| Atmosphere / overview haze | Local table sizes/steps and overview ray composition. Physical world atmosphere is unchanged. |
| Clouds | Skips the sky shader cloud branch. Separate volumetric cloud quality has no implemented path and is not offered. |
| Bloom / auto exposure / brightness | Post shader bloom branch, exposure readback and mipmap work. Manual brightness is disabled while auto exposure is on. With bloom and auto exposure both off, scene mipmap generation is skipped. |
| AA / mipmaps / voxel texture style / anisotropy | Temporal or multisample resolve, actual texture min/mag filters and supported anisotropy. Pixel style remains selectable. |
| Performance | Native-resolution diagnostics and frame graph; scroll diagnostics with arrows/wheel. Reports label GPU values unavailable until completed queries exist and memory as an estimate. |

There is no independent model-detail level. Repeated models keep their existing renderer/instancing path. The ordinary OpenGL 3.3 draw path is the default on all devices. Per-mesh compute dispatch is experimental behind `-Dvoxel.experimentalGpuDraw=true`; no measured benefit is claimed. Production HiZ CPU readback is disabled: mixed transparent depth and uncertain history must never hide terrain. Frustum culling remains first. HiZ stays available to explicit diagnostic fixtures, pending an opaque conservative source and an actual benefit measurement.

Detailed meshes use one worker, four immutable jobs including one staged upload, a 48 MiB bound per result (at most four retained jobs, including one upload), a 2 ms render-thread work budget and a 2 MiB upload budget per frame. GL copies use 256 KiB slices. The work budget is soft: capturing a snapshot and allocating GPU storage cannot be preempted. Old valid meshes survive until a revision-fenced replacement is uploaded. World edits, membership and accepted mesh swaps reset temporal history; untouched dirty chunks outside a reduced detail range do not reset it every frame. Immutable roots and six border snapshots preserve full-cell greedy, fractional surfaces, materials, LEDs and transparent neighbor rules. Production detailed meshes include otherwise hidden opaque faces at chunk boundaries. This bounded extra geometry keeps borders closed when neighbors swap independently after additions/removals. It does not reduce quality; hidden triangles are counted in telemetry. The ordinary mesher retains exact neighbor-culling parity for fixtures and tools. Queues coalesce edits and reject unloaded/replaced/revised chunk or model dependencies. Shadow/probe coverage and already resident edited meshes remain eligible as background work outside the main detail range. Visible/near gaps rank first; age eventually outranks distance. Dirty chunks and complete column membership update on edits, residency and mesh swaps; the visual mask is reused until membership or a distance-cell boundary changes. Distant selected sets and transition scratch are reused. Distant work has one worker, eight pending jobs and a 128 MiB / 4096-mesh cache limit. Full-cell builders and masks use primitive reusable scratch; fractional traversal retains the sparse surface builder and its existing bounds.

Lighting and roof occupancy retain their existing geometry revision cache and bounded background lighting worker. Sky coverage rebuilding is still synchronous on geometry changes; its cost is included in render CPU time. Probe and shadow work now have separate GPU timings. Accepted lighting bakes reuse same-sized texture/buffer storage through subimage/subdata uploads and a reusable root atlas array. Unchanged fine data is not reuploaded for sky-only updates. Dirty-region partial uploads and multi-draw submission remain measurement candidates rather than claimed optimizations.

## Persistence and display recovery

Desktop preferences are version 2 in `.voxel-one/graphics.properties` under the device user's home, separate from saves/accounts. Atomic replacement follows a file force. Version 1 scale/TAA values migrate. Invalid, nonfinite, future-version or oversized files recover to defaults. Explicit launch properties override loaded values and appear by property name in Graphics: `voxel.noTaa`, `voxel.msaa`, `voxel.renderScale`, `voxel.dynamicResolution`, `voxel.atmosphereQuality`, `voxel.overviewHaze`. Invalid overrides are ignored with a warning. A later runtime Apply uses the draft values; the explicit launch override takes precedence again on the next launch.

Apply retains the working renderer while constructing targets. Failure restores its display/resources/preferences. Display mode/resolution changes retain the old renderer for a 15-second Keep / Revert test. Enter confirms; Escape reverts. Timeout or focus loss reverts. Only confirmation saves the new display preference. A pending marker records that a display test was not confirmed; a cold launch with that marker recovers the saved last-good profile in windowed mode. Ordinary quality changes save without a countdown. No restart is needed for implemented controls.

## Performance recording on the desktop

Use F9 or Graphics / Performance / Record. The engine excludes 120 warm-up frames, then captures up to 60,000 frames in bounded primitive arrays. F9 stops. CSV/JSON export runs on one background worker in `.voxel-one/performance`. A warm-up-only capture is labelled empty. F10 remains the separate video recorder.

Reports include wall frame time, update/stream/render/overlay/video capture CPU time, snapshot/mesh worker/upload time and bytes, completed GPU pass timings, mesh draw calls/triangles including shadow/probe submissions (fullscreen, UI and particles are excluded), chunk queues and oldest age, LOD tiles, heap, render-thread allocation, GC, estimated geometry/target memory and actual scene scale. GPU frame time is a delayed EMA, not a per-frame GPU percentile. Other GPU columns are last completed measurements. Unavailable CSV values are empty. VRAM is not driver-reported and excludes driver overhead and some volume/probe allocations. Export names/metadata do not include save names, player positions, accounts or runtime logs.

JSON frame percentiles use nearest rank. The 1% low is 1000 divided by the mean duration of the slowest ceil(1%) frames. Recorder-copy CPU overhead is recorded separately; asynchronous encoding/export cost is not attributed to the render thread. Measure observer overhead by repeating an equal route with capture on/off. Compare video inactive/active runs separately. Hardware vendor/renderer/GL version, OS, JVM, resolution, effective settings and route are recorded. CI records the exact `GITHUB_SHA` through the filtered build resource; local builds label an unavailable revision `unknown`. Supply `-Dvoxel.buildRevision=EXACT_SOURCE_SHA` for an identified local package.

Portable deterministic replay (Java 21 and Python required):

```sh
python3 deploy/run_rendering_benchmark.py --java java --route GROUND --preset BALANCED --runs 3 --warmup 120 --frames 600
python3 deploy/compare_performance.py target/performance-replay/GROUND/run-*/performance-*.json --output target/performance-replay/GROUND/comparison.json
```

Windows PowerShell does not expand globs for Python; pass the three JSON paths explicitly. Linux graphical runs must add `--display "$DISPLAY"`. Routes are GROUND, FLIGHT, TELEPORT, ISOMETRIC, CITY, INTERIOR and FRACTIONAL. Seed 42, a fixed synthetic gallery/city, a fixed night clock for INTERIOR, fractional LED/material geometry and camera frame index make replay repeatable. The default freezes city clock/population focus and separates simulation with a render-only replay and fixed resident scene; `--streaming` enables the actual world streamer as a separate workload. Run each cache condition as a separate group; clearing the synthetic home resets save/preferences, not the driver's shader/disk caches. No cold driver-cache claim is made. The comparison tool refuses unlike hardware/source/settings/route/cache and empty captures. Reduced-quality preset runs must be reported separately from unchanged-quality optimization comparisons.

Reference desktop thresholds, recorder-on/off costs, Windows packaged playtesting and physical-device performance require follow-up measurements. Linux software GL checks validate function and safety; they do not prove the proposed 1080p Balanced target.

## Existing Rendering menu

Rendering (R) stays beside Graphics (G), Controls and City Saves. Its shared quality rows use the same Graphics transaction and atomically saved graphics profile. A failed Apply restores the working renderer and shared values. The menu follows the active renderer after a swap. Particle, saturation, contrast and cloud-coverage preferences remain in `rendering.properties`; they load and survive Graphics replacements. Missing `graphics.properties` imports valid legacy shared rows. Once a Graphics profile exists, its shared values take precedence at relaunch. Nonfinite, unsupported and malformed legacy rows are ignored. Legacy manual exposure retains its 0.1 minimum.

## Native iPhone subset

The native SceneKit toolbar opens a touch-friendly, scrollable Graphics sheet. Supported drafts are AA Off/2x/4x, 30/60 FPS cap, detail 32/64/128/180 blocks, bloom and opt-in experimental planet atmosphere. Metal sample support and screen refresh limit effective values. AA maps to `SCNView.antialiasingMode`; the frame cap maps to the view and display link; detail maps to camera clipping and conservative chunk-node hiding. Defaults preserve 180-block coverage, 2x AA and 30 FPS cap. Native Low/Balanced/High are local provisional presets; they all keep atmosphere opt-in and coverage intact. Desktop GL controls are explained as unavailable.

Apply saves the validated versioned JSON atomically outside native world data, then applies supported renderer values. Cancel/Reset preserve the applied profile until Apply. The sheet clears movement and captures touch; local movement pauses while online polling continues. Physical world atmosphere and server state are unchanged. `ios/check-simulator.sh` builds this actual client and runs required graphics draft/Apply/relaunch/landscape and control regressions on the controller's assigned iPhone Simulator. Simulator timing is not device evidence. Physical iPhone performance, thermal behavior, signing and distribution remain follow-up gates.
