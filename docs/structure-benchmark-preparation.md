# Structure benchmark preparation

Experimental gameplay now uses the separate VSTR format documented in
[`structures.md`](structures.md). These VXBF fixtures remain an unqualified
benchmark corpus; the development caps deliberately reject their full geometry.
Reference qualification and stable rollout remain gated.

Status: generated input corpus only. No supported structure limits or completed
performance qualification. Fixture generation time is not capture/export time.
The game has not previewed, placed, saved or synchronized these files. The native
conversion admission probe below exercises the production decoder and records
rejection; the VXBF files themselves remain intermediate inputs.

## Reproduce

Run from this feature worktree with Python 3.11+ and Java. No accounts are used.
All binary output and temporary data stay under `target/` by default.

```sh
python3 tools/structures/fixtures.py generate city --manifest dashboard/evidence/structure-city-v1.json
python3 tools/structures/fixtures.py generate maximum --manifest dashboard/evidence/structure-maximum-v1.json
python3 tools/structures/test_fixtures.py
java -cp target/classes tools/structures/CheckFixtureModels.java target/structure-fixtures/city-v1.vxbf.gz 256
java -cp target/classes tools/structures/CheckFixtureModels.java target/structure-fixtures/maximum-v1.vxbf.gz 512
python3 tools/structures/inspect_fixtures.py --output dashboard/evidence/structure-fixture-diagnostic.png
```

The Java check needs compiled production classes (`mvn test-compile`). It checks
every dependency against `ModelDefinition` serialization, readback, fingerprint
equality and `ModelMesher`, retaining the existing 200,000-byte and 8,192-quad
per-model limits. It does not register 512 models in the existing 128-model
library. Experimental structures now use a separate bounded world-component
namespace; its functional tests transfer 512 dependencies without increasing
the legacy inventory allowance. This probe does not qualify that transfer.

Generate again under `--output target/structure-fixtures-repeat` and compare both
compressed and decoded SHA-256 hashes to check reproducibility. Gzip uses an
empty filename and zero timestamp. Record Python and zlib versions: compressor
versions may change compressed bytes while leaving decoded bytes identical.
The version, seed, counts and hashes are in the two evidence manifests. Source
and manifest links refer to this feature branch pending controller publication.
The controller owns binary artifact publication. The 33,506,457-byte city and
54,394,399-byte maximum files exceed the 6 MB dashboard artifact limit; they
remain under `target/structure-fixtures/`, with reproducible source in Git.

## Corpus and intermediate format

VXBF version 1 is a benchmark intermediate representation, not a game save or
player structure package. It contains eight-byte `VXBF0001` magic, a big-endian
32-bit JSON header length, then the JSON header, dependencies, geometry leaves
and model instances. No simulation or transient entity state is included.

Each dependency has a big-endian uint16 local ID, uint8 resolution, uint8 ASCII
name byte length, name, and resolution³ opaque ARGB uint32 voxels (x fastest,
then y, then z). Geometry is big-endian `HHHBBBBHI`: cell x/y/z, subdivision
depth, subcell x/y/z, material, RGB. Instances use `HHHHB`: cell x/y/z, local
dependency ID, quarter-turn rotation. Header counts delimit the sections.
VXBF has no world ID remapping, anchor or spatial partition index. The separate
VSTR format implements those package fields; VXBF remains a benchmark source.

City bounds are 512×128×512. There are 121 four-storey buildings, a road grid,
isolated half-cell pylon segments, 256 unique 32³ models, and 10,240 instances
using all four rotations. Total occupancy is 978,288 cells. Dependencies have
deterministic dense colours to exercise serialization and model surface costs.

