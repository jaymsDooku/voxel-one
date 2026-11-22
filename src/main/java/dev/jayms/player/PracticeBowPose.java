package dev.jayms.player;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static dev.jayms.combat.CombatEngine.BOW_FULL_DRAW_TICKS;

/** Bow geometry uses a grip at the origin and points its arrow along local -Z. */
final class PracticeBowPose {
    static final float ARROW_LENGTH = .64f;
    static final float NOCK_Y = .057f;
    static final float TIP_Y = .43f;

    private PracticeBowPose() {}

    static float fraction(float drawTicks) {
        return Math.max(0, Math.min(BOW_FULL_DRAW_TICKS, drawTicks)) / BOW_FULL_DRAW_TICKS;
    }

    static float tipY(float drawTicks) { return TIP_Y - .042f * fraction(drawTicks); }

    static float tipZ(float drawTicks) { return .119f + .055f * fraction(drawTicks); }

    static float stringZ(float drawTicks) { return tipZ(drawTicks) + .30f * fraction(drawTicks); }

    static Matrix4f firstPerson(float walkPhase, float walkAmount, float equipDrop, float drawTicks) {
        float pull = fraction(drawTicks);
        float bob = walkAmount * (1 - .82f * pull);
        return new Matrix4f().translation(
                        .24f - pull * .065f + .022f * bob * (float) Math.sin(walkPhase),
                        -.29f + pull * .19f - .78f * equipDrop + .016f * bob * (float) Math.cos(walkPhase * 2),
                        -.83f - pull * .06f)
                .rotateZ(-.25f + .11f * pull).rotateY(.69f).scale(.88f);
    }

    static Matrix4f aimingAvatar(float pitch) {
        return new Matrix4f().translation(.13f, 1.36f, 0)
                .rotateX((float) Math.toRadians(pitch)).translate(0, 0, -.52f).scale(.8f);
    }

    static Matrix4f arrow(float x, float y, float z, float vx, float vy, float vz) {
        var tip = new Matrix4f().translation(x, y, z);
        float speedSquared = vx * vx + vy * vy + vz * vz;
        if (speedSquared > 1.e-8f) {
            float inverseSpeed = 1 / (float) Math.sqrt(speedSquared);
            tip.rotate(new Quaternionf().rotationTo(0, 0, -1,
                    vx * inverseSpeed, vy * inverseSpeed, vz * inverseSpeed));
        }
        return tip;
    }
}
