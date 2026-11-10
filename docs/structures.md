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

File imports first copy the opened compressed source into a private temporary file
in chunks of at most 64 KiB, enforcing the 64 MiB limit before staging. The worker
then streams that snapshot through the same strict decoder used for network byte
arrays. This removes the full compressed byte array from file import. Cancellation,
malformed data and callback failures delete the temporary file; a successful load
also deletes it. A process crash can leave a temporary file for later cleanup.
Staging does not change package identity, admission caps or world state. Decoded
cell/model storage and placement still need further large-region work.

Capture saves and library exports stream gzip output directly into a temporary
file beside the destination, with a 64 KiB disk-output buffer. The existing
compressed and decoded byte ceilings apply while writing. The completed stream
is closed before the final cancellation check and atomic replacement. Before
replacement, cancellation or failure removes the stage and preserves any prior
package. A successful replacement is committed; no progress callback runs after
it. Network encoding still returns the same byte-array format. Decoded assets,
world saves, placement staging and crash durability need further qualification.

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

Placement staging retains mixed 16-resolution cells as immutable packed wire
images rather than retaining their child-node graphs. Each cell image is at most
64 KiB. Its wrapper identity stays stable for map lookups and undo; uniform cells
and model instances keep their shared trees. Point and aligned-region queries walk
private packed bytes. Mutable copies, raycasts and leaf/region traversal may
reconstruct a temporary graph without retaining a decoded cache. Prepared whole-cell
world backing images stay packed; active chunk geometry and ordinary edits retain
their normal tree paths. This trades bounded decoding work for lower retained
fractional backing geometry. It does not establish peak-memory, frame or operation-time
budgets, and it does not change geometry or placement limits.

Whole-cell history replacements share compact immutable replay rows between staged
and live histories. Uniform cells share one immutable edit; fractional cells retain
primitive row data and a bounded local lookup index. Ordered views materialize edit
records and keys during traversal. Direct replacements keep row position; removals
and later edits preserve the existing global replay order. Snapshot roots share
segments and fork edits through copy-on-write indexes. Ordinary edits and legacy
initial replay still retain individual records. Saves and network replay keep their
existing row formats and 2,000,000-record limit. Row scans now allocate transient
records/keys, so serialization, late joins and eager rendering snapshots still need
large-world qualification. Join snapshots share compact history roots rather than
flattening every row. The ready-phase delta checks both changed current rows and
removed snapshot rows, so undo of the final fractional leaf still clears that cell
for a client finishing its initial load. This comparison still scans rows under
authority locking and needs large-world timing work. This does not complete
region-backed persistence or admit the required city/maximum fixtures.

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
Ordinary worlds without these components retain version-8 saves; component
worlds use version 9 with explicit integer model IDs. Offline structure placement
now stages version 10 with bounded replay sections and explicit integer model
IDs. Later saves retain version 10 after successful publication or loading.
Versions 3–9 remain readable; older clients cannot read version 10. Network protocol 31 carries atomic dependency/cell batches;
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

The fixture verifier now reports canonical history cost and occupied 16-cell
chunks without creating millions of Edit objects. The currently retained city
input needs 1,008,528 records across 2,173 chunks; the maximum input needs
11,967,232 records across 8,192 chunks. Their busiest chunks need 1,528 and
1,536 records respectively. These counts assume an empty destination. Existing
destination history and later edits add cost. The maximum exceeds the current
2,000,000-record persistence limit even before rendering or synchronization.
Both inputs also exceed the 256-chunk placement cap. Current input hashes and
compressed sizes are in `dashboard/evidence/structure-history-partition-cost.json`;
they differ from older receipts and must not be mixed with older timings.

The next storage change must migrate canonical history, rather than raise its
record cap. A bounded first step is a private region index with whole-cell or
fractional-tree records and global dependency identities. Import old ordered
history on a worker, preserve within-cell edit precedence, and compare replay
against the legacy map before using this index for saves. Bound each region and
the total decoded bytes. Keep the old format readable. Only then connect a
versioned region manifest to atomic placement, save/load, undo and late-join
transfer. Publish one validated generation; never expose partial region commits.
Undo must reject conflicting later edits. Measure live geometry, old/new roots
and retained undo together, not serialized storage alone. This migration is not
implemented or qualified by the diagnostic.

A tools-only migration prototype now groups final canonical cell states into
16-cell regions. Replay checks cover terrain generator versions 1, 2 and 3,
explicit air overrides, fractional LED colours, model IDs, negative coordinates,
world boundaries, immutable snapshots and later edits. Its source is
`tools/structures/RegionHistoryPrototype.java`; run the integration check with
`python3 -m unittest discover -s tools/structures -p 'test_region_history.py'`
after compiling production classes. It retains seed and generator version and
publishes a result only after migration finishes. Diagnostic count budgets and
cancellation are checked; these are not decoded-byte or peak-memory bounds.
Its private uncompressed version-1 codec now bounds input/output to 8 MiB,
100,000 cells, 1,024 regions and 131,072 leaves. It validates region coordinates,
unique regions, ordered local cell indices, cell ownership, size-16 geometry,
materials, CRC and end-of-input before returning a result. Leaf counts are
checked after each cell parses, so rejection can follow one extra tree of at
most 4,096 leaves. This is not a measured heap bound. Cancellation is checked
during I/O and before returning final results. A 96-byte independent golden
container, 47 rejection cases and all three terrain-version replay cohorts pass.
See `structure-region-history-codec.json`.

The private dependency envelope now stores only referenced model definitions,
deduplicated by existing fingerprint, plus source-ID bindings and the bounded
region payload. It carries no author/account fields. Diagnostic bounds are
16 MiB total, 8 MiB serialized definitions, 512 distinct definitions and 1,024
alias bindings. Existing per-model limits remain: 8/16/32 resolution, 200,000
serialized bytes and 8,192 mesh quads. A single bounded definition can parse
before the aggregate byte limit rejects it; these limits do not measure heap.
Decode rejects missing/extra bindings, duplicate identities, unused definitions,
bad references, CRC and trailing data. Restore remaps IDs into a private copied
ModelLibrary and private world. It never publishes to a live authority. Simple
512-definition and 1,024-alias cohorts, conflict/empty-cache restoration and 47
rejection cases pass. Report: `structure-region-dependencies.json`. Production
save/load, generation publication, undo and network integration remain pending.

`RegionGenerationPrototype.java` now tests private file publication of this
envelope in isolated synthetic directories. It writes a cancellable sibling file
in at most 64 KiB writes, forces it, checks the expected prior generation under
an OS file lock and requires atomic replacement. Restart by a fresh store instance
validates bounded file bytes, CRC, generation metadata and the nested manifest.
Read size comes from the same opened handle as the bytes, so an atomic path
replacement cannot mix two generation sizes. Stale writers and changed staged
bytes reject. An error after rename is checked against the exact published bytes;
it must not be treated as proof the old generation survived. Directory fsync,
machine-crash/power-loss guarantees, orphan-stage recovery and responsive live
publication are still pending. This synchronous experiment is not a production
save path or a 100 ms owner-thread qualification. Results and fresh CLI screenshot:
`structure-region-generation.json` and `structure-generation-cli.png`.

The tools-only `RegionAuthorityPrototype` now connects the actual shared
StructureEngine API to private generation files. It stages frozen geometry,
models and a final-cell index off-lock, fences both source root and saved
generation, then publishes a preallocated root after strict file publication.
Snapshot views retain one coherent root. Direct final-cell indexing avoids
reconstructing a global Edit map. The CLI passes all four rotations, placement,
undo, restart loading, stale-root/disk-generation rejection, unrelated-edit
preservation and injected failure/cancellation cases. Report and fresh CLI image:
`structure-region-authority.json` and `structure-authority-cli.png`.

A separate tools-only `RegionWorldLibraryPrototype` now validates an exact-ID
physical-world/library envelope. It round-trips the existing world-model codec,
retaining unplaced definitions, duplicate-fingerprint aliases, original IDs and
authors. The detached restore does not remap IDs or mutate its source library.
CRC, lengths, counts, dependencies and cancellation are checked under private
16 MiB envelope and 8 MiB library limits. This is not a complete world save;
offline survival metadata is optional, while server-player and live city state still need a production migration path. The private generation authority now writes this
envelope as generation v2;
LocalGame and the server remain unchanged. Reports and fresh CLI
image: `structure-world-library.json` and `structure-library-cli.png`.

Private generation v2 retains the full library, including models left unused by
undo, exact IDs, authors and duplicate-fingerprint aliases. Loading v1 retains
its original dependency-remapping behavior; the next authority save upgrades to
v2. This cannot recover assets already omitted by an older v1 file. A
dependency-only v1 stage cannot downgrade an existing v2 generation.
The same force, atomic-replace, root/generation fences and cancellation recovery
apply to both versions. Fresh CLI results: `structure-library-generation.json`
and `structure-library-generation-cli.png`.

This adapter is not used by LocalGame or the server. Its physical manifest
preserves optional offline inventory, health and dropped-item metadata, but
does not install server-player or live city state;
synthetic edits are dirty until a successful later publication. Runtime undo is
not restored after restart. Complete legacy-world migration, responsive live
publication and full storage/render/network qualification remain required before
production integration. No admission cap or production format has changed.
No city-sized storage, save, network, atomic commit or undo claim follows from
these small replay checks. Results: `structure-region-history-prototype.json`.

`RegionOfflineMetadataPrototype` now stores the existing 36-slot inventory,
health and up to 100,000 dropped items in a private 2 MiB CRC envelope. It uses
the existing item/inventory validation and requires model dependencies. Optional
offline metadata is attached to world/library envelope v2 inside the existing
private generation v2. Old files retain absent metadata rather than fabricated
survival state. The private authority carries metadata in its source root and
preserves it on later publications; updates invalidate stale stages. A generation
stage cannot discard existing metadata. These fields are never structure-package
contents. No LocalGame/server save format or behavior has changed. Report and
fresh CLI screenshot: `structure-offline-metadata.json` and
`structure-offline-metadata-cli.png`. Complete legacy migration, city-sidecar and
server-player handling, responsive integration and qualification remain pending.

`RegionOfflineImportPrototype` parses bounded offline v3..9 base-file bytes into
a detached region/library/survival envelope. It uses the production versioned
readers, streams edits into private replay and checks final encoded admission
before return. Thirteen synthetic fixtures cover all seven formats and the
three terrain versions where the format stores them. Older formats retain the
implicit legacy terrain version, version 3's absent model table, legacy item
restrictions and pre-v6 default colour behavior. Sidecars, server state and
automatic migration are not implemented; actual game save paths remain unchanged.
Private limits are 16 MiB source, 8 MiB model section and 200,000 source edits,
plus existing final-cell/region/leaf/envelope limits. These are diagnostic limits.
Current report and fresh CLI image: `structure-offline-legacy-import.json` and
`structure-offline-legacy-import-cli.png`. Earlier v8/v9 fixture evidence is
retained in `structure-offline-import.json`.

`RegionSidecarsPrototype` preserves typed city and optional city-history bytes
inside private world/library envelope v3 and the existing generation frame.
Combined sidecar content is capped at 4 MiB for this experiment; larger valid
city/history saves reject rather than truncate. Known city format headers,
history version/framing, CRC and its SHA-256 binding to the exact city bytes are
checked. City/history bodies are preserved verbatim, not semantically validated
by this envelope. Generated synthetic files pass their normal loaders after
private-generation restart. Getters copy bytes; root updates invalidate stale
stages, later geometry saves retain sidecars, and a store stage cannot omit
existing sidecars. No arbitrary names or paths are embedded. These bytes never
enter reusable structure packages. Automatic migration, coordinated restoration
of multiple legacy files and live city/server integration remain pending.
Current report and CLI image: `structure-sidecar-preservation.json` and
`structure-sidecar-preservation-cli.png`.

`RegionOfflineBundlePrototype` now captures only the offline base file and its
fixed `.city`/`.city.history` siblings. It requires a caller-held source monitor
and monotonic source revision before reading. Regular files use NOFOLLOW and
same-opened-handle size bounds, with reads and cancellation checks in at most
64 KiB chunks. The 16 MiB base and combined 4 MiB sidecar limits remain. Orphan or empty
history files reject rather than disappearing. Capture rechecks source bytes and
absence; parsing/staging may run outside the monitor. Publication requires the
same monitor, unchanged revision and exact source bytes/absence before the private
generation commit. No legacy files are replaced. This contract requires all
source writers to cooperate; it does not fence arbitrary external processes.
Owned parent directories are trusted. Full-source copying/revalidation is still
synchronous and not qualified for the 100 ms game-thread budget. Actual game
save integration and coordinated legacy-file replacement remain pending.
Report and fresh CLI image: `structure-offline-bundle.json` and
`structure-offline-bundle-cli.png`.

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
Offline/default sources keep full validation. Final commit also reuses the staged
empty-destination proof when every affected chunk has a matching non-null live
stamp. A missing or changed stamp anywhere restores the full destination scan.
That fallback checks cancellation every 128 destinations. Model revision,
cancellation and live-body checks still run at commit. This removes repeated
validation and occupancy reads for unchanged server chunks; it does not remove
history/save costs, expand admission or qualify city/max and tick/frame budgets.

Structure transaction history copies preserve the ordered edit map and share
per-cell key indexes until either copy edits that cell. Owner tokens fence both
sides of each copy boundary, including nested copies. Generic map sources keep
the validated rebuilding path. This avoids regenerating the full cell index for
each offline/server structure transaction. It still clones two top-level maps
and remains O(history size) under the existing authority lock. First edits to
shared fractional indexes clone their key sets. Full-history serialization and
the 2,000,000-edit persistence ceiling remain; see
`dashboard/evidence/structure-history-copy-validation.json` for scoped allocation
measurements and explicit exclusions from city/max/system qualification.

Offline/server world saves buffer primitive writes in 64 KiB before closing the
temp file and performing the existing rename. Save schemas and bytes stay the
same. The actual offline version-8/version-9 save/reload probe compares complete
file hashes against the parent implementation; see
`dashboard/evidence/structure-buffered-save-validation.json`. Those historical
20-sample timings cover buffering without file forcing, use a warm cache and
exclude load/verification work. Current writers also flush the buffer and call
`FileChannel.force(true)` on the writing channel before closing and renaming.
A force failure exits before publication. Structure placement and undo require
atomic replacement: unsupported atomic moves reject through rollback without a
non-atomic retry. Ordinary saves retain their compatibility fallback. Directory
durability barriers are not added; no fallible I/O follows a successful rename. Complete power-loss durability
and full city/max durable-save qualification remain pending, alongside full-history
serialization and the 2,000,000-edit persistence ceiling.

Offline/server world loaders buffer primitive reads in a bounded 64 KiB input
stream. Parsers, count limits and EOF checks are unchanged. The warm-cache
constructor diagnostic and late-EOF/invalid-version retry checks are recorded in
`dashboard/evidence/structure-buffered-load-validation.json`. Those measurements
include history reconstruction, but do not qualify cold region loading, usable
nearby geometry or full city/max/system budgets.

Server placement and undo stage the synchronization cell list and compressed
publication before taking the final authority lock. The short dependency lookup
uses that lock and verifies the captured model revision. Final commit rechecks
model revision, destination/chunk state and live bodies under the same lock before
saving or broadcasting. Undo also rechecks every placed cell and keeps its history
record on staging failure, cancellation or concurrent edits. Serialization checks
cancellation between definitions and cells and after compression closes. No partial
event is broadcast. The wire format and byte limits stay unchanged. Offline
authorities retain their commit path. This removes server publication compression
from the critical section; history copying, geometry staging, durable save and
queue publication still hold the lock. Late-join synchronization is unchanged.
This is not a tick, frame, memory, city/max or shaped-network qualification.

Server tick diagnostics retain at most 4,096 actual tick starts, lock waits and
work durations. `MultiplayerServer.tickTiming()` returns immutable aggregate
snapshots with lifetime tick/failure counts, window span/rate and nearest-rank
median/p95/worst milliseconds. Interval samples count gaps between actual starts;
lock wait starts when the scheduled callback is invoked and excludes prior
scheduler queue delay. Work includes city advancement, item updates and tick
broadcast construction under the authority lock, including failed ticks. Snapshot
copying is bounded; sorting happens after releasing the metrics lock. No player
or account data is retained. These measurements expose cadence and lock contention;
they do not by themselves qualify the required 20 Hz or p95 <50 ms budgets.

The server now targets 50 ms monotonic tick deadlines (20 Hz). A completed tick
schedules exactly one next callback. Slow ticks, authority-lock waits and periodic
saves skip missed slots instead of running catch-up bursts. The next callback is
scheduled at least 1 ms after completion, leaving an opportunity for commands;
this is not a monitor-fairness guarantee. City simulation still advances by actual
elapsed time. Separate elapsed-time gates keep item fall/pickup at a 100 ms target
and city snapshots at 200 ms; missed item/broadcast slots are not replayed.
Shutdown stops rescheduling, and a failing callback retains the prior stop-on-error
behavior. The small TLS diagnostic is separate from full city/max, shaped-network
and physical-GPU qualification, which remain pending.

Server placement and undo capture only affected geometry buckets under the
authority lock, then stage frozen cell replacements on the worker. A candidate
merge checks every captured chunk/terrain stamp under the final lock. Matching
stamps install the staged buckets into a snapshot of the current world, preserving
new edits in other chunks. Any changed/unknown stamp uses the existing full
restage path, preserving nearby edits before the usual atomic save/publication.
Owner-token changes protect the live world, older snapshots and candidates from
later staging edits. Cancelling or discarding staging changes no live cells.
The normal path moves per-cell geometry copying outside the final lock; history
updates, top-level map copying, save I/O and changed-chunk fallback remain locked.
The scoped geometry probe records the CPU/lock-time tradeoff separately from full
city/max and system qualification in `structure-cell-patch-validation.json`.

Offline structure commit and rollback now install a whole cell through
`World.replaceCell` instead of replaying each leaf through renderer-facing edits.
Destination geometry is canonicalized without changing source bytes, then copied
to collision storage and grafted into any resident chunk. Canonical edit history,
final edit revision, model/fractional metadata, sky invalidation and cold replay
remain intact. Sky/mesh invalidation coalesces within the owner-thread call; no
intermediate air/leaf states are rendered. History for complete cells clears the
old cell index once and inserts the same air-prefix/nonoverlapping leaf sequence
without quadratic coverage scans. Generic histories retain legacy replay. Offline
and server history staging use this path. Save schemas, file ordering, preimages
and atomic-save/rollback sequencing are unchanged. Owner-thread history copying,
whole-structure application and disk I/O still need full-scale qualification; the
scoped diagnostic is `structure-world-cell-validation.json`.

### Legacy geometry undo comparison

Undo compares voxel values, rather than octree leaf-list representation. Legacy
packages can contain redundant branches that world storage collapses. An unchanged
placed cell must remain undoable after that normalization. The comparison shares
identity fast paths, handles air and uniform nodes without expansion, and allocates
no leaf lists. Different voxel values still reject undo before publication.

The local regression reproduces the old rejection using a self-contained legacy
package, then checks successful undo and rejection after a later fractional edit.
Random mixed trees, redundant branches down to individual voxels, air, coloured
materials, frozen source bytes and differing resolutions are covered separately.
This fix does not change package bytes, model identity, placement admission limits
or the outstanding city/maximum and physical-GPU/network qualification gates.

### Server save-prefix staging

Structure placement and undo stage a private sibling save prefix on the worker.
The prefix contains the existing save header, immutable model definitions and a
copied edit history with the requested cell replacements. Serialization and prefix
flush run outside the server authority lock. Histories expose an owner-thread
revision paired with object identity; ordinary map writes, removals, clearing and
complete-cell updates invalidate the captured revision. Final publication rejects
a changed history with a retry message, including edits outside the footprint,
rather than overwriting concurrent work with a stale save.

