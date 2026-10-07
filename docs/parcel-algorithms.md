# Zone parcel layouts

Choose a zoning tool, then click the layout row or press `[` / `]` to cycle the 15 algorithms. Draw the zone and press Enter to save the selected layout. Parcel edges appear inside the zone.

Press `V` to compare a preview on existing zones without changing them. Press `P`, then click an empty zone, to apply the selected layout. An existing building or purchased plot prevents layout changes. Escape leaves the tool. Prebuilt stress-grid zones retain their fixed layout.

The layout report shows parcel count and the number of candidate building sites. A site includes the building footprint and its entrance strip. The simulation only buys sites wholly inside one parcel, and reserves that parcel for one building. A layout with no sites remains available for comparison; use merge or another algorithm to make room. Existing cities keep their old cell-scanning layout until an empty zone is explicitly changed.

## Portfolio

| Priority | Algorithm | Method |
| --- | --- | --- |
| P0 | Recursive Road-Frontage Split | Recursively cuts along the longest frontage axis, falling back to the longest zone axis. |
| P0 | Competitive Voxel Flood-Fill Growth | Farthest-point seeds compete through a deterministic four-neighbour unit-cost flood. |
| P0 | Grid / Orthogonal Subdivision | Clips axis-aligned lots to the zone mask. |
| P1 | Binary Space Partitioning | Recursively bisects the longest axis at the cell median. |
| P1 | Weighted Voronoi | Assigns cells by seed distance divided by a seeded positive weight. |
| P1 | Road-Anchored Voronoi | Places farthest-point seeds on road frontage; assigns cells by distance. |
| P1 | Terrain-Cost Parcel Growth | Multi-source Dijkstra growth costs one plus the maximum neighbouring height change. |
| P1 | Shape-Grammar / Rule-Based | Alternates axis rules and one-third cuts to form strips and deeper lots. |
| P2 | Straight-Skeleton | Raster approximation: inward unit-speed boundary wavefronts form directional basins, then subdivide. |
| P2 | Medial-Axis / Skeleton Split | Finds ridges in the boundary-distance field, spaces ridge seeds, then grows parcels. |
| P2 | Constraint-Optimised | Bounded search compares grid and eight growth layouts, scoring area error, perimeter and missing frontage. |
| P2 | Historical / Incremental | Retains previous cell boundaries, subdivides oversized parcels and parcels newly available cells. |
| P0 | Parcel Merge | Repeatedly merges adjacent parcels up to twice the target area. |
| P0 | Parcel Split | Subdivides previous parcels with half the target area. |
| P0 | Parcel Validation & Repair | Clips cells to the zone, removes overlaps, separates disconnected pieces and fills gaps. |

All entries implement `ParcelGenerator`. `Request` accepts a zone mask, road-frontage cells, target area, random seed, positive terrain costs and prior parcels. Output cells are immutable. Every algorithm finishes with repair, so its parcels cover the mask exactly once and are connected. Prior input is used by merge, split, repair and historical subdivision; without prior input, the editing operations start from a grid.

City layouts target 144 cells, or 576 for farms. The seed is the zone ID. The skeleton algorithms operate on ground cells and do not implement continuous vector straight-skeleton or medial-axis geometry. The optimiser is a bounded heuristic, not a proof of a global optimum. Frontage and target area are preferences; clipped zones can contain small or landlocked parcels. Full building sites and existing city access checks still decide development.

Terrain previews use flat costs. Applying a terrain-growth layout uses the authoritative terrain heights. The preview label states this difference. Applied cells, algorithm and parcel IDs persist in city snapshot format 16. Older parcel-free save formats remain readable. Multiplayer uses protocol 27 so both ends agree on the new snapshot and commands.
