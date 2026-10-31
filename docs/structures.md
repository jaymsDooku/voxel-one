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

Capture/import and placement builders now use stable two-pass 14-bit radix ordering over packed spatial keys and source indices. Cancellation is checked at most 1,024 loop entries apart, including prefix sums and final storage construction. Spatial order, duplicate rejection and published immutability remain unchanged. At 4,000,000 cells, temporary sort arrays use 80,065,536 bytes excluding headers, versus 32,000,000 bytes for the previous packed-long sort. This extra bounded memory buys cancellable sorting; admission limits are unchanged.

The 20-run shared-VPS component probe measured control/new p95 of 136.783/101.631 ms at 1,000,000 keys and 528.801/445.349 ms at 4,000,000 keys. It does not construct populated city/max fixtures or measure complete workflow budgets. See `structure-order-validation.json` for commands, results and limits.

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
