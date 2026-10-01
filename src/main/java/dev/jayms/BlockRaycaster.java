package dev.jayms;

import org.joml.Vector3f;

public class BlockRaycaster {

    private BlockRaycaster() {}

    public static BlockHit cast(World world, Vector3f origin, Vector3f direction, float reach) {
        if (reach < 0 || direction.lengthSquared() == 0) {
            return null;
        }

        Vector3f ray = new Vector3f(direction).normalize();

        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        int stepX = step(ray.x);
        int stepY = step(ray.y);
        int stepZ = step(ray.z);

        float deltaX = boundarySpacing(ray.x);
        float deltaY = boundarySpacing(ray.y);
        float deltaZ = boundarySpacing(ray.z);

        float nextX = firstBoundary(origin.x, x, ray.x);
        float nextY = firstBoundary(origin.y, y, ray.y);
        float nextZ = firstBoundary(origin.z, z, ray.z);

        float distance = 0;

        int normalX = 0;
        int normalY = 0;
        int normalZ = 0;

        while (distance <= reach) {
            if (!world.isLoaded(x, y, z)) {
                return null;
            }

            try {
                int type = world.getBlock(x, y, z);
                var model = world.models().get(type);
                if (model != null) {
                    var hit =
                            model.definition()
                                    .voxels()
                                    .raycast(
                                            origin.x - x,
                                            origin.y - y,
                                            origin.z - z,
                                            ray.x,
                                            ray.y,
                                            ray.z,
                                            distance,
                                            Math.min(
                                                    reach,
                                                    Math.min(nextX, Math.min(nextY, nextZ))));
                    if (hit != null)
                        return new BlockHit(
                                x, y, z, hit.nx(), hit.ny(), hit.nz(), hit.distance(), 0);
                } else if (type != ChunkGenerator.AIR) {
                    var hit =
                            world.cell(x, y, z)
                                    .raycast(
                                            origin.x - x,
                                            origin.y - y,
                                            origin.z - z,
                                            ray.x,
                                            ray.y,
                                            ray.z,
                                            distance,
                                            Math.min(
                                                    reach,
                                                    Math.min(nextX, Math.min(nextY, nextZ))));
                    if (hit != null) {
                        int depth =
                                Math.max(
                                        0,
                                        Math.min(
                                                4,
                                                Math.round(
                                                        (float)
                                                                (-Math.log(hit.side())
                                                                        / Math.log(2)))));
                        boolean noNormal = hit.nx() == 0 && hit.ny() == 0 && hit.nz() == 0;
                        return new BlockHit(
                                x,
                                y,
                                z,
                                noNormal ? normalX : hit.nx(),
                                noNormal ? normalY : hit.ny(),
                                noNormal ? normalZ : hit.nz(),
                                hit.distance(),
                                depth);
                    }
                }
            } catch (IllegalStateException e) {
                System.err.println("Raycaster stopped: " + e.getMessage());
                return null;
            }

            if (nextX <= nextY && nextX <= nextZ) {
                distance = nextX;
                nextX += deltaX;
                x += stepX;

                normalX = -stepX;
                normalY = 0;
                normalZ = 0;
            } else if (nextY <= nextZ) {
                distance = nextY;
                nextY += deltaY;
                y += stepY;

                normalX = 0;
                normalY = -stepY;
                normalZ = 0;
            } else {
                distance = nextZ;
                nextZ += deltaZ;
                z += stepZ;

                normalX = 0;
                normalY = 0;
                normalZ = -stepZ;
            }
        }

        return null;
    }

    private static int step(float direction) {
        if (direction > 0) return 1;
        if (direction < 0) return -1;
        return 0;
    }

    private static float boundarySpacing(float direction) {
        return direction == 0 ? Float.POSITIVE_INFINITY : Math.abs(1.0f / direction);
    }

    private static float firstBoundary(float origin, int cell, float direction) {
        if (direction == 0) {
            return Float.POSITIVE_INFINITY;
        }

        float boundary = direction > 0 ? cell + 1 : cell;
        return (boundary - origin) / direction;
    }
}
