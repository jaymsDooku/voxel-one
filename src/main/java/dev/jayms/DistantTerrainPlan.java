package dev.jayms;

import java.util.*;

/** Globally aligned quadtree tiles; coarse parents remain available while children build. */
public final class DistantTerrainPlan {
    public static final int RADIUS = 2048, ROOT_SIZE = 1024;

    public record Tile(int x, int z, int size) {
        public int step() {
            return size / 16;
        }

        public boolean touches(int bx, int bz) {
            return bx >= x - 1 && bx <= x + size && bz >= z - 1 && bz <= z + size;
        }
    }

    public record Node(Tile tile, List<Node> children) {
        public boolean available(Set<Tile> ready) {
            return ready.contains(tile)
                    || !children.isEmpty() && children.stream().allMatch(n -> n.available(ready));
        }

        public void select(Set<Tile> ready, List<Tile> out) {
            if (!children.isEmpty() && children.stream().allMatch(n -> n.available(ready)))
                children.forEach(n -> n.select(ready, out));
            else if (ready.contains(tile)) out.add(tile);
        }
    }

    private final List<Node> roots = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private final WorldBounds bounds, coverageBounds;

    public DistantTerrainPlan(int px, int pz) {
        int minX = Math.floorDiv(px - RADIUS, ROOT_SIZE) * ROOT_SIZE;
        int minZ = Math.floorDiv(pz - RADIUS, ROOT_SIZE) * ROOT_SIZE;
        int maxX = (Math.floorDiv(px + RADIUS - 1, ROOT_SIZE) + 1) * ROOT_SIZE;
        int maxZ = (Math.floorDiv(pz + RADIUS - 1, ROOT_SIZE) + 1) * ROOT_SIZE;
        bounds = new WorldBounds(px - RADIUS, pz - RADIUS, px + RADIUS, pz + RADIUS);
        coverageBounds = new WorldBounds(minX, minZ, maxX, maxZ);
        for (int x = minX; x < maxX; x += ROOT_SIZE)
            for (int z = minZ; z < maxZ; z += ROOT_SIZE) roots.add(build(x, z, ROOT_SIZE, px, pz));
        // Build inexpensive coverage first, refining the nearest regions next.
        tiles.sort(
                Comparator.<Tile>comparingInt(Tile::size)
                        .reversed()
                        .thenComparingDouble(t -> distance(t, px, pz)));
    }

    private Node build(int x, int z, int size, int px, int pz) {
        Tile tile = new Tile(x, z, size);
        tiles.add(tile);
        int distance = distance(tile, px, pz);
        int desired = distance < 128 ? 16 : distance < 512 ? 64 : distance < 1536 ? 256 : 1024;
        List<Node> children = new ArrayList<>();
        if (size > desired) {
            int half = size / 2;
            for (int a = 0; a < 2; a++)
                for (int b = 0; b < 2; b++)
                    children.add(build(x + a * half, z + b * half, half, px, pz));
        }
        return new Node(tile, List.copyOf(children));
    }

    private static int distance(Tile t, int px, int pz) {
        return Math.max(
                Math.max(t.x - px, px - t.x - t.size), Math.max(t.z - pz, pz - t.z - t.size));
    }

    public WorldBounds coverageBounds() {
        return coverageBounds;
    }

    public WorldBounds bounds() {
        return bounds;
    }

    public List<Tile> tiles() {
        return Collections.unmodifiableList(tiles);
    }

    public List<Tile> select(Set<Tile> ready) {
        List<Tile> result = new ArrayList<>();
        roots.forEach(n -> n.select(ready, result));
        return result;
    }
}
