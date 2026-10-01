package dev.jayms.ui;

import dev.jayms.net.model.SparseVoxelOctree;

import org.joml.Vector3f;

/** Surface picking and brush operations independent of the editor's GL viewport. */
public final class ModelSculptor {
    public enum Tool {
        ADD,
        PAINT,
        ERASE,
        PICK
    }

    public record Target(int x, int y, int z, int side, int color) {}

    public static Target target(
            SparseVoxelOctree tree, Vector3f origin, Vector3f direction, Tool tool, int brush) {
        if (!origin.isFinite() || !direction.isFinite() || direction.lengthSquared() == 0)
            return null;
        Vector3f ray = new Vector3f(direction).normalize();
        var hit = tree.raycast(origin.x, origin.y, origin.z, ray.x, ray.y, ray.z, 0, 100);
        Vector3f point;
        if (hit != null) {
            point = new Vector3f(origin).fma(hit.distance(), ray);
            float offset = tool == Tool.ADD ? .00001f : -.00001f;
            point.add(hit.nx() * offset, hit.ny() * offset, hit.nz() * offset);
        } else {
            if (tool != Tool.ADD || Math.abs(ray.y) < .00001) return null;
            float t = -origin.y / ray.y;
            if (t < 0) return null;
            point = new Vector3f(origin).fma(t, ray);
            point.y = .00001f;
        }
        int n = tree.size();
        int px = (int) Math.floor(point.x * n),
                py = (int) Math.floor(point.y * n),
                pz = (int) Math.floor(point.z * n);
        if (px < 0 || py < 0 || pz < 0 || px >= n || py >= n || pz >= n) return null;
        int side = Math.max(1, Math.min(8, brush));
        return new Target(
                px / side * side, py / side * side, pz / side * side, side, tree.get(px, py, pz));
    }

    public static boolean apply(SparseVoxelOctree tree, Target target, Tool tool, int color) {
        if (target == null || tool == Tool.PICK) return false;
        boolean changed = false;
        for (int x = target.x(); x < Math.min(tree.size(), target.x() + target.side()); x++)
            for (int y = target.y(); y < Math.min(tree.size(), target.y() + target.side()); y++)
                for (int z = target.z();
                        z < Math.min(tree.size(), target.z() + target.side());
                        z++) {
                    int old = tree.get(x, y, z), value = tool == Tool.ERASE ? 0 : color;
                    if (tool == Tool.ADD && old != 0
                            || tool == Tool.PAINT && old == 0
                            || old == value) continue;
                    tree.set(x, y, z, value);
                    changed = true;
                }
        return changed;
    }

    private ModelSculptor() {}
}
