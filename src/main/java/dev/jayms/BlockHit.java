package dev.jayms;

public record BlockHit(
        int x, int y, int z, int normalX, int normalY, int normalZ, float distance, int depth) {
    public BlockHit(int x, int y, int z, int nx, int ny, int nz) {
        this(x, y, z, nx, ny, nz, 0, 0);
    }
}
