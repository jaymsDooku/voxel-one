# Basalt Courtyard v1

`dev.jayms.combat.arena.BasaltCourtyard` is an original, opt-in practice arena fixture.
It uses the engine's existing stone, white and ochre materials. No map, art, text or
code from another game is included. The layout supports arranged practice and adds
no combat preset: timing, knockback, kits and final multiplayer scenarios belong to
the combat and match-controller work items.

## Use and ownership

```java
try (var arena = BasaltCourtyard.generate()) {
    World world = arena.world();
    var north = BasaltCourtyard.spawn(BasaltCourtyard.Side.NORTH);
    // Place participant feet at north.feet(), yaw at north.yawDegrees().
    boolean clear = arena.safeLanding(new Vec3(6, 1, 0), .3, 1.8);
    arena.reset(); // Between matches, on the world owner thread.
}
```

The fixture owns a fresh `World` and 240 resident `Chunk`s. Do not stream, unload or
replace these chunks. No existing world is overwritten. Close it after the session.
Full-height empty columns prevent natural fallback terrain from occluding the fixture
in engine lighting queries. All generation uses `Chunk.setBlock`/`World.addChunk`; reset uses `World.setBlock`
so normal mesh, collision and edit invalidation applies. Reset restores every owned
cell, including removed floor, broken cover, partial edits and added obstacles.
It leaves edits outside its footprint and external participant/match state alone.

This work does not install an entry point or implement `CombatWorld`. Item 2 must
bind the fixture to the match controller, enforce opt-in and spectator isolation,
and use authoritative projectile/body sweeps. `safeLanding` is a conservative
geometry check for participant destinations. It accepts full-cell floor, cover and
platform tops, rejects unsupported air, walls, spectator decks and bodies outside
`PLAY`, and checks the whole body and full support footprint. It rejects partial,
model and water support after edits. Dimensions are bounded to half-width <= 2 and
height <= 4 blocks. It does not add faction, deathban or combat rules.

## Coordinate contract

One unit is one block. Coordinates are local. +Y is up; +Z is south.
`Camera` yaw 0 faces +X; yaw 90 faces +Z. Bounds are half-open. Points mirror by
negation; integer cells mirror by `x -> -x-1`, `z -> -z-1`. Materials and geometry
are mirrored across both axes, including the detached galleries.

| Feature | Coordinates / contract |
| --- | --- |
| Stable ID | `basalt-courtyard-v1` |
| Owned resident volume | X `[-32,32)`, Y `[-32,128)`, Z `[-48,48)` |
| Arena outer footprint | 64 × 80 blocks, X `[-32,32)`, Z `[-40,40)` |
| Participant bounds | X `[-31,31)`, Y `[1,24)`, Z `[-39,39)`; whole body must fit |
| Floor | Y=0 cells; ordinary feet Y=1 |
| Facing starts | North `(0,1,-28)`, yaw 90; south `(0,1,28)`, yaw -90; separation 56 blocks |
| Team starts | Three slots at X -2,0,2; slot order mirrors through origin |
| Flat exchanges | `(0,1,-8)` and `(0,1,8)`; lane X `[-8,8)`, Z `[-36,36)` |
| Low cover | Four 4 × 4 × 1 islands, X `[-14,-10)` / `[10,14)`, Z same |
| Interception | Ray `(12,1.5,5)` toward +Z meets cover at Z=10, distance 5; use engine `BlockRaycaster` for point rays |
| Safe pearl destination | `(6,1,0)` with half-width .3, height 1.8 |
| Rejected destination | `(12,1,12)` is inside cover; `(0,2,0)` has no support |
| Elevated flanks | X `[-26,-20)` / `[20,26)`, Z `[-5,5)`; top feet Y=3 |
| Flank access | Two-block-deep, one-block-high steps at Z `[-7,-5)` / `[5,7)`; jump two single-block rises, from either end |
| Sprint-kite loop | X +/-28, Z +/-36; clear and fully supported around all obstacles |
| Enclosure | One-block-thick continuous walls at outermost X/Z cells, Y=1..8 inclusive; white coping |
| Headroom | Open sky; all interior cells Y=3..23 are clear, including above platforms |
| Spectators | `(0,11,-44)` / `(0,11,44)`; detached 16 × 6 decks, two-block gaps outside walls, outer/back rails |

`COVER`, `PLATFORMS`, `PLAY`, `FLAT_LANE` and `FOOTPRINT` are immutable reusable
bounds. `Fixture.collides` uses current engine `VoxelQueries`, including partial
edits; unloaded geometry is solid. There is no invisible ceiling. The match owner
must prevent participants leaving over walls and prevent spectator interaction;
this fixture does not claim that geometry alone provides access control.

