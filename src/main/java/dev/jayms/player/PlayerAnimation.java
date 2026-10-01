package dev.jayms.player;

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
        float gait = (float) Math.cos(phase) * amount;
        float attack = attack(progress);
        return new Pose(
                -gait,
                gait * (holding ? .5f : 1) + (holding ? .3f : 0) + attack * 1.8f,
                gait * 1.4f,
                -gait * 1.4f,
                -attack * .18f,
                attack);
    }

    public static float attack(float progress) {
        if (progress <= 0 || progress >= 1) return 0;
        return (float) Math.sin(Math.sqrt(progress) * Math.PI);
    }

    private PlayerAnimation() {}
}
