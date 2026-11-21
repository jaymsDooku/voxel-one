package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.PlayerState;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatGeometry;
import dev.jayms.combat.CombatRules;
import dev.jayms.player.PlayerDimensions;

import java.util.random.RandomGenerator;

import static dev.jayms.combat.CombatEngine.HOTBAR_SIZE;
import static dev.jayms.combat.CombatEngine.MAX_HEALTH;
import static dev.jayms.combat.CombatEngine.TICKS_PER_SECOND;

/** Drives the offline opponent through the same movement and attack inputs as a player. */
final class SparringPartner {
    private static final float YAW_STEP = 30, PITCH_STEP = 22.5f;
    private static final double EMERGENCY_HEALTH = 5.25;
    private static final int MAX_HEAL_SETUP_TICKS = 16, HEAL_FOLLOW_THROUGH_TICKS = 4;
    private static final int[] RETREAT_TURNS = {0, 65, -65, 110, -110};

    private final CombatRules rules;
    private final Arena arena;
    private final RandomGenerator random;
    private int lastHits;
    private long lastSprintClick = -1;
    private long healReadyTick, healSetupTick = -1, healFollowThroughUntil;
    private float healingYaw, healingPitch;
    private int clickRate, cadenceTicks, clickProgress;
    private int strafeTicks;
    private float strafe;

    SparringPartner(CombatRules rules, Arena arena, RandomGenerator random) {
        this.rules = rules;
        this.arena = arena;
        this.random = random;
    }

    Intent input(long tick, PlayerState bot, PlayerState opponent) {
        Vec3 offset = opponent.feet().minus(bot.feet());
        double range = Math.hypot(offset.x(), offset.z());
        double targetYaw = range == 0 ? bot.yaw() : Math.toDegrees(Math.atan2(offset.z(), offset.x()));
        double targetPitch = Math.toDegrees(Math.atan2(offset.y() - .25, range));

        // A targeted click can stop sprint even when the damage is rejected.
        int hits = bot.stats().hits();
        boolean sprintStopped = lastSprintClick >= 0 && bot.sequence() == lastSprintClick && !bot.sprinting();
        boolean wTap = hits > lastHits || sprintStopped;
        lastHits = hits;
        lastSprintClick = -1;
        boolean click = clickDue();
        Intent recovery = recoveryInput(tick, bot, opponent, targetYaw);
        if (recovery != null) {
            strafeTicks = 0;
            return recovery;
        }

        float yaw = turnToward(bot.yaw(), targetYaw, YAW_STEP);
        float pitch = turnToward(bot.pitch(), targetPitch, PITCH_STEP);
        float forward = range > 2.6 ? 1 : range > 1.7 ? .6f : .15f;
        if (wTap) forward = 0;
        float sideways = strafeInput(range <= rules.reach() + .45);
        // Let the previous pose show the turn before the bot can swing.
        Vec3 eye = bot.feet().add(0, PlayerDimensions.EYE_HEIGHT, 0);
        boolean swing = click
                && aimedAt(eye, bot.yaw(), bot.pitch(), opponent.feet(), rules.reach())
                && aimedAt(eye, yaw, pitch, opponent.feet(), rules.reach());
        if (swing && forward > 0 && bot.food() > 6) lastSprintClick = tick;
        return new Intent(tick, yaw, pitch, forward, sideways, false, forward > 0,
                swing, false, 0);
    }

