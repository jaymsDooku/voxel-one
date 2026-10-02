package dev.jayms.player;

import dev.jayms.net.city.CityFrame;

import org.joml.Vector3f;

/** Ray/horse bounds intersection, including legs so normal ground-level aim can mount. */
public final class HorseInteraction {
    public static float distance(CityFrame.Horse horse, Vector3f eye, Vector3f aim) {
        double angle = Math.toRadians(horse.yaw());
        float rx = (float) (Math.abs(Math.cos(angle)) * 1.2 + Math.abs(Math.sin(angle)) * .45),
                rz = (float) (Math.abs(Math.sin(angle)) * 1.2 + Math.abs(Math.cos(angle)) * .45);
        float near = 0, far = Float.POSITIVE_INFINITY;
        float[] min = {horse.x() - rx, horse.y(), horse.z() - rz},
                max = {horse.x() + rx, horse.y() + 2.3f, horse.z() + rz};
        for (int axis = 0; axis < 3; axis++) {
            float d = aim.get(axis), origin = eye.get(axis);
            if (Math.abs(d) < 1e-6) {
                if (origin < min[axis] || origin > max[axis]) return Float.POSITIVE_INFINITY;
                continue;
            }
            float a = (min[axis] - origin) / d, b = (max[axis] - origin) / d;
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
            if (near > far) return Float.POSITIVE_INFINITY;
        }
        return near;
    }

    private HorseInteraction() {}
}
