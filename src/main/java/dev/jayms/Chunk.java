package dev.jayms;

public class Chunk implements AutoCloseable {

    public static final int WIDTH = 16;
    public static final int LENGTH = 16;
    public static final int HEIGHT = 16;

    private int[] blocks = new int[WIDTH * LENGTH * HEIGHT];
    private boolean dirty = true;
    private Mesh mesh;

    public int getBlock(int x, int y, int z) {
        if (!inside(x, y, z)) {
            return 0;
        }

        return blocks[index(x, y, z)];
    }

    public void setBlock(int x, int y, int z, int color) {
        if (!inside(x, y, z)) {
            throw new IndexOutOfBoundsException(
                    "Block outside chunk: " + x + ", " + y + ", " + z
            );
        }

        blocks[index(x, y, z)] = color;
        dirty = true;
    }

    public int index(int x, int y, int z) {
        return x + WIDTH * (z + LENGTH * y);
    }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && x < WIDTH &&
                y >= 0 && y < HEIGHT &&
                z >= 0 && z < LENGTH;
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
        mesh = new Mesh(MeshDataGenerator.generate(this));
    }

    @Override
    public void close() throws Exception {
        mesh.close();
    }
}
