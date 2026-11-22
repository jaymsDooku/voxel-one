package dev.jayms.player;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PracticeBowPoseTest {
    @Test void fullDrawRaisesTheBowAndMovesTheNockBackBetweenBothLimbTips() {
        assertTrue(PracticeBowPose.stringZ(20) > PracticeBowPose.stringZ(12));
        assertTrue(PracticeBowPose.stringZ(12) > PracticeBowPose.stringZ(0));
        assertTrue(PracticeBowPose.tipY(20) < PracticeBowPose.tipY(0));
        assertTrue(PracticeBowPose.tipZ(20) > PracticeBowPose.tipZ(0));
        assertTrue(PracticeBowPose.stringZ(20) - PracticeBowPose.ARROW_LENGTH < 0,
                "A fully drawn arrow should still reach past the grip");
        assertEquals(PracticeBowPose.stringZ(20), PracticeBowPose.stringZ(40));

        var idle = PracticeBowPose.firstPerson(0, 0, 0, 0);
        var full = PracticeBowPose.firstPerson(0, 0, 0, 20);
        assertTrue(full.m31() > idle.m31());
        assertTrue(full.m30() < idle.m30());
        for (float aspect : new float[]{4f / 3, 16f / 9, 21f / 9}) {
            for (float tip : new float[]{-PracticeBowPose.tipY(20), PracticeBowPose.tipY(20)}) {
                var p = new Matrix4f().perspective((float) Math.toRadians(70), aspect, .03f, 10)
                        .mul(full).transformProject(new Vector3f(0, tip, PracticeBowPose.tipZ(20)));
                assertTrue(Math.abs(p.x) < .95 && Math.abs(p.y) < .95, "Drawn limbs have room at aspect " + aspect);
            }
        }
    }

    @Test void aimedAvatarAndArrowUseTheirForwardDirection() {
        float pitch = 24;
        var aim = PracticeBowPose.aimingAvatar(pitch).transformDirection(new Vector3f(0, 0, -1)).normalize();
        assertEquals(Math.sin(Math.toRadians(pitch)), aim.y, 1e-6);
        assertTrue(aim.z < 0);

        for (Vector3f velocity : new Vector3f[]{new Vector3f(2, -.4f, 1), new Vector3f(0, 0, 2), new Vector3f(0, -3, 0)}) {
            var trace = PracticeBowPose.arrow(5, 6, 7, velocity.x, velocity.y, velocity.z);
            assertEquals(new Vector3f(5, 6, 7), trace.transformPosition(new Vector3f()));
            var direction = trace.transformDirection(new Vector3f(0, 0, -1)).normalize();
            assertEquals(1, direction.dot(velocity.normalize()), 1e-6);
        }
    }
}
