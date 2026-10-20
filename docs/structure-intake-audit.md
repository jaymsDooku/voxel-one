# Reusable structures: prerequisite audit

Status: historical prerequisite audit. Experimental implementation now exists;
see `structures.md` for current behavior and unqualified limits. This audit is
not performance qualification or a supported-city claim.

## Existing paths

- `net/WorldVoxels.java` stores sparse overrides of seeded terrain. Each world
  cell has a 16³ octree. Capture must sample resolved terrain plus overrides,
  rather than treating the edit history as the complete world.
- `net/Protocol.java` represents edits with cell coordinates, subdivision depth,
  subcell coordinates, material/type and colour. Structure rotation must preserve
  these fields and rotate subcell coordinates as well as cell coordinates.
- `net/model/ModelDefinition.java` retains 8/16/32 per-axis model resolution,
  opaque ARGB colours and a 200,000-byte serialized definition limit. Its SHA-256
  fingerprint includes the name and serialized voxels. Registration freezes a
  copy; model-library snapshots share immutable definitions and copy their maps.
- `net/model/ModelLibrary.java` allows 128 custom models per world and 16 per
  author. Entry serialization writes a one-byte ID. These allowances cannot
  restore 512 unique custom dependencies in an empty destination world. Raising
  the count alone is insufficient: dependency identity, wire IDs, item/material
  classification and persistence need a compatibility design.
- `net/LocalGame.java` reads save versions 3–8 and writes version 8 through a
  temporary file and rename. Loading rejects more than 2,000,000 edits. Four
  million occupied structure cells therefore cannot simply become individual
  legacy edits without changing persistence and its validation.
- `net/MultiplayerServer.java` also rejects more than 2,000,000 saved edits.
  Edits, model creation and persistence use synchronized server paths. Large
  structure work must be staged outside those critical sections, then revalidate
  destination revisions at commit. Streaming old edit packets individually would
  not establish atomic visibility or the required cancellation semantics.
- `ui/ModelEditor.java` has model undo/redo. This is not world-placement undo.
  Placement needs a transaction record and checks against later destination edits.
- `net/city/StructureBlueprint.java` generates procedural city buildings;
  `physics/VoxelStructures.java` handles structural physics. Neither supplies a
  reusable player-captured package or the requested benchmark fixtures.
- `deploy/generate_base10_city_fixtures.py` creates CityFrame compatibility
  fixtures. Those are not the specified million-cell city benchmark.

## Benchmark prerequisite

Versioned intermediate fixtures now have a deterministic generator, streamed
parser, manifests, checksums, dependency checks through the production codec and
mesher, and a diagnostic plot. See `structure-benchmark-preparation.md`.
These are not the eventual player package format. Their compressed sizes do not
predict that format's sizes. Binary outputs remain local pending controller
publication; source and manifest URLs are also pending controller publication.

The reference target has been selected through the private dashboard. Exact
target specifications remain there; they are not copied as private answer text
into this repository. Target provisioning and execution have not been confirmed.
The benchmark runbook records network and topology requirements, but those
conditions have not been provisioned or verified. Geometry fragmentation and
instance limits must still be benchmark-established before feature implementation
under intake step 1. Physical-GPU qualification remains pending. No new
desktop/session access request is needed. No performance or supported-city claim
is made.

## Validation scope

This change is an audit document and local progress entry. Existing model,
fractional-world, sculptor and protocol tests provide a baseline only. They do
not validate structures. Browser playtesting does not apply to this document;
the new gameplay workflow and implementation media remain untested and pending.
See `dashboard/evidence/structure-intake-audit.json` for executed checks.
Current fixture-tool checks are in
`dashboard/evidence/structure-fixture-validation.json`.
Current local CPU primitive trials and regressions are in
`dashboard/evidence/structure-cpu-probe.json`; they do not qualify the reference
target or the structure feature.
