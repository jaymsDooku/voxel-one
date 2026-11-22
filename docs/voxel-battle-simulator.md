# Voxel Battle Simulator

This is an offline desktop arena in the shared Java engine. Select **Voxel Battle
Simulator** in the desktop game catalog and click **Open arena**, or run:

```sh
mvn -Dmaven.repo.local=target/maven-cache package -DskipTests
java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --game battle --offline
```

Use `-XstartOnFirstThread` before `-jar` on macOS. Java 17 or newer is required.
The arena opens directly. It does not sign in, connect to a server, load a world,
or write a save. Network and world arguments are rejected. F10 recordings are
the only arena output, in the existing `.voxel-one/recordings` folder under the
user's home. Sandbox and City keep their existing launch, port and save paths.

| Input | Action |
| --- | --- |
| WASD | Move relative to the camera |
| Mouse / arrow keys | Aim the chase camera; the crosshair selects the struck box |
| Left mouse button | Jab with the left arm |
| Right mouse button | Heavy hit with the right arm |
| R | Start a fresh round, restore controls and reset the camera |
| F10 | Start or stop the engine framebuffer recorder |
| Tab / click | Free or recapture the mouse |
| Escape | Quit |

Close to punching range before attacking. Blue is the human fighter; amber is
the sparring partner. Each fighter has a head, torso, two arms and two legs.
The HUD shows each part's health, lost parts and the fighter's state. The partner
approaches, faces the player and attacks, including heavy hits. A lost left arm
disables jabs; a lost right arm disables heavy hits. One lost leg reduces speed
to 45%; two reduce it to 15%. A destroyed head or torso ends participation and
releases the remaining attachments. Press R to restore both fighters.

## Implementation plan and bounded defaults

The implementation uses the engine's `RigidBody`, `Constraints.Hinge`,
`PhysicsWorld`, voxel cube mesh, shader, overlay and `ScreenRecorder`. The arena
uses a fixed floor and four walls, with the same rendered and colliding bounds.
There are 12 fighter bodies and ten attachment joints at reset, plus five static
terrain bodies. Detachment removes joints and reuses existing bodies; it creates
no extra debris. Reset replaces the physics world, including its contact history.
No persistent game or network schema changes are needed.

`BattleArena.Rules` is the configuration boundary. Initial values:

| Setting | Default |
| --- | --- |
| Part masses | Head 4 kg, torso 18 kg, each arm 3 kg, each leg 6 kg |
| Health | Head 100, torso 140, each arm 64, each leg 80 |
| Jab / heavy damage | 24 / 38 |
| Jab / heavy point impulse | 7 / 28 N s |
| Knockdown threshold | 20 N s |
| Knockdown recovery | 2.2 s |
| Light-hit stagger | 0.28 s |
| Jab / heavy cooldown | 0.48 / 0.816 s |
| Opponent minimum cooldown | 1.05 s |
| Walking speed | 3.5 blocks/s |
| Melee contact reach | 3.35 blocks from attacker torso |
| Physics timestep | 1/120 s, unchanged from the engine lab |

Standing fighters use kinematic poses. A hit releases attached boxes to dynamic
physics before applying `impulseAt` to the struck part at contact. Strong hits
enter DOWN; light hits enter STAGGERED. Existing joints remain during either
state. Recovery restores the standing pose and retains damage and missing parts.
This is a bounded arcade controller, not a dynamic balance controller. The engine
uses conservative rotated AABB collision, not oriented-box narrowphase. Detached
parts collide with terrain, with mutual debris collision disabled to avoid piles
locking up the prototype. These choices keep reset and solver cost bounded.

The normal attack path consumes one swing even on a miss and damages only the
nearest attached target box on the aim ray. Both fighters share that path and the
same impact, damage, detachment and recovery rules. The opponent cycles its aim
among parts; every third successful attack attempt prefers the heavy arm.

## Validation and qualification

Baseline before implementation: `SandboxIdentityTest` 5 tests, `CityTest` 11
tests and `PhysicsTest` 25 tests passed. Targeted combat regressions cover local
damage and angular impulse, nearest-part ray resolution and swing deduplication,
symmetric limb loss and disabled actions, intact knockdown and recovery,
detached-part floor collision, opponent behavior and 40 complete breakup/reset
cycles. The reset assertions require 17 total bodies and ten joints, all health
restored, controls enabled and cooldown/AI state reset.

```sh
mkdir -p target/tmp
mvn -Dmaven.repo.local=target/maven-cache \
  -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" \
  -Dtest=BattleArenaTest,SandboxIdentityTest,CityTest,PhysicsTest,CityMultiplayerTest,MultiplayerTest,RegionalSaveCompatibilityTest,ProtocolCompatibilityTest test
python3 deploy/run_battle_smoke.py --display "$DISPLAY"
```

The native harness inherits the assigned display and XAUTHORITY, uses a synthetic
home in ignored `target/battle`, and drives production controls through X11. It
checks WASD movement, real mouse attacks, heavy knockdown, AI retaliation, F10,
loss of the native jab action with a surviving heavy attack, eight R resets,
Tab then R control recovery and Escape. Explicitly labelled
render-thread fixtures exercise symmetric detachment and core breakup through
the same production combat, physics and rendering path. Media labels distinguish
these fixtures from real-input combat. Browser playtesting does not apply to this
native LWJGL client.

Executed local checks on Linux x86_64 with Java 25.0.3:

