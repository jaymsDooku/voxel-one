package dev.jayms.player;

import dev.jayms.net.Items;
import org.joml.Matrix4f;

/** Rendering-only grips for Items' original 16-voxel tools; icons and drops use their own poses. */
public final class HeldToolPose {
    // Normalized model coordinates: handle occupies x/z [7,9]/16, y [1,12]/16.
    // Grip at voxel (8,4,8), below the sword guard at y=6. Shaft is +Y;
    // the broad head lies in XY, with the axe's cutting edge towards -X and faces +/-Z.
    public static final float ANCHOR_X = 8f / 16, ANCHOR_Y = 4f / 16, ANCHOR_Z = 8f / 16;

    public static Matrix4f world(int type, Matrix4f arm) {
        if (!Items.tool(type)) throw new IllegalArgumentException("Not a tool");
        // Skin's lower palm is centered at (1,-9,-2) rig pixels. Wrist flexion
        // compensates the resting arm, but stays local during gait and downward strikes.
        return grip(type, new Matrix4f(arm).translate(PlayerAnimation.PIXEL, -9 * PlayerAnimation.PIXEL, -2 * PlayerAnimation.PIXEL)
                .rotateZ(-.25f).rotateX(-.65f), 1.1f);
    }

    public static Matrix4f firstPerson(int type, Matrix4f hand) {
        if (!Items.tool(type)) throw new IllegalArgumentException("Not a tool");
        // Skin ends at .75; anchor sits inside the upper fist, not beyond its tip.
        return grip(type, new Matrix4f(hand).translate(-.04f, .70f, -.04f), .86f);
    }

    /** Tool-only camera framing; apply to the fist and tool together. */
    public static Matrix4f firstPersonHand(Matrix4f hand) {
        return new Matrix4f(hand).translateLocal(-.40f, -.17f, -.55f);
    }

    private static Matrix4f grip(int type, Matrix4f wrist, float scale) {
        // Wrist -> shaft tilt and head-facing yaw -> display scale -> negative anchor.
        // Positive scale and rotations preserve handedness, including front-facing F5.
        // Both materials share category metadata. The broad face remains visible;
        // axe cutting edges and sword guards keep the same handed orientation.
        int category = (type - Items.WOODEN_PICKAXE) % 4;
        float tilt = switch (category) { case 1 -> -.24f; case 2 -> -.18f; default -> -.30f; };
        float facing = switch (category) { case 1 -> -.55f; case 3 -> -.70f; default -> -.85f; };
        return wrist.rotateX(.25f).rotateZ(tilt).rotateY(facing).scale(scale)
                .translate(-ANCHOR_X, -ANCHOR_Y, -ANCHOR_Z);
    }

    private HeldToolPose() {}
}
