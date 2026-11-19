package dev.jayms.player;

import dev.jayms.net.Items;
import org.joml.Matrix4f;

/** Camera-space viewmodel for the right hand in the practice and faction arenas. */
public final class PracticeHandPose {
    // The wrist frame is centered on the handle. The sleeve starts just below the palm,
    // then leaves the frame through the bottom edge.
    static final float PALM_WIDTH = .22f, PALM_DEPTH = .19f;
    static final float PALM_BOTTOM = -.13f, PALM_TOP = .115f, SLEEVE_BOTTOM = -.47f;

    public record Pose(Matrix4f hand, Matrix4f item) { }

    public static Pose sample(float walkPhase, float walkAmount, float swing, float equipDrop, boolean blocking,
                              int item) {
        boolean sword = item == Items.STONE_SWORD || item == Items.WOODEN_SWORD;
        boolean guard = blocking && sword;
        float t = clamp(swing);
        // Two envelopes put the strongest inward motion in the first half of the swipe,
        // then bring the wrist back to the same idle transform at the end.
        float swipe = guard ? 0 : pulse((float) Math.sqrt(t));
        float follow = guard ? 0 : pulse(t);
        float sway = (float) Math.sin(walkPhase) * clamp(walkAmount);
        float bob = (float) Math.cos(walkPhase) * clamp(walkAmount);
        boolean raisedSword = sword && !guard;
        float equipTravel = raisedSword ? 1.32f : 1.04f;
        float wristX = guard ? .43f : raisedSword ? .715f : .735f;
        var hand = new Matrix4f()
                .translate(wristX - .37f * swipe + .07f * follow + .014f * sway,
                        (guard ? -.53f : -.655f) + .14f * swipe - .115f * follow + .017f * bob
                                - equipTravel * clamp(equipDrop),
                        (guard ? -1.02f : -1.08f) - .11f * swipe)
                .rotateZ((guard ? .48f : .20f) + .40f * swipe - .21f * follow)
                .rotateX(-.25f + .20f * swipe - .29f * follow)
                .rotateY((guard ? .15f : -.13f) - .39f * swipe);

        var held = new Matrix4f(hand);
        if (sword) {
            if (guard) {
                held.rotateZ(.67f).rotateY(.23f).scale(.89f);
            } else {
                // A shallow left-facing twist gives the blade a little visible depth.
                held.rotateZ(.075f).rotateX(.05f).rotateY(-1.675f).scale(.98f, 1.49f, .83f);
            }
            held.translate(-HeldToolPose.ANCHOR_X, -HeldToolPose.ANCHOR_Y, -HeldToolPose.ANCHOR_Z);
        } else {
            held.translate(-.075f, .052f, -.045f)
                    .rotateY(.43f)
                    .scale(.27f)
                    .translate(-.5f, 0, -.5f);
        }
        return new Pose(hand, held);
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    private static float pulse(float progress) {
        return progress > 0 && progress < 1 ? (float) Math.sin(Math.PI * progress) : 0;
    }

    private PracticeHandPose() { }
}