After the engine revalidates models, geometry and cancellation, the authority checks
live bodies and appends current inventories, health and dropped items under its
lock. The file is flushed and forced before strict atomic replacement, followed by
live geometry/history publication and broadcast. Staged files have distinct names
from ordinary saves. Resource cleanup covers cancellation, rejected revalidation,
writer errors, failed rename and abandoned staging. The format and admission caps
remain unchanged. Ordinary save publication retains its existing fallback policy.

The direct-commit fallback also constructs its one-cell wire event under the lock;
it is not an isolated replay of the previous staged production path.
The 20-run synthetic server diagnostic uses 131,072 existing whole-cell edits and
one replacement cell, with three warmups. Exact save bytes match the prior path.
Direct-commit fallback median/p95/worst was 87.402302/238.923021/267.954182 ms;
staging was 82.620994/161.387811/179.472714 ms; final guarded publication was
3.021576/7.639779/19.790220 ms. This isolates authority save phases on the shared
VPS. It excludes full placement validation, dependencies, inventories, drops,
clients, ticks, rendering and peak memory. History copying still acquires the lock;
final tail serialization and disk force also remain guarded. These results do not
establish the 100 ms stall budget or city/max support. Busy-world retry behavior,
partitioned persistence and full system qualification remain unfinished.

### Bounded server history-copy reads

Server structure save staging now captures a revision-checked history cursor under
the authority lock. It reads at most 1,024 immutable edit references per lock hold
and rebuilds the detached history/index on the worker between reads. Each batch,
including completion, checks the captured revision before iterator access. The
server also checks source-history identity. Any write, replacement, removal or
clear invalidates the copy; COW index-owner changes alone do not. Final publication
retains the existing revision/identity guard. Cancellation during copying creates
no save-prefix file. Offline transaction copying retains the existing shared-index
clone path.

A 20-run isolated copy diagnostic with 262,144 whole-cell edits and three warmups
reported whole-clone guarded median/p95/worst of
50.540579/193.049155/207.736418 ms. The maximum guarded batch per run was
0.075254/0.146869/0.157552 ms; the sum of guarded reads was
8.454132/11.909500/13.481683 ms. Total batched copy time was
299.527839/385.274064/398.501998 ms. Median thread allocation increased from
23,068,952 to 72,337,480 bytes. Exact replay order and source independence matched.
This trades more total CPU/allocation for shorter guarded work; it does not prove
tick/frame/stall or peak-memory budgets. The probe has no competing thread, server,
save, geometry, network or rendering. GC pauses and busy-world retries remain part
of outstanding full qualification. Existing admission and persistence caps remain.

### Reusing validated history keys

Bounded cursor batches now retain immutable keys together with immutable edits.
They snapshot each key/value immediately under the authority lock and retain no
mutable map entries. Worker copying reuses those validated strings through the
same index/revision update path as ordinary puts; public puts still validate keys.
Cancellation checkpoints remain before every copied edit. Cursor and final
identity/revision guards are unchanged, and cell indexes are still rebuilt.

Both diagnostic modes use the new key/value capture; the rebuild control uses
public puts and is not an exact replay of the parent binary.
Two sequential diagnostic JVMs each used 262,144 whole-cell edits, three warmups
and 20 measured runs. Rebuilding keys versus reusing them gave total batch-copy
median/p95/worst of 264.209773/313.733590/317.726842 ms versus
195.776655/279.573552/308.566410 ms. Median thread allocation was
73,377,864 versus 50,331,400 bytes. Reuse-mode maximum guarded-batch
median/p95/worst was 0.092402/0.190105/0.761216 ms. The rebuild control had a
148.055859 ms worst guarded sample; its cause was not instrumented. No stall-budget
pass follows from these diagnostics. Allocations still exceed the shared-index
whole-clone baseline (23,068,952 median bytes), and total system/GC behavior needs
qualification. This probe has no competing thread, server, geometry, save, network
or rendering. Package limits and stable-rollout gates remain unchanged.

### Skipping proven-empty capture chunks

Capture validates both world-space bounding corners using long additions before
consulting hints. It caches a conservative whole-chunk emptiness result in a
bounded byte array (at most 65×17×65 = 71,825 bytes for unaligned maximum bounds).
The scan keeps y/z/x encounter order and skips only the current x run to the next
16-cell boundary. Dependency order and package bytes remain stable. Cancellation
runs before each span and each visited cell; progress accounts for skipped cells.

World snapshots prove a chunk empty only when its entire y range is outside
Terrain.MIN_Y through Terrain.MAX_Y (-32 through 95) and every edited cell in it
is air. Missing or air-only override buckets may therefore be skipped above or
below terrain. Occupied overrides, fractional geometry and model instances prevent
skipping. Chunks intersecting the full terrain range return unknown, regardless of
local column height. Unknown sources retain cell-level capture checks. Server
snapshots expose the same chunk hint; their prior per-cell behavior remains.
This changes neither package format nor geometry/dependency/admission caps.

The 20-run sparse-box diagnostic uses one occupied cell at world y=180 in otherwise
air boxes of 512×128×512 and 1,024×256×1,024 cells. It uses actual World snapshots,
three warmups, disabled-hint control first and exact encoded-byte checks outside
timing. It is not the populated city/maximum fixture. City-size box capture
median/p95/worst was 563.252185/725.220276/833.815266 ms without chunk hints and
25.661882/30.513305/32.654733 ms with hints. Maximum-size box results were
4543.203166/4626.064057/4641.366858 ms and
198.389395/204.199894/206.134547 ms. Untimed cell checks fell from 33,554,432 or
268,435,456 to 4,096; each mode read one octree. Both modes use current chunk-run
capture code, not an exact parent-binary replay. No placement, save, peak-memory,
GPU or network qualification follows from these sparse-box measurements.

### Cancellable spatial sorting

Capture/import and placement builders use stable two-pass 14-bit radix ordering. The current sorter keeps two integer index buffers and one integer key buffer in first-pass order. The key buffer keeps second-pass reads sequential. The final index buffer is returned directly. At 4,000,000 cells, sort arrays use 48,065,536 bytes excluding headers and the caller-owned input, down from 80,065,536 bytes in the earlier cancellable sorter. Cancellation is checked at most 1,024 loop entries apart, including prefix sums and final storage construction. Spatial order, duplicate rejection and published immutability remain unchanged. Admission limits are unchanged.

The historical 20-run shared-VPS component probe for the earlier packed-key cancellable sorter measured control/new p95 of 136.783/101.631 ms at 1,000,000 keys and 528.801/445.349 ms at 4,000,000 keys. It does not construct populated city/max fixtures or measure complete workflow budgets. See `structure-order-validation.json` for commands, results and limits.

The current 20-run component comparison measured prior/current median, p95 and worst of 74.852/87.814/89.253 ms versus 63.700/78.315/80.258 ms at 1,000,000 keys; at 4,000,000 keys, 319.260/382.178/503.763 ms versus 369.961/443.111/547.835 ms. The 40% sort-array reduction trades some maximum-size sort speed on this shared VPS. These sequential probes overlap other local checks and do not establish complete workflow budgets, peak memory or supported city limits. See `structure-sort-memory.json`.

### Bounded package I/O buffers

Structure package export and import use a 65,536-byte buffer above the existing decoded-byte budget streams. Export flushes through the 512 MiB decoded limit and the 64 MiB compressed limit. Import read-ahead also passes through the decoded limit. CRC, raw gzip completion, trailing-data rejection, record cancellation and atomic file publication remain unchanged. Each active codec call adds one bounded buffer; package dimensions, dependencies and provisional geometry/placement limits are unchanged.

A 20-run shared-VPS codec probe used an admitted 256×1×256 geometry-only package with 65,536 occupied cells, seeded brick/stone materials, no models and 131,686 compressed bytes. The exact parent codec was compiled separately with the same shared dependencies. Control/new encode median/p95/worst was 265.684/359.727/459.498 ms and 65.317/117.925/119.632 ms. Decode was 184.856/312.870/405.008 ms and 43.567/68.864/91.284 ms. Each process had three warmups and `-Xmx1g`; control ran first and structure tests ran concurrently. This is a small codec diagnostic, not the populated city/maximum fixture or a full-system qualification. Commands and environment are in `dashboard/evidence/structure-buffer-validation.json`.

### Repeated model dependencies during capture

Capture memoizes package dependency IDs for up to 512 encountered frozen model-definition identities. Repeated instances reuse that result instead of serializing and hashing the same definition again. Different definition records still use the existing fingerprint deduplication, including aliases with identical content. Definitions beyond the memoization bound take the original path. The cache is local to one capture. Model encounter order, package format, immutability and all admission limits remain unchanged.

A shared-VPS diagnostic captured 10,240 repeated instances of four 16-resolution definitions. Their opaque uniform shells hide seeded coloured 14×14×14 interiors, giving small rendered surfaces but nontrivial serialized definitions. The package is 30,608 compressed bytes. It is not the required city fixture. Three warmups and 20 measured runs per implementation used a separately compiled exact parent codec with identical shared dependencies. Control/new capture median/p95/worst was 3467.099/3557.839/3647.257 ms and 11.154/19.782/23.855 ms. Every captured package matched an independently assembled reference outside timing. These capture-only results do not qualify full fixtures, rendering, memory, persistence or network budgets. See `structure-capture-model-validation.json` for commands and environment.

### High-column streaming readiness

High-altitude camera and edited-structure requests now mark their columns as needing natural base coverage as soon as the bounded worker queue accepts them. Previously, high chunks could fill all 16 worker slots before any natural base request entered the queue. A newly resident high chunk could then make its column appear ready while base chunks were still missing. All three streaming request paths share the same marking step. A new marker also invalidates cached rendered-column completeness. Worker capacity and frame work budgets are unchanged.

A deterministic integration check holds the terrain worker behind a latch. With 16 high-only requests and zero resident base chunks, the exact parent reports sky-ready and rendered-column-complete; the fix reports both false. After release, all ten base chunks at y=-2 through 7 become resident and readiness becomes true. This checks production streaming logic with an injected generator, not physical-GPU performance. The parent Intel Mac feature build exposed this race even though its PR run passed. Details are in `structure-stream-readiness-validation.json`.

### Conservative offline validation reuse

Offline snapshots now expose canonical geometry stamps only for chunks that were unloaded when the snapshot was taken. The snapshot keeps an immutable set of resident positions and its captured terrain revision. Live offline authorities return unknown for currently resident chunks, since directly supplied chunk geometry may differ from the canonical edit snapshot. Resident-at-snapshot chunks, later residency, changed geometry, terrain changes and terrain ABA therefore retain the existing live collision and chunk-mesh checks. Workers never inspect the live residency map. Unchanged unloaded chunks reuse preparation proofs as multiplayer already does. No admission or publication rule changes.

A 20-run shared-VPS diagnostic used real LocalGame commits and saves for 16 whole cells across 16 unloaded chunks. Parent/new commit cell reads were 65,552/16. Commit time including durable save median/p95/worst was 21.187/30.416/34.148 ms and 2.878/3.990/4.028 ms. Each mode had three warmups, used fresh synthetic save files, and reconstructed every placed cell from saved edits outside timing. These sparse, small offline placements do not qualify city/maximum or frame/network budgets. See `structure-offline-stamp-validation.json` for commands and limits.

### Cancellable file reads during import

Import reads the observed compressed file size in chunks of at most 65,536 bytes and checks cancellation between reads. The single compressed input array is bounded by 64 MiB. Reading accounts for progress 0–10; decoding and validation account for 10–100. A truncated file or an extra byte after the expected length rejects the import as changed and asks for a retry. The decoder still validates gzip completion, CRC, resource limits, materials and dependencies before returning a package. Format and admission limits are unchanged.

Filesystem tests cover valid large input and legacy entry-point byte compatibility, monotonic progress, cancellation before malformed decoding, growth/truncation during reading and the 64 MiB compressed ceiling. Cancellation cannot interrupt a filesystem read already in progress. Concurrent same-size rewrites have no file identity snapshot guarantee and still must pass all decoder checks. These correctness tests do not qualify city/maximum performance. See `structure-file-read-validation.json`.

### Counting dependency surfaces without render buffers

Structure package and placement validation now use a count-only mode in the existing greedy model mesher. The scan, oriented colour mask, rectangle merging, clearing and surface-limit checks are shared with rendering. Count-only mode skips vertex/index emission. Rendered geometry still uses the original emission path. All per-model and package face limits remain unchanged.

Exact parent/current vertex and index array hashes matched across 12 fixtures at resolutions 8, 16 and 32. A shared-VPS component diagnostic ran three warmups and 20 measured batches per mode, alternating full render/count-only work. Each batch processed 64 seeded, fully occupied 16-resolution models with 1,536 merged faces each. Full/count median allocated bytes on the current thread were 85,284,352/140,800; p95 time was 111.575/66.905 ms. This is total thread allocation, not peak memory. It does not qualify populated fixtures or full-system budgets. Commands, full statistics and limits are in `structure-face-count-validation.json`.

### Render-face scratch allocation

The model mesher now emits coordinates and triangle indices directly. It preserves the order of float operations, vertex winding, normals, colours and greedy face merging. Twelve deterministic fixtures at resolutions 8, 16 and 32 produce the same raw render-buffer SHA-256 as parent `a41b2c8033d03cc8c69bf1cc95023772148c2c3e`: `39a6a09f93231f026425c3d20eda01ee780ac1508328e2c4ba57b68a4e367dc0`. Per-model and structure limits are unchanged.

On the shared Linux VPS, 20 measured batches after three warmups each rendered 64 coloured solid 16-resolution models with 1,536 quads per model. Parent/current median times were 84.550/78.655 ms, p95 109.632/86.878 ms and worst 120.433/87.925 ms. Median current-thread allocated bytes fell from 85,284,352 to 59,332,096. This measures total allocation, not peak memory. The count-only path remained at 140,800 allocated bytes per batch. Both executions overlapped the targeted test process; timings are diagnostic and do not establish a performance guarantee.

This component probe does not qualify populated city or maximum packages, physical-GPU frame times, durable saves, cold loads, server ticks or synchronization to 32 clients. Those gates remain pending.

### Full-fixture receipt checks

`tools/structures/check_qualification.py` checks measurement receipts; it does not run benchmarks, verify hardware claims or approve rollout. Run it with the exact tested source and agreed reference environment:

```sh
python3 tools/structures/check_qualification.py RECEIPT.json --head EXACT_SOURCE_SHA --environment-id AGREED_REFERENCE_ID
```

The receipt uses schema 1, `source_head`, `environment` and exactly two `fixtures`: `city` and `maximum`. Environment fields record client/server CPU, RAM and OS, GPU and driver, reference identity, network shaping and its measurement. Physical GPU and network-shaping verification must be explicit. Each fixture records its package SHA-256, dimensions, occupied cells, model and instance counts, compressed and decoded bytes, geometry leaves, admission and workflow evidence. Decoded size must be a positive integer no greater than 512 MiB; compressed size remains bounded to 64 MiB. City metadata must include 100 or more buildings, roads, pylons and fractional geometry. Maximum metadata must describe four million occupied cells, 512 dependencies and fragmented geometry. Corpus identity and these statements still require independent inspection.

Each fixture needs 20–100 measured runs with unique positive IDs, completion, 32 synchronized and rendered clients, empty dependency caches and exactly one placement. Required timing keys are `capture_s`, `export_s`, `preview_initial_s`, `preview_move_s`, `preview_rotate_s`, `placement_s`, `durable_save_s`, `cold_load_s`, `sync_all_clients_s` and `upload_s`. Other required metrics are maximum action acknowledgement `ack_ms`, `max_main_thread_stall_ms`, `min_fps`, `server_hz`, `tick_p95_ms`, `frame_p95_ms`, `frame_worst_ms`, `peak_memory_bytes` and `traffic_bytes`. Measure the affected workflow interval, including nearby usable geometry for placement. Verify progress/cancellation for long operations, actual durability/cold caches, upload exclusion from placement and synchronization timing from commit. An evidence reference is required; a text claim alone is not independent proof.

The checker computes median, nearest-rank p95 and worst. FPS and tick-rate worst values use the minimum. All runs must meet the acknowledgement, stall, frame-rate and server-rate limits; tick p95 must be strictly below 50 ms. Operation p95 limits follow the agreed city/maximum budgets. Every maximum-package upload must finish within 45 s. Missing, non-finite, negative, boolean or implausibly large numeric measurements fail. Duplicate JSON fields fail, and receipt input is bounded to 16 MiB. Synthetic test fixtures exercise acceptance and rejection paths; they are not game benchmark evidence. A successful checker result only means the supplied receipt meets this contract. Source, fixture, media, hardware and measurements still require independent review.

### Count-only geometry traversal

Capture and package decoding now call `SparseVoxelOctree.leafCount()` instead of constructing a leaf list only to get its size. The direct recursive traversal counts stored non-air leaves, including redundant legacy branches; it does not canonicalize geometry or count occupied voxel volume. Material validation, all caps and package bytes remain unchanged.

A shared-VPS component probe alternated list counting and direct counting for a frozen 16-resolution checkerboard tree with 2,048 leaves. Each of 20 measured runs after three warmups made 128 calls. List/direct median times were 9.325/2.412 ms, p95 23.462/2.604 ms and worst 27.311/2.763 ms. Median current-thread allocation fell from 14,789,632 bytes to zero per batch. These are component measurements with concurrent tests, not peak memory or city qualification. Full-fixture admission, physical-GPU and network gates remain pending.

### Streaming material validation

Package construction now visits stored leaves directly for material validation rather than building temporary `Leaf` records and a list. The visitor preserves octant order, coordinates, sizes and colours. Checked validation failures stop traversal immediately. Cancellation is checked at each visited leaf. Material rules, errors, geometry caps, model limits and package formats remain unchanged.

A shared-VPS traversal diagnostic compared list traversal and a reused visitor over a frozen 2,048-leaf, 16-resolution tree. After three warmups, 20 measured batches each visited 128 trees and checked the same ordered coordinate/colour checksum. List/visitor median times were 11.619/4.534 ms, p95 22.313/4.619 ms and worst 25.068/4.674 ms. Median current-thread allocated bytes were 14,793,728/0. Counter and visitor setup were excluded; tests overlapped the probe. These are traversal diagnostics, not full material-validation, peak-memory or city qualification measurements. Full-fixture and physical qualification remain pending.

### Placement anchor input

The `at X Y Z` command validates the candidate anchor against the existing world and build-height bounds before replacing the current target or scheduling a preview. An invalid candidate reports `Placement anchor outside world` and retains the prior target. Valid negative coordinates and exact world boundaries remain accepted. Server placement checks remain authoritative and unchanged.

### Resident geometry snapshot proofs

Offline structure snapshots now include immutable trees from resident chunks, including directly supplied chunks whose geometry differs from canonical terrain/history. Capture, occupancy checks and mesh validation read that detached geometry. Resident proof identity includes the chunk object, geometry version and canonical geometry/terrain stamp. Live edits, chunk replacement, residency transitions and terrain-generation changes invalidate reuse. Unknown or changed proofs still require authoritative destination and mesh checks; model-library revision checks remain unchanged.

In a shared-VPS diagnostic with 16 occupied cells across 16 resident chunks, three warmups and 20 real offline commits compared the separately compiled parent World with this implementation. Commit-time cell reads fell from 65,552 to 16. Parent/current median was 21.243/2.640 ms, p95 40.857/3.614 ms and worst 43.310/6.453 ms. Preparation was excluded; commit included durable save. Placed cells and reloaded geometry were checked outside timing. These small-fixture results do not establish city/maximum, peak-memory, GPU, frame, tick or network qualification. No admission limits increase.

### World-cell decode validation

