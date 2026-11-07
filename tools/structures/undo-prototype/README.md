# Undo storage diagnostic

These candidate classes are outside `src/main/java` and are not used by game undo.
`UndoPrototypeCells` is a diagnostic fork of the package cell store at
`f7742adf5c1a9d5a788057fd49cb01e45ec15039`, with position/index accessors.
`UndoPrototypeImage` tests packing private undo state without exposing lazy trees
as map values. The default change was rejected after measuring with the live
world retained: world cells already share immutable octree nodes with placements.

From the repository root on Linux, after compiling the game:

```sh
mkdir -p target/structure-undo-diagnostics/classes
javac --release 17 -cp target/classes -d target/structure-undo-diagnostics/classes tools/structures/undo-prototype/UndoPrototypeCells.java tools/structures/undo-prototype/UndoPrototypeImage.java tools/structures/undo-prototype/UndoPrototypeChecks.java tools/structures/UndoImageProbe.java tools/structures/UndoLiveRetentionProbe.java
java -Xmx128m -cp target/structure-undo-diagnostics/classes:target/classes dev.jayms.net.structure.UndoPrototypeChecks
java -Xmx1g -cp target/structure-undo-diagnostics/classes:target/classes dev.jayms.net.structure.UndoImageProbe map
java -Xmx1g -cp target/structure-undo-diagnostics/classes:target/classes dev.jayms.net.structure.UndoImageProbe packed
java -Xmx1g -cp target/structure-undo-diagnostics/classes:target/classes dev.jayms.net.structure.UndoLiveRetentionProbe world
java -Xmx1g -cp target/structure-undo-diagnostics/classes:target/classes dev.jayms.net.structure.UndoLiveRetentionProbe map
java -Xmx1g -cp target/structure-undo-diagnostics/classes:target/classes dev.jayms.net.structure.UndoLiveRetentionProbe packed
```

Run the processes one at a time without concurrent tests or native playtesting.
Both fixtures have 50,000 cells. The uniform case shares two trees, as placement
already does. The mixed case uses distinct two-leaf trees. `UndoImageProbe`
excludes live world storage. It records one post-GC retained-heap estimate and
20 retain/capture and visit timings after three warmups. Map retention assigns an
existing reference. Visits check positions, a position-derived voxel color and
occupied count; they are not full guarded-undo timings.

`UndoLiveRetentionProbe` retains an actual `WorldVoxels` populated through
`replaceCell`. Modes retain only the world, the world plus the existing map, or
the world plus the packed candidate. Each fixture has three post-GC heap-delta
samples. Warmup comes before these samples. The world and selected undo storage
stay reachable during measurement. Compare the complete retained state before
choosing a default. These estimates are not peak memory or a full-system memory
qualification. The diagnostic does not place structures, save a world, run
rendered clients, shape traffic, or qualify any supported limit.

`test_undo_storage.py` compiles these tools into an isolated temporary build
folder and runs four deterministic invariant groups. Performance probes are
manual diagnostics and do not run in ordinary CI.
