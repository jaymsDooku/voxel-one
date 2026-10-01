package dev.jayms.net.model;

/** Models can be generated directly in Java using the same API as the player editor. */
public final class ModelGenerators {
    public static final int CLAY = 0xffbc5939,
            RIM = 0xffe28352,
            SOIL = 0xff593b27,
            STEM = 0xff358449,
            LEAF = 0xff50b552,
            PETAL = 0xffed5791,
            CENTER = 0xffffce54;

    public static ModelDefinition flowerPot() {
        SparseVoxelOctree tree = new SparseVoxelOctree(32);
        for (int y = 0; y < 13; y++)
            for (int x = 0; x < 32; x++)
                for (int z = 0; z < 32; z++) {
                    double r = Math.hypot(x - 15.5, z - 15.5), outer = 6 + y * .25;
                    if (y >= 10) outer = 10;
                    if (r <= outer && (y < 2 || r >= outer - 1.6))
                        tree.set(x, y, z, y >= 10 ? RIM : CLAY);
                    if (y == 9 && r < 8) tree.set(x, y, z, SOIL);
                }
        tree.fill(15, 10, 15, 17, 25, 17, STEM);
        for (int offset = 0; offset < 5; offset++) {
            tree.fill(17 + offset, 16 + offset / 2, 14, 18 + offset, 17 + offset / 2, 18, LEAF);
            tree.fill(14 - offset, 19 + offset / 2, 14, 15 - offset, 20 + offset / 2, 18, LEAF);
        }
        for (int x = 10; x <= 21; x++)
            for (int z = 10; z <= 21; z++) {
                double r = Math.hypot(x - 15.5, z - 15.5);
                if (r < 5.8) {
                    int y = 25 + (r < 2 ? 1 : 0);
                    tree.set(x, y, z, r < 2 ? CENTER : PETAL);
                    if (r >= 2 && r < 5) tree.set(x, y + 1, z, PETAL);
                }
            }
        return new ModelDefinition("Flower pot", tree);
    }

    private ModelGenerators() {}
}
