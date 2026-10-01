package dev.jayms;

public class Chunk implements AutoCloseable {

    public static final int WIDTH = 16;
    public static final int LENGTH = 16;
    public static final int HEIGHT = 16;

    private int[] blocks = new int[WIDTH * LENGTH * HEIGHT];
    private boolean dirty = true;
    private Mesh mesh;
    private World world;
    private ChunkPos position;

    public void attach(World world, ChunkPos position) {
        this.world = world;
        this.position = position;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean dirty() {
        return dirty;
    }

    public int neighbor(int x, int y, int z) {
        if (inside(x, y, z) || world == null) return getBlock(x, y, z);
        int wx = position.chunkX() * 16 + x,
                wy = position.chunkY() * 16 + y,
                wz = position.chunkZ() * 16 + z;
        return world.isLoaded(wx, wy, wz) ? world.getBlock(wx, wy, wz) : 0;
    }

    public int getBlock(int x, int y, int z) {
        if (!inside(x, y, z)) {
            return 0;
        }

        return blocks[index(x, y, z)];
    }

    public void setBlock(int x, int y, int z, int color) {
        if (!inside(x, y, z)) {
            throw new IndexOutOfBoundsException("Block outside chunk: " + x + ", " + y + ", " + z);
        }

        blocks[index(x, y, z)] = color;
        dirty = true;
    }

    public int index(int x, int y, int z) {
        return x + WIDTH * (z + LENGTH * y);
    }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT && z >= 0 && z < LENGTH;
    }

    public Mesh getMesh() {
        return mesh;
    }

    public void checkMesh() {
        if (dirty) {
            generateMesh();
            dirty = false;
        }
    }

    public void generateMesh() {
        if (mesh != null) mesh.close();
        mesh = new Mesh(MeshDataGenerator.generate(this));
    }

    @Override
    public void close() throws Exception {
        if (mesh != null) mesh.close();
    }
}
