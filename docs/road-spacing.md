# Road user spacing

Automatic city pedestrians and horse riders keep a configurable centre-to-centre gap on public road cells and their walkable one-cell-wide verges and entrance approaches. These areas carry foot traffic; this version has no separate pavement entity. Building interiors keep their existing routes and work stations.

Both the offline game and multiplayer server accept:

```sh
java -jar voxel-one-1.0-SNAPSHOT-client.jar --offline --game city --pedestrian-spacing 1.0 --mounted-spacing 1.6
java -jar voxel-one-1.0-SNAPSHOT-server.jar --game city --pedestrian-spacing 1.0 --mounted-spacing 1.6
```

Distances are in blocks. Defaults are `0.8` for pedestrians and `1.4` for horses and their riders. Values must be finite and between `0` and `2`, inclusive. The larger gap applies to a mixed pair. Zero disables that category's own gap; a mixed pair still uses the other category's gap. Large gaps reduce traffic capacity on the three-cell-wide roads.

Settings apply at simulation startup, including loaded worlds. Restart the offline game or server to change them. Multiplayer movement uses the server's settings. The world and network formats are unchanged. JVM properties `voxel.road.pedestrianSpacing` and `voxel.road.mountedSpacing` provide the same settings; command-line flags override them.

Road users wait or pass around another user while keeping their destination. A short clear route or a walkable verge lets traffic pass a stationary road user. Building interiors are excluded from these detours. Intermediate road waypoints allow room to pass instead of forcing every user through one cell centre. Movement checks the full step so a fast rider cannot pass through a waiting pedestrian. Existing overlapping pairs can move apart. New settlers and parked horses start apart; horses wait on the north-south road so they do not block the starting work yards.

This controls automatic city traffic and its clearance around parked or player-ridden horses. It does not change the human player's movement controls or apply spacing inside buildings.

Automatic riders wait to mount until the larger mounted gap is clear. On arrival, an NPC horse uses a clear parking cell within 4 blocks when one is available, keeping the doorway and rider apart. Existing overlap can move apart; spacing does not force users through a blocked route.
