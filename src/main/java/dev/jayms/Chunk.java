package dev.jayms;

public class Chunk implements AutoCloseable {

    public static final int WIDTH = 16;
    public static final int LENGTH = 16;
    public static final int HEIGHT = 16;

    private final java.util.Map<Integer, Integer> models = new java.util.HashMap<>();

    public java.util.Map<Integer, Integer> models() {
        return java.util.Collections.unmodifiableMap(models);
    }

    private final dev.jayms.net.model.SparseVoxelOctree blocks =
            new dev.jayms.net.model.SparseVoxelOctree(256);
    private boolean dirty = true;
    private long geometryVersion;
    /** Geometry changes only; mesh/neighbor invalidation does not rebuild sky coverage. */
    public long geometryVersion(){return geometryVersion;}

    public boolean isEmpty() {
        return blocks.nodes() == 0;
    }

    private Mesh mesh;
    private float waterHeight = Float.NaN;
    public float waterHeight() { return waterHeight; }
    private World world;
    private ChunkPos position;

    public void attach(World world, ChunkPos position) {
        this.world = world;
        this.position = position;
    }

    public ChunkPos position() {
        return position;
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

        return dev.jayms.net.WorldVoxels.decode(blocks.uniform(x * 16, y * 16, z * 16, 16));
    }

    public void setBlock(int x, int y, int z, int color) {
        if (!inside(x, y, z)) {
            throw new IndexOutOfBoundsException("Block outside chunk: " + x + ", " + y + ", " + z);
        }

        int i = index(x, y, z);
        geometryVersion++;
        blocks.fill(
                x * 16,
                y * 16,
                z * 16,
                x * 16 + 16,
                y * 16 + 16,
                z * 16 + 16,
                dev.jayms.net.WorldVoxels.encode(color));
        if (dev.jayms.net.Blocks.isModel(color)) models.put(i, color);
        else models.remove(i);
        dirty = true;
    }

    public dev.jayms.net.model.SparseVoxelOctree cell(int x, int y, int z) {
        return blocks.region(x * 16, y * 16, z * 16, 16);
    }

    public dev.jayms.net.model.SparseVoxelOctree snapshot() {
        return blocks.copy().freeze();
    }

    public int value(int x, int y, int z) {
        if (x >= 0 && y >= 0 && z >= 0 && x < 256 && y < 256 && z < 256) return blocks.get(x, y, z);
        return world == null
                ? 0
                : world.value(
                        position.chunkX() * 256 + x,
                        position.chunkY() * 256 + y,
                        position.chunkZ() * 256 + z);
    }

    public int material(int x, int y, int z) {
        if (x >= 0 && y >= 0 && z >= 0 && x < 256 && y < 256 && z < 256)
            return dev.jayms.net.WorldVoxels.decode(blocks.get(x, y, z));
        if (world == null) return 0;
        return world.material(
                position.chunkX() * 256 + x,
                position.chunkY() * 256 + y,
                position.chunkZ() * 256 + z);
    }

    public void apply(dev.jayms.net.Protocol.Edit edit) {
        int x = Math.floorMod(edit.x(), 16),
                y = Math.floorMod(edit.y(), 16),
                z = Math.floorMod(edit.z(), 16);
        if (edit.depth() == 0
                && dev.jayms.net.Blocks.material(edit.type()) != dev.jayms.net.Blocks.LED) {
            setBlock(x, y, z, edit.type());
            return;
        }
        int side = 16 >> edit.depth();
        int fx = x * 16 + edit.ix() * side,
                fy = y * 16 + edit.iy() * side,
                fz = z * 16 + edit.iz() * side;
        geometryVersion++;
        blocks.fill(
                fx,
                fy,
                fz,
                fx + side,
                fy + side,
                fz + side,
                dev.jayms.net.WorldVoxels.encode(edit));
        if (edit.depth() == 0) models.remove(index(x, y, z));
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
        mesh = null; waterHeight = Float.NaN;
        if (isEmpty()) return;
        MeshData data = MeshDataGenerator.generate(this);
        waterHeight = Float.NaN;
        if (data.surface()!=null)for(int i=0;i<data.vertices().length;i+=9)
            if(data.surface()[i/3+2]==-2&&data.vertices()[i+4]>.5f)
                waterHeight=Float.isNaN(waterHeight)?data.vertices()[i+1]:Math.max(waterHeight,data.vertices()[i+1]);
        mesh = new Mesh(data);
    }

    @Override
    public void close() throws Exception {
        if (mesh != null) mesh.close();
    }
}
