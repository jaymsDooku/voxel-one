package dev.jayms;

import java.util.ArrayList;
import java.util.List;

public class Chunk {

    public static final int WIDTH = 16;
    public static final int LENGTH = 16;
    public static final int HEIGHT = 16;

    private int[] blocks = new int[WIDTH * LENGTH * HEIGHT];

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
    }

    public int index(int x, int y, int z) {
        return x + WIDTH * (z + LENGTH * y);
    }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && x < WIDTH &&
                y >= 0 && y < HEIGHT &&
                z >= 0 && z < LENGTH;
    }

    public MeshData generate() {
        return MeshDataGenerator.generate(this);
    }

}