World-cell decoding uses the checked stored-leaf visitor for material checks. This removes the temporary leaf list while preserving the cell wire layout, protocol version, material/position rules and frozen decoded result. Unknown, piece-encoded, encoded-air and fractional-model leaves remain rejected.

A shared-VPS decoder diagnostic compared separately compiled parent Protocol with current source. After three warmups, 20 batches each decoded 64 cells containing 2,048 leaves and 12,886 wire bytes per cell. Parent/current median time was 18.631/16.529 ms, p95 28.378/20.904 ms and worst 31.603/26.592 ms. Median current-thread allocation was 13,245,440/5,853,696 bytes per batch. Every decode checked position, occupied count and sample values; an exact roundtrip byte check ran before timing. This is cumulative allocation rather than peak memory and is not full structure synchronization or city/maximum qualification.

### Octree rotation

Quarter-turn geometry and model rotation remap octants directly and share immutable
leaves. Mixed legacy branches still collapse as they did under leaf-fill rotation;
zero-turn and uniform-region shortcuts remain. Returned trees are frozen and
detached from later edits to the source. All four rotations match the previous
algorithm byte-for-byte in seeded tests at resolutions 1, 8, 16, 32 and 256,
including encoded fractional materials and redundant legacy branches. Thread
interruption cancels rotation without changing the source.

A shared-VPS component diagnostic compared the retained leaf-fill reference with
octant rotation. After three warmups, 20 alternating measured batches each rotated
64 frozen, 2,048-leaf, 16-resolution trees by 90 degrees. Reference/current median
was 62.311/3.007 ms, p95 84.953/3.944 ms and worst 88.427/4.773 ms. Median
current-thread allocation was 48,290,816/2,697,216 bytes per batch. Exact output
bytes were compared before timing; output count and sample values were checked
after each batch. These are rotation-only diagnostics, not peak-memory or full
capture, preview, placement, save, frame, tick or network qualification. Package
admission limits and all city/maximum qualification gates remain unchanged.

### Structure-model admission

Staging structure dependencies, accepting their synchronized definitions and
reading their world-save entries use the exact greedy face counter instead of
building temporary render arrays. Standalone model registration, legacy custom
model acceptance and legacy world entries retain their existing paths. The
8,192-quad per-model limit, definition codec, fingerprints, IDs, freezing and
world dependency count/byte budgets remain unchanged. Rendering still builds
the same geometry when a model is used.

A shared-VPS staging diagnostic compared the separately compiled parent library
with this implementation. After three warmups, 20 measured batches each staged
64 distinct definitions with 1,536 quads per model. Parent/current median was
133.609/108.418 ms, p95 162.922/113.429 ms and worst 164.750/122.998 ms.
Median current-thread allocation was 91,433,560/32,242,264 bytes per batch.
Every batch checked IDs/fingerprints and the same serialized world-model SHA-256
outside timing. Parent and current ran in separate processes, parent first.
This is component staging and cumulative allocation, not peak memory, world-load,
placement, frame, tick, network or city/maximum qualification. All admission and
rollout gates remain.

### In-memory structure geometry

Saved structure cells keep uniform values inline and mixed geometry as exact
octree bytes in immutable 64 KiB segments. Individual cell access reconstructs a
frozen tree; export copies stored bytes without reconstructing it. Sorting keeps
position, model and geometry associations together. The appendable segment tail
is copied when publishing a snapshot, so later builder growth cannot change it.
Internal geometry storage is bounded at 512 MiB; existing package decoding,
material, leaf, instance and dependency limits still apply. This changes memory
representation, not VSTR versions or exported bytes. Placement and undo storage
remain separate work.

Cell records compare physical geometry values, positions and model references.
Their hashes use position and model reference, avoiding geometry traversal.
Callers can compare immutable cell lists by value; they must not depend on a
stable Java object identity for a reconstructed tree. Redundant legacy branches
still retain their original wire bytes.

A shared-VPS diagnostic compared admitted packages with separately compiled parent
package/cell classes. Three warmups preceded 20 measured export and full-visit
passes. Parent and current ran in separate processes, parent first. Every export
and final decode/export roundtrip matched the same package SHA-256.

| Geometry fixture | Retained heap estimate, parent/current bytes | Export p95, parent/current ms | Full visit p95, parent/current ms |
| --- | ---: | ---: | ---: |
| 100,000 uniform cells | 5,843,096 / 981,656 | 88.975 / 96.670 | 1.642 / 4.889 |
| 50,000 mixed cells, two fractional leaves each | 29,722,440 / 4,055,696 | 150.319 / 59.427 | 17.590 / 53.822 |

This trades tree-access cost for lower retained memory. Median allocation per
full visit increased from 0 to 2,400,000 bytes for the uniform fixture and from
0 to 31,600,000 bytes for the mixed fixture. The single post-GC retained-heap
delta is JVM-sensitive and is not peak memory. Uniform export p95 also increased
in this run; no general speed improvement is claimed. Full results, including
median and worst times, are in `structure-packed-geometry-validation.json`.
These small package diagnostics do not qualify city/maximum capture, preview,
placement, persistence, memory, frame, tick or network budgets. Admission caps,
supported-limit publication and stable rollout gates remain unchanged.

### Count-only chunk geometry validation

Structure placement now checks chunk geometry through `MeshDataGenerator.count`.
Count and render paths share the same fractional sampler, greedy full-cell face
scanner, neighbor visibility and water policy. Counting skips vertex/index
emission and material-buffer construction. It retains small thread-local scratch
buffers and fractional scan masks; this is not an allocation-free path.
The existing 6,000,000 vertex-float, 2,000,000 index and 24,576 fractional-cell
face limits remain. A rejected placement still publishes no world changes.
Rendered geometry, materials and wire formats are unchanged.

A component probe compared separately compiled parent `07fd590d` render validation
with count-only validation. Three warmups preceded 20 runs per fixture. The
parent ran first in its own JVM, then the count path ran in a separate JVM.
Parent/current render hashes and quad counts match for all three fixtures.
Timings include each operation; render hashing and fixture construction are
outside timing. Count invocation includes reflection overhead. Allocation is
current-thread allocation, not peak process memory.

| Fixture | Quads | Parent/count median ms | Parent/count p95 ms | Parent/count worst ms | Parent/count allocated bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| Full-cell water, glass and model marker | 720 | 2.373 / 2.658 | 3.671 / 3.268 | 3.927 / 4.057 | 224,752 / 104 |
| Eight separated stone checkerboard cells | 98,304 | 65.960 / 16.302 | 71.883 / 16.868 | 121.753 / 17.466 | 123,572,272 / 17,832 |
| Eight separated colored checkerboard cells | 98,304 | 66.201 / 10.481 | 68.604 / 11.014 | 74.641 / 11.178 | 123,572,272 / 17,832 |

The small fixture median and worst time increased slightly. Fragmented fixtures
show lower validation cost, but this does not establish placement latency,
frame or tick budgets. No city, maximum-package, GPU, shaped-network or
32-rendered-client qualification is claimed. Admission and rollout gates remain.

### Buffered structure synchronization

Structure synchronization now buffers decoded reads and encoded writes in
65,536-byte blocks. The decoded limiter remains below the read buffer; decoded
and compressed output budgets remain below the write buffer. Gzip completion,
CRC checks, cancellation between dependencies/cells, duplicate rejection and all
existing transfer limits remain. Stored octree leaves are counted directly
rather than materializing a temporary leaf list. Protocol and wire bytes do not
change. This does not raise the current two-million-cell/leaf transfer limits.

An isolated codec probe used 64 fractional cells, 131,072 geometry leaves and one
32-resolution model dependency. Its compressed delta is 192,676 bytes. Three
warmups preceded 20 runs per operation in separate parent/current JVMs, parent
first, without concurrent tests or native playtesting. Construction, SHA-256 and
per-cell physical equality checks are outside timing. Parent/current wire hash
is `09f19df5b369cbe0c86debd89b89a9726cbcd4a84a04273b3d2e18cca9460e79`.

| Operation | Parent/current median ms | Parent/current p95 ms | Parent/current worst ms | Parent/current thread allocation bytes |
| --- | ---: | ---: | ---: | ---: |
| Encode | 229.507 / 88.774 | 236.544 / 96.959 | 249.087 / 98.141 | 7,919,896 / 793,952 |
| Decode | 144.300 / 22.557 | 157.263 / 29.997 | 164.123 / 38.717 | 13,255,528 / 5,926,304 |

These are CPU codec timings and current-thread allocation, not peak memory,
network traffic or synchronization to rendered clients. New tests compare an
independent legacy writer byte-for-byte across buffers, preserve model IDs and
physical geometry including air, reject malformed CRC/size/truncated/duplicate/
trailing input, and retain cancellation and the decoded allocation guard. Small
counter limits exercise the guard; no full 512 MiB allocation is claimed. Real
TLS structure placement, late joining, restart and regressions also passed.
Full city/maximum admission and physical-GPU, frame/tick, shaped-network and
32-rendered-client qualification remain pending. Supported limits stay gated.

### Undo storage and live-world sharing

The packed-undo default was rejected. `WorldVoxels.replaceCell` copies the SVO
wrapper and shares immutable octree nodes. Existing undo maps therefore share
those nodes with the live world. A storage-only comparison can make packing look
cheaper while omitting the node graphs that the world still retains. Packing
also adds preparation and guarded-undo reconstruction work.

The candidate stays in `tools/structures/undo-prototype`, outside game sources.
Its deterministic checks cover physical/frozen values, position bounds,
model-reference values, all orientations, cancellation and immutable views. The
live game retains its existing undo maps, guards, history policy and formats.
Reproduction commands and measurement limits are in that directory's README.

The diagnostics compare 50,000 shared-uniform or two-leaf mixed cells. The
storage-only probe uses three warmups and 20 capture/visit runs; the live-world
probe retains actual `WorldVoxels` geometry and records three post-GC heap-delta
samples per fixture/mode. Neither is a peak-memory or full-system qualification.
The validation report records both comparisons and the decision. Compressing
canonical world regions is the next storage problem to measure before revisiting
undo packing. Full city/maximum and physical-GPU/network qualification stay gated.

The retained live-world diagnostic medians were:

| Retained state | Shared-uniform bytes | Mixed bytes |
| --- | ---: | ---: |
| World only | 5,512,400 | 34,449,384 |
| World plus existing undo map | 5,953,856 | 35,139,288 |
| World plus packed candidate | 6,011,200 | 38,417,160 |

All raw samples and the storage-only results are recorded in
`structure-undo-sharing-validation.json`. The mixed packed state retained
3,277,872 more bytes than the existing map with the world alive. These three
post-GC estimates are diagnostic, not a 20-run peak-memory qualification. Existing
undo remains the default; the candidate is not shipped in the game client.


### Synchronization position index

Structure delta decoding now tracks positions in one primitive `long[]` instead
of a `HashSet<String>`. Each valid position has an exact 53-bit key: 21 bits per
horizontal axis and 11 bits vertically. Coordinate checks precede packing.
Sorting the keys detects duplicates without hash chains, while the decoded cell
list retains its wire order. Duplicate validation finishes before the delta can
be returned or applied. A declared transfer count allocates at most 16,000,000
payload bytes for this index at the existing 2,000,000-cell limit; that number
excludes the array header, sorting workspace and the rest of the decoded delta.
Truncated or duplicate packages still reject without world mutation.

`tools/structures/WirePositionsProbe.java` compares the parent decoder and this
source with a shuffled 100,000-cell uniform delta. It uses separate JVMs, three
warmups and 20 measured decodes. Cell values and order are checked outside the
timed interval, and encoded bytes must match exactly. Decode median/p95/worst
fell from 74.964/102.579/158.642 ms to 38.203/55.839/57.548 ms. Median current-thread
allocation fell from 21,045,896 to 10,948,616 bytes. These are CPU/allocation
diagnostics, not peak-memory, frame-time, network or full-fixture qualification.
All city/maximum admission, physical-GPU and 32-rendered-client gates remain.


### Streamed canonical cell replay

`Protocol.CellState.forEachEdit` emits the air prefix and stored octree leaves in
canonical order without temporary leaf or replay lists. The existing `edits()`
API still returns the same mutable list, collected from that visitor. Indexed
whole-cell history replacement consumes the visitor directly. Edit records,
keys, history order, copy isolation, wire format and persistence caps stay the
same. Source trees must remain unchanged during a visit; structure publication
uses detached/frozen cells and stages history before publishing world changes.

`tools/structures/HistoryReplayProbe.java` compares the parent and this source in
separate JVMs, parent first, with three warmups and 20 measured builds. The
64-cell fixture has 131,072 fractional LED leaves and produces 131,136 history
edits. Serialized edit bytes and physical source leaves match. Median thread
allocation fell from 76,654,352 to 67,113,744 bytes. CPU results were mixed:
median/p95/worst changed from 120.918/208.163/221.251 ms to
133.338/174.608/176.591 ms. This reduces temporary allocation; it is not evidence
of lower median latency or a 100 ms main-thread bound. Hash checks run outside
timing, and GC/JIT effects remain possible in this single ordered comparison.

Canonical history still retains edit records, strings and map/index nodes.
Those persistent costs, owner-thread history/save work and larger partitioned
publication/storage remain unfinished. This is not peak-memory or full-city
qualification. City/maximum admission and physical-GPU, shaped-network,
32-rendered-client and 20-run full-workflow gates remain in force.


### Immutable history key reuse

Canonical history now reuses validated depth-zero key strings in its cell index.
Replacing an equal single-cell index member retains the existing immutable key.
Whole-cell replay computes one coordinate key and uses it for the air prefix,
full-cell leaves and cell-index lookups. Fractional edit keys are still distinct.
Those internal keys are derived from the cell and emitted immutable edits;
external map writes still pass the same public key validation. No string pool,
format change, replay reordering, revision change or resource-cap increase is
introduced. Copy-on-write history snapshots remain independent.

`tools/structures/HistoryKeyProbe.java` compares parent/current history builds
for 50,000 uniform or two-leaf mixed cells. Separate JVMs run parent first,
three warmups and 20 measured builds per fixture; serialized edit hashes match.
Median thread allocation changed from 29,697,640 to 15,697,640 bytes for uniform
cells and from 81,546,232 to 50,746,232 bytes for mixed cells. Uniform
median/p95/worst changed from 69.911/136.872/152.520 ms to
22.177/77.927/105.220 ms; mixed changed from 216.514/279.648/291.513 ms to
148.425/233.366/241.328 ms. These are single ordered CPU/allocation diagnostics,
not full placement or a 100 ms main-thread guarantee.

The same probe keeps an actual `WorldVoxels` instance alive with history, then
repeats with an additional copy-on-write history snapshot. Three post-GC samples
per fixture/mode estimate retained heap; these are not peak-memory measurements
or 20-run full-system qualification. All raw samples are in
`structure-history-key-validation.json`. The diagnostic medians were:

| Retained state | Fixture | Parent bytes | Current bytes |
| --- | --- | ---: | ---: |
| World and history | Uniform | 23,325,192 | 17,834,296 |
| World and history | Mixed | 78,762,728 | 76,013,752 |
| World, history and copy | Uniform | 29,265,808 | 23,534,064 |
| World, history and copy | Mixed | 89,516,992 | 86,753,888 |

Edit records, map nodes and canonical geometry still have substantial retained
costs. City/maximum admission, partitioned storage/publication/transfer,
owner-thread work and physical-GPU/shaped-network/32-rendered-client full-workflow
qualification remain unfinished. Supported-city and stable-rollout gates stay.


### Worker-built offline placement history

Offline library placement now builds its replacement save history on the library
worker. The game thread serves at most one queued history-read request per frame.
Each request captures at most 32 immutable batches of 1,024 key/Edit pairs:
32,768 pairs total. Index construction and placement replay happen on the worker,
with progress and cancellation checks. The queue accepts one pending request,
requires reads to run on its creating owner thread, and releases waiting workers
when closed or cancelled. Main pumps it even when the structure menu is hidden.

The private result is bound to its offline world, source history identity and
revision, and the exact immutable engine-prepared cell map. Final owner-thread
commit rejects stale history before publication, including equal-value history
replacement and edits outside the destination. Such edits require a retry;
they are never overwritten. Existing model, terrain, collision, durable-save and
undo guards remain. Inventory, health and drops are read at final commit, so
collecting items or taking damage during staging does not restore stale state.
Legacy/custom nonindexed history maps retain their previous synchronous path.

This moves the indexed placement-history phase only. Snapshot capture, conflict
revalidation, geometry publication, disk saving and undo still include owner-thread
work. No complete 100 ms stall bound or full-city latency claim is made.

`tools/structures/OfflineHistoryStagingProbe.java` compares the old synchronous
history algorithm with this staging path for 100,000 source edits plus 1,000 new
cells. The baseline reproduces history copy/replay only on the same unchanged
WorldVoxels implementation; it is not a full parent client/commit benchmark.
Separate JVMs run three warmups and 20 iterations. The staged probe sleeps 33 ms
between synthetic owner pumps; this is not rendered FPS. Ordered replay hashes
match at 101,000 edits. Measured results were:

| Metric | Synchronous | Staged |
| --- | ---: | ---: |
| Wall median/p95/worst ms | 32.936 / 88.848 / 145.750 | 204.159 / 266.902 / 270.702 |
| Per-run maximum owner duration median/p95/worst ms | 32.934 / 88.845 / 145.748 | 1.949 / 2.480 / 3.601 |
| Median owner-thread allocated bytes | 9,617,488 | 806,848 |
| Median worker-thread allocated bytes | No worker | 14,093,016 |

Staging trades higher wall latency and total allocation for shorter owner-thread
history reads. It rebuilds a detached index instead of copying shared index
entries. Five owner requests served the staged fixture: capture plus four groups.
The worker CPU p95 was 54.860 ms. Allocation is not retained or peak memory, and
owner-read timings exclude final commit/save/render work. Full city/maximum,
physical-GPU, shaped-network, 32-rendered-client and 20-run workflow gates remain.

### Offline placement save staging

The offline structure-library worker now writes the immutable model/history part
of a private save before publication. Version 8/9 field order stays unchanged:
the worker reserves the fixed 72-byte inventory plus one-byte health slot. After
model, destination and history revalidation, the owner patches that slot with
current survival state, appends current drops, forces the file and requests a
strict atomic replacement. The private save is bound to the exact prepared
history/cell map and model entries. Stale history or mismatched models require
retry before any world or save publication. Cancellation, close and failed
publication discard the private file. Legacy history-map callers and undo retain
the existing synchronous save path.

This removes model/history serialization and bulk buffered writes from the
library placement's owner completion. It does not remove geometry publication,
current drop serialization, file force or atomic rename from the owner. It does
not establish a 100 ms bound or qualify city/maximum packages. Those gates remain.

A synthetic 100,000-edit durable-save probe ran three warmups and 20 measured
saves per mode in separate JVMs, baseline first. Both modes wrote 2,400,119 bytes
with SHA-256 `142b1d16d06d2d90af542685be6742e766a76e798f5cebe6a10b191c2828d46c`.
The baseline calls existing `LocalGame.save()` (which attempts atomic replacement
and permits fallback); staged publication requires atomic replacement. This is
a save-only comparison, excluding structure validation and geometry publication.
Owner duration median/p95/worst was 33.957/37.802/37.985 ms synchronously and
2.482/2.697/2.784 ms for staged publication. Total wall duration was
33.957/37.802/37.986 ms versus 34.447/37.057/41.900 ms. These local filesystem
samples do not bound force/rename latency or establish peak-memory, frame,
server-tick, network or city/maximum qualification.

Private offline save staging also checks cancellation/interruption before each
file write of at most 64 KiB, including model definitions before history begins.
The output buffer may flush smaller records sooner. Cancelling a private save
closes its stream, removes the private file and preserves the prior durable
world. Final owner publication remains uncancellable after authoritative commit
begins. This bounds bytes between write checks, not model serialization time,
filesystem latency or cancellation latency.

