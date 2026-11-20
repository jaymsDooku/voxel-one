# Agentic voxel building API

## Reference and first build

Use the 1938 Essex House Hotel in Miami Beach as a design reference. The [Miami Design Preservation League archive](https://mdpl.org/archives/2020/04/1950s-ad-for-the-essex-house-hotel/) documents its ocean-liner shape, portholes, lighted spire and geometric mosaics. The [Wolfsonian walking guide](https://wolfsonian.org/_assets/docs/miami-beach-deco-walk.pdf) provides context for patterned terrazzo floors. These features exercise rounded massing, openings perpendicular to multiple faces, fine trim and a walkable interior.

Build **Breakwater House** as an original hotel with a curved street corner, bands of recessed window glass, rooftop portholes and a lit tower, an open porch, and a geometric lobby floor. The roofline and two elevations should be readable at street distance. An avatar should be able to walk through the open entrance and stand on the lobby floor.

## Authoring and review workflow

1. **Plan.** Assign bounded parts for the massing, openings, street elevations, interior and roof. Give each part a coordinate envelope and state which parts intentionally overlap. The assembler controls order so carve and detail layers apply after the shell.
2. **Write.** Each builder supplies operations in the versioned recipe grammar. Shapes use local half-block coordinates, making small trim and patterned surfaces possible. The recipe is the editable source.
3. **Compile.** The compiler checks part bounds, volume, work and output budgets, then emits ordered canonical engine edits. Validation errors point to the recipe line. The same recipe produces the same edit stream.
4. **Inspect.** The loopback Asset Manager and CLI return the same structured edit list and metrics. Use the native preview to check silhouette, facades, collision, doorway clearance and the lobby without opening an existing world.
5. **Revise and hand off.** Review the assembled building from street level and inside. Return issues to the part author, compile again, and archive the source and playtest evidence with the work item.

## Run it

```powershell
mvn verify
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --building-compile assets/buildings/breakwater-house.vbuild
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --building-preview assets/buildings/breakwater-house.vbuild
```

The compiler writes JSON to stdout. Redirect it to a file if an agent or tool needs the artifact. The native preview opens an offline inspection plot. Walk with WASD, look with the mouse, and use F for flight or F10 to record.

In the desktop Asset Manager, the existing bearer-authenticated local service exposes `GET /api/buildings/schema` and `POST /api/buildings/compile`. Send the raw UTF-8 recipe as the POST body. Compilation returns data without installing the building into a save. The compiler and preview work without starting the browser service.

## Recipe v1

Lines beginning with `#` are comments. Bounds are half-open and Y is vertical. Every operation must be inside its part. Part bounds may overlap and later writes win, including `AIR` cuts.

```text
building example 32 20 24
part shell 0 0 0 32 20 24
box SNOW 0 0 0 32 20 24
box AIR 2 1 2 30 18 22
box AIR 13 1 0 19 8 3
cylinder AIR z 4 11 0 10 17 2
end
part trim 0 0 0 32 20 24
box LED:64d8cd 0 18 0 32 19 1
end
```

Commands are `building ID WIDTH HEIGHT DEPTH`, `part ID X0 Y0 Z0 X1 Y1 Z1`, `box MATERIAL X0 Y0 Z0 X1 Y1 Z1`, `cylinder MATERIAL AXIS X0 Y0 Z0 X1 Y1 Z1` and `end`. The cylinder sweeps an ellipse along X, Y or Z. Materials include AIR, GRASS, DIRT, STONE, SAND, SNOW, WOOD, LEAVES, PLANKS, BRICKS, GLASS and colored `LED:rrggbb`. See the schema endpoint for exact limits and field order.


## Full octree detail

Append `grid16` to the building header to use integer coordinates measured in sixteenths of a game block. Keep the same commands and half-open part bounds. Whole-block massing and fine cuts can live side by side in one part.

```text
building detail-demo 160 108 112 grid16
part sidelights 32 13 47 58 76 53
box WOOD 32 13 47 58 76 53
box AIR 34 23 47 56 69 53
box GLASS 34 23 49 56 69 50
box WOOD 44 23 47 45 69 49
end
```

The compiler paints sparse engine octrees in bounded tiles. Uniform regions merge automatically, and the output uses the largest matching native cube at each position, from whole blocks to 1/16-block details. The envelope can span 256 game blocks per axis. Compilation visits occupied tiles and skips empty space. The fill-work and placement-edit budgets still apply. The schema endpoint reports the limits.

The [Westminster study](westminster-build-plan.md) shows how to plan a larger complex as bounded parts and fit close-up details into the same envelope.

The opt-in JSON result has `schemaVersion: 2`, `unitsPerBlock: 16`, and an `editsByDepth` array ordered from whole blocks to sixteenths. Dimensions and `occupied` are in recipe units. Existing half-block recipes still return v1 JSON and the same canonical edit stream.

**Cedar Court Entry** is an original detail study informed by the Gamble House in Pasadena. The [Historic American Buildings Survey](https://www.loc.gov/resource/hhh.ca0279.sheet?st=gallery) lists 11 measured drawings. The [Gamble House Conservancy research guide](https://gamblehouse.org/learn/) identifies floor plans, elevations, and a site plan. Its [joinery notes](https://gamblehouse.org/design-without-compromise/) document proud pegs, inlay, and stepped wood profiles. The sample uses deep eaves, layered brackets, round pegs, narrow glazing bars, and patterned flooring to exercise all five edit depths. The colored points behind the sidelights use the engine's LED material.

```powershell
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --building-compile assets/buildings/cedar-court-entry.vbuild
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --building-preview assets/buildings/cedar-court-entry.vbuild
```
