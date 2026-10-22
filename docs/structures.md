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

VSTR version 2 is a strict gzip-compressed binary package with name, dimensions,
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

Validated packages retain cell coordinates and model references in primitive arrays,
with frozen geometry references. Reading a cell creates a temporary record; the
public list remains immutable and spatially ordered. This reduces retained
per-cell objects without increasing admission limits.
Constructor validation uses primitive sort keys for canonical ordering and duplicate
detection. Capture/decode accumulate cells in bounded primitive buffers and pass a temporary
read-only view through full constructor validation. Capture copies source trees
before freezing them. Decoded trees are owned by the importer. The private capture/import path validates this owned buffer directly; public
constructor inputs are copied before freezing. Both paths enforce all package
checks. Sorting still creates final storage separately, and placement staging
needs further memory work.

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

Capture asks immutable world snapshots whether a cell is known empty before
materializing its octree. Unknown answers retain the original one-read capture
path. Bounds checks, cancellation and progress still run for every coordinate;
fractional geometry and model dependencies follow the same package validation.

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

Placement undo stores an immutable empty-value view over the placed cell keys.
This relies on the occupied-destination rejection policy: every admitted before
image is empty. The authority still checks for later changes before undo.

Detached chunk validation checks staged cells first. For untouched cells, the
snapshot may skip an octree lookup only when its conservative hint proves air.
Unknown hints retain the full cell read; mesh and model budgets still apply.


Placement staging retains immutable relative-coordinate arrays and frozen tree
references instead of one object key and hash-map entry per occupied cell. The
footprint bounds limit each coordinate key to 28 bits. Sorted keys use binary
search for lookups; the authority still receives the same immutable Map API.
This reduces retained staging storage on the measured small fixture. It does
not remove owner-thread commit, mesh, persistence, history or admission limits.


VSTR readers accept versions 1 and 2; new exports use version 2. Version 1 readers
reject version 2 packages. Existing standalone voxel-model files, model transfer,
model fingerprints and saved-world formats are unchanged. A v1 package is decoded
with its original model codec and can be exported as v2 without changing content.

Version 2 adds one encoding byte per model dependency. Mode 0 contains the
existing length-prefixed `ModelDefinition` representation. Mode 1 contains the
name (Java modified UTF, validated as 1–32 ASCII name characters), a resolution
byte (8, 16 or 32), a fixed occupancy bitmap of resolution³/8 bytes, then RGB
triples for occupied voxels. Index order is x fastest, then y, then z; voxel i
uses bit `1 << (i & 7)` of byte `i >>> 3`. Air has no RGB triple; opaque black has
a set occupancy bit and three zero bytes. Alpha is restored as 255. Encoding
chooses the smaller representation before writing. Legacy definitions containing
redundant octree branches retain mode 0 so their exact fingerprints survive. Sparse/uniform components
usually retain their existing octree bytes; dense components avoid per-voxel
alpha and octree-node overhead. Decoding still enforces existing model limits,
package budgets, surface/geometry limits and dependency validation. This format
change does not qualify city admission or performance.


Import validates each dependency's identity, occupancy and surface budget as it
is decoded, before retaining later definitions or reading world cells. The final
package validation still checks references, geometry, counts and byte integrity.


World snapshots share frozen cell trees through chunk buckets. Each snapshot
copies the chunk index; the first subsequent edit to a shared bucket copies that
bucket before replacing a cell. Single-cell buckets store one local key directly;
other buckets use local integer keys. Repeated and nested snapshots remain
independent, including after clear/replay. Cell reads are immutable; world edits
use `WorldVoxels.apply`, or `replaceCell` for a validated complete 16³ cell.
Whole-cell replacement copies the tree wrapper and shares persistent nodes, so
caller edits and snapshots remain independent. Server structure staging uses
this path instead of replaying every leaf into its private geometry snapshot.
Edit history is still generated and saved in the existing format; this does not
remove history limits or make the full commit asynchronous. Snapshot creation and mutation remain owner-thread
operations. A handed-off snapshot can be read independently; concurrent mutation
of the same WorldVoxels instance is not supported. No save or wire format changes.

This bounds per-bucket copying to 4,096 cell slots but still copies the whole
edited-chunk index. Widely scattered edits can therefore increase snapshot work.
Measured CPU probes cover compact and scattered 100,000-cell worlds; they are
not city/max, peak-memory or reference GPU/network qualification. Owner-thread
application, persistence and other outstanding gates above remain unresolved.


Edit histories index a single edit key directly for cells with one retained edit.
A second distinct key promotes the cell index to an ordered set; removal back
to one key releases that set. Global replay order remains in the existing
ordered history map. Collection views are read-only; callers mutate through
put/remove/clear so the cell index stays consistent. This changes no file or
wire representation and does not remove the 2,000,000-edit persistence limit.
The compact index saves memory for single-edit cells, with an extra wrapper
for cells that retain multiple fractional edits. Measured tradeoffs are recorded
in the history-index validation report; they do not establish supported city
limits or qualify whole-operation timing budgets.


Package loading and decoding have cancellable worker APIs. Saved-library loads
and imports pass their operation flag through dependency parsing, cell parsing
and final validation. Server placement decode uses the peer cancellation flag
before staging. Import progress reports 0–99 during parse and 100 after gzip
integrity and package validation. A cancellation raised by a progress callback
still aborts delivery. Existing decode/load entry points preserve their formats
and validation rules. Cancellation is checked between bounded decode/validation
units, not by interrupting an individual file read, model mesh or final cell
sort. This does not qualify city-scale latency or change placement admission.


Encoding and asset-file saving also accept cancellation/progress callbacks.
Capture, import, export and upload preparation use those flags. File saves stage
bytes in a temporary file in the destination directory, check cancellation
between writes of at most 64 KiB and immediately before atomic replacement,
and clean failed staging files. Cancellation before replacement preserves an
existing destination. Save progress stays below 100 until return; the UI marks
100 on successful completion. A successful atomic replacement commits the asset
file; later cancellation does not hide that success or undo the file. Callbacks
run only before this commit boundary. This does not add fsync durability or
qualify latency, city admission or world-placement persistence budgets.


Detached structure chunk validation installs complete 16³ world cells through
aligned persistent octree subtree replacement. It visits source nodes to
normalize legacy redundant branches, shares unchanged immutable nodes and
copies destination ancestor paths. The source tree and encoded identity stay
unchanged. Chunk metadata records whole-cell model IDs and fractional-cell
counts, matching leaf replay; validation still runs the same CPU mesh and
instance budgets. This reduces staging work without changing world publication,
file/wire formats, geometry detail or admission caps. The detached assembly
probe is documented separately from full city/max and system qualification.


Server staging records opaque, process-local geometry stamps for successfully
validated chunks. Final commit reuses that CPU mesh validation only if the live
stamp still matches under the authority lock. Stamps include terrain identity
and a stable generation, chunk-bucket identity and its edit revision. Snapshot
trust is lost after any terrain generation change, including a change away and
back. Changed chunks and unknown stamps always run full validation. This cache
belongs only to one prepared placement and retains at most 256 chunk stamps.
Offline/default sources keep full validation. Destination occupancy, model
revision, cancellation and live-body checks still run at commit. This removes
one repeated validation phase for unchanged server chunks; it does not remove
history/save costs, expand admission or qualify city/max and tick/frame budgets.