Maximum bounds are 1,024×256×1,024. Exactly 4,000,000 cells are occupied, including
16,384 model instances and 512 unique 16³ models. Other occupied cells contain
two opposite-corner 1/16 cubes. Cell origins are spaced by two cells on each
axis. Thus each of the 7,967,232 leaves has six exposed faces, with no touching
coplanar faces to merge. It has 47,803,392 geometry faces regardless of colour
merging. The selection bounds intentionally include empty space; occupied
geometry need not touch each selection boundary. This fixture stresses geometry
complexity, not uniform occupancy throughout all 256 vertical cells.

`surface_quads` counts exposed geometry leaf faces before greedy meshing and
excludes model surfaces. City road faces can merge; do not present its count as
actual rendered quads. `model_surface_unit_faces` records the dense model surface
count separately. Actual production model quad totals and encoded bytes are
recorded by the Java check. Dependency bytes in the fixture manifest measure
this intermediate representation, not the game's octree codec. Fractional leaf
count excludes leaves inside model definitions.

The parser bounds compressed bytes at 64 MiB, decoded bytes at 512 MiB and the
header at 4,096 bytes. A bitset bounded by declared fixture dimensions checks
occupied cells and overlapping instances. It checks model opacity, identity,
counts, dimensions and geometry fields, and rejects truncation/trailing data.
It accepts only these exact versioned fixture headers. It is not a general
untrusted player-package importer; that requires its own decoder and tests.

## Qualification runbook: not executed

The private dashboard defines the selected reference target. Provisioning and
execution on that target remain unconfirmed. Verify actual desktop and separate
dedicated-server hardware, OS builds, drivers, SSD models/interfaces and firmware,
Java versions, heaps and game revision against that specification. Record
effective graphics preset, view distance and 1080p output. Retain the complete
installed-package manifests with the qualification records. Do not substitute
the development VPS or infer availability from a target specification.

Use 32 independently authenticated production-protocol clients: one importer
and 31 active observers, with cohorts inside, near and outside the affected
region. Distribute load generators and demonstrate CPU/network headroom. A
headless cohort establishes server/network load only. Rendering needs the
reference desktop and physical GPU. Clear dependency caches for synchronization.

Shape and verify 1 Gbps server aggregate, 100 Mbps client download, 20 Mbps client
upload and 50 ms RTT. Run primary qualification at 0% injected loss and resilience
qualification at 1% injected random loss. Record shaping configuration and
measured achieved bandwidth, RTT and loss without retaining private addresses
or credentials in public artifacts.

Run at least 20 trials per fixture and loss profile, with one large placement
at a time. Preserve per-run observations. Report median, nearest-rank p95,
worst and peak memory, frame times, server tick durations and traffic. Measure
capture, export, initial preview, movement/rotation, placement, durable save,
cold region load, empty-cache synchronization and upload separately. Placement
includes validation, collision checks and nearby usable geometry, excludes
upload; synchronization starts at authoritative commit. Define cold-cache
preparation and verify it rather than calling a warm replay cold.

Check city/maximum p95 budgets: capture and export each 5/15 s, initial preview
0.5/1 s, preview updates 100/100 ms, placement and durable save each 5/15 s,
cold region load 8/20 s, and synchronization to all clients 10/30 s. Maximum
upload must be ≤45 s. Track acknowledgements ≤100 ms, progress/cancellation for
operations over 1 s, main-thread stalls ≤100 ms, ≥30 FPS and 20 Hz server ticks
with p95 tick duration <50 ms. Cancellation, failure and concurrent edits must
also leave no partial placement. Bounded functional tests cover these workflows;
full-corpus execution and performance qualification remain unfinished.

Do not publish supported geometry or instance limits until both corpora pass
on the agreed environment. The current measurements establish fixture counts
and model validity only. The diagnostic PNG visualizes the actual fixture
streams; it is neither a running-game screenshot nor evidence of GPU performance.

## Production CPU primitive probe

