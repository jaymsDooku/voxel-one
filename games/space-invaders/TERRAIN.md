# Alien planet terrain

Level 3 now draws the generated alien planet behind the existing airborne Space Invaders combat. Open **Planet lab** to change the seed, scale, roughness, crater density, biome weights and colour palette. **Generate** applies valid settings. Invalid settings retain the last valid scene. Seed text must contain 1–128 characters. Scale ranges from 16 to 256 voxels, roughness from 0 to 2, crater density from 0 to 1, and each biome weight from 0 to 10. At least one biome weight must be positive.

The fixed seed gallery contains acheron, glass-sea, silent-impact and fungal-moon. The five overlays show elevation, biome, slope, caves and traversal. Survey coordinates move the view through the same world. The landmark selector finds rare formations in a fixed region around the origin. Select **Geology cutaway** to inspect underground layers and cavities. Click a solid voxel in the section to excavate it. This removes only that voxel; lower and adjacent rock keep their original geology. Essential routes are protected. Generate resets excavation edits.

## Terrain and materials

Broad, warped elevation shapes produce basins and open plains. Ridged and directional noise adds twisting volcanic mountain chains. Surface detail stays small. Biome weights blend the ground colours and elevation profiles. Obsidian badlands have sharp dark ridges and orange vents; crystal fields have mineral spires, violet radiation crystals and teal skies; toxic basins have lime acid pools, clustered branched fungi and green skies; impact deserts have pale dust, exposed strata and more impacts. Light comes from the north-west, fog mutes distant terrain, and sparse accents identify minerals and hazards. The combat fleet keeps its bright gold colour.

Impacts use independently placed small and large craters. Floors depress, rims rise, ejecta extend outside the rim, large craters have central peaks, and erosion reduces and widens the rim. Overlap is additive. Landmark candidates use biome suitability, rarity, fixed world-space height anchors, spacing priority and route exclusions. They include giant crystal clusters, collapsed calderas, enormous arches, hollow domes with openings, supported overhangs, meteorites, fungal towers and deep fissures. Vegetation uses biome, slope, altitude and a clustered density field.

A 3D material field contains caves, underground basalt/shale/rock layers, mineral clusters and sparse emissive veins. Surface cliffs show the same materials used by the section and excavation. Raised volume spans preserve openings in arches, hollows and overhangs. Caves concentrate in selected geological regions. Chunks and formations use absolute world coordinates, so chunk generation order does not alter geometry.

## API and performance

`Planet` in `terrain.mjs` exposes:

- `surface(x,z)` and `material(x,y,z)`: base elevation and voxel material.
- `chunk(cx,cz,size=16)` and `chunkAsync(cx,cz,size=16)`: deterministic base samples, including a shared border. The deferred API coalesces identical pending requests.
- `checkSeam(a,b)`: check shared elevations, biome weights and volume materials.
- `validate()`: flood-fill the landing-to-relay-to-extraction corridors, checking ground, hazard exclusion, a maximum one-voxel step and four empty overhead voxels for player/enemy clearance.
- `excavate(x,y,z)`: record an integer voxel edit outside the protected route.

Configuration and cached samples are immutable. Feature and chunk caches retain at most 64 entries each. Surface and vegetation caches retain at most 16,384 and 4,096 entries. Edits do not change the underlying elevation or geology fields.

`terrain-scene.mjs` generates all base columns before volume decorations. `terrain-worker.mjs` runs this work in a module Worker. The browser retains the old scene until the new scene is ready. Later requests supersede earlier ones. The static terrain drawing is cached until the scene, overlay or viewport changes. When Workers are unavailable, generation uses a clearly labelled main-thread fallback. The deferred `chunkAsync` API alone is not a worker thread.

The lab reports generation milliseconds, checked border samples, seam mismatches, inaccessible objectives, open columns, cover columns and landmarks. Scene reports also count biome samples, hazards, cave voxels and the maximum sampled chunk time. Those counts describe the sampled survey, not the entire infinite world. The current game remains airborne; terrain hazard materials and the three navigation anchors do not add ground missions or hazard damage rules.

Run `node --test games/space-invaders/game.test.mjs games/space-invaders/terrain.test.mjs games/space-invaders/terrain-scene.test.mjs`. Run `deploy/test_alien_terrain.cjs` with installed desktop Chromium and WebKit. The check starts and stops its own loopback HTTP server for this checkout. The live checks cover the fixed seed gallery, all four biome signatures, overlays, invalid input, real canvas excavation, Worker responsiveness, latest-request ordering, flight/fire, pause/resume and classic restart. Reports and fresh screenshots go in `dashboard/evidence/alien-terrain-*`.

Performance reference: Linux x64, Intel Core Processor (Haswell, no TSX), Chromium 153.0.8010.12 at 1280×900. Cold 16-voxel base chunks must have p95 generation time ≤8 ms. Worker generation of a 72×48-column survey must take ≤1500 ms. The desktop harness enforces these limits across the fixed seed gallery and extreme scales. WebKit remains a workflow regression check; these performance limits do not apply to it.
