package dev.jayms.combat;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.Vec3;

/** Shared fixed-tick motion for fighters on the arena's full blocks. */
final class CombatMovement {
    private static final double AIR_DRAG = .91;
    private static final double GROUND_DRAG = .6 * AIR_DRAG;
    private static final double GROUND_ACCELERATION = .1, AIR_ACCELERATION = .02;
    private static final double SPRINT_MULTIPLIER = 1.3, INPUT_DAMPING = .98;
    private static final double JUMP_SPEED = .42, SPRINT_JUMP_PUSH = .2;
    private static final double GRAVITY = .08, VERTICAL_DRAG = .98;
    private static final double MOTION_DEADZONE = .005, COLLISION_EPSILON = 1e-7;
    private static final int JUMP_COOLDOWN = 10;

    record Input(float yaw, float forward, float strafe, boolean jump, boolean sprinting,
                 double groundSpeedMultiplier, double inputScale) {}

    /** Motion is the carried velocity after drag, in blocks per tick. */
    record State(Vec3 feet, Vec3 motion, boolean grounded, int jumpCooldown) {}

    /** Displacement is the actual movement after clipping, before drag. */
    record Step(State state, Vec3 displacement, boolean jumped, boolean hitHorizontalWall) {}

    private CombatMovement() {}

    static Step step(Arena arena, int id, State state, Input input) {
        Vec3 feet = state.feet();
        Vec3 carried = state.motion();
        double x = clean(carried.x()), y = clean(carried.y()), z = clean(carried.z());
        int cooldown = input.jump() ? Math.max(0, state.jumpCooldown() - 1) : 0;
        // Paper handles a grounded jump before travel, even if a hit already supplied upward motion.
        boolean jumped = input.jump() && state.grounded() && cooldown == 0;
        Vec3 heading = CombatGeometry.direction(input.yaw(), 0);
        if (jumped) {
            cooldown = JUMP_COOLDOWN;
            y = JUMP_SPEED;
            if (input.sprinting()) {
                x += heading.x() * SPRINT_JUMP_PUSH;
                z += heading.z() * SPRINT_JUMP_PUSH;
            }
        }

        double acceleration = state.grounded() ? GROUND_ACCELERATION * input.groundSpeedMultiplier() : AIR_ACCELERATION;
        if (input.sprinting()) acceleration *= SPRINT_MULTIPLIER;
        // EntityLiving damps the input axes before Entity applies its deadzone and length cap.
        double forward = input.forward() * input.inputScale() * INPUT_DAMPING;
        double strafe = input.strafe() * input.inputScale() * INPUT_DAMPING;
        double lengthSquared = forward * forward + strafe * strafe;
        if (lengthSquared >= 1e-4) {
            double strength = acceleration / Math.max(1, Math.sqrt(lengthSquared));
            x += (heading.x() * forward - heading.z() * strafe) * strength;
            z += (heading.z() * forward + heading.x() * strafe) * strength;
        }

        Vec3 next = CombatGeometry.clipMovement(arena, id, feet, new Vec3(x, y, z));
        Vec3 displacement = next.minus(feet);
        boolean blockedX = clipped(x, displacement.x());
        boolean blockedY = clipped(y, displacement.y());
        boolean blockedZ = clipped(z, displacement.z());
        boolean grounded = y < 0 && blockedY
                || y == 0 && state.grounded() && CombatGeometry.supported(arena, next);
        double drag = state.grounded() ? GROUND_DRAG : AIR_DRAG;
        Vec3 motion = new Vec3(blockedX ? 0 : x * drag, ((blockedY ? 0 : y) - GRAVITY) * VERTICAL_DRAG,
                blockedZ ? 0 : z * drag);
        return new Step(new State(next, motion, grounded, cooldown), displacement, jumped, blockedX || blockedZ);
    }

    private static double clean(double velocity) {
        return Math.abs(velocity) < MOTION_DEADZONE ? 0 : velocity;
    }

    private static boolean clipped(double requested, double actual) {
        return Math.abs(requested - actual) > COLLISION_EPSILON;
    }
}