`run_probe.py` compiles and runs the unchanged production `MeshDataGenerator`
with 1, 8, 64 and 512 isolated fragmented cells per chunk. Each cell contains
the maximum fixture's two opposite-corner 1/16 leaves, giving exactly 12 faces
per cell. It performs three warmups and defaults to 20 recorded trials at each
density. It also checks empty geometry, six-face solid-chunk greedy merging and
interrupted-worker cancellation. No game state or player profile is loaded.

```sh
python3 tools/structures/run_probe.py --runs 20 --label LOCAL-VPS-CPU-PREREQUISITE --output target/structure-probe
python3 tools/structures/test_probe.py
python3 tools/structures/plot_probe.py target/structure-probe/report.json dashboard/evidence/structure-cpu-probe.png
```

Use the selected reference target's Java executable, heap and environment label
when running there; `--java`, `--javac`, `--xms`, `--xmx` and `--classpath` are
explicit options. Preserve `report.json` and `trials.jsonl`. Defaults use a
256 MiB initial and 1 GiB maximum heap for development only. The tool does not
assert that its environment label matches any physical target.

The report includes all trials, nearest-rank p95/median/worst, CPU/kernel/Java,
source/trial hashes, exact command, mesh output bytes, heap-pool peak sum and
process RSS high-water mark. Heap-pool maxima are not simultaneous and are
labelled as a conservative sum; RSS is a process-lifetime maximum. There is no
GPU memory measurement. Mesh setup and explicit pre-trial GC are excluded from
timing; mesher scratch is warmed. Timing is not a frame time or main-thread stall.

The probe is useful for chunk-density planning, but is not complete-fixture
meshing, model-instance rendering, capture, placement, durability, synchronization
or a cold-cache load test. It always reports `NOT QUALIFIED`. Do not derive
supported limits by multiplying its per-chunk timings. The current world mesh
layout allocates 216 bytes per quad across vertex, index and surface arrays.
Fully materializing all 47,803,392 maximum-fixture quads would therefore require
10,325,532,672 bytes for those arrays alone, excluding octrees and model data.
That arithmetic is not a measured peak or proof that bounded streaming will
fail; it shows why full residency must not be assumed.

The local development run is recorded in
`dashboard/evidence/structure-cpu-probe.json`. Its 512-cell median/p95/worst CPU
mesh times were 1,784.8/2,600.7/3,111.9 ms. The different, one-trial reporter smoke
run is recorded separately; it is not added to the 20-trial statistics. Those
VPS numbers do not establish limits for the selected reference target. The PNG
plots the recorded 20-trial data and explicitly labels the unqualified scope.

## Execution-provider handoff

Create and verify the deterministic allowlisted bundle:

```sh
python3 tools/structures/build_handoff.py
python3 tools/structures/verify_handoff.py target/structure-handoff/structure-prerequisite-v1.tar --manifest dashboard/evidence/structure-execution-handoff.json
python3 tools/structures/test_handoff.py
```

The archive includes fixture binaries, their manifests, prerequisite tools and
this runbook. It contains no runtime logs, profiles, accounts, credentials,
environment dumps or private dashboard messages. Source files are hashed, not
bundled: use the exact controller-published feature checkout. Before compiling,
verify its Java source set, source hashes and `pom.xml` against `HANDOFF.json`.
Record the controller-published checkout commit in the provider's result; the
local archive source hashes do not pretend that an unpublished commit exists.
The verifier reads archive members without extracting them. Its external manifest
must come from the trusted controller publication. Rebuild the bundle after any
source integration or prerequisite-tool edit. The bundle is too large for the
6 MB inline dashboard limit; transfer/publication remains the controller's job.

An execution provider still needs to confirm the selected machines are available
and accept the bundle. The provider's actual hardware/software observations,
source/fixture hashes, geometry/model-instance counts, timing distributions,
frame times, server ticks, RAM/VRAM and verified network conditions must accompany
any results. Record synchronization times and errors as well. The current bundle
does not implement a complete renderer/32-client qualification scenario. Its CPU
probe reports cannot substitute for those measurements or close the readiness
gate. No provider availability or external test result is claimed here.

