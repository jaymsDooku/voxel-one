# Stress Test Grid save

Start an offline city, then open Controls → City saves. Select **Stress Test Grid** and click **Load selected**. The game creates this independent save once. It does not replace an existing save with that name or alter Original city.

The layout contains 1,000,000 vacant buildable plots in a 1000 × 1000 grid. Each plot is 16 × 16 blocks. Three-block-wide paved roads provide two lanes with a painted divider between every row and column, plus the outside perimeter. The total footprint is 19,003 × 19,003 blocks.

The centered residential area has 400,000 plots. The surrounding commercial, industrial and agricultural rings have 200,000 plots each. Exact percentages require splitting the boundary square layers. The split follows fourfold rotational symmetry, so the layout stays centered.

Click the colored map to visit a part of the grid. The map samples the full layout; nearby boundaries show individual plots. The last pixel on the lower-right map edge visits plot #1,000,000. Use the usual camera controls and F6 to change views. Save current and Save a copy preserve the complete layout and simulation clock.

This is a vacant zoning benchmark, with no starting residents or completed buildings. It tests the large plot layout, road queries, navigation, terrain and save loading. It does not represent one million citizens or one million completed buildings. The layout is fixed so the benchmark stays reproducible. Use another city save for road and zoning edits. The mayor dashboard remains available.

Plot geometry and road terrain are generated from a compact saved descriptor. Every plot has its own stable ID and polygon. The engine loads nearby terrain and draws nearby zone boundaries rather than allocating all road voxels or drawing all polygons each frame. Long road routes use grid intersections rather than a search over the entire city area.

Stress saves use city format 15. Ordinary saves retain format 14. Wire protocol 26 carries format 15 snapshots. Older city save formats still load through their existing readers.