| Check | Result |
| --- | --- |
| `BattleArenaTest` | 9 passed, 0 failed/errors/skipped |
| `SandboxIdentityTest` | 5 passed, 0 failed/errors/skipped |
| `CityTest` | 11 passed, 0 failed/errors/skipped |
| `PhysicsTest` | 25 passed, 0 failed/errors/skipped |
| `CityMultiplayerTest` | 1 passed, 0 failed/errors/skipped |
| `MultiplayerTest` | 15 passed, 0 failed/errors/skipped |
| `RegionalSaveCompatibilityTest` | 4 passed, 0 failed/errors/skipped |
| `ProtocolCompatibilityTest` | 3 passed, 0 failed/errors/skipped |
| `mvn -q -Dmaven.repo.local=target/maven-cache -DskipTests package` | Passed; builds client/server/launcher jars; does not execute tests |
| `python3 deploy/run_battle_smoke.py --display "$DISPLAY"` | Combat, action loss, both Main launch routes, reset and fresh F10 media passed |
| `git diff --check` | Passed |

The broad test command above ran before the final arm-animation correction. The
final focused command reran `BattleArenaTest,SandboxIdentityTest,CityTest,PhysicsTest`
with the same Maven repository and temporary-directory arguments. All 50 tests
passed. The separate multiplayer/save/protocol checks cover the unchanged shared
network and persistence code. These runs total 73 distinct passing tests, rather
than one full-suite run. Hosted cross-platform `mvn verify` remains pending.

Playtest: synthetic offline Linux/X11 profile, inherited assigned DISPLAY and
XAUTHORITY, 960x640. W moved the human toward the partner. Real LMB input struck a
part; real RMB input caused intact DOWN with all five opponent joints retained
and measurable dynamic rotation. AI attacks reduced human part health. Controlled
fixtures then detached the human left arm and opponent right leg, and destroyed
both cores through production `impact` and physics. A native LMB press could no
longer start a swing after left-arm loss; RMB could. Eight native R presses
restored 17 total bodies and ten joints without growth. Tab then R restored mouse
capture, and Escape exited. The actual `Main.main` CLI and catalog selection
routes also opened the arena, accepted R and exited with Escape. Expected and
observed results matched every assertion. No GL errors occurred at captures.

Fresh media from this implementation is in `dashboard/evidence/battle-*.png` and
`battle-fight.mp4`. The clip contains real-input combat and intact knockdown,
followed by the controlled symmetric detachment/core-breakup fixtures described
above. It is not an uninterrupted human-input match. The media verifier decoded
all 40 H264 frames, confirmed changing images and matching MP4 frame counts, and
checked the 6 MB artifact cap. The clip is 826,915 bytes and 14.143 seconds long.
First, middle and last decoded frames and selected final screenshots were visually
inspected. Transient reports and full
runtime diagnostics stay inside ignored `target/battle` and are not publication
artifacts.

Hardware: VPS guest CPU reports `Intel Core Processor (Haswell, no TSX)`; renderer
is `llvmpipe (LLVM 15.0.6, 256 bits)`. The arena uses its bounded flat-shaded scene,
VSync enabled, 960x640, and the shared 120 Hz timestep. Its 51 measured frame
intervals include scripted input, screenshots and F10 recording: median
269.545 ms, p95 386.056 ms. Per-rendered-frame simulation cost was median
18.771 ms, p95 32.984 ms. Catch-up is capped at 100 ms of simulation per frame,
so this software-rendered run does not establish real-time gameplay at 60 FPS.

The reference harness uses current production Sandbox and City clients with an
explicit LOW profile, 960x640, detail distance 16, horizon 512, shadows off and
fixed daytime. This bounds the VPS workload. Default graphics did not produce a
usable bounded reference on this machine. Reference runs include terrain
streaming and use small, explicitly reported sample counts. Their workload and
the arena's workload differ; they are diagnostic references for later hardware
qualification.

| Software-rendered workload | Samples | Median frame time | p95 frame time |
| --- | ---: | ---: | ---: |
| Battle arena with capture/input | 51 | 269.545 ms | 386.056 ms |
| Sandbox bounded LOW reference | 15 | 1290.146 ms | 1391.580 ms |
| City bounded LOW reference | 15 | 1219.516 ms | 1709.194 ms |

The final complete native harness exited 0, including both bounded references.
The reference helper is `deploy/BattleBaselineSmoke.java`; it sets and records its
synthetic graphics profile explicitly. Each new harness run gets a unique ignored
scratch directory and synthetic home, preserving earlier reports.

The proposed tracked diff was audited against initial checkout
`a9e8eed8683ccac1c2f6dcde4388c6a198a7d6ef`, along with all added source, tests,
documentation and media. There are no unrelated file deletions or unmerged index
entries. Shared edits add only the battle catalog identity and launch routing;
Sandbox/City persistence and network implementations are untouched. The runner's
synchronized review base is still pending. Any integration that changes source
requires fresh affected checks and playtest/media before independent review.

Local media is pending runner publication. Independent exact-head review,
latest-master integration checks, ordinary hosted CI and physical-GPU performance
qualification remain required. Software-rendered VPS measurements are diagnostic;
they do not qualify the 60 FPS target or authorize stable rollout. No unrelated
source deletions are intended. The controller must supply the synchronized review
base before the final full-diff audit and resulting-head validation.