A fresh save-only probe after this change used three warmups and 20 measured
runs per mode. Synchronous owner median/p95/worst was
33.204/43.115/44.307 ms; staged owner was 2.473/4.520/13.795 ms. Total wall
median/p95/worst was 33.204/43.115/44.308 ms versus
33.089/43.863/44.315 ms. All output bytes still match the hash recorded above.
No city, maximum fixture, peak-memory, rendering or network qualification follows
from this synthetic save probe.

### Uniform preparation cache

Preparation already shares frozen cells with the same uniform material. Its
material lookup now uses primitive keys and tree references instead of boxed keys
and map entries. It grows within the package's geometry-cell count, which the
existing 131,072-leaf admission cap bounds. Each final array has at most 262,144
slots; old and new arrays coexist while growing. Rehash and long collision scans
check cancellation every 1,024 steps. Instance caching remains separately bounded
by 512 dependencies. Formats, accepted limits and frozen-cell sharing are unchanged.

A proposed 1,024-entry cache was rejected. With 100,000 cells and 4,096 repeating
materials, it retained 76,000 after-image tree objects instead of 4,096. Three
post-GC samples retaining real WorldVoxels geometry and actual engine undo records
had median estimates of 14,530,272 bytes for that proposal and 11,217,400 bytes for
the parent. The final primitive cache retains 4,096 objects and measured
11,246,680 bytes. These are noisy retained-heap estimates, not peak-memory results
or a retained-memory improvement. Every sample checked actual undo afterward.

Separate parent/current JVMs each ran three warmups and 20 preparation runs per
fixture. Ordered cell wire hashes match. The repeated, unique and 4,096-material
palette fixtures have 100,000 cells; the bounded mixed fixture has 10,000 cells,
10% with two fractional leaves. Median current-thread allocation changed from
43,690,392 to 42,213,384 bytes for repeated, 50,644,400 to 46,284,880 for unique,
41,650,784 to 39,919,792 for palette and 7,550,124 to 7,406,140 for mixed.
Preparation p95 changed from 200.292 to 148.276 ms, 238.216 to 232.889 ms,
183.201 to 184.161 ms and 842.425 to 876.300 ms respectively. Palette and mixed
p95 regressed; no general latency improvement or hard stall bound is claimed.

An earlier exploratory 100,000-cell mixed case took parent median 9,033.051 ms.
The final mixed probe deliberately uses 10,000 cells to bound diagnostic runtime.
These probes do not qualify city/maximum admission, placement, persistence,
rendering, network or supported limits. Full qualification and stable-rollout
gates remain. See `structure-uniform-cache-validation.json` for exact commands,
samples, source hashes and limits.

### Private server survival metadata preservation

The tools-only world/library envelope now has inner format 4 for synthetic server survival state: at most 10,000 player records and 100,000 drops, bounded to 4 MiB with CRC and cancellation. Names, inventories, health and drops retain exact values. Model dependencies must exist. Inventories copy on capture and access; duplicate exact names, malformed fields and oversized input reject. Case-distinct identifiers remain distinct, matching the saved layout. This is saved-world state, never reusable structure content or authentication data.

Private generation restart and later geometry commits preserve server metadata and typed sidecar bytes. Existing formats 1–3 remain readable. Saved offline/server modes cannot be switched or stripped by a later stage, and a metadata update invalidates older staged work. The 16 MiB whole-envelope limit remains. The boundary fixture encoded 10,000 synthetic players and 100,000 drops in 2,610,020 bytes. This is a codec boundary check, not a multiplayer capacity claim.

Playtest: `java -Xmx512m -cp target:target/classes RegionServerMetadataCheck` passed 34 rejection cases and restart checks. The viewer ran the actual child CLI with exit 0; its own-window image was inspected. No multiplayer server was started and no authentication data was read. Manual saved-tail layout parity is not production server-save execution. The prefix-only sidecar fixture checks framing/byte preservation, not semantic CityFrame validity. Production server import, owner-thread integration, ordinary-edit persistence, directory durability, full city/maximum benchmarks, physical GPU and shaped 32-client qualification remain pending. See `dashboard/evidence/structure-server-metadata.json`.

### Private modern server base-file importer

The tools-only importer accepts `Protocol.MAGIC` plus modern server markers `-3` through `-9`. It preserves stored terrain identity, final geometry, the full saved model library (including unused aliases), saved player names/inventories/health and drops. It uses existing model, edit, inventory and drop codecs with the correct format branches. Older unversioned files reject explicitly. Duplicate identities/drops, trailing input, invalid counts/fields, missing models, unsupported terrain and cancellation reject before returning a candidate. It never opens authentication data or mutates a live server.

Admission is deliberately bounded to 16 MiB source, 8 MiB model section, 200,000 source edits, 100,000 final touched cells, 1,024 regions, 131,072 geometry leaves, 10,000 player records and 100,000 drops. The resulting private envelope must also fit 16 MiB, with server metadata at most 4 MiB. These are private migration caps, not published city support. Optional typed sidecar bytes stay outside structure packages. All buffers and parsing remain synchronous; game-thread latency and peak memory are unqualified.

Playtest: `RegionServerImportCheck` exercises 13 hand-built saved-schema fixtures across seven markers and three terrain versions where stored, private commit/restart, retry, malformed input and cancellation. These fixtures use production field codecs and expected geometry readers; no real server-save writer or server loader was executed. The prefix-only city sidecar verifies transport only, not semantic city validity. No authentication files were read and no multiplayer server started. Automatic live source-file migration and live owner-state publication and full city/maximum physical-GPU/32-client qualification remain pending. See `dashboard/evidence/structure-server-import.json`.

### Private server file-bundle source fence

`RegionOfflineBundlePrototype.captureServer` explicitly chooses the server codec while sharing the offline coordinator's fixed base/`.city`/`.city.history` slots and immutable snapshot. Capture and commit require the same caller-held monitor, a monotonic revision supplied by all cooperating writers, and unchanged exact file bytes and absence. Private parsing/staging can run outside the source monitor. A stale source, unsaved revision change, changed/removed/new sidecar or wrong selected codec rejects before destination publication. The source files are never replaced. This is a private migration contract, not a live server lock integration or exclusion of arbitrary external writers.

The shared synthetic suite now exercises both offline and server modes, including complete encoded payload equality before and after sidecar migration/restart, cancellation, failed publication cleanup and competing destination stages. Server fixtures use hand-built saved-world schema bytes; city/history files come from actual synthetic `CitySimulation.save`. The sidecar envelope preserves bytes and binding, not full semantic city validity. All existing byte/record limits remain. No authentication files are read and no multiplayer server starts. Automatic live migration, owner-thread use, directory durability and full city/maximum physical-GPU/32-client qualification remain pending. See `dashboard/evidence/structure-server-bundle.json`.

### Private LocalGame owner-queue backup adapter

`RegionOfflineOwnerPrototype` exercises a real synthetic `LocalGame` through the existing `StructureOwnerQueue`. A worker captures versioned history/model identity and revision plus detached survival data on the owner. History records stream back in batches of at most 1,024 through owner requests; bounded region construction and private file staging run on the worker. Publication must run directly on the original owner thread, after geometry/model and exact survival revalidation. The read-only queue never carries a mutation request. A different destination world identity or changed destination generation rejects. The live game and its legacy save path are never replaced.

The existing private migration limits remain: 200,000 source edits, 100,000 final touched cells, 1,024 regions, 131,072 leaves, 8 MiB region/library sections, 16 MiB private envelope and 100,000 drops within 2 MiB offline metadata. Custom unversioned history maps and uninitialized city-mode history reject explicitly rather than omit state. Game state must remain exclusively owner-thread managed; this adapter does not add synchronization to existing public fields. Owner metadata copies/revalidation and final private file publication remain synchronous and unqualified for 100 ms stalls or frame budgets.

Playtest uses actual LocalGame, owner queue and private generation restart, with geometry/model/inventory/health/drop changes between capture and commit, history identity/ABA, changes during capture, cancellation, wrong thread, wrong destination and competing stages. It captures a fresh CLI image. No production backend is selected, no legacy save is written, no server starts and no authentication data is read. City-state coordination, restoring/selecting a live region backend, server ownership, ordinary-edit persistence, directory durability and full city/maximum physical-GPU/32-client qualification remain pending. See `dashboard/evidence/structure-offline-owner.json`.

### Bounded city-history snapshot stream

`CityHistory.writeSnapshot(OutputStream, String, int)` writes the existing HIS1 version-1 history frame without opening files, collecting samples or changing simulation state. The caller supplies the lowercase SHA-256 of the exact matching city bytes and a byte budget from zero through the existing 64 MiB history cap. The limit includes the checksum and rejects before exceeding the caller's budget. The caller's stream is flushed and remains open; on failure it may contain a partial frame and must be discarded. No hidden full-history byte buffer is allocated by the writer.

The existing file save delegates to the same writer with its existing 64 MiB cap and publication policy. Serialization/fingerprint failures now remove the temporary history file and preserve the prior history. The field order, version, city binding and CRC remain unchanged. The private owner adapter uses this writer to capture city and history together; live region restore and backend selection remain pending.

Playtest: actual synthetic city save, stream/file byte equality, independent empty version-1 wire contract, production history load, exact/small output budgets, invalid binding, throwing sink, source immutability and retry. The populated history was 239,147 bytes; 11 rejection cases passed. Existing city history, history integration/network, city slots and format-16 regressions also passed. This persistence-only change uses a CLI integration and fresh own-window CLI image; browser/game rendering is not affected. Full city/maximum performance, owner-thread stall/peak-memory, physical-GPU and shaped-32-client qualification remain pending. See `dashboard/evidence/structure-history-snapshot.json`.

### Coordinated private active-city owner snapshot

The private LocalGame owner adapter now captures an active city's frame and history together. Owner-only capture writes the current frame through the existing version-17 codec, hashes those exact bytes, and uses the bounded history snapshot writer with the remaining combined 4 MiB sidecar budget. It never calls `collectHistory` or `save`; snapshot reads do not initialize or advance the simulation. City-mode history must already have a nonempty catalog and a clock no later than the frame. Otherwise capture rejects and the owner must initialize valid history through the normal game workflow. Sandbox frames retain the existing policy of no history sidecar.

The token retains simulation/history identities, history revision and exact serialized bytes. Owner revalidation checks all of them before private publication, including frame, clock and event-flag changes that may not increment history revision. The existing geometry/model/survival and destination-generation fences remain. Captured city/history bytes stay in the private world envelope and never enter reusable structure packages. Tests restore both through production loaders and compare frame, history identity, revision, catalog and latest time.

Playtest: actual synthetic LocalGame/city owner-queue capture, private generation restart, stale frame/history/clock/flags/identity rejection, capture-time changes, pre/mid cancellation and retry. Five affected integration tests passed; the CLI passed 28 rejection cases and kept legacy files untouched. Snapshot state remained unchanged. Fresh CLI image was inspected. This is a tools-only backup adapter; no live region backend, automatic restore or server owner path is selected. Owner serialization/revalidation is synchronous and unqualified for 100 ms/frame/peak-memory budgets. Full city/maximum admission, physical-GPU, shaped-32-client and directory durability qualification remain pending. See `dashboard/evidence/structure-city-owner.json`.

### Strict bounded city-history stream restore

`CityHistory.readSnapshot(InputStream, String, GameConfig, double, int)` decodes the existing HIS1 version-1 frame without opening files or closing the caller's stream. The caller supplies the exact city SHA-256, settings, finite nonnegative elapsed time and a budget from zero through 64 MiB. The decoder consumes at most the budget plus one over-limit probe; short, trailing, corrupt, wrong-city, invalid-clock/count and zero-progress sources reject. Existing epoch/metric/bucket limits remain. On malformed input it throws and returns no partial history; it never resets caller state. The caller handles source cancellation/timeouts and discards consumed partial input on failure.

The existing file loader delegates to the strict reader while retaining its established fresh-history fallback and sanitized rejection note. This gives coordinated region restores a strict path without changing normal saved-world recovery behavior. Tests verify stream ownership, exact limits, corrupt checksums, count violations with repaired checksums, malformed clocks, I/O failure, zero progress, source immutability and retry. Existing production history/save/network/format regressions also run. This does not select a live region backend or qualify complete city/maximum restores. See `dashboard/evidence/structure-history-reader.json`.

### Private detached complete restore

The tools-only world/library experiment can strictly decode optional city frames and matching history before physical restoration. It returns detached state, keeps missing history explicit, rejects malformed or cancelled input, and never installs live owner state. Five affected integrations and a synthetic restart/cancellation CLI passed; see `dashboard/evidence/structure-complete-restore.json`. Seventeen legacy cases cover empty wire fixtures only. Existing production constructor normalization prevents claiming full simulation frame equality after construction. Live region persistence, owner installation and all full-fixture qualification gates remain pending.

Private complete restore also accepts an expected game configuration. It rejects a city/sandbox sidecar mismatch before physical restoration, following LocalGame policy. Missing legacy sidecars remain valid. This guard is tested in `dashboard/evidence/structure-restore-mode.json`; live backend integration and full qualification remain pending.

The private generation store now exposes strict detached restart through `loadComplete`: validate terrain identity and whole-library format, then game mode and complete state before returning. It rejects dependency-only legacy generations without automatic migration and leaves disk bytes unchanged on failure. Empty saves return absence. Four affected integrations passed; see `dashboard/evidence/structure-generation-restart.json`. This is not production owner installation or full qualification.

The private engine authority now has an explicit strict startup constructor. It checks whole-library generation, terrain identity, expected game mode and city/history semantics before exposing its root. The synthetic engine workflow passed placement, strict restart, overlap rejection, undo and a second strict restart while retaining sidecars and survival state. The old opaque inspection path remains. This is not production owner/backend installation; full qualification stays pending. See `dashboard/evidence/structure-authority-restart.json`.

Normal offline saving now checks the existing2,000,000-edit persistence limit before opening any temporary file. This prevents overwriting a prior save with a file the loader cannot read. Oversized saves leave prior world/city/history bytes intact; versions8/9 and retry/reload passed targeted tests. This retains the existing limit and does not admit the maximum fixture. See `dashboard/evidence/structure-save-bounds.json`.

`WorldVoxels.History.cellCopyCursor()` now exposes touched cell coordinates in immutable batches≤1024 under an owner-lock/revision contract. Multiple fractional edits yield one cell; explicit air stays present. Independent COW copies remain isolated. A small actual offline save/reload and per-cell wire reconstruction passed26 production regressions plus3 affected integrations. The cursor enables future region persistence but does not switch the production backend or qualify city/maximum budgets. See `dashboard/evidence/structure-cell-cursor.json`.

`WorldVoxels.cellSnapshot(history)` pairs indexed cell batches with detached COW geometry. The capturing owner validates world/history identities, revisions and terrain generation for batches and immediately before publication. Worker reads return frozen cells and reject terrain changes. Callers must separately fence models and survival state. Snapshot creation still copies the chunk map, so owner-stall/memory qualification remains pending.37 production tests and3 affected integrations passed; see `dashboard/evidence/structure-cell-snapshot.json`. No region backend is selected by this API.

The private offline owner backup now has a fenced world-cell path. It reads positions on the owner and frozen geometry on a worker, then revalidates world/history, models, survival and city/history before private generation commit. The caller must supply a consistent world/history pair and serialize mutations on that owner. A4829-cell generated-city fixture passed strict restart and10 failure cases;34 production tests and4 affected integrations passed. This retains existing private limits and does not install a live backend or qualify large cities. See `dashboard/evidence/structure-owner-cells.json`.

A local 20-run diagnostic compares replay and world-cell private backup on a 4,829-cell generated city. Cell capture median/p95/worst was 38.15/54.04/67.75 ms, versus replay35.38/66.42/76.83 ms. Maximum owner-pump p95 was10.56 ms versus31.12 ms. Median capture increased, so this is not a general speedup claim. Heap is sampled live-JVM usage only. Canonical payloads and both strict restarts match. Raw rows and host/method limits are in `dashboard/evidence/structure-owner-capture-benchmark.json`. This small VPS diagnostic does not qualify the supplied city/maximum fixtures, GPU,32-client or rollout budgets.

Indexed private backup can now accept the existing offline input ceiling of 2,000,000 records while enumerating final cells directly. Replay backup retains its 200,000-record diagnostic cap. Final cell/chunk budgets are checked before cloning the world map; geometry/leaf/envelope caps remain unchanged. A 200,704-record history describing only 49 uniform cells passed capture, strict restart and stale-state/retry checks. This does not admit either full city/maximum fixture or establish supported large-structure limits. See `dashboard/evidence/structure-dense-history.json`.

### Incremental owner snapshot experiment

The production world helper now offers an owner-only builder that copies at most128 chunk-map entries per advance. The private indexed backup uses it with cancellation and revision fencing. A257-chunk correctness fixture completes in3 calls and rejects stale partial captures. Existing one-shot capture remains available. Count batching does not establish a100ms stall guarantee: allocation/rehash, GC and metadata capture still need measurement. This does not select a production region backend or raise supported limits. See dashboard/evidence/structure-incremental-capture.json for actual checks and qualification gaps.

### Capture iteration after churn

Chunk and distinct-history-cell indexes use linked iteration so capture cursors do not scan unused retained hash-table capacity between entries. History copies retain this map type. This adds entry-link memory overhead; peak memory remains unmeasured. A synthetic8192-to257-chunk churn check verifies3 capture batches,unchanged source and stale-clear rejection over20 runs. Diagnostic timings vary across runs; no general speedup or100ms guarantee is claimed. See dashboard/evidence/structure-chunk-churn.json. Full fixtures,production backend integration and qualification gates remain pending.

### Private region stream codec

The diagnostic region codec accepts caller-owned input/output streams while retaining its version1 bytes and existing caps. The decoder expects one frame ending at EOF, checks CRC, supports cancellation and reads at most its byte cap plus one trailing-data probe. The writer can leave a prefix on failure; callers must stage output before publication. Neither API closes caller streams. Blocking IO belongs on a worker. This removes the stream API's need for a whole encoded-input array; decoded geometry is still materialized. It does not select a production backend or establish memory/performance qualification. See dashboard/evidence/structure-region-stream.json.

### Private envelope section views

The private envelope decoder now parses its library and geometry through bounded views of the existing encoded envelope array. The geometry parser uses the region stream API. Each view ends at its declared section boundary, preventing a valid geometry parser from reading following metadata. Versions 1–4 retain byte-exact roundtrips; invalid lengths and CRC corruption still reject. This removes redundant encoded section copies. The outer envelope and decoded state remain materialized, so no peak-memory improvement is claimed. Existing limits and production integration gates remain unchanged. See dashboard/evidence/structure-envelope-view.json.

### Generation payload range decoding

The private version2 generation loader passes its validated file array directly to a bounded offset/length envelope decoder. This avoids another encoded payload array; legacy dependency generations keep their prior path. Envelope CRC and section boundaries exclude unrelated prefix/suffix bytes. Tests cover all four envelope versions, padded array ranges, invalid ranges and actual generation restart. File bytes, decoded state and staging buffers still allocate; no peak-memory or speedup claim. Formats, limits and production rollout gates remain unchanged. See dashboard/evidence/structure-generation-range.json.

### Stored override cursor

A detached production world snapshot can now enumerate stored override cells on one worker without rebuilding or consulting flat leaf-edit history. Batches contain at most1,024 frozen cells and preserve explicit air; implicit seeded terrain is omitted. A caller cell budget and terrain epoch bound the traversal, and failure invalidates that cursor. Source changes still require the final owner validation to reject publication. The private backup uses this cursor and can restart a synthetic2,054-cell world with no flat records. This is a persistence building block: normal startup/save and production region backend integration remain unchanged. Existing caps and qualification gates remain in force. See dashboard/evidence/structure-world-override.json.

