package dev.jayms.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Orthographic depth from the four exterior sides of a building preview. */
public final class PreviewSideDepth {
    private PreviewSideDepth() {}

    /** Side order matches the shader: +X, -X, +Z, -Z. */
    public static Matrix4f projection(int side, Vector3f min, Vector3f max) {
        Vector3f center = new Vector3f(min).add(max).mul(.5f);
        float halfWidth = (side < 2 ? max.z - min.z : max.x - min.x) * .5f;
        float halfHeight = (max.y - min.y) * .5f;
        float depth = side < 2 ? max.x - min.x : max.z - min.z;
        float offset = (depth * .5f + 1) * (side % 2 == 0 ? 1 : -1);
        Vector3f eye = new Vector3f(center).add(side < 2 ? offset : 0, 0, side < 2 ? 0 : offset);
        return new Matrix4f().ortho(-halfWidth, halfWidth, -halfHeight, halfHeight, 1, depth + 1)
                .lookAt(eye, center, new Vector3f(0, 1, 0));
    }

    /** Looking up one block for every two along the wall normal clears a distant wing.
     * Shearing just the image's vertical coordinate preserves the linear depth comparison. */
    public static Matrix4f skyProjection(int side, Vector3f min, Vector3f max) {
        float height = max.y-min.y;
        float rise = .5f * (side<2 ? max.x-min.x : max.z-min.z);
        return new Matrix4f().m11(height/(height+rise)).m21(rise/(height+rise))
                .mul(projection(side,min,max));
    }
}
