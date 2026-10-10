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
    private int radius=RADIUS;private float errorPixels=12;
    private float projectionY; private int viewportHeight; private boolean orthographic; private float elevation;

    public DistantTerrainPlan(int px, int pz) { this(px,pz,0,0,false,0); }
    public DistantTerrainPlan(int px,int pz,float projectionY,int viewportHeight,boolean orthographic,float elevation){this(px,pz,projectionY,viewportHeight,orthographic,elevation,RADIUS,12);}
    public DistantTerrainPlan(int px,int pz,float projectionY,int viewportHeight,boolean orthographic,float elevation,int radius,float errorPixels) {
        if(radius<512||radius>RADIUS||!Float.isFinite(errorPixels)||errorPixels<6||errorPixels>24)throw new IllegalArgumentException("Invalid visual LOD settings");
        this.radius=radius;this.errorPixels=errorPixels;
        this.projectionY=projectionY;this.viewportHeight=viewportHeight;this.orthographic=orthographic;this.elevation=elevation;
        int minX = Math.floorDiv(px - radius, ROOT_SIZE) * ROOT_SIZE;
        int minZ = Math.floorDiv(pz - radius, ROOT_SIZE) * ROOT_SIZE;
        int maxX = (Math.floorDiv(px + radius - 1, ROOT_SIZE) + 1) * ROOT_SIZE;
        int maxZ = (Math.floorDiv(pz + radius - 1, ROOT_SIZE) + 1) * ROOT_SIZE;
        bounds = new WorldBounds(px - radius, pz - radius, px + radius, pz + radius);
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
        int desired = viewportHeight==0 ? (distance < 128 ? 16 : distance < 512 ? 64 : distance < 1536 ? 256 : 1024)
                : dev.jayms.render.ScreenError.tileSize((float)Math.hypot(Math.max(0,distance),elevation),projectionY,viewportHeight,orthographic,errorPixels);
        // Orthographic zoom cannot justify refining the entire offscreen horizon.
        // Keep visual parent coverage outside a conservative two-viewport neighborhood.
        if(orthographic&&viewportHeight>0&&distance>Math.max(256,4/Math.max(.00001f,Math.abs(projectionY))))
            desired=Math.max(desired,distance<512?64:distance<1536?256:1024);
        // Keep complete nearby geometry available for the detailed-chunk replacement mask.
        if(distance<96)desired=16;
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
