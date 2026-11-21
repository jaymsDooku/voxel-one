# Palace of Westminster voxel study

UK Parliament describes a Palace nearly 300 metres long. Its restoration factsheet identifies the three major skyline landmarks and shows the façade from the Thames. The public precinct plan establishes the relationship of the courts, the hall and the terrace. Parliament gives the hammer-beam roof span as 73.2 by 20.7 metres. The study uses an approximate two metres per block. The court sizes, setbacks and individual façade bays are design estimates. [Palace today](https://www.parliament.uk/about/living-heritage/building/palace/architecture/the-palace-today/) · [Palace factsheet](https://www.parliament.uk/globalassets/documents/lords-information-office/2013/palace-of-westminster-factsheet.pdf) · [Precinct plan](https://www.parliament.uk/globalassets/visit/venue-hire/lords/maps/palace_of_westminster_mobility_access_routes_map_a3_portrait.pdf) · [Westminster Hall roof](https://www.parliament.uk/about/living-heritage/building/palace/westminsterhall/architecture/the-hammer-beam-roof-/)

[Thames elevation and footprint sketches](westminster-study.svg). The footprint is stretched horizontally for legibility. Use the coordinate schedule to place geometry.

## Coordinate schedule

Use a 150 × 51 × 76 block envelope. In the recipe, X increases from geographical north to south and Z increases inland. Elevations are relative to the base. Ranges are half-open and refer to principal masses. Buttresses, ledges and finials may extend beyond those ranges within each part.

| Part | X | Z | Top Y | Recognisable features |
| --- | --- | --- | --- | --- |
| Elizabeth Tower | 3–14 | 14–25 | 48 | Four clock dials, gilded frames, belfry and tall narrowing roof |
| Thames range | 18–136 | 16–37 | 20 | Repeated pointed windows, stone mullions, projecting bays, slate roof and terrace |
| Victoria Tower | 134–149 | 44–59 | 49 | Broad square shaft, corner turrets, crenellation and recessed portal |
| Central Tower | 67–82 | 30–45 | 46 | Stepped transition from square base to octagonal spire above the lobby |
| Westminster Hall | 4–41 | 57–68 | 17 | Long pitched roof, buttresses, gable tracery and one open hall volume |
| Landward range | 28–135 | 53–57 | 15 | Lower connecting wing behind the two open courts |
| Commons Court | 32–68 | 38–53 | Open | Open sky and a distinct floor within the block |
| Royal Court | 79–112 | 38–53 | Open | Open sky between the range and landward wings |
| Riverside terrace | 3–147 | 8–15 | 2 | Low stone paving, intermittent balustrade and foreground separation |

## Build sequence

1. Compile a sparse palace-sized envelope and a distant corner sample before authoring the asset. Keep existing recipe limits on edit count, operation count and octree fill work. Verify that the larger grid preserves the order and encoding of older output.
2. Assemble the plinth, riverside range, three transverse connections, landward range and two courts. Keep the connecting Hall passage and interior floor usable in the preview.
3. Set the skyline in whole blocks. Give the Thames range a long dark roof ridge, the Hall a transverse gable, Elizabeth a sharp taper, Central an octagonal taper and Victoria a flat parapet with corner turrets.
4. Add details at their reading distance. Use whole-block geometry for piers and recesses, half and quarter blocks for cornices and mullions, and finer units for clock hands, grille trim and gable accents. Colour the walls warm limestone and keep glazing and roof material darker.
5. Compile the complete recipe, compare its block extents and separate court volumes against this schedule, check clock faces and fine edits, and capture a wide shot plus detail views in the native renderer. Comparison with Cedar Court covers planning traceability, coverage and voxel detail. The examples have different subjects and scope, so a comparison cannot isolate planning as the cause of a quality difference.

The study covers the exterior complex, selected courtyards and a simplified Westminster Hall interior. The preview has a flat inspection plot and promenade. The surrounding Westminster district and Parliamentary room layout are outside this study.

## Rebuild and preview

The second detail pass gives each clock face a stepped circular rim, twelve Roman hour marks, sixty minute ticks and six cross-bearing shields. Each elevation uses an outward-facing coordinate system so its inscriptions read correctly. Sand, snow, wood and brick stand in for gilding, opal glass, painted metal and enamel in the game's material palette. The shields and pale clock face draw on [Parliament's Elizabeth Tower conservation account](https://www.parliament.uk/about/living-heritage/building/palace/big-ben/elizabeth-tower-and-big-ben-conservation-works-2017-/turning-big-bens-clock-dials-blue/). The three projecting river pavilions, roof dormers and courtyard galleries are interpretations at the study scale.

```sh
python deploy/author_westminster.py
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --building-compile assets/buildings/westminster.vbuild
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --building-preview assets/buildings/westminster.vbuild
```

Walk towards the terrace for the façade or press **F** to fly up to the clock and over the courts. The model preview is temporary.

## Double-scale palace

Use `westminster-grand` as the dimensional reference. Its 16 parts include both end towers, the Central Tower, Westminster Hall, the Thames range, two open courts, the connecting wings, and their roofs and ornament. The double-scale copy uses the same axes and ground datum. Every horizontal distance and elevation doubles, including the setbacks, the width of the clock hands, and the depth of the masonry joints.

| Measurement in blocks | Grand | Double |
| --- | ---: | ---: |
| Site width × depth | 300 × 152 | 600 × 304 |
| Recipe height | 102 | 204 |
| Elizabeth Tower cap above base | 96 | 192 |
| Central Tower cap above base | 92 | 184 |
| Victoria Tower cap above base | 98 | 196 |

1. **Lay out the palace.** Double each part boundary and each row stride. Keep the bay, hour-mark, and turret counts. Scale the AIR cuts with the surrounding stone so that galleries, windows, and courts retain their proportions.
2. **Compile the detail.** Build a separate grid16 asset from the grand recipe. Check the material geometry against its source and require exactly eight times the occupied volume. Keep limits on authoring operations, octree work, and emitted placements.
3. **Place the site.** Size the resident chunk range for the built height and keep the higher tower meshes available when the camera crosses the site. Frame both ends of the riverfront from the opening spawn.
4. **Inspect at both distances.** In the native client, verify all three tower caps above the old height limit and check for paving and open space in both courts. Capture the opening silhouette, fly to the four-faced clock, then follow the riverfront to inspect the sills, buttresses, and roof.
