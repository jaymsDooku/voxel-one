# Experimental reusable structures

Structures copy physical construction across world cells. They contain blocks,
fractional geometry and immutable voxel model definitions, without scaling.
Players, inventories, drops, businesses and other simulation state are excluded.
Placed copies do not follow later edits to a saved package or source model.

This implementation is experimental. City performance, maximum fragmented
construction, physical-GPU rendering and the agreed shaped-network budgets are
not qualified. No supported-city claim or stable rollout is made.

## Use

The default structure-library key is F12; it can be remapped in Controls. Aim at
a cell, open the library and type `first`, then press Enter. Close the library,
aim at the opposite corner, reopen and enter `second`. Use `anchor` on a selected
cell to align placement; otherwise the minimum corner is the anchor. The anchor
must be inside the selection. Selection corners include their entire world cells.

Type `capture House` to save the selected physical region as House. Up/Down
browses saved `.vstr` packages. `at 20 64 30` sets the world cell at which the
anchor is aligned. `rotate` turns the package another 90 degrees around the
vertical axis; no scaling or arbitrary-angle rotation is provided. Bounds and
an anchor cross appear in the world view. `preview` checks destination cells;
movement and rotation invalidate older conflict results. `place` places a copy
and keeps the selected package available for repeated placement.

Placement rejects every occupied destination cell corresponding to an occupied
source cell, even if only a small fraction of that cell is occupied. Empty source
cells do not erase destination terrain. Preview is advisory: the authority checks
again before publication. Offline and server commits also reserve live player,
citizen, horse and cow bodies. Every nonempty resulting cell is checked as a whole
cell, including fractional geometry and model instances. Bodies entering after
preview or staging reject the entire commit without dependency, geometry or save
changes. The same check applies to undo restoring nonempty cells; undo removing
geometry may proceed around bodies. Commit-time validation probes changed cells
under each live body instead of scanning the full structure for every body;
reservation sizes and authority locking remain the same. Escape or `cancel`
requests cancellation before commit.
A completed commit cannot be cancelled. In multiplayer, cancellation is confirmed
by the server and can arrive after upload; do not assume a request rolled back an
already completed placement.

`undo` reverses the most recent retained placement as one operation. It refuses
to overwrite cells changed since placement; unrelated cells are preserved. Undo
history is session-local, with at most eight records per actor and a global target
of 262,144 occupied cells. Older records age out first; the newest admitted
placement remains undoable even when it exceeds that target. Dependencies remain deduplicated
after undo so other placed copies continue to work.

`export` writes the selected self-contained package to the library's `exports`
folder. `import PATH` validates a package before adding it to the local library.
The library is under the game's device profile in `structures/`. The current
browser shows the first 256 package names in sorted order. Import errors
do not alter the world. Captures include implicit seeded terrain and edited
geometry; model definitions are copied and frozen.

## Format and safety limits

VSTR version 1 is a strict gzip-compressed binary package with name, dimensions,
anchor, deduplicated model definitions and occupied cells ordered by 16-cell
spatial sections. Geometry keeps its original 1/16-cell detail; voxel models keep
their own 8/16/32 resolution, colours and 8,192-quad/200,000-byte per-model limits.
Definitions are identified by SHA-256 over the existing name/voxel serialization.
Placement remaps them to immutable world definitions, baking quarter-turn model
orientation into a copied definition rather than stretching it.

Enforced format ceilings are 1,024×256×1,024 cells, 4,000,000 occupied cells,
512 unique dependencies, 64 MiB compressed and 512 MiB decoded. Decoding checks
the version, order, duplicate positions, material values, references, gzip CRC
and lengths. Invalid dimensions/counts fail before world publication.

Additional provisional development caps are 131,072 geometry leaves and 16,384
model instances per package, 1,048,576 model-definition surface quads,
1,048,576 instance-weighted model quads, and 262,144 instance-weighted
model quads per affected chunk, with at most 256 affected chunks per placement.
Detached production CPU meshes validate each affected chunk before publication,
including unchanged geometry. The authority repeats this check under its lock
to cover concurrent edits outside the footprint. Chunk edges are conservatively
counted as exposed. These are safety caps, not benchmark-established
supported limits. A representative city or maximum fragmented fixture can exceed
them and is currently rejected. World-only structure definitions have a separate
budget of 2,048 definitions and 128 MiB serialized model data. These allowances
do not change the legacy 128-custom-model world library or 16-model author quota.

World-only component IDs occupy 1024–66559 and cannot enter inventory byte slots.
Existing worlds without these components retain their version-8 offline or
version-8 server save format. Worlds containing them use version 9, with explicit
integer model IDs. Older world versions remain readable; old clients cannot read
the new save format. Network protocol 31 carries atomic dependency/cell batches;
the desktop client can still reconnect to supported legacy servers without
structure commands. Updated servers require an updated client.

Build/pose validation permits cells from y=-1024 through y=1023 while natural
terrain remains unchanged. High/low edited chunks and nearby air use the existing
bounded terrain worker. Such worlds have a provisional 2,048-chunk residency cap;
ordinary terrain retains its 1,690-chunk cap.

Staging reuses immutable trees for repeated whole-cell materials and repeated
instances of the same dependency. Identity rotations retain persistent octree
nodes through frozen snapshots. Editing one placed cell still creates independent
world geometry. Undo history targets 262,144 retained cells and evicts older
operations first; the newest admitted placement remains one undoable operation
even when it exceeds that target. This history policy does not increase package,
world, geometry or affected-chunk admission limits.

## Remaining qualification work

Reference hardware and network qualification is deferred. The current commit
path serializes placements and validates/stages work off-thread, but final owner
thread application, persistence, initial dependency loading and render uploads
can still exceed frame/tick budgets. These are unresolved performance gates,
not passed tests. The existing server simulation schedule also needs measurement
against the requested 20 Hz requirement. The current maximum world history is
2,000,000 edits; a placement exceeding it is rejected before publication.

The benchmark corpus and CPU probes in `structure-benchmark-preparation.md` remain
prerequisite tools. Their timings are not capture/placement or 32-client city
qualification. Final supported geometry/instance limits and stable rollout stay
gated until the agreed city and fragmented scenarios pass all budgets.
