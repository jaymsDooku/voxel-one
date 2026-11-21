package dev.jayms.player;

import dev.jayms.combat.CombatGeometry;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AvatarAimTest {
    @Test void avatarFacePointsAlongTheCombatRayAtPositiveAndNegativePitch() {
        float yaw = 37;
        var bodyFacing = new Matrix4f().translation(2, 74, -3)
                .rotateY((float) Math.toRadians(-yaw - 90)).scale(PlayerSkin.PLAYER_SCALE);
        for (float pitch : new float[] {-50, 0, 31}) {
            var forward = PlayerModel.headPose(bodyFacing, pitch)
                    .transformDirection(new Vector3f(0, 0, -1)).normalize();
            var ray = CombatGeometry.direction(yaw, pitch);
            assertEquals(ray.x(), forward.x, 1e-6);
            assertEquals(ray.y(), forward.y, 1e-6);
            assertEquals(ray.z(), forward.z, 1e-6);
        }
    }
}