### Production bounded offline replay

Version-10 offline structure saves keep seed, generator, full model definitions, inventory, health and dropped items. Replay starts with an integer record count, bounded by the existing 2,000,000-record cap. Each following section stores chunk X/Y/Z as three integers, record count and body length as unsigned shorts, a CRC32 integer, and the edit payload. Sections contain 1–1,024 consecutive records from the same chunk and exactly 24 bytes per record, at most 24,576 payload bytes. Regions may repeat; file order remains replay order. Empty replay has only the zero count. The dropped-item tail follows the declared replay, and version 10 rejects trailing file bytes.

Both staged writes and normal version-10 resaves use the bounded encoder. Loading checks section count, length, checksum, edit validity and chunk identity before applying that section to private load history. Failed or cancelled staging removes its private file. Successful structure publication still requires the existing exact-state validation and atomic save replacement. Older readers do not understand version 10.

This bounds replay section buffers, not total live memory. The canonical History still materializes all records. There is no indexed lazy startup, disk-backed world root or lifted city/maximum admission cap. Full fixture admission and all physical-GPU, 32-client and 20-run performance qualification remain pending.

### Production server replay sections

Staged server structure saves now use marker -10 after the existing protocol magic, followed by seed, generator, the full model library, the same bounded replay sections as offline version 10, and the existing player/inventory/health/drop tail. The server validates referenced model IDs before applying a complete section to private load history. Version -10 rejects trailing bytes. Existing server formats remain readable. Legacy ordinary worlds retain version -8/-9 until a staged structure save publishes; version -10 then remains in use for later saves and restarts. The format flag changes only after successful atomic publication. Network protocol and city sidecar bytes remain unchanged.

Replay buffers are bounded; live history is still materialized and still capped at 2,000,000 records. This does not lift geometry, model-surface or affected-chunk caps and does not qualify full city/maximum fixtures, cold-cache loading, physical rendering or synchronization budgets.

### Cancellable server model-prefix writes

Server structure save staging now checks cancellation below the 64 KiB file buffer. Oversized model writes are split into writes of at most 65,536 bytes, with a check before each write and flush. Cancellation before entry creates no stage directory; cancellation during or after prefix encoding closes/removes the private stage and preserves the committed world. The final atomic publication step remains uncancellable.

Tests exercise both a 512 KiB bulk write and actual production model serialization before replay, followed by cleanup and retry. File metadata can precede the first model slice, so total staged length is not a single-write size. This is a bounded cancellation checkpoint, not a measured cancellation-latency or stall guarantee. Full qualification remains pending.

### Region-only UI snapshots

Capture, destination preview and offline placement now assemble snapshots on a worker through bounded owner requests. Each request copies at most 128 relevant chunk entries, preserving canonical COW buckets, actual resident geometry and validation stamps. The owner checks edits, resident geometry, terrain and model revisions before each batch and at completion. The worker builds the bounded chunk grid and lookup; no live chunk-map iterator spans frames. Busy commands are rejected before snapshot work starts.

Detached sources keep their captured geometry after later edits. Terrain changes invalidate their reads. Some unrelated global geometry changes conservatively reject a partial snapshot and require retry. The synchronous whole-world snapshot API remains for compatibility. Model metadata and the region lookup still materialize, and COW edits can allocate bucket copies. A 128-chunk batch bound does not establish a 100 ms stall, memory or frame-time guarantee. Admission caps and full city/maximum, physical-GPU and 32-client qualification remain pending.

### Position-only conflict queries

The UI conflict worker reads the immutable occupied-position index without decoding source-package fractional geometry. It skips destinations proven empty, checks actual occupancy when a hint is unknown and stops at the first conflict. Empty source cells are not checked or changed. Placement still performs authoritative validation; the UI still discards results for changed targets or orientations.

An admitted 50,000-cell/100,000-leaf fractional fixture was measured over 20 runs after three warmups on the VPS. Hinted-clear query p95 was 2.65 ms versus 50.85 ms for the previous loop, with median query allocation 1,200,248 versus 35,200,024 bytes. This excludes region snapshot construction, rendering, frame/tick/network/durability and UI acknowledgement; it is not the required full-fixture qualification. Exact commands, environment and all scenarios are recorded in `dashboard/evidence/structure-position-preview.json`. All supported-limit and rollout gates remain pending.

### Worker-staged offline undo

Offline undo now starts a cancellable worker operation. It captures and validates the placed region, copies save history and writes a private save before owner-thread publication. Cancelling before publication leaves the placed construction and committed file intact. Failed publication retains the undo record for retry. Current inventory, health and dropped items are patched at publication, as for placement.

The owner reuses detached equality checks only when every trusted chunk stamp is unchanged. Changed or unavailable stamps require authoritative comparisons, and changed placed cells reject undo. Final rollback snapshots, cell replacement and legacy-map saves still involve owner work. This does not establish a 100 ms stall/latency guarantee, lift admission caps or complete city/maximum, physical-GPU or 32-client qualification. See `dashboard/evidence/structure-worker-undo.json` for actual tests.

### Verified rollback-image reuse

The structure engine now passes its frozen, verified before-images to local publication. Successful authoritative checks still precede publication; the before-images are an internal engine contract, not wire data. Legacy authority staging overrides retain their dispatch, and direct local commits still capture current rollback state.

Whole-cell replacement also counts canonical replay records without building a throwaway edit list. Replay order, saved formats and edit revisions remain unchanged. Final per-cell owner publication and fallback checks remain, so this is not a 100 ms stall guarantee or full city/maximum qualification. Exact tests are in `dashboard/evidence/structure-publication-images.json`.


### Worker-prepared offline cell installation

Offline structure history staging prepares each cell's detached replay state, exact record count, cell key and canonical frozen geometry on its worker. Owner publication installs that immutable data after the existing authoritative destination, history, model and player checks. The private-constructor preparation result contains no world reference. Mutable callers remain mutable; later caller edits cannot change staged results. Original redundant source branches still determine canonical replay records and revision counts, while installed geometry folds equivalent branches. Direct legacy replacement and rollback still prepare synchronously.

This removes cell validation/canonicalization from the staged owner path. It retains more prepared geometry during staging. History traversal, per-cell installation, resident updates and fallback checks remain owner work. No main-thread latency or peak-memory qualification follows, and no package format, admission cap or supported city limit changes. See `structure-cell-prepare.json` for current validation.


### Prepared-cell component measurement

A 20-run CLI comparison on the current direct/prepared APIs used admitted 64×1×64 fixtures with 4,096 cells and either 4,096 uniform or 131,072 fractional leaves. Three warmups, alternating path order, fresh nonresident worlds; source fractional geometry repeats 32 LED/glass unit leaves per cell. This is not a historical parent-binary replay. Exact geometry/material/colour, history contents, per-chunk replay order, revision and unrelated-edit checks passed after every run.

Fractional direct-owner median/p95/worst was 162.945373/186.831847/193.220087 ms. Worker preparation was 91.544849/115.997464/127.934529 ms; prepared-owner installation 81.536372/106.842094/110.901081 ms. Sum of per-run worker+owner phases was 179.668086/199.660728/209.749606 ms. Median owner allocation fell from 69,486,856 to 43,114,376 bytes; combined allocation was 69,503,312 bytes. This shifts work and allocation; it does not establish lower total cost. Owner installation still exceeds 100 ms in this component case. Queue/transfer, setup/verification, conflict checks, resident meshes, save/network and complete placement are excluded.

Whole-probe peak RSS was 591,528 KiB with `-Xmx512m`, including fixtures, warmups, verification and Swing evidence. It is not retained or per-phase peak memory. CPU/OS/Java, raw samples and independently checked statistics are in `structure-prepared-cell-benchmark.json`. Full city/maximum, GPU, 32-client and latency/memory/frame/tick/traffic/durability qualification remain pending. No supported city limit or cap increase follows from this diagnostic.


### Immutable prepared replay records

Worker cell preparation now builds immutable edit records, key strings and a private read-only cell index. Staged global history and world chunk history reuse this data. Every history owns its ordered-map entries; edits copy a shared cell index before changing it. The replay contains no geometry. Clear/full-cell duplicate writes keep exact revision counts and final order. Legacy map/state paths and saved formats remain unchanged.

The same admitted 4,096-cell component probe measured fractional prepared-owner median/p95/worst of 17.959288/67.130305/74.876080 ms, versus prior 81.536372/106.842094/110.901081 ms. Median owner allocation fell from 43,114,376 to 9,192,264 bytes. Worker preparation was 141.230388/170.318103/181.610022 ms; combined measured phases were 167.6186295/194.513675/197.024598 ms. Combined fractional median allocation was 67,608,528 bytes; uniform combined allocation increased to 4,383,376 bytes from 3,427,344. Preparation retains additional arrays/indexes earlier.

These are separate-process shared-VPS observations with the same helper, not interleaved version trials. Queue/transfer, setup/verification, conflicts, resident meshes, save/network and full placement are excluded. Whole-probe peak RSS 593,980 KiB with -Xmx512m includes fixtures, warmups, verification and Swing; it is not retained/per-phase memory. Per-record map insertion and per-cell root/metadata/resident work remain on owner. See `structure-prepared-replay.json` for raw samples, commands and checks. No cap increase or complete 100 ms/city/GPU/32-client qualification follows; rollout gates remain.

### Prepared placement neighbor invalidation

Offline prepared structure publication now uses `World.replaceCells` to group neighbor mesh invalidation by changed source chunk. It keeps virtual per-cell installation, geometry/history order, raw revision counts, resident updates and caller-owned rollback. Nested batches share the outer group. The outer `finally` clears batching state and flushes notifications even after a partial failure, so later ordinary edits notify normally. This helper is not a transaction and does not replace authoritative validation or save publication.

A regression check compares resident and stored fractional geometry, replay order/revisions, explicit air, multiple chunks and unload/reload against individual installs. Nine edits in one source chunk notify its loaded neighbor once instead of nine times. Other checks cover nested virtual dispatch, post-install failure, null input and ordinary edits after a batch. Existing placement rollback/retry, worker undo, save failure, TLS multiplayer and streaming tests remain required. This reduces repeated mesh invalidation work; no wall-clock speedup or 100 ms stall guarantee is claimed. All geometry/instance/chunk/history caps remain unchanged, and full city/maximum admission and qualification remain pending.

Validation of this batching change: all 121 targeted tests and the full 1,013-test Java suite passed, with one optional browser test skipped in the full suite. Fresh Main/GLFW workflow passed; all 24 video frames decoded and footprint/cancelled-undo images inspected. Software rendering at HUD 1 FPS is functional evidence only. Detailed commands, source binding and limits: `dashboard/evidence/structure-cell-batch.json`. New source and media await controller publication and exact-source hosted checks.

### Neighbor batching component measurements

`NeighborBatchBenchmark` compares individual prepared installs with the batch API on the same immutable replacements. Both admitted fixtures contain 4,096 cells in 64×1×64 bounds; uniform has 4,096 leaves, fractional has 131,072 leaves. Four empty neighbor chunks are loaded. Three warmups precede 20 measured runs; three installation paths rotate order. Every path verifies exact geometry/material/colour, complete history contents, per-chunk replay order, revisions and an unrelated edit. Notifications fall from 1,024 to 4.

| Fixture / prepared owner path | Median ms | p95 ms | Worst ms | Median allocated bytes |
| --- | ---: | ---: | ---: | ---: |
| Uniform individual | 2.767346 | 5.244825 | 5.441996 | 1,939,688 |
| Uniform batch | 3.292908 | 7.119381 | 12.929391 | 1,753,008 |
| Fractional individual | 21.173334 | 33.308374 | 46.627648 | 9,216,840 |
| Fractional batch | 19.665899 | 64.387612 | 75.172040 | 9,030,160 |

Allocation and notification counts improve; timing does not improve consistently. In particular, fractional p95 worsens. These shared-VPS measurements do not establish a speedup, a main-thread stall bound or a supported limit. Setup, validation of the package/fixture, queue/transfer, authoritative conflicts, changed-cell resident geometry/meshing/GPU, saves/network and full placement are excluded. Worker cell preparation is measured separately. Whole Java child peak RSS is 608,052 KiB with `-Xmx512m`, including fixtures, warmups, verification and Swing; allocation counters do not measure retained or peak memory.

Playtest: the actual CLI completed all 20 runs per path/fixture, its result window was captured on inherited display/authentication and inspected, and all raw statistics were independently recomputed. Two selected tooling integrations passed in 8.681 seconds and compile the new benchmark through the common source list. No production/native harness source changed; all 228 hashes still match the completed native workflow. No new Maven/native run is claimed. Browser playtesting does not apply to this CLI-only change. Commands, raw samples, environment and limits: `dashboard/evidence/structure-neighbor-benchmark.json`. Full city/maximum, physical GPU, shaped 32-client and all required game budgets remain pending; rollout stays gated. New tooling/media await controller publication and exact-source checks.

### Immutable prepared cell images

Prepared offline installation now shares a factory-owned frozen cell image between canonical world overrides and edited-cell metadata. `WorldVoxels.prepareCellImage` copies/freezes a detached wrapper on the worker; `installCellImage` stores it without another owner-side wrapper copy. Later edits still fork persistent geometry. The existing tree-taking `replaceCell` keeps its caller-copy/freeze behavior and signature. The image factory enforces 16-resolution storage, not material, destination or resource policy; `World.prepareCellReplacement` retains its existing canonical replay/material preflight before preparing an image. Authority checks, rollback, replay order/counts, package/world formats and all admission limits remain unchanged.

Tests cover worker preparation, mutable caller independence, frozen mutation rejection, reuse across positions/worlds, older snapshots, later edits, mutable copies, exact bytes for air/uniform/model/fractional/packed/redundant cells, legacy API isolation and invalid input without world mutation. Production tests verify shared immutable metadata and independent placed-world edits plus resident unload/reload. Full game qualification is still required; this change does not establish a retained-memory or main-thread timing bound.

Validated immutable-cell-image source: all 125 targeted tests passed; the full Java run completed with exit 0, 1,017 tests across 203 fresh current suites, zero failures/errors and one optional `AssetManagerIntegrationTest.browserWorkflow` skip. Commands and raw component measurements are recorded in `dashboard/evidence/structure-cell-image.json`.

Playtest: `python3 deploy/run_structure_playtest.py --display "$DISPLAY"` passed in production Main/GLFW on inherited X11, llvmpipe, and an isolated synthetic profile. It exercised named capture, browsing, self-contained export/import, all four rotations, footprint/conflicts, preserved empty space, player/terrain rejection, placement/undo, cancellation, malformed import atomicity, repeated placement, dependency restoration, restart, inventory and model editor regressions. All 228 production/harness hashes match the benchmark, native run and current source. No temporary profile files remain. The fresh F10 video fully decoded: 23 frames, 41.469 seconds, 466,739 bytes, 960×544, SHA-256 `6d4016df78a1babe89e8541d6bd67439226fd4479dca02385388d97f901e305b`. Midpoint and cancelled-undo views were inspected. The 1 FPS software-rendered run is functional evidence only.

The unchanged component benchmark completed three warmups and 20 runs per path/fixture. Prepared owner allocation fell by 261,888 bytes per path/fixture versus the prior separate run; worker allocation increased by 65,536 bytes for uniform and 163,840 bytes for fractional geometry. Whole child peak RSS rose from 608,052 to 625,844 KiB. Fractional batched owner median/p95/worst were 19.352275/32.357289/55.994688 ms; uniform values were 2.644744/4.372926/7.044521 ms. These separate shared-VPS cohorts establish neither a speedup nor lower retained/peak memory. Full placement, changed-cell resident geometry, meshes/GPU, persistence and networking are excluded. Both full fixtures still exceed production admission caps; full city/maximum, physical GPU, shaped 32-client and required 20-run game qualification remain pending. Stable rollout stays gated.

`git diff --check` passed. No tracked deletions exist against review base `71612f09bfec4549456b3317676e76cdced085a4`; restored MP3 tests/fixtures and Hub workflows match it. No unrelated deletions are intended. Source, sanitized reports and fresh media await controller publication and exact-source hosted checks. Continue live region/root/history/backend work after those checks; do not mark ready from this scoped result.

### Private directory experiment

Immutable directory roots were tested without changing the live chunk map. See `docs/structure-directory.md` for contracts, exact checks and all measurement limits. The tree keeps old roots intact, but its query cost is substantially higher than the hash-map baseline. Production retains its hash map. Full backend and city/maximum qualification remain pending.

### Private shared hash directory

A 64-shard linked hash experiment makes read-only root capture cheap, but its placement-shaped writes still copy all 8,192 entries. It stays outside production. See `docs/structure-shared-directory.md` for exact contracts, checks and measurements. Full backend and qualification gates remain pending.

### Immutable chunk roots

`WorldVoxels` now shares immutable bitmap hash-trie roots. Existing cell-bucket copy-on-write isolation remains; full-hash collisions use balanced coordinate trees. A primitive, sampled read cache verifies the root and every coordinate. Flat history order and all admission/format/material policies stay unchanged. Focused checks pass; full regression and fresh native validation are pending. Diagnostic tradeoffs and final results are tracked in `dashboard/evidence/structure-hash-trie.json`. Random reads remain costlier than the prior linked map. Full city/maximum and all required game qualification remain pending.

Immutable-root validation completed: 1,025 Java tests across 204 fresh suites passed with one optional browser skip; all 31 region-backend integrations and fresh Main/GLFW workflow/media passed. See `docs/structure-hash-trie.md` for exact results, source binding, the corrected parent Mac save fixture and measured read-cost tradeoffs. Full city/maximum and physical GPU/32-client qualification remain pending; rollout stays gated.


### Persistent insertion-ordered history roots

`History.copyOf` now shares immutable key/order roots and rotates existing per-cell COW ownership. Replay insertion order and read-only entry contracts remain unchanged. Offline structure staging captures roots once and revalidates identity/revision once on the owner; server staging captures under its authority lock. Both final commit fences remain. Worker preparation/save staging stay detached. This replaces the old repeated-batch history copier; it does not raise admission limits or remove full-history serialization.

Full Java retry passed 1,031 tests/205 fresh suites with one optional browser skip and zero failures/errors; all 32 backend checks and the serial Main/GLFW workflow passed. Fresh video was fully decoded and inspected; all 230 source hashes match. The first full-run obsolete reflection-test error is retained in the report, alongside the actual staging/concurrent-edit/retry/cancellation replacement coverage. Copy allocation is lower in the 100,000-record component fixture; reads, iteration and first writes remain slower. See `docs/structure-history-roots.md` and `dashboard/evidence/structure-history-roots.json` for exact metrics, contracts and limits. Source/media await controller publication and exact-source hosted checks. Full city/maximum, remaining live backend and required physical-GPU/shaped 32-client/20-run game qualification remain pending; stable rollout gated.


### Bounded regional replay delivery

Version 10 loaders share a regional decoder with a worker-owned section sink.
It validates the checksum, count, coordinates, values and application predicate
for a whole section before delivering an immutable list of at most 1,024 edits.
Delivery retains file order, including recurring regions, and leaves the save
tail unread. The sink can throw `IOException`; cancellation is checked before
I/O, per record, and before and after delivery. Blocking I/O still belongs on a
worker. A sink must stage privately: an error in a later section does not undo
previous deliveries. Whole-world publication remains a separate transaction.

This seam lets future disk-backed migration consume validated sections without
first constructing a global replay map. Existing offline/server loaders still
rebuild that map through the same decoder. It adds no lazy startup, disk-backed
live history, new save version, admission allowance or qualification claim.
The 2,000,000-record persistence limit and all structure limits remain intact.


### Private replay spool

