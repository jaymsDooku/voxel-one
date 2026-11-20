package dev.jayms.combat;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatMovement.Input;
import dev.jayms.combat.CombatMovement.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatMovementTest {
    private static final Vec3 SPAWN = new Vec3(.5, 1, .5);
    private static final Vec3 ZERO = new Vec3(0, 0, 0);
    private static final double EPSILON = 1e-5;
    private static final Arena FLOOR = field((x, y, z) -> y == 0);

    @Test
    void walkingAccumulatesVelocityAndCoastsAfterRelease() {
        State state = new State(SPAWN, ZERO, true, 0);
        var walking = input(1, 0, false, false);
        var first = CombatMovement.step(FLOOR, 1, state, walking);
        assertEquals(.098, first.displacement().x(), EPSILON);
        assertEquals(.053508, first.state().motion().x(), EPSILON);
        assertTrue(first.state().grounded());

        var next = CombatMovement.step(FLOOR, 1, first.state(), walking);
        assertEquals(.151508, next.displacement().x(), EPSILON);
        assertEquals(-.0784, next.state().motion().y(), EPSILON);

        var released = CombatMovement.step(FLOOR, 1, next.state(), input(0, 0, false, false));
        assertEquals(.082723368, released.displacement().x(), EPSILON);
        for (int i = 0; i < 12; i++) {
            released = CombatMovement.step(FLOOR, 1, released.state(), input(0, 0, false, false));
        }
        assertEquals(0, released.displacement().x(), EPSILON);
        assertEquals(1, released.state().feet().y(), EPSILON);
    }

    @Test
    void diagonalInputKeepsWalkingSpeedAndUsesCameraYaw() {
        State state = new State(SPAWN, ZERO, true, 0);
        var diagonal = CombatMovement.step(FLOOR, 1, state, new Input(90, 1, 1, false, false, 1, 1));
        assertEquals(-.098 / Math.sqrt(2), diagonal.displacement().x(), EPSILON);
        assertEquals(.098 / Math.sqrt(2), diagonal.displacement().z(), EPSILON);
        assertEquals(.098, Math.hypot(diagonal.displacement().x(), diagonal.displacement().z()), EPSILON);
    }

    @Test
    void airSteeringUsesItsOwnAccelerationAndRetainsMoreMomentum() {
        State midair = new State(new Vec3(.5, 4, .5), new Vec3(.2, 0, 0), false, 0);
        var steered = CombatMovement.step(FLOOR, 1, midair, new Input(0, 0, 1, false, false, 1.4, 1));
        assertEquals(.2, steered.displacement().x(), EPSILON);
        assertEquals(.0196, steered.displacement().z(), EPSILON);
        assertEquals(.182, steered.state().motion().x(), EPSILON);
        assertEquals(.017836, steered.state().motion().z(), EPSILON);
        assertEquals(-.0784, steered.state().motion().y(), EPSILON);
        assertFalse(steered.state().grounded());
    }

    @Test
    void groundSpeedAndItemUseScaleTheInputAcceleration() {
        State state = new State(SPAWN, new Vec3(.1, 0, 0), true, 0);
        var first = CombatMovement.step(FLOOR, 1, state, new Input(0, 1, 0, false, false, 1.4, .2));
        assertEquals(.12744, first.displacement().x(), EPSILON);
        assertEquals(.06958224, first.state().motion().x(), EPSILON);
    }

    @Test
    void sprintJumpCoversGroundBeforeAirControlAndLands() {
        State state = new State(SPAWN, ZERO, true, 0);
        Input sprintJump = input(1, 0, true, true);
        var launch = CombatMovement.step(FLOOR, 1, state, sprintJump);
        assertTrue(launch.jumped());
        assertFalse(launch.state().grounded());
        assertEquals(.3274, launch.displacement().x(), EPSILON);
        assertEquals(.42, launch.displacement().y(), EPSILON);

        var airborne = CombatMovement.step(FLOOR, 1, launch.state(), sprintJump);
        assertFalse(airborne.jumped());
        assertEquals(.2042404, airborne.displacement().x(), EPSILON);
        assertEquals(.3332, airborne.displacement().y(), EPSILON);

        var landed = airborne;
        for (int i = 0; i < 18 && !landed.state().grounded(); i++) {
            landed = CombatMovement.step(FLOOR, 1, landed.state(), input(0, 0, false, false));
        }
        assertTrue(landed.state().grounded());
        assertEquals(1, landed.state().feet().y(), EPSILON);
        assertEquals(-.0784, landed.state().motion().y(), EPSILON);
    }

    @Test
    void ceilingClipsJumpAndHoldingUsesTenTickCooldown() {
        Arena ceiling = field((x, y, z) -> y == 0 || y == 3);
        Input held = input(0, 0, true, false);
        var launch = CombatMovement.step(ceiling, 1, new State(SPAWN, ZERO, true, 0), held);
        assertTrue(launch.jumped());
        assertEquals(.2, launch.displacement().y(), EPSILON);
        assertEquals(-.0784, launch.state().motion().y(), EPSILON);
        var current = launch;
        for (int tick = 2; tick <= 10; tick++) {
            current = CombatMovement.step(ceiling, 1, current.state(), held);
            assertFalse(current.jumped());
        }
        assertTrue(current.state().grounded());
        assertTrue(CombatMovement.step(ceiling, 1, current.state(), held).jumped());
    }

    @Test
    void risingOverACubeResolvesVerticalBeforeHorizontalAxes() {
        Arena cube = field((x, y, z) -> y == 0 || (x == 1 && y == 1 && z == 0));
        State state = new State(new Vec3(.5, 1.55, .5), new Vec3(.5, .5, 0), false, 0);
        var moved = CombatMovement.step(cube, 1, state, input(0, 0, false, false));
        assertEquals(.5, moved.displacement().x(), EPSILON);
        assertEquals(.5, moved.displacement().y(), EPSILON);
        assertFalse(moved.hitHorizontalWall());
        assertTrue(CombatGeometry.free(cube, moved.state().feet()));
    }

    @Test
    void terrainClipsTheBlockedComponentWhilePreservingTangentialMotion() {
        Arena wall = field((x, y, z) -> y == 0 || (x == 1 && y > 0));
        State state = new State(SPAWN, new Vec3(1.2, -.0784, .3), true, 0);
        var moved = CombatMovement.step(wall, 1, state, input(0, 0, false, false));
        assertTrue(moved.hitHorizontalWall());
        assertEquals(.69, moved.state().feet().x(), EPSILON);
        assertEquals(.3, moved.displacement().z(), EPSILON);
        assertEquals(0, moved.state().motion().x(), EPSILON);
        assertEquals(.1638, moved.state().motion().z(), EPSILON);
        assertTrue(CombatGeometry.free(wall, moved.state().feet()));
    }

    @Test
    void motionRespectsFactionEntryRestrictions() {
        Arena boundary = new Arena() {
            @Override public boolean solid(int x, int y, int z) { return y == 0; }
            @Override public boolean contains(Vec3 feet) { return true; }
            @Override public boolean canEnter(int playerId, Vec3 feet) { return playerId == 2 || feet.z() <= .8; }
        };
        State state = new State(SPAWN, new Vec3(.3, 0, .8), true, 0);
        var barred = CombatMovement.step(boundary, 1, state, input(0, 0, false, false));
        assertTrue(barred.hitHorizontalWall());
        assertEquals(.8, barred.state().feet().z(), EPSILON);
        assertEquals(.3, barred.displacement().x(), EPSILON);
        assertEquals(0, barred.state().motion().z(), EPSILON);
        var permitted = CombatMovement.step(boundary, 2, state, input(0, 0, false, false));
        assertFalse(permitted.hitHorizontalWall());
        assertEquals(.8, permitted.displacement().z(), EPSILON);
    }

    @Test
    void walkingOffALedgeFallsAndRecoversGroundContact() {
        Arena ledge = field((x, y, z) -> y == 0 || (x == 0 && y == 1));
        State state = new State(new Vec3(.5, 2, .5), new Vec3(.9, -.0784, 0), true, 0);
        Input coast = input(0, 0, false, false);
        var edge = CombatMovement.step(ledge, 1, state, coast);
        assertEquals(1.4, edge.state().feet().x(), EPSILON);
        assertEquals(2, edge.state().feet().y(), EPSILON);

        var falling = CombatMovement.step(ledge, 1, edge.state(), coast);
        assertFalse(falling.state().grounded());
        assertEquals(-.0784, falling.displacement().y(), EPSILON);
        var landed = falling;
        for (int i = 0; i < 10 && !landed.state().grounded(); i++) {
            landed = CombatMovement.step(ledge, 1, landed.state(), coast);
        }
        assertTrue(landed.state().grounded());
        assertEquals(1, landed.state().feet().y(), EPSILON);
    }

    private static Input input(float forward, float strafe, boolean jump, boolean sprint) {
        return new Input(0, forward, strafe, jump, sprint, 1, 1);
    }

    private static Arena field(Voxels voxels) {
        return new Arena() {
            @Override public boolean solid(int x, int y, int z) { return voxels.solid(x, y, z); }
            @Override public boolean contains(Vec3 feet) { return true; }
        };
    }

    @FunctionalInterface
    private interface Voxels {
        boolean solid(int x, int y, int z);
    }
}
