package dev.jayms;

import org.joml.Vector3f;

public class BlockRaycaster {

    private BlockRaycaster() {
    }

    public static BlockHit cast(
            World world,
            Vector3f origin,
            Vector3f direction,
            float reach
    ) {
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
                if (world.getBlock(x, y, z) != ChunkGenerator.AIR) {
                    return new BlockHit(
                            x, y, z,
                            normalX, normalY, normalZ
                    );
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
        return direction == 0
                ? Float.POSITIVE_INFINITY
                : Math.abs(1.0f / direction);
    }

    private static float firstBoundary(
            float origin,
            int cell,
            float direction
    ) {
        if (direction == 0) {
            return Float.POSITIVE_INFINITY;
        }

        float boundary = direction > 0 ? cell + 1 : cell;
        return (boundary - origin) / direction;
    }

}
