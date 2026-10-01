package dev.jayms.player;

import org.joml.Matrix4f;

/** Classic 8-pixel head, 8x12x4 torso, and 4x12x4 limbs, in sixteenths of a block. */
public final class PlayerAnimation {
    public static final float PIXEL = 1f / 16,
            HEAD = 8 * PIXEL,
            BODY_WIDTH = 8 * PIXEL,
            BODY_HEIGHT = 12 * PIXEL,
            DEPTH = 4 * PIXEL,
            LIMB_WIDTH = 4 * PIXEL,
            LIMB_HEIGHT = 12 * PIXEL;

    public record Pose(
            float leftArm,
            float rightArm,
            float leftLeg,
            float rightLeg,
            float bodyTwist,
            float attack) {}

    public static Pose pose(float phase, float amount, float progress, boolean holding) {
        return pose(phase, amount, progress, holding, false);
    }

    public static Pose pose(
            float phase, float amount, float progress, boolean holding, boolean placing) {
        float gait = (float) Math.cos(phase) * amount;
        float rest = gait * (holding ? .5f : 1) + (holding ? .3f : 0);
        float strength = placing ? .25f : 1;
        float swing = attack(progress) * strength;
        float right = rest;
        if (progress < 1) {
            // Lift immediately to shoulder height, strike down, then recover to the walk pose.
            float start = placing ? .45f : (float) Math.PI / 2;
            float end = placing ? .08f : -.18f;
            right =
                    progress <= .72f
                            ? start + (end - start) * smooth(progress / .72f)
                            : end + (rest - end) * smooth((progress - .72f) / .28f);
        }
        return new Pose(-gait, right, gait * 1.4f, -gait * 1.4f, -swing * .18f, swing);
    }

    public static float attack(float progress) {
        if (progress <= 0 || progress >= 1) return 0;
        return (float) Math.sin(progress * Math.PI);
    }

    public static Matrix4f firstPersonHand(
            float phase, float amount, float progress, boolean placing) {
        float strength = placing ? .25f : 1;
        float swing = attack(progress) * strength;
        float drop =
                progress >= 1
                        ? 0
                        : progress <= .72f
                                ? smooth(progress / .72f)
                                : 1 - smooth((progress - .72f) / .28f);
        drop *= strength;
        float bob = (float) Math.sin(phase) * amount * .015f;
        return new Matrix4f()
                .translate(.52f - swing * .16f, -.90f + bob - drop * .23f, -.7f - swing * .06f)
                .rotateY(-.15f - swing * .3f)
                .rotateZ(-.12f - swing * .15f)
                .rotateX(-.35f - drop * .35f);
    }

    private static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private PlayerAnimation() {}
}