`RegionReplaySpool` stages validated version 10 replay sections in an owned
private temporary file on a worker. It retains a path, count and encoded size;
it does not build a global edit map or section index. The existing
2,000,000-record limit yields a worst-case 88,000,004-byte file when every edit
needs its own 20-byte section header. A caller may tighten this byte budget.
Each section still contains at most 1,024 edits. Reopening checks the sealed
length and total count, then validates every section and exact EOF. A sink must
stage privately because later sections or the final EOF check can fail.

Cancellation, malformed input, I/O errors and callback exceptions close and
remove the operation's own file during staging. Completed handles are confined
to their creating worker, reject reentrant operations, and require close to
remove their file. Cancellation or sink failure during a read leaves the
completed handle available for retry. The source and caller stream remain
owned by the caller; the world tail is left unread. Callers must validate that
tail and dependencies, revalidate authority, and publish a whole transaction
separately. File forcing applies only to this private candidate; it is not a
world commit. Blocking disk operations must never run on the owner thread.

This is a migration prerequisite, not a new live startup path or save format.
Existing loaders still build global history maps. Live region roots, lazy read
states, geometry/history/save publication and large multiplayer integration
remain unfinished. No structure/persistence allowance or qualification gate
changed. See the scoped `structure-replay-spool.json` evidence report.

The caller must supply a stable source generation and retain final identity/revision
fences. Per-section CRC is not a source-generation or adversarial identity proof.
Completed spools live in a trusted private staging directory. Crash orphan
recovery is not implemented; normal close/failure cleanup is scoped to one file.


### Offline startup loading

The renderer now loads the offline save on one read-only worker after the
window and overlay exist. While it waits, it polls input and draws the number
of decoded replay records with an Escape hint. Legacy and regional decoders
check cancellation per record; input below buffering checks around reads of at
most 64 KiB. Only a completed private `LocalGame` is assigned to the scene.
Close fences publication with cooperative cancellation and orderly shutdown.
It does not interrupt an active file-reading worker from the renderer: NIO
interrupt-driven channel closure can itself wait for active I/O. A daemon
blocked inside the filesystem can remain until that call returns, then observes
the cancel flag; it never writes the source. No GL work runs on this worker.

Initial geometry replay preserves order and polls input after 128 records or
2 ms of work. It draws an initial acknowledgement, then coalesces progress
redraws until at least 16,666,667 ns after the last draw finishes. Cheap batches
therefore do not each force a frame swap. These are scheduling defaults, not
measured stall bounds: one edit or a GL swap can take longer. Escape also
cancels this phase.
Normal cleanup saves world/vehicle state only after scene initialization
finishes. Existing explicit asset-intake and city-startup writes retain their
separate behavior; this is not a general transaction over every startup sidecar.

Live history and initial geometry still materialize in memory with existing
limits. The replay spool is deliberately not added as an extra disk pass on
ordinary loads. This responsiveness step does not provide lazy region roots,
loaded/pending/failed physics/render queries, large atomic publication or city
qualification. Full performance, memory and multiplayer gates remain pending.

### Dense replay loading

Validated offline replay and version-10 server replay use an exact-key path for
finest-grid (depth-4) records. A valid 1/16-cell edit cannot fully cover a different
valid key. Loading removes that key, then appends the new record so replay order
is unchanged. Coarser ancestor edits remain in history. Larger incoming edits
still use coverage pruning. The generic editing API keeps its existing behavior;
the fast path requires every existing replay record to be valid.

This avoids a whole-cell key snapshot and coverage scan for each finest-grid
record. It does not remove the eager history/geometry load, change any save
format, increase admission limits, or qualify city/maximum packages. The small
`deploy/ValidatedReplayBenchmark.java` fixture measures history construction only;
it is not a capture, placement, durable-save, rendering or network benchmark.

On the shared 4-vCPU Haswell VPS (Linux 6.1.0-50-cloud-amd64, Oracle Java
25.0.3, 512 MiB heap), eight fully fragmented cells/32,768 records took a median
9,979.77 ms with coverage scanning and 57.76 ms with validated exact-key replay.
Median thread allocation was 588,356,936 versus 47,804,328 bytes. There were two
warmups and five measured runs. Part of this diagnostic overlapped the full
Java regression; it is not a controlled reference-environment performance claim.
Thread allocation is not peak memory. Raw measurements and commands are in
`dashboard/evidence/structure-dense-replay.json`. Required 20-run full-fixture
qualification remains pending.

### Private offline geometry replay

After the saved state and asset intake are ready, offline startup captures an
immutable history root and prepares the geometry on one daemon worker. The
worker builds a private `World`, including the same ordered edits, fractional
geometry, sky indexes and model-library identity used by normal play. A saved
stress grid is installed before replay. No chunks, terrain scheduler, GL objects
or file handles are created by this preparation. The render thread shows progress
and handles Escape while the worker checks cancellation before each edit.

The completed world transfers once, only if the original history identity and
revision and model-library identity and revision still match. Source changes,
cancellation and worker failure publish no partial geometry. Closing the startup
handle never joins or interrupts an active worker. A cancelled worker clears its
private world at its next cancellation check. A completed result discarded in a
close race contains only heap state and is reclaimed normally. Closing the handle
after transfer does not close the live world. The product loader supplies indexed
history; this private startup helper rejects generic maps before starting work.

This moves CPU replay off the render owner. It still materializes full history,
geometry and indexes in memory. Multiplayer startup retains its existing replay
path. Source save formats, normal editing, model resolution, admission caps and
qualification gates are unchanged. It does not provide lazy disk-backed regions
or prove a 100 ms acknowledgement, 30 FPS, city/maximum or network budget.

Asset intake, city-sidecar loading and later GPU initialization retain their
existing owner-side paths. This change is not a general nonblocking startup
transaction or a guarantee for all loading stages.

### Bounded private replay cell builder

Private offline replay now retains one mutable 16³ cell. Consecutive edits to
that cell update the builder without copying/freezing its whole tree each time.
Changing coordinates flushes the final frozen cell and its edited-cell image.
An interleaved return visit starts from the previous private canonical value.
Original per-record history order, coverage pruning, edit version, sky journal,
sky versions and ceiling flags stay intact. Finest-grid history replay uses the
validated exact-key path. Ordinary editing keeps its existing path.

This path rejects an existing, loaded, closed, scheduled or already-prepared
world, including recursive calls from the first progress callback. No caller may
read the private world before successful completion. Failure or cancellation
requires discarding it; the existing worker does that. Only the complete,
source-fenced world transfers to the owner. Private canonical revision counts
advance per cell flush during construction; no prior observer exists there.

A headless two-cell/8,192-piece diagnostic used one warmup and five measured
runs. Median replay was 2,345.89 ms with per-record apply and 14.95 ms with the
cell builder. Median thread allocation was 152,160,656 versus 14,077,224 bytes.
The fixture alternates bricks/glass at the finest world-cell detail. Every piece
and the full history count were checked. This excludes construction, decoding,
cleanup, owner handoff, rendering, capture/export/placement/save and network;
thread allocation is not peak memory. The shared VPS run is not reference
qualification. Commands/raw measurements are in
`dashboard/evidence/structure-cell-replay.json`.

Global history, geometry and indexes still materialize in memory. No package
format or admission limit changes. Full city/maximum, physical-GPU, shaped
32-client and 20-run qualification and rollout gates remain pending.

The coarse diagnostic (16,384 whole-cell edits) had median 31.77 ms before and
32.26 ms with the builder. Median thread allocation rose from 33,115,432 to
36,261,040 bytes. This is a measured tradeoff; the builder does not improve every
replay shape. Both diagnostics ran serially after full regression. Neither is
full-fixture or reference-environment qualification.

### Real TLS observer-delivery diagnostic

`StructureMultiplayerTest` supports 3–32 synthetic protocol clients through
`-Dvoxel.structure.clients=N`. Production authentication limits remain active;
the 32-client case waits 306 seconds between connection batches. All profiles,
accounts and TLS files are synthetic and isolated in the test directory.

For each of four road rotations, the test now waits for both endpoints on every
client and checks the far-end dependency's rotated geometry. Sender completion
alone is insufficient. Existing checks cover movement, occupied-cell rejection,
cancelled upload, contested placement, undo, late join and restart recovery.

An optional `-Dvoxel.structure.report=PATH` writes a sanitized JSON result only
after all workflow assertions, including restart, pass. `PATH` must not exist;
previous reports are never overwritten. `requestToAllClientsMs` starts before
encoding and includes encoding, upload, validation, durable publication and
observer polling. It is not a server-commit-to-synchronization measurement.
Tick diagnostics summarize the server's bounded 4,096-sample window; they are
not isolated placement-phase tick measurements. Workflow time includes login
rate-limit waits.

This is a 512-instance road with 512 sparse model dependencies on unshaped TLS
loopback. Only its first placement starts with empty structure-dependency caches.
Four rotations are four samples of different orientations, not 20 independent
full-fixture runs. Protocol clients do not render frames. The report deliberately
sets `qualification` to `false`. It does not qualify city/maximum admission,
physical GPUs, shaped-network synchronization, traffic, peak memory, frame times,
action acknowledgements or main-thread stalls.

### Regional offline load staging

Version-10 offline loads now use the bounded replay spool before rebuilding the
private edit history. The loader validates every replay section, reads and
validates the drop list, and requires exact source EOF before expanding history.
Malformed or truncated tails therefore fail before global history allocation.
The source save is never rewritten by loading.

The worker owns one temporary file in `java.io.tmpdir`, capped at 88,000,004 bytes
by the existing two-million-record replay limit. It rechecks length, count,
section CRCs, edit validity and exact spool EOF during replay. Success, failure,
callback failure and cooperative cancellation close/delete the spool. Cleanup
I/O failures propagate; the incomplete private result cannot be published.
Blocking filesystem calls still require a worker and cannot promise immediate
cancellation. The owner does not interrupt or join that worker.

Progress counts validated source records at 128-record intervals. It may reach
the record total before the private history drain finishes; task completion is
separate. The valid-load tradeoff is temporary disk space and a second disk pass.
The history and geometry remain eager after startup. This is neither resident
region paging nor a city/maximum performance qualification. Versions 3–9 and all
save/package/admission caps remain unchanged.

### Single-pass package decode validation

The decoder now checks geometry materials, cell bounds, dependency identity and
aggregate instance surfaces while parsing each cell. Only after exact compressed
stream length, checksum and EOF checks pass does it create a private decode
receipt. Its definitions are frozen and its builder owns detached geometry bytes.
Public package construction and capture still run their full content validator.
No caller can supply the private receipt.

Decoded cell positions have already passed strict ordering checks. Ordered
finalization checks ordering/duplicates again and copies every published array,
but does not allocate or sort a permutation array. Geometry segments retain the
same immutable snapshot rules. The wire format and all caps remain unchanged.

A serial parent/current public-decoder diagnostic used identical files: 100,000
uniform cells, or 64 dense cells with 131,072 leaves. Each path had three warmups
and 20 measured runs with a 512 MiB heap. Every run verified counts and exact
re-encoded package bytes outside timing. Median decode times changed from
40.29 to 32.56 ms for uniform and 22.74 to 17.52 ms for dense. Median thread
allocation changed from 8,092,352 to 4,426,672 bytes and from 9,884,896 to
5,312,256 bytes, respectively. Uniform p95 worsened from 64.39 to 70.36 ms;
its allocation p95 also rose. These mixed results do not show a universal gain.

The component test excludes acquisition, capture/export, placement, saves,
network, rendering and peak memory. Serial process order, JIT, GC and shared-host
effects remain. It does not qualify city/max admission or required full-fixture
performance. Supported-limit claims and stable rollout remain gated.


### Compact imported model dependencies

The package decoder now retains dense model definitions as exact octree wire bytes plus a private dense colour array. Repeated mesh sampling reads that array without rebuilding an octree. Mutable copies reconstruct the original tree; fingerprints, redundant branches, colours, per-model limits and package formats remain unchanged. Sparse models keep persistent nodes under a conservative density heuristic. Capture/public construction keeps its existing shared immutable nodes, avoiding an extra dense copy while the world library still holds the source model.

Read-only full-fixture scans confirmed city counts of 978,288 occupied cells, 968,048 geometry leaves, 256 models, 10,240 instances, 2,173 regions, 1,572,864 dependency quads and 62,914,560 instance quads. Maximum counts are 4,000,000 occupied cells, 7,967,232 geometry leaves, 512 models, 16,384 instances, 8,192 regions, 786,432 dependency quads and 25,165,824 instance quads. These are diagnostic scans, not production admission or performance qualification.

The model component benchmark compares decode plus immutable snapshot creation, three warmups and 20 measured runs of the first fixture model, followed by retention of all fixture dependencies with exact identity checks. Its post-GC heap delta is an estimate, not peak memory. Dense snapshots trade slower construction for lower retained storage. Full results and commands are recorded in `dashboard/evidence/structure-model-snapshots.json` after validation. No geometry, surface, chunk, history or supported limits are raised. Full city/maximum placement, durable storage, rendering, shaped 32-client synchronization and the required 20-run hardware qualification remain pending; stable rollout remains gated.

The storage estimate covers unplaced decoded package dependencies only. World registries and mutable editor copies can reconstruct node graphs; the estimate is not a whole-world or placement peak-memory result. Non-sampling queries still reconstruct a bounded per-model tree. Renderer/raycast costs need their own qualification before broader use.


Final serial model results on the shared Debian 12/Haswell VPS, Java 25.0.3 with a 768 MiB heap (tree -> packed):

| Fixture | Post-GC retained dependency estimate, bytes | Decode + snapshot p95, ms | Mesh-count p95, ms |
| --- | ---: | ---: | ---: |
| City, 256 models | 288,276,200 -> 77,395,680 | 4.539760 -> 11.895167 | 8.313979 -> 5.991671 |
| Maximum, 512 models | 72,310,120 -> 19,807,944 | 1.180342 -> 2.633130 | 0.715389 -> 0.968613 |

Snapshot construction is slower in both cases; maximum mesh p95 and worst also regress. These component results do not establish an overall speedup. The report contains all 20 samples, medians, p95 and worst values, exact input hashes and excluded phases. Full Main/GLFW functional workflow passed on frozen source with fresh decoded/inspected F10 media; software rendering does not qualify the frame budget. The first full Java regression was interrupted and is not counted as passed; a bounded final-source retry is recorded separately in the evidence receipt.

The resumed final-source Maven regression subsequently passed: 1,070 tests in 214 fresh suites, zero failures/errors and one optional browser skip, with no missing/stale source-class reports. Earlier interrupted/timeout attempts remain recorded separately. Production/native source and saved decoded media hashes still match; new source and evidence await runner publication and exact-head hosted checks. Full city/maximum qualification remains pending.


### Direct validated geometry decoding

The production importer now validates world-cell wire data directly into its bounded geometry arena instead of constructing and serializing a temporary octree for every cell. It retains the same size/tag/depth, material, empty-cell, leaf-budget and cancellation checks. Cell metadata is indexed only after a complete valid tree; the package still becomes visible only after stream CRC, length and EOF validation. Extended resolution headers normalize as before, and redundant branches keep their exact topology. Capture and public construction still validate their input trees. Production limits are unchanged.

Four new tests cover 128 deterministic random differential cases, extended headers, redundant branches, segment crossings, malformed/truncated trees, invalid materials/depth, exact leaf budgets, cancellation and old snapshot independence. All 41 focused checks in five suites passed.

An isolated build changes only three analysis constants: 8,388,608 leaves, 2,097,152 dependency quads and 67,108,864 instance quads. It never creates a world. Both reference fixtures pass strict decode, full decoded-body byte roundtrip, progress-based cancellation and CRC rejection in baseline/current analysis builds. The unchanged production controls still reject those fixtures. The reference maximum has 4,000,000 occupied cells and 7,967,232 leaves across 8,192 regions; it does not by itself qualify every possible spatial fragmentation pattern.

Each row below uses one full-fixture warmup and 20 measured strict decodes, serial processes on the shared Haswell/Debian 12 VPS with Java 25.0.3 and a 1 GiB heap. Median/p95/worst exclude fixture hashing, System.gc, a 100 ms pause, count checks, export and rejection checks.

| Fixture/build | Median decode ms | p95 ms | Worst ms | Median main-thread allocated bytes |
| --- | ---: | ---: | ---: | ---: |
| City baseline | 7795.957955 | 7972.094241 | 8157.483453 | 2,431,903,592 |
| City direct decode | 7404.3129085 | 7734.470881 | 7867.686107 | 2,391,250,152 |
| Maximum baseline | 16060.3500065 | 16352.208072 | 17129.728476 | 3,460,693,592 |
| Maximum direct decode | 11654.3780005 | 12018.719278 | 12365.053555 | 1,101,999,500 |

Maximum sampled decode heap fell from 593,287,224 to 478,323,024 bytes; maximum double-package roundtrip sampled heap fell from 1,049,063,208 to 882,860,304 bytes. City sampled decode heap rose from 181,403,240 to 263,813,528 bytes and process RSS high water rose from 377,446,400 to 423,002,112 bytes. Allocation reduction does not imply a universal peak-memory gain. Heap sampling is every 5 ms and can miss instantaneous peaks; RSS high water is cumulative within each process. The single export observations are not export p95 results.

See `dashboard/evidence/structure-geometry-stream-decode.json` for raw samples, class/source provenance, commands and limitations. This decode-only experiment does not establish capture, preview, placement, durable save, cold load, upload, synchronization, frame or tick budgets. Production admission, live region-backed world/history/persistence and physical-GPU/shaped-32-rendered-client qualification remain incomplete; supported limits and stable rollout stay gated.

### Conservative chunk mesh admission

Detached structure placement chunks first bound mesh quads by six faces per occupied fractional voxel plus six faces per full world cell. Model instances remain subject to their separate surface limits. A bound within both existing vertex and index limits admits the chunk without scanning its exact surface. A larger bound falls back to the unchanged exact scanner; it does not reject a chunk merely because the bound is loose. Rendering, exact mesh counts, production package limits and the 256-chunk placement cap are unchanged. This reduces validation work for sparse fractional construction; it does not establish city or maximum-size support.

A shared-VPS component probe (3 warmups, 20 calls per mode, 1 GiB heap) measured sparse 128-cell validation p95 at 109.789 ms exact versus 0.654 ms bounded. Dense 16-cell fallback measured 18.308 ms exact versus 14.870 ms bounded/fallback. These small CPU observations do not qualify city placement, rendering, server ticks or reference-hardware budgets. Full samples, source hashes and limitations are in `dashboard/evidence/structure-chunk-budget-bound.json`.

### History capacity preflight

Offline and server worker staging now count the final canonical replay-map size before retaining replacement replay keys and records. The count subtracts every replaced cell's existing records, adds one clear record per empty or fractional cell and its nonoverlapping leaves, and counts a full-cell replacement as one key. It checks the final transaction rather than intermediate growth, because later cells can free capacity. Cancellation and the existing source revision fences remain in place. The 2,000,000-record cap is unchanged. This bounds rejected staging work; it does not replace eager flat history or provide city/maximum-size support. Region-backed authoritative state/history remains required.

### Primitive live-region cell tables

Multi-cell `WorldVoxels` buckets use primitive 12-bit local keys and frozen geometry references. Sparse arrays stay at most half full; inserting beyond 1,024 entries switches to 4,096 direct slots. Single-cell buckets remain unchanged. Copy-on-write snapshots clone the table arrays and share frozen values. Explicit air remains a stored override. Region occupancy scans do not allocate map entries. Generic cell iteration does create boxed immutable entries, so capture/iteration costs still need full-fixture measurement.