## Validation and preview

Run Java 17 Maven checks with a writable local dependency cache and test temp dir:

```sh
mkdir -p .tmp/arena/java-tmp
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -q \
  -Dmaven.repo.local=.tmp/m2 \
  -DargLine="-Djava.io.tmpdir=$PWD/.tmp/arena/java-tmp" verify
```

`BasaltCourtyardTest` instantiates the generated world. It checks both-axis
symmetry over all 983,040 owned cells, repeat generation, boundary continuity,
headroom, facing and team starts, spectator separation, point-ray interception,
whole-body routes, the ground-level kite loop, actual `Player` sprint/jump/wall
collision, landing edge cases, partial-edit reset, external edit preservation and
idempotence. It does not test through a second abstract arena implementation.

`deploy/BasaltCourtyardPreview.java` runs the actual fixture, engine mesher,
`RenderPipeline`, shaders and `Player`. It uses an in-memory synthetic world and
an isolated profile; no account, server or player save is loaded. Compile with a
Java 17 compiler, or `javac --release 17`, after Maven compilation:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -q -Dmaven.repo.local=.tmp/m2 \
  dependency:build-classpath -Dmdep.outputFile=.tmp/arena/classpath
mkdir -p .tmp/arena/classes .tmp/arena/profile .tmp/arena/recording
javac --release 17 -cp "target/classes:$(cat .tmp/arena/classpath)" \
  -d .tmp/arena/classes deploy/BasaltCourtyardPreview.java
/usr/lib/jvm/java-17-openjdk-amd64/bin/java \
  -Duser.home="$PWD/.tmp/arena/profile" \
  -Djava.io.tmpdir="$PWD/.tmp/arena/java-tmp" \
  -Dorg.lwjgl.system.SharedLibraryExtractPath="$PWD/.tmp/arena/natives" \
  -cp ".tmp/arena/classes:target/classes:$(cat .tmp/arena/classpath)" \
  BasaltCourtyardPreview --display "$DISPLAY" dashboard/evidence .tmp/arena/recording
```

The preview records overview and a real central-lane sprint through
`ScreenRecorder.toggle`/`capture`, the same recorder path used by F10. It also
captures spawn and flank views and asserts reset recovery, a complete 56-block
sprint, a wall stop and the jump route. This is fixture validation, not a playable
multiplayer match. Final match scenarios and multiplayer motion recording remain
acceptance for item 2. Software rendering does not qualify physical-GPU gameplay
performance or stable rollout.

### Measured local playtest

On Linux with Java 17.0.21 and llvmpipe (LLVM 15.0.6, 256 bits), the generated
fixture produced 24 nonempty meshes. After 420 sprint steps at 1/60 second, the
north player reached Z=27.622 from Z=-28, with feet Y=1.000. The boundary probe
stopped at X=30.700, matching the 0.3-block player radius against the wall at X=31.
The 180-step jump route peaked at Y=4.311 and crossed to Z=2.686 on the raised
flank. Floor deletion and a new lane obstacle both recovered through reset.

The three PNG captures show the generated overview, spawn lane and flank. The
F10-path MP4 contains 90 captured frames over 117.783 seconds, 2,288,520 bytes.
Decoded frames at 1.0 and 82.4481 seconds were visually inspected. The slow software
rendering capture is scene/workflow evidence, not a frame-rate qualification.
All media is from this implementation; controller publication is pending.

The final targeted Java 17 check passed all 48 tests with no failures, errors or
skips:

```sh
timeout 120s env JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -q \
  -Dmaven.repo.local=.tmp/m2 \
  -DargLine="-Djava.io.tmpdir=$PWD/.tmp/arena/java-tmp" \
  -Dsurefire.reportNameSuffix=arena-final \
  -Dtest=BasaltCourtyardTest,PlayerTest,PlayerCameraTest,FractionalWorldTest,TerrainGenerationTest,WorldSkyVisibilityTest,ProtocolCompatibilityTest test
```

The full `verify` run, with the same local cache and test temp directory, was
interrupted (exit 130) after it stopped advancing. It had reported 181 tests,
0 failures, 0 errors and 1 skip. This is not a full-suite pass. Full hosted CI,
latest-master integration and validation of any resulting source changes remain
pending with the controller. There are no intended deletions of existing source.
The diff adds the arena, its tests, the preview and this contract; the progress
entry and four sanitized media artifacts support the handoff.
