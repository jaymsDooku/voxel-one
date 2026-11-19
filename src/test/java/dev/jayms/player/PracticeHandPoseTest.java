package dev.jayms.player;

import dev.jayms.net.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PracticeHandPoseTest {
    private static final Vector3f GRIP = new Vector3f(
            HeldToolPose.ANCHOR_X, HeldToolPose.ANCHOR_Y, HeldToolPose.ANCHOR_Z);
    private static final Vector3f TIP = new Vector3f(.5f, 1, .5f);
    private static final float[] ASPECTS = {4f / 3, 16f / 9, 21f / 9};

    @Test void idleSwordAndPalmFitTheLowerRightCorner() {
        var pose = sample(1, 0, false);
        for (float aspect : ASPECTS) {
            Vector3f grip = screen(pose.item(), GRIP, aspect);
            Vector3f tip = screen(pose.item(), TIP, aspect);
            assertTrue(grip.x > .405f && grip.x < .83f);
            assertTrue(grip.y > -.94f && grip.y < -.84f);
            assertTrue(tip.x > 0 && tip.x < grip.x);
            assertTrue(tip.y > .37f && tip.y < .49f);
            double tilt = Math.toDegrees(Math.atan2((grip.x - tip.x) * aspect, tip.y - grip.y));
            assertEquals(21, tilt, 2, "Blade should follow the reference angle from vertical");
            assertTrue(screen(pose.hand(), new Vector3f(0, PracticeHandPose.SLEEVE_BOTTOM, 0), aspect).y < -1);
        }
    }

    @Test void idleSwordShowsTheBroadFaceOnTheLeft() {
        var sword = sample(1, 0, false).item();
        var broadFace = new Vector3f(.5f, .72f, 17f / 32);
        var edge = new Vector3f(19f / 32, .72f, .5f);
        float leftFaceExposure = sword.transformDirection(new Vector3f(0, 0, 1)).normalize()
                .dot(sword.transformPosition(new Vector3f(broadFace)).negate().normalize());
        assertTrue(leftFaceExposure > .06f && leftFaceExposure < .18f,
                "Only a sliver of the left blade face should be visible");
        assertTrue(sword.transformDirection(new Vector3f(1, 0, 0))
                .dot(sword.transformPosition(new Vector3f(edge)).negate()) > 0);
        for (float aspect : ASPECTS) {
            assertTrue(screen(sword, broadFace, aspect).x < screen(sword, edge, aspect).x,
                    "The visible broad blade face belongs to the left of its edge");
        }
    }

    @Test void theBladeMeshClearsTheScreenEdgeAtFourByThree() {
        var voxels = Items.get(Items.STONE_SWORD).model().voxels();
        int size = voxels.size();
        for (int frame = 0; frame < 12; frame++) {
            var idle = PracticeHandPose.sample(frame * (float) Math.PI / 6, 1, 1, 0, false, Items.STONE_SWORD);
            var transform = new Matrix4f().perspective((float) Math.toRadians(70), 4f / 3, .03f, 10).mul(idle.item());
            for (int x = 0; x < size; x++) {
                for (int y = 3 * size / 8; y < size; y++) {
                    for (int z = 0; z < size; z++) {
                        if (voxels.get(x, y, z) == 0) continue;
                        for (int corner = 0; corner < 8; corner++) {
                            var p = transform.transformProject(new Vector3f(
                                    (x + (corner & 1)) / (float) size,
                                    (y + (corner >> 1 & 1)) / (float) size,
                                    (z + (corner >> 2 & 1)) / (float) size));
                            assertTrue(p.x > .10f && p.x < .98f && p.y > -1 && p.y < .58f,
                                    "The drawn blade and guard should clear the view edges: " + p);
                        }
                    }
                }
            }
        }
    }

    @Test void skinAndWeaponKeepTheirGripDuringSwipesGuardsAndEquips() {
        for (int item : new int[]{Items.WOODEN_SWORD, Items.STONE_SWORD}) {
            for (boolean block : new boolean[]{false, true}) {
                for (int frame = 0; frame <= 48; frame++) {
                    float t = frame / 48f;
                    var pose = PracticeHandPose.sample(t * 6, 1, t, t, block, item);
                    var handGrip = new Matrix4f(pose.hand()).invert().transformPosition(pose.item()
                            .transformPosition(new Vector3f(GRIP)));
                    assertEquals(0, handGrip.length(), 1e-5, "Handle stays inside the palm");
                    assertTrue(pose.item().determinant() > 0, "Sword must not reflect");
                    assertWithinDepth(pose.item(), 4f / 16, 1f / 16, 6f / 16, 12f / 16, 1, 10f / 16);
                    assertWithinDepth(pose.hand(), -PracticeHandPose.PALM_WIDTH / 2, PracticeHandPose.SLEEVE_BOTTOM,
                            -PracticeHandPose.PALM_DEPTH / 2, PracticeHandPose.PALM_WIDTH / 2, PracticeHandPose.PALM_TOP,
                            PracticeHandPose.PALM_DEPTH / 2);
                }
            }
        }
    }

    @Test void swipeReachesInwardEarlyThenSettlesToIdle() {
        var idle = sample(1, 0, false);
        assertTrue(idle.item().equals(sample(0, 0, false).item(), 1e-7f));
        assertTrue(idle.hand().equals(sample(0, 0, false).hand(), 1e-7f));
        float inward = screen(sample(.25f, 0, false).item(), TIP, 16f / 9).x;
        float recovery = screen(sample(.75f, 0, false).item(), TIP, 16f / 9).x;
        assertTrue(inward < recovery - .12f);
        assertTrue(recovery < screen(idle.item(), TIP, 16f / 9).x);
    }

    @Test void guardCoversInwardWithoutSwipingAndEquipLowersBoth() {
        var idle = sample(1, 0, false);
        var guard = sample(1, 0, true);
        assertTrue(guard.item().equals(sample(.25f, 0, true).item(), 1e-7f));
        for (float aspect : ASPECTS) {
            Vector3f idleGrip = screen(idle.item(), GRIP, aspect);
            Vector3f guardGrip = screen(guard.item(), GRIP, aspect);
            Vector3f guardTip = screen(guard.item(), TIP, aspect);
            assertTrue(guardGrip.x < idleGrip.x && guardGrip.y > idleGrip.y);
            assertTrue(guardTip.x < 0 && guardTip.y > guardGrip.y);
            assertTrue(screen(sample(1, 1, false).item(), TIP, aspect).y < -1);
            assertTrue(screen(sample(1, 1, false).hand(), new Vector3f(), aspect).y < -1);
        }
        assertTrue(faceTowardCamera(guard) > .65f);
    }

    private static PracticeHandPose.Pose sample(float swing, float drop, boolean guard) {
        return PracticeHandPose.sample(0, 0, swing, drop, guard, Items.STONE_SWORD);
    }

    private static Vector3f screen(Matrix4f transform, Vector3f vertex, float aspect) {
        return new Matrix4f().perspective((float) Math.toRadians(70), aspect, .03f, 10)
                .mul(transform).transformProject(new Vector3f(vertex));
    }

    private static float faceTowardCamera(PracticeHandPose.Pose pose) {
        return pose.item().transformDirection(new Vector3f(0, 0, 1)).normalize().z;
    }

    private static void assertWithinDepth(Matrix4f transform, float minX, float minY, float minZ,
                                          float maxX, float maxY, float maxZ) {
        for (float x : new float[]{minX, maxX}) {
            for (float y : new float[]{minY, maxY}) {
                for (float z : new float[]{minZ, maxZ}) {
                    float depth = transform.transformPosition(new Vector3f(x, y, z)).z;
                    assertTrue(depth < -.10f && depth > -2.1f, "Camera-safe depth: " + depth);
                }
            }
        }
    }
}