    private Intent recoveryInput(long tick, PlayerState bot, PlayerState opponent, double targetYaw) {
        if (tick < healFollowThroughUntil) {
            return retreat(tick, healingYaw, healingYaw, healingPitch, false, bot.slot());
        }
        boolean emergency = bot.health() <= EMERGENCY_HEALTH;
        if (MAX_HEALTH - bot.health() < rules.healing() || tick < healReadyTick) {
            healSetupTick = -1;
            return null;
        }
        int slot = healingSlot(bot);
        if (slot < 0) return null;
        if (healSetupTick < 0) healSetupTick = tick;

        double course = retreatCourse(bot, targetYaw + 180);
        float yaw = turnToward(bot.yaw(), course, YAW_STEP);
        var direction = CombatGeometry.direction((float) course, 0);
        double speed = direction.x() * bot.velocity().x() + direction.z() * bot.velocity().z();
        // Put the potion ahead along the escape path. Slower movement needs a steeper throw.
        float throwPitch = speed > .11 ? -71 : -74;
        float pitch = turnToward(bot.pitch(), throwPitch, PITCH_STEP);
        // Predict where the pursuer will be when a typical grounded throw lands.
        Vec3 rivalTravel = new Vec3(opponent.velocity().x(), 0, opponent.velocity().z());
        double rivalSpeed = rivalTravel.length();
        if (rivalSpeed > .26) rivalTravel = rivalTravel.scale(.26 / rivalSpeed);
        Vec3 splashGap = bot.feet().minus(opponent.feet())
                .add(direction.scale(throwPitch == -71 ? .55 : .35)).minus(rivalTravel.scale(3));
        boolean space = opponent.health() == MAX_HEALTH
                || Math.hypot(splashGap.x(), splashGap.z()) >= rules.splashRadius() - .3;
        boolean stable = bot.grounded() || emergency && bot.velocity().y() <= 0;
        boolean ready = stable && (space && speed > .07 || emergency) && Math.abs(pitch - throwPitch) < .01f
                && Math.abs(Math.IEEEremainder(course - yaw, 360)) <= 6;
        if (ready) {
            healingYaw = yaw;
            healingPitch = pitch;
            healFollowThroughUntil = tick + HEAL_FOLLOW_THROUGH_TICKS;
            healReadyTick = tick + 10;
            healSetupTick = -1;
        } else if (tick - healSetupTick >= MAX_HEAL_SETUP_TICKS) {
            // Resume fighting if the opponent denied space for a clean potion.
            healSetupTick = -1;
            healReadyTick = tick + 5;
            return null;
        }
        return retreat(tick, course, yaw, pitch, ready, slot);
    }

    private double retreatCourse(PlayerState bot, double awayYaw) {
        for (int turn : RETREAT_TURNS) {
            double course = awayYaw + turn;
            Vec3 step = CombatGeometry.direction((float) course, 0).scale(1.25);
            if (CombatGeometry.clipMovement(arena, bot.id(), bot.feet(), step).minus(bot.feet()).length() > 1.1) {
                return course;
            }
        }
        return awayYaw;
    }

    private static Intent retreat(long tick, double course, float yaw, float pitch, boolean throwPotion, int slot) {
        double relativeYaw = Math.toRadians(course - yaw);
        return new Intent(tick, yaw, pitch, (float) Math.cos(relativeYaw) * .85f,
                (float) Math.sin(relativeYaw) * .85f, false, false, false, throwPotion, slot);
    }

    private boolean clickDue() {
        // Vary the rate each second. Keep sub-tick progress instead of rounding the click interval.
        if (cadenceTicks == 0) {
            clickRate = random.nextInt(8, 11);
            cadenceTicks = TICKS_PER_SECOND;
        }
        cadenceTicks--;
        clickProgress += clickRate;
        if (clickProgress < TICKS_PER_SECOND) return false;
        clickProgress -= TICKS_PER_SECOND;
        return true;
    }

    private float strafeInput(boolean inRange) {
        if (!inRange) {
            strafeTicks = 0;
            return 0;
        }
        if (strafeTicks == 0) {
            float strength = random.nextFloat(.45f, .86f);
            strafe = strafe == 0 ? (random.nextBoolean() ? strength : -strength)
                    : Math.copySign(strength, -strafe);
            strafeTicks = random.nextInt(7, 16);
        }
        strafeTicks--;
        return strafe;
    }

    private static int healingSlot(PlayerState bot) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (bot.inventory().get(i).item() == Item.HEALING) return i;
        }
        return -1;
    }

    private static float turnToward(float from, double to, float maxStep) {
        double delta = Math.IEEEremainder(to - from, 360);
        return from + (float) Math.max(-maxStep, Math.min(maxStep, delta));
    }

    static boolean aimedAt(Vec3 eye, float yaw, float pitch, Vec3 target, double reach) {
        Vec3 ray = CombatGeometry.direction(yaw, pitch).scale(reach);
        // An eye inside the expanded hitbox can register a ray in any direction.
        double forward = (target.x() - eye.x()) * ray.x() + (target.z() - eye.z()) * ray.z();
        return forward > 0 && Double.isFinite(CombatGeometry.firstMeleeBody(eye, ray, target));
    }
}