A shared-VPS component probe used 3 warmups and 20 copies per occupancy/mode. At 4,096 cells, median allocation per bucket copy fell from 163,936 bytes (`HashMap`) to 16,448 bytes. At 1,024 cells it fell from 41,056 to 12,368 bytes; the dense transition at 1,025 cells uses 16,448 bytes. These are copy allocations, not whole-world retained memory or city performance. Exact samples and environment are in `dashboard/evidence/structure-region-cell-table.json`. World formats, replay history, model behavior, production limits and qualification gates remain unchanged.

### Packed snapshot point and aligned reads

Packed octree `get` and `uniform` now walk their private immutable wire images without rebuilding nodes or retaining caches. Sibling skipping is bounded by the existing cell/model image and depth. A large prefix scan is still possible for fragmented cells. Packed-model point reads keep the existing dense sample path; mutable copies, raycasts, region extraction and leaf visits still decode nodes as before. Noncanonical branches retain their mixed-region result even when their leaves share a colour. Region bounds now reject integer-overflow coordinates on both ordinary and packed trees. Formats and limits are unchanged.

The CPU component probe used 3 warmups and 20 batches of 64 queries per fixture/query/mode. It compared direct reads with a simulated previous strict decode-before-each-query path. Fragmented point-query p95 fell from 24.393 ms to 0.442 ms per batch; aligned-query p95 fell from 21.722 ms to 0.313 ms. Both fragmented direct modes measured zero allocation in the query interval, versus median 8,994,304 bytes per baseline batch. Packing cost is excluded. These are shared-VPS component observations, not capture/placement, frame, tick or network qualification. Raw samples and source hashes are in `dashboard/evidence/structure-packed-cell-queries.json`.

### Adaptive rendering-history snapshots

`World.editsSnapshot` retains the previous flat immutable map for sparse histories: at most eight rows per indexed chunk on average. Denser histories capture detached per-chunk `History` roots and expose immutable rows on demand. The threshold is an internal storage choice, not an admission cap. Both paths retain exact keys, iteration order and source independence. Capture must run on the world owner thread; the finished snapshot can be read by workers. Snapshots hold no live World or Chunk reference.

This removes eager row retention from dense snapshots used by distant terrain and sky visibility. It does not remove row scans, their temporary objects, downstream mesher work, or owner work proportional to chunk count. Shared roots can make scans and lookups slower; sparse histories keep the old path because their root metadata cost more retained memory in the component comparison. No universal memory or speed gain, frame budget or supported-size increase is claimed. Full source-scoped results belong in `dashboard/evidence/structure-render-snapshot.json` after final validation. Region-backed persistence, city/maximum admission and reference hardware/network qualification remain unfinished; rollout gates stay in place.

The final shared-VPS diagnostic used three warmups and 20 samples per mode on two synthetic worlds, Java 25.0.3 and a 512 MiB heap. Eight fragmented cells contained 32,776 replay rows. Source-history plus one snapshot reachable storage measured 5,719,120 bytes for flat storage and 477,424 bytes for adaptive storage; this excludes geometry, other owners, players, class metadata and whole-process RSS. Snapshot creation median allocation fell from 6,262,128 to 2,192 bytes; p95 creation time was 8.140080 versus 0.108208 ms. These are component observations, not city budgets.

The cost moves to reads: fragmented full-scan p95 rose from 1.660299 to 7.089069 ms, with median allocation 524,512 versus 4,983,944 bytes. Full keyed lookup p95 rose from 11.804194 to 21.926991 ms, with median allocation 32 versus 5,505,440 bytes. The ordinary fixture used 512 single-row cells in separate chunks. Both flat and adaptive source-plus-snapshot graphs measured 402,000 bytes; adaptive creation added 32 median allocated bytes for the count pass. Its median creation time rose from 0.485938 to 0.564348 ms while p95 fell from 0.802388 to 0.710404 ms. Shared-VPS timing/JIT variation prevents a universal speed claim. All raw samples, commands, source/class fences and excluded qualification appear in the report. Retained graphs are individual post-run observations, not 20 peak-memory samples.

### Bounded exact regional replay loading

Version 10 load paths retain exact clear-plus-disjoint-fractional-row groups as shared primitive history rows. A private loader carries at most 4,097 rows across validated 1,024-record sections; other legacy sequences use ordinary replay. The loader preserves file order, keys, positions, colours, material types and revision stamps without rebuilding or normalizing geometry. Noncandidate ordinary rows bypass buffering. CRC, position/value/model checks, spool/tail validation and source-record limits remain in place. LocalGame still validates the complete private spool and world tail before building resident history. A failed private load must be discarded; this is not live publication or a new world format.

The final serial decoder-only comparison uses the exact parent regional decoder with unchanged ordinary history paths, three warmups and 20 runs per fixture in separate 512 MiB JVMs. Dense canonical and legacy fixtures each contain eight cells and 32,776 rows; sparse and threshold fixtures have eight cells with 16 and 264 rows. Eligibility uses allocation-free local overlap checks at up to 32 rows and a 512-byte bitset for larger groups. Both strategies preserve the same exact rows; this is not an admission-limit change.

Dense canonical load p95 measured 88.451316 ms before versus 19.326817 ms after, with median thread allocation 55,935,216 versus 5,265,808 bytes. One retained-history graph measured 10,560,744 versus 473,208 bytes. Re-encoding p95 measured 29.325526 versus 14.460228 ms, while median allocation increased from 6,298,112 to 9,968,512 bytes. Full keyed lookup p95 measured 18.255125 versus 17.908805 ms, with allocation rising from 32 to 5,505,440 bytes. These numbers exclude geometry, disk/tail staging, live World reconstruction, rendering, other owners and networking; the graph is not peak process memory.

Sparse retained history measured 8,056 versus 4,984 bytes, with load allocation falling from 19,352 to 11,616 bytes. Sparse load p95 nevertheless rose from 0.256178 to 0.341853 ms, and lookup p95 rose from 0.042107 to 0.080986 ms. At 33 rows per cell, retained history measured 86,544 versus 83,064 bytes and median load allocation 327,320 versus 118,384 bytes, while lookup p95 rose from 0.116110 to 0.194078 ms. The nonclear-root legacy fixture retained the same 10,560,744-byte graph; its load p95 measured 90.645937 versus 86.072366 ms, with 48 extra median allocated bytes. Shared-VPS/JIT variation prevents a universal speed claim. Every measured load preserved exact ordered rows, revision and complete re-encoded bytes. Raw samples, commands, source/class fences and exclusions belong in `dashboard/evidence/structure-replay-loader.json` after final validation.

At the regional-loader stage, resident authority history became smaller for eligible groups, while startup still reconstructed ordinary live World histories and geometry. The private startup extension below addresses eligible history rows. Region-backed authority/persistence, geometry residency, legacy replay scaling, the 2-million-record barrier, full city/maximum admission and reference hardware/network qualification remain unfinished. No supported limits or rollout gates changed.

### Private startup history replay (qualification pending)

Private World startup now sends each contiguous cell's replay rows through the bounded history writer before installing that cell's geometry. Exact clear-led, disjoint fractional rows retain their original order in compact storage. Legacy, overlapping and nonclear sequences retain ordinary history semantics. The existing per-row geometry, edit counters and sky metadata still run; later world edits and immutable snapshots retain their behavior. Cancellation or failure discards the private World. No live-world transaction, save format, admission cap or model limit changed.

A synthetic component comparison rebuilt the exact parent `03448e35e9cf340b0b4e8c1ad9b6a7d44a1afabb` World class against the same ordinary history paths. Separate 512 MiB JVMs ran three warmups and 20 samples for dense, sparse and legacy eight-cell fixtures. Every sample compares full ordered rows, per-history revisions, World revision and complete cell geometry with ordinary owner replay. Fixture creation, comparison, snapshots and retained-graph traversal are outside load timing. The comparison does not measure disk loading, peak process memory, rendering, network traffic or city/maximum qualification. Shared VPS/JIT/GC variation limits timing claims. Full regression and native checks overlapped part of this diagnostic run; its timings are not controlled benchmark results.

| Fixture | Replay rows | Retained history bytes, parent → current | Load median / p95 / worst ms, parent → current | Median allocation bytes, parent → current |
| --- | ---: | ---: | --- | ---: |
| Dense canonical |32776|10542256 →475424|65.327573 /93.177193 /118.966518 →32.780007 /58.492309 /64.994707|58844376 →13865656|
| Sparse canonical |16|10368 →7072|0.073949 /0.145147 /0.154999 →0.142229 /0.389077 /1.936461|26584 →21600|
| Legacy nonclear |32776|10542256 →10542256|77.163560 /125.481797 /144.246409 →72.160692 /155.301425 /156.876416|58844568 →58845152|

Retained graph counts only the per-chunk history map and objects reachable from it. It excludes authority/source history, canonical geometry, edited-cell images, model definitions and the rest of World. Sparse timing and legacy p95 worsen in this run. These measurements do not establish supported city limits. Geometry remains resident; legacy replay and region-backed authority/persistence, record/chunk/geometry/instance admission barriers and all full-scale reference, physical-GPU and shaped 32-client budgets remain unfinished. Stable rollout stays gated.

Local validation passed 21 focused tests and the full 226-suite regression (1115 tests, zero failures/errors, one browserWorkflow skip). Production Main/GLFW playtesting passed capture/import/rotation/overlap/cancellation/undo and existing inventory/editor/model-restart flows on an isolated synthetic offline profile. Fresh media decoded completely and was visually inspected. Software rendering at 1 FPS is functional evidence only. See `dashboard/evidence/structure-startup-compact-history.json`; report, source and fresh media await controller publication and exact-head hosted checks. These passes do not complete the qualification gates above.

### Packed private startup geometry (qualification pending)

Initial replay now prepares the existing factory-owned immutable cell image when a contiguous cell visit ends. It checks cancellation before installation, then shares that image's frozen tree between canonical geometry and the edited-cell index. Later ordinary edits copy the image first. History order/revisions, counters, sky metadata, explicit-air overrides and model IDs retain their semantics. Geometry remains resident; this does not provide region paging, a new save format or higher admission limits.

The exact parent `5d0811e0f1d0ebda29ab4b18067fc1de029cc21c` World class was rebuilt against the same history implementation. Serial 512 MiB JVMs ran three warmups and 20 samples per mode/fixture before other owned full/native checks. Each eight-cell fixture checks full ordered rows, history/World revisions, complete cell geometry and a 256-point query checksum. Startup and point queries are timed separately; source creation, validation, snapshots and graph scans are excluded. Shared VPS/JIT/GC variation still limits timing claims.

| Fixture | Combined retained cell-owner bytes, parent → current | Startup median / p95 / worst ms, parent → current | Median allocation bytes, parent → current | 256-query p95 ms, parent → current |
| --- | ---: | --- | ---: | ---: |
| Dense canonical, 32776 rows |2075376 →1120816|25.321489 /32.666706 /33.006898 →28.108116 /35.923935 /36.301840|18323016 →19018248|0.154555 →1.326647|
| Sparse canonical, 16 rows |17904 →15984|0.139263 /0.259519 /0.290705 →0.195177 /0.257951 /0.262484|23600 →26160|0.080569 →0.113442|
| Legacy nonclear, 32776 rows |20656528 →19701968|72.740586 /81.476703 /123.625566 →73.424260 /99.704444 /106.818109|59632112 →60327344|0.077380 →1.455901|

The combined graph includes source and captured History, per-chunk World history, canonical cells and the edited-cell index, deduplicating shared objects. World-only cell-owner graphs measured 1602032 →647472 bytes (dense), 12784 →10864 bytes (sparse) and 11668864 →10714304 bytes (legacy). Raw fixture lists, Terrain, model definitions, renderer, other World metadata and peak staging/process memory are excluded. Point-query median allocation stayed 6144 bytes for all fixtures/modes. Packed point reads scan immutable wire bytes; they use less retained memory but can take longer. These results do not establish city, frame, tick or network budgets.

The choice reuses the engine's existing packed cell representation and remains subject to all qualification gates. Region-backed authority/persistence, legacy-history memory, record/chunk/geometry/instance admission barriers, full city/maximum operation runs, physical GPU and shaped 32-client tests remain pending. Supported limits and stable rollout stay gated.

Local validation passed 27 focused tests and the full 226-suite regression (1117 tests total, zero failures/errors, one browserWorkflow skip). Production Main/GLFW workflow passed on an isolated synthetic offline profile, including import/rotation/conflict/cancellation/undo and existing inventory/editor/model-restart regressions. All fresh video frames decoded and representative staging/conflict frames were inspected. Software rendering at 1 FPS remains functional-only evidence. See `dashboard/evidence/structure-startup-packed-geometry.json`; new source, report and media await controller publication and exact-head hosted checks. Full-scale qualification remains pending.

### Legacy offline history loading (qualification pending)

The private v3-v9 offline load loop now uses the bounded replay writer. Each version retains its existing record decoder; v5 fractional LEDs still restore white because that format stores no colour. Clear-led disjoint fractional groups keep compact primitive history rows in exact order. Ordinary nonclear/overlap groups retain the same fallback. Model definitions, inventory/drop parsing, replay revisions, progress counts and v3-v9 save/upgrade behavior remain. Later failure or cancellation discards the private LocalGame. The legacy multiplayer `put` path is unchanged.

A component comparison rebuilt exact parent `cd84bc2251448cbd19577b731165dc395fb09f94` LocalGame against the same ordinary history implementation. Separate 512 MiB JVMs ran three warmups and 20 samples for each mode/fixture before owned full/native checks. Timings cover real warm-cache file loading, including model, inventory, history and tail decoding. Fixture construction, verification, keyed reads, save/force/complete-byte checks and graph traversal are outside load timing. Every sample checks ordered rows, history revisions, dependencies and complete saved bytes. This is not a cold-cache benchmark. Shared VPS/JIT/GC variation limits timing claims.

| Fixture | Records | Retained loaded-state bytes, parent → current | Load median / p95 / worst ms, parent → current | Median allocation bytes, parent → current |
| --- | ---: | ---: | --- | ---: |
| v8 dense |32776|10625432 →537896|53.005144 /91.006949 /101.568274 →13.429103 /20.151027 /21.736167|56089136 →4634960|
| v9 dense plus 32-resolution dependency |32777|10626704 →539208|53.663857 /67.141269 /68.831770 →15.868668 /21.245946 /21.918494|56105168 →4650312|
| v8 sparse |16|72744 →69672|1.125842 /1.523757 /1.737240 →1.174170 /1.250345 /2.220329|2154248 →2146632|
| v8 nonclear |32776|10625432 →10625432|54.717667 /75.102926 /84.357098 →60.400467 /74.391644 /85.012368|56088928 →56088976|

Retained loaded-state graph includes history, model library, inventory and drops. A shared History snapshot adds 200 bytes in these fixtures. Fixture arrays, file/path objects, live World geometry, renderer and peak staging/process memory are excluded. Nonclear history still has its ordinary retained graph; its median/worst load time rises in these samples. No universal speed, peak-memory or city-budget claim is made.

This removes eligible legacy-load row expansion, not the resident-world, server legacy map, 2-million-record persistence or other admission barriers. Region-backed authority/persistence, full city/maximum reference operations, physical GPU and shaped 32-client qualification remain unfinished. Supported limits and stable rollout stay gated.

Local validation passed 29 focused tests and the full 227-suite regression (1122 tests total, zero failures/errors, one browserWorkflow skip). Production Main/GLFW workflow passed in an isolated synthetic offline profile, including capture/import/rotation/conflict/cancellation/undo and existing inventory/editor/model-restart flows. All fresh video frames decoded and representative frames were inspected. Software rendering at 1 FPS remains functional-only evidence. See `dashboard/evidence/structure-legacy-offline-load.json`; new source, report and media await controller publication and exact-head hosted checks. Full-scale qualification remains pending.

### Keyed legacy server history and bounded overlays (qualification pending)

Legacy multiplayer saves keep keyed `put` semantics. A bounded private loader now compacts only valid clear-led disjoint groups for previously absent cells. It carries at most 4097 rows; existing cells, overlaps, duplicate keys and nonclear sequences retain ordinary puts. Old key positions and overlapping rows remain. Row/model validation, version readers, player/drop tails, wire formats and admission limits are unchanged. A later load failure discards the private server state.

Compact segment overrides now use immutable pages of at most 32 rows. A point overwrite copies one page and its directory, bounded by the cell replay size (at most 4097 rows), instead of copying every earlier override. Masked removals copy only touched pages and release empty pages. Previous snapshots keep their old arrays. Ordinary non-bulk map storage stays unchanged. This is an internal storage choice, not a new supported limit.

The exact parent `bd80f5b76338ed069f59a533671143a98a3f871d` SegmentedHistory was rebuilt against unchanged storage method signatures. Serial 512 MiB JVMs ran three warmups and 20 samples per mode/fixture before owned full/native checks. Baseline intake uses validated pure puts; current intake uses KeyedReplayLoader. Both overlay cases start with the same compact cell and time 512 keyed overwrites. Every sample checks exact ordered rows, revisions and keyed values. Overlay samples also check old snapshots and later coverage. Fixture setup, result checks and graph scans are excluded. Shared VPS/JIT/GC variation limits timing claims.

| Fixture | Final rows | Retained history bytes, parent → current | Median / p95 / worst ms, parent → current | Median allocation bytes, parent → current |
| --- | ---: | ---: | --- | ---: |
| Dense intake |32776|10560728 →473160|40.027937 /83.801927 /97.578516 →2.884606 /4.369603 /7.208993|50542536 →924264|
| Sparse intake |16|8040 →4936|0.015658 /0.032727 /0.041721 →0.103953 /0.194436 /0.230100|14896 →8504|
| Nonclear intake |32776|10560728 →10560728|42.129592 /56.158941 /64.868988 →42.732853 /55.539871 /65.601283|50542536 →50542584|
| Recurring/alternating cells |8194|2613488 →549848|17.701159 /31.350262 /37.568062 →13.086440 /24.265641 /24.598457|21852848 →12035016|
| 512 overwrites in a compact cell |4097|100368 →86936|9.483042 /17.405404 /20.261765 →0.388095 /0.435113 /0.556000|12532784 →701344|

The overlay graph plus its before-snapshot measured 100808 →87344 bytes. After full-cell coverage, the target history alone measured 688 bytes in both modes; old snapshots still retain their earlier state. Other graph figures count only history roots. File decoding, model libraries, server geometry, TLS/network work, rendering and peak staging/process memory are excluded. Sparse intake slows in these samples; nonclear median/worst time rises. This is not a server tick, city, frame or memory-budget qualification.

Real synthetic server integration separately checks legacy load/resave/restart, saved tail/dependencies, missing models and malformed tails, and actual TLS clients/late joins. Region-backed authority/persistence, whole-world metadata, record/chunk/geometry/instance admission barriers and full city/maximum/reference/physical-GPU/shaped-32-client qualification remain pending. Supported limits and stable rollout stay gated.

Local focused validation passed 30 tests in eight suites. A full Maven invocation ended with exit 143 after 147 suites; a bounded continuation passed the 82 unfinished suites on unchanged source. Aggregate coverage matches all 229 source suites (1129 tests total, zero failures/errors, one browserWorkflow skip). This is suite coverage across two invocations, not an uninterrupted full-command pass. Actual legacy server/TLS integration and native Main/GLFW workflow passed. All fresh video frames decoded and representative staging/conflict frames were inspected. Software rendering at 1 FPS remains functional-only evidence. See `dashboard/evidence/structure-keyed-server-history.json`; new source, report and media await controller publication and exact-head hosted full validation. Full-scale qualification remains pending.

### Private server startup geometry

Server construction groups only adjacent rows for the same cell. It applies them in their saved order to one mutable 16³ cell tree. Multi-row runs install a packed immutable image; single-row runs keep ordinary replacement to avoid repeated packing on interleaved legacy rows. Returning cells start from their previously installed values. The global geometry change count still advances once per source row. Loaded history, material/colour encoding, terrain under unedited subcells, save formats and model limits are unchanged.