## Execution status

No provider inquiry, email, purchase or private artifact transfer has been made.
Commercial sourcing is not part of the fixture/probe execution workflow. The
prepared archive remains available for controller-owned transfer if a suitable
execution environment becomes available. Reference GPU/network qualification is
unexecuted. Local CPU measurements do not establish supported structure limits.


## Native VSTR admission probe

`NativeFixture.java` converts the exact versioned VXBF corpus into native VSTR
wire bytes, using production model and octree codecs. It is a trusted offline
benchmark tool, not a player importer or a way to bypass game admission. It
never writes a world. It requires the exact generated header, bounds all input
counts and output bytes, sorts cells into native section order, and writes through
a temporary file. Failed conversion leaves no destination package. Existing
outputs are refused. Large binary output stays under `target/`.

```sh
java -cp target/classes tools/structures/NativeFixture.java --self-test
java -Xmx1g -cp target/classes tools/structures/NativeFixture.java city target/structure-fixtures/city-v1.vxbf.gz target/structure-fixtures/city-v2.vstr
java -Xmx1g -cp target/classes tools/structures/NativeFixture.java maximum target/structure-fixtures/maximum-v1.vxbf.gz target/structure-fixtures/maximum-v2.vstr
```

First verify the VXBF inputs with `fixtures.py verify`. The converter retains
geometry dimensions, materials and LED colours. Each dependency in this corpus
has one fixed instance orientation (`id % 4`); the converter rotates that model
once because VSTR stores no separate rotation per instance. Inputs that use a
different instance rotation are rejected. The small self-test compares native
wire bytes with `StructurePackage.encode()` and roundtrips through the production
decoder, including maximum section coordinates, whole/half/1/16 geometry, coloured
LED material and a rotated asymmetric model.

Historical VSTR v1 sizes (not operation timings; retained in the original report):

| Corpus | Occupied cells | Decoded bytes | Compressed bytes | Production decode |
| --- | ---: | ---: | ---: | --- |
| City | 978,288 | 58,000,837 | 38,191,053 | Rejected: `Experimental geometry cap` |
| Maximum | 4,000,000 | 309,713,208 | 55,607,892 | Rejected: `Experimental geometry cap` |

The native city exceeds the requested 16–32 MiB package range despite its source
VXBF fitting that range. The maximum native file fits 64 MiB compressed and
512 MiB decoded. Neither is an admitted structure. The converter deliberately
uses the native wire codecs without constructing an admitted `StructurePackage`,
then calls the actual production loader to expose its rejection. Safety caps
remain unchanged. Do not treat generated bytes, successful conversion, or
rejection as successful capture/import/placement or performance qualification.
The report is `dashboard/evidence/structure-native-fixtures.json`.


The current converter writes VSTR v2 using the same production dependency codec
as new exports. The table above records the earlier v1 encoding. Compare v2
results against that report, retaining the original VXBF input hashes. Both
encodings still face the same development admission gates. Smaller bytes alone
do not establish successful placement or performance qualification.


VSTR v2 native measurements (same VXBF source corpora; byte sizes only):

| Corpus | Decoded bytes | Compressed bytes | Final production admission |
| --- | ---: | ---: | --- |
| City | 41,073,093 | 30,258,485 | Rejects dependency surface cap while decoding dependencies |
| Maximum | 305,479,992 | 53,637,310 | Rejects geometry cap |

The city now fits the requested 16–32 MiB package range. Both remain rejected
structures. Import now checks dependency occupancy, identities and surface totals
before reading later definitions or cell payloads, so the first city rejection
is earlier than the historical geometry rejection. This is the same enforced
surface cap, not a new lower limit. Version 1 files follow the same early checks.
Generation, byte-size reduction and rejection do not qualify capture/export
latencies, placement, persistence, rendering or synchronization. The current
report is `dashboard/evidence/structure-v2-validation.json`.
