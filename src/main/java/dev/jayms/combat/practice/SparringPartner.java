package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.PlayerState;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatGeometry;
import dev.jayms.player.PlayerDimensions;

import java.util.random.RandomGenerator;

import static dev.jayms.combat.CombatEngine.HOTBAR_SIZE;
import static dev.jayms.combat.CombatEngine.TICKS_PER_SECOND;

/** Drives the offline opponent through the same movement and attack inputs as a player. */
final class SparringPartner {
    private static final float YAW_STEP = 30, PITCH_STEP = 22.5f;

    private final double reach;
    private final RandomGenerator random;
    private int lastHits;
    private long healReadyTick;
    private int clickRate, cadenceTicks, clickProgress;
    private int strafeTicks;
    private float strafe;

    SparringPartner(double reach, RandomGenerator random) {
        this.reach = reach;
        this.random = random;
    }

    Intent input(long tick, PlayerState bot, PlayerState opponent) {
        Vec3 offset = opponent.feet().minus(bot.feet());
        double range = Math.hypot(offset.x(), offset.z());
        int heal = healingSlot(bot);
        boolean recovering = heal >= 0 && tick >= healReadyTick;
        double targetYaw = range == 0 ? bot.yaw() : Math.toDegrees(Math.atan2(offset.z(), offset.x()));
        double targetPitch = Math.toDegrees(Math.atan2(offset.y() - .25, range));
        float yaw = turnToward(bot.yaw(), targetYaw, YAW_STEP);
        float pitch = turnToward(bot.pitch(), recovering ? -80 : targetPitch, PITCH_STEP);
        boolean throwPotion = recovering && pitch <= -80;
        if (throwPotion) healReadyTick = tick + 9;

        // Only a confirmed hit releases W. The normal forward input resumes on the next tick.
        int hits = bot.stats().hits();
        boolean wTap = hits > lastHits;
        lastHits = hits;
        float forward = range > 2.6 ? 1 : range > 1.7 ? .6f : .15f;
        if (recovering) forward = -.65f;
        else if (wTap) forward = 0;
        float sideways = strafeInput(!recovering && range <= reach + .45);
        // Let the previous pose show the turn before the bot can swing.
        Vec3 eye = bot.feet().add(0, PlayerDimensions.EYE_HEIGHT, 0);
        boolean swing = clickDue() && !recovering
                && aimedAt(eye, bot.yaw(), bot.pitch(), opponent.feet(), reach)
                && aimedAt(eye, yaw, pitch, opponent.feet(), reach);
        return new Intent(tick, yaw, pitch, forward, sideways, false, forward > 0,
                swing, throwPotion, recovering ? heal : 0);
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
        if (bot.health() >= 11) return -1;
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