This path accepts only a fresh unpublished world. Failure or cancellation requires discarding it; it does not offer rollback for a live world. It bounds temporary replay geometry to one cell, but still retains all loaded world geometry. It does not provide region paging or establish city/maximum support. Component timing and allocation results must include the sparse/interleaved cases; packing is not an unconditional speed improvement.

Synthetic component measurements on the recorded Debian/Haswell virtual host (512 MiB JVM; three warmups, 20 samples per mode; complete raw samples in `dashboard/evidence/structure-server-startup-geometry.json`):

| Fixture | Retained geometry bytes, per-row → grouped | p95 ms, per-row → grouped |
| --- | ---: | ---: |
| Two dense cells, 8194 rows | 281152 → 42560 | 7.122046 → 11.242843 |
| 64 sparse cells | 4632 → 4632 | 0.525411 → 0.863695 |
| Two alternating cells, 512 rows | 24832 → 24832 | 0.370908 → 0.988388 |

The dense case trades startup time for a smaller retained geometry graph. Sparse/interleaved runs gain no retained-memory reduction and incur overhead. These warm synthetic measurements exclude terrain, history, models, file decoding, verification and constructors from the measured work. Retained graph size is not peak memory. They do not qualify reference hardware, cold region loads, city/maximum packages, physical GPU rendering or network synchronization.

### Multiplayer join history comparison

Join catch-up compares the resident cell indexes when both inputs are indexed histories. Shared snapshot cell identities prove unchanged values. Value-only ordinary and compact overwrites detach that identity before mutation. Other changed cells receive exact keyed comparisons; a second cell-index pass finds whole-cell removals. Shared bulk segments can skip unchanged compact rows. Equal-value writes and row reorderings do not create false deltas. Generic maps retain the original row-union path.

The output is a set of changed cell positions; cell delivery order may differ. The existing authority lock, complete-cell payloads and dependency transfer remain. This reduces comparison work for shared snapshots. It still scans resident cell indexes and rows in changed or independently built cells, and still builds outgoing payloads under the existing lock. It is not region paging or proof of a maximum lock stall. First ordinary-cell mutation after a snapshot can copy that cell's ordinary key index.

Warm synthetic comparison, three warmups and 20 alternating-order samples per mode/fixture on the recorded virtual host:

| Fixture | p95 ms, row union → indexed | Median allocated bytes, row union → indexed |
| --- | ---: | ---: |
| Shared dense, 32776 rows | 50.891702 → 0.145536 | 29760000 → 496 |
| One changed dense cell | 28.591457 → 0.395247 | 29760072 → 158080 |
| Independent equal dense histories | 32.647222 → 24.948121 | 29760000 → 9176304 |
| Shared sparse, 4096 cells | 3.596624 → 2.833929 | 656128 → 496 |
| One wholly removed dense cell | 26.273962 → 0.036027 | 27539696 → 640 |

Every result matches the exact row-union changed-cell set. Timing excludes fixture/snapshot creation and verification. This measures history comparison, not server lock duration, ticks, peak memory, transport, rendering or city qualification. Full raw samples, environment and the post-snapshot write-cost comparison belong in `dashboard/evidence/structure-cell-index-join.json`.

The exact-parent post-snapshot write probe records the added COW cost. For 512 overwrites in one ordinary 4097-row cell, p95 rises 1.181101→2.972452 ms and median allocated bytes rise 636584→862264. The compact-cell case records p95 1.582059→1.438435 ms and allocation 725888→713808 bytes; separate JVM/JIT variation limits timing attribution. Both modes verify every old/new keyed value, row count and revision. These results exclude snapshot construction and do not measure retained or peak memory. The first ordinary-cell overwrite can clone its key index; join comparison savings are not free for writers.

### Indexed private replay staging

The v10 offline loader still validates a whole source and its tail before draining private replay into resident history. Its temporary replay now has a separate disk index. Each fixed 28-byte entry stores an offset, region, record count and payload checksum. An in-memory CRC seals the index; no resident section directory is built. The existing 2,000,000-record cap bounds index storage to 56,000,000 bytes and combined temporary replay/index storage to 144,000,004 bytes. These are temporary disk bounds, not a larger world or structure allowance.

Full traversal remains buffered and sequential. It checks the index before delivery and each complete section against the sealed metadata. Selective traversal checks index metadata, then reads only matching region payloads, preserving recurring sections in file order. A single bounded frame is reused. Unselected payload integrity is deferred until accessed; a selected prefix is private staging and does not authorize whole-world publication. Both files are removed on failure or close. Owner-thread and reentrancy fences remain.

This is a private readback foundation. LocalGame still drains all records, and live World/server state is still resident. The index is not a durable world format, region cache or live paging backend. Save/wire formats and admission caps are unchanged.

Actual private-file component comparison, three warmups and 20 samples per mode/fixture on the recorded virtual host:

| Fixture | Stage p95 ms, parent → indexed | Full traversal p95 ms | Selected traversal p95 ms | Combined staging bytes |
| --- | ---: | ---: | ---: | ---: |
| Clustered, 8192 rows | 13.075110 → 12.988322 | 10.223468 → 11.286021 | 8.399568 → 2.375791 | 196772 → 196996 |
| Fragmented, 8192 one-row sections | 14.960719 → 22.380965 | 10.813502 → 19.195977 | 8.092057 → 10.181894 | 360452 → 589828 |
| Empty | 1.218050 → 1.595676 | 0.161621 → 0.212694 | 0.159096 → 0.168516 | 4 → 4 |

Index validation and extra private-file writes add cost. Fragmented selected traversal also regresses at p95; selective reads are not always faster. Stage includes file forces. Read callbacks include rolling row-order hashes; construction, close and cleanup are outside timing. Reads use warm OS cache. Raw samples, allocation regressions, environment and exclusions are in `dashboard/evidence/structure-replay-disk-index.json`. This does not measure cold region loads, durable world saves, live paging, retained/peak process memory, frames, server ticks, traffic or city qualification.

### Bounded private replay pages

`RegionReplayPage` builds one detached worker result from the temporary replay index. It is not connected to live World/server paging. Its private staging caps are 4097 source rows and 64 edited cells, with caller budgets allowed only to tighten them. These caps do not change advertised structure or world limits.

The spool checks the selected source-row count before decoding payloads. Overwritten rows and explicit air still count. It checks the count again while consuming metadata, so changed metadata cannot enlarge staging before its final checksum rejection. Cell admission precedes history insertion. All selected rows and sealed metadata must validate before geometry construction starts. Failure or cancellation returns no page; the spool stays caller-owned and can be retried.

The page retains ordered, read-only history and factory-owned frozen cell images. Geometry is built one 16³ cell at a time over the caller's implicit terrain. Relative positions, materials, LED colours, fractional holes, explicit air and model IDs survive. Model definitions and model policy remain caller-owned. A result survives spool closure; later edits to a world that installs its images fork geometry without changing the page. A future live installer must still fence authority identity, terrain/model revisions and destination edits before publication.

Logical staging bounds are at most 4097 history rows, 64 cell images and one mutable cell during construction. Each image has at most 4096 leaves and 65536 encoded bytes. The image payload ceiling is therefore 4194304 bytes; Java objects, maps, temporary encoding buffers and caller-owned state add memory. This is not a measured JVM heap or peak-memory limit. Cancellation is checked between rows and cell preparation; no 100 ms stall guarantee is claimed.

The earlier single-page CLI checks used 577 source rows, 513 selected rows, 505 surviving history rows, 64 cells and 442 geometry leaves. Three warmups and 20 samples on the recorded shared VPS gave private stage median/p95/worst 4.140635/5.186620/10.134185 ms and page construction 9.850149/15.844318/19.173552 ms. Stage includes temporary file forces; reads use warm cache. These component measurements are not cold region loading, durable world saves, live paging or city qualification. Full samples and environment are recorded in `dashboard/evidence/structure-replay-page.json`.

LocalGame still drains the complete replay into resident history. Every private page read still scans the complete disk index; a region with more than 64 edited cells rejects rather than returning a partial page. Live use needs bounded cell batches, faster region lookup, cache lifetime, asynchronous requests, authority fences, dirty overlays and durable generations. Existing city/maximum admission and rollout gates remain unchanged.


### Bounded local-cell windows

`RegionReplayPage.loadWindow` returns a page for an explicit half-open local cell-key range, at most 64 keys wide. Keys use `x | (z << 4) | (y << 8)` within one 16³ region; negative world coordinates use their low four bits. The returned range also covers unedited implicit terrain, so an empty image map does not mean unknown coverage. The older whole-region entry point remains, with its caller cell/row budgets.

A separate caller budget bounds all decoded rows in the selected region, up to the existing 2,000,000-record replay ceiling. The sealed index checks this budget before any payload decode. All matching-region rows still undergo CRC and model-policy validation, including cells outside the requested range. Selected source rows are counted before history insertion, including rows later overwritten. At most 4097 selected rows and 64 images are retained. One bounded 1024-row decoded section and private compaction/encoding buffers add temporary memory. No page escapes on failure or cancellation.

The private selected-row cap is 4097 rather than 4096 because a complete coloured 16³ cell may need one clear plus 4096 distinct leaves. Tests retain all 4097 ordered rows and check every resulting voxel; 4098 selected rows reject even if the last row merely overwrites an earlier one. This staging cap change does not raise any public structure, model, geometry, instance, chunk or world persistence limit.

Sixty-four disjoint windows can read all 4096 edited cells of one region without constructing a whole-region history in the loader. Each window preserves its filtered source order. Concatenating window histories does not reconstruct the original cross-window replay order; future persistence must preserve that order separately. Combining images into live authority still requires destination/model/terrain revision fences and atomic publication.

Every window still scans the complete disk index and decodes all matching-region payloads. Reading all 64 windows repeats that work 64 times. This is a bounded private staging API, not an efficient live pager or a qualified region load. Faster lookup, indexed cell batches, cache lifetime, dirty overlays and durable generations remain unfinished. Public limits and all reference/physical/network qualification gates remain.

The final cell-window CLI uses an 8192-row region with 4096 edited cells and 90112 leaves. Each of 64 windows retains 128 rows and 64 cell images. A second fixture has one fully coloured cell: 4097 ordered history rows and 4096 leaves. Both preserve air, fractional material/LED colour and model references; the complete assembled geometry matches independent replay.

Three warmups and 20 warm private-file samples per fixture on the recorded shared VPS:

| Component | Median ms | p95 ms | Worst ms |
| --- | ---: | ---: | ---: |
| 8192-row temporary stage, including forces | 7.499505 | 8.568869 | 8.614243 |
| Sum of all 64 window loads | 229.980595 | 257.652642 | 271.506669 |
| Complete batch including history checks and image installation | 232.702737 | 259.761855 | 274.921268 |
| Full coloured-cell temporary stage | 5.153640 | 6.082540 | 7.894533 |
| Full coloured-cell window load | 5.700266 | 6.360834 | 7.464834 |

Fixture/oracle construction, final independent geometry verification and cleanup are excluded. Reads use warm OS cache; each window repeats all matching-region decoding. The 128 MiB JVM heap setting is not a peak-memory measurement. These results do not establish city, cold-load, durable-save, frame, tick, network, acknowledgement or main-thread stall budgets. Exact commands, environment, raw samples and exclusions are recorded in `dashboard/evidence/structure-replay-window.json`.

### Optional copied region cell index

`RegionReplayCells` builds a temporary index for one region. A fixed 4096-cell count table replaces the proposed external sort runs: the first validated traversal counts rows, and the second writes each cell's records into its assigned file range. Each 28-byte record contains a matching-region source ordinal plus the unchanged 24-byte edit. Sorting at most 4097 selected records by that ordinal restores filtered cross-cell order. No resident per-record directory is built.

The root retains 81928 bytes of primitive count, offset and checksum arrays, plus Java objects. Its copied file is at most 56000000 logical bytes under the existing 2,000,000-record limit. One maximum source spool plus one copied index can use 200000004 logical disk bytes; filesystem allocation, Java objects and buffers add resources. Multiple retained indexes add their own costs and need a bounded cache before live integration. These are staging bounds, not measured peak memory or supported city limits.

Build checks the complete matching region and its model policy twice before returning a root. Selected source-row counts are then available from sealed memory metadata, so oversized windows reject before payload I/O or policy callbacks. Reads validate every selected copied-cell CRC before calling policy, check positions and ordinal bounds, reject duplicate ordinals, and return immutable rows. Read policy runs only for selected rows. Post-build corruption in other copied cells is deferred until those cells are read. The original scan path keeps its stricter per-read whole-matching-region validation. Neither path authorizes whole-world publication; callers still own model/terrain/authority revision fences.

The copied root works after its source spool closes. Its own close removes the file. Cancellation or failure during counting/scatter removes private prefixes; ownership and reentrancy checks remain. Build performs two matching-region payload traversals and four global source-metadata scans. The root cannot be reopened after process restart because its sealed metadata is private memory. It does not validate the original world tail or other regions; it must stay private until whole-world metadata and tail validation succeeds. It is not a durable archive or live pager. `RegionReplayPage.loadIndexed` uses the same bounded history/geometry assembler as the scan entry points. LocalGame and live World/server loading remain unchanged.

Actual comparison: one 128 MiB JVM, three warmups and 20 samples per fixture, alternating scan/index read order. Shared source staging is measured separately. Index build includes both source traversals, positional writes and file force. Page-load sums exclude oracle checks, private installation and cleanup. Reads use warm OS cache on the recorded shared VPS.

| Fixture / component | Median ms | p95 ms | Worst ms |
| --- | ---: | ---: | ---: |
| 4096 cells, 64 windows: scan reads | 230.469397 | 234.199173 | 238.284708 |
| Index build | 22.384037 | 25.555743 | 32.753483 |
| Indexed reads | 56.319536 | 62.905185 | 65.282851 |
| Build plus indexed reads | 78.767660 | 87.536573 | 98.036334 |
| One full coloured cell: scan read | 4.928441 | 6.765341 | 7.599728 |
| Index build | 13.476200 | 16.522828 | 22.601521 |
| Indexed read | 3.831063 | 6.729368 | 6.755726 |
| Build plus indexed read | 17.389233 | 22.861884 | 28.307061 |

The single-cell setup cost regresses total time; indexed staging is not an unconditional speedup. For the 8192-row region, copied data is 229376 bytes. Policy callbacks are 16384 during build, then 8192 across all indexed reads versus 524288 across scan reads. For the 4097-row coloured cell, copied data is 114716 bytes and setup adds 8194 policy callbacks. Full samples, shared-stage costs and environment are recorded in `dashboard/evidence/structure-replay-cells.json`. No cold-load, durable-save, retained/peak-memory, GPU, frame/tick/network, city or maximum-fixture qualification follows from these component results.

### Private copied-index cache

`RegionReplayCache` now bounds the lifetime of copied region indexes on one worker. It is synchronous and package-private. Callers retain ownership of the source spool. Only detached `RegionReplayPage` results escape; callers must fence model, terrain and authority revisions before installation.

The constructor requires explicit caps: 1–4 retained regions, at most 56000000 logical copied-data bytes, and at most 2000000 source rows per region. It reserves `maxRegionRows * 28` bytes before each build, evicting least recently used roots first. This includes the possible in-flight copied file in both count and byte bounds. Reservations are conservative: with the maximum row budget, only one maximum-sized root fits. Empty roots still consume a region slot. At most four retained/building roots use 327712 primitive metadata bytes, plus Java objects and temporary buffers. These bounds exclude the caller-owned source spool, returned pages, filesystem allocation and process heap overhead. They do not establish peak memory or city support.

A failed or cancelled build removes its private prefix. A failed read may retain a fully built root for retry. Eviction may remove old roots even when a later request fails. If deleting a root fails, its handle and accounting remain owned and retryable; close does not pretend it succeeded. A hit still checks selected-row policy and CRCs. A miss still needs the source spool and performs the existing two matching-region traversals and four global metadata scans. Cached reads can survive source close; uncached reads cannot. Changing source/model/terrain authority requires a new validated cache lifetime or separate caller fences.

Five tests cover LRU order, pre-build byte reservation, empty-root count limits, detached results, policy/row failure and retry, cancellation during a build, cleanup failure and retry, ownership/reentrancy and closed-source behavior. `python3 tools/structures/run_replay_cache_check.py` exercises the actual cache integration with six synthetic regions and ten requests, independent history/geometry oracles, copied-file bounds and error recovery. Browser playtesting does not apply to this worker file integration. Its screenshot is a captured result window, not gameplay or performance evidence. No timing qualification is claimed.

This cache is not wired into live World/server authority or rendering. Asynchronous requests, progress/cancellation across worker requests, whole-world metadata/tail validation, revision fences, dirty overlays, original global replay order and durable generations remain unfinished. LocalGame still drains the complete replay into resident history. Public admission limits, required city/maximum acceptance and physical-GPU/shaped-32-client qualification gates remain unchanged.

### Private asynchronous replay worker

`RegionReplayWorker` now owns one source spool and one bounded copied-index cache on a single daemon worker. A factory opens and validates the source on that worker. The factory must validate the full prefix metadata/model definitions and tail before returning; it must clean resources it has not returned if opening fails. The worker is not an alternative world parser or a live installation path.

The owner API queues at most eight requests, plus one active request. Callers may tighten the queue to 1–8 slots. Submission rejects a full queue before allocating another request. Queue entries contain a region, a cell range, a row budget and revision tags. File I/O, policy checks, history assembly and geometry construction run on the worker. The owner polls phase/error snapshots and bounded row counters. Snapshots do not expose page results. There are no owner callbacks, owner joins or owner-side file reads in this API. These design bounds do not prove the 100 ms acknowledgement/stall budgets.

Queued cancellation removes the request and frees its slot. Running cancellation is cooperative through existing replay checks; a cancelled request never publishes a late page. Terminal publication and cancellation use one atomic state transition. Request failure permits later requests to retry; source-opening or fatal worker failure completes outstanding requests and begins cleanup. Results are detached immutable pages. Owner-side extraction rejects mismatched supplied revision tags. The opener and installer must still capture/check actual authority/model/terrain identities and revisions; tags alone do not authorize installation.

Close signals cancellation without joining a worker blocked in I/O. Cleanup stays on the resource-owning worker. If deletion fails, the worker reports `CLEANUP_FAILED`, retains the handles and waits. A later explicit close requests another cleanup attempt. It does not poll or spin on filesystem failures. Closed/failed status is the cleanup receipt; calling close alone is not proof that files have been removed. A blocked filesystem call cannot be forcibly completed by this helper.

`LocalGame.readRegionalTail` factors the current offline loader's existing drop-record and EOF checks. The resident loader still uses that gate before draining replay. Worker factories can use the same gate after staging a regional body; invalid or trailing tails must discard the private spool before returning any source. This validates the tail, not the world prefix, model definitions or current authority. Transient drops remain outside structure contents.

Seven worker tests cover thread ownership, detached geometry, progress, bounded opening queues, cancellation before/during reads, no late publication, policy failure/retry, source/extraction revision tags, missing/invalid/trailing tails, fatal failures, and cleanup failure followed by explicit retry. `python3 tools/structures/run_replay_worker_check.py` exercises the actual worker/cache/tail integration and independent history/voxel oracles. Browser playtesting does not apply to this worker file integration. Its captured result window is not a gameplay performance test.

The service is not yet called by live World/server rendering or authority. LocalGame still loads all replay into resident history. Production source opening with full metadata/model validation, real installation fences, request scheduling/priorities, dirty overlays, original cross-region replay order, durable generations and full world integration remain unfinished. Source/spool/cache/page limits and existing provisional structure admission caps remain in force. Required city/maximum, physical-GPU, shaped-32-client and all reference performance qualification remain pending; supported-city claims and stable rollout stay gated.
