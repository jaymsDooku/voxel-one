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
    private int fractionalCells;
    public boolean hasFractional(){return fractionalCells!=0;}
    private long geometryVersion, meshRevision;
    // Only deterministic generation plus authoritative replay may retain this flag.
    boolean terrainEquivalent;long terrainSeed;int terrainVersion;Object terrainGrid;ChunkPos terrainPosition;
    boolean matchesTerrain(dev.jayms.net.Terrain terrain,ChunkPos position){return position.equals(terrainPosition)&&terrainSeed==terrain.seed&&terrainVersion==terrain.version&&terrainGrid==terrain.stressGrid();}
    private long notifiedGeometryVersion=Long.MIN_VALUE;
    boolean takeGeometryChange(){if(notifiedGeometryVersion==geometryVersion)return false;notifiedGeometryVersion=geometryVersion;return true;}
    public long meshRevision(){return meshRevision;}
    /** Geometry changes only; mesh/neighbor invalidation does not rebuild sky coverage. */
    public long geometryVersion(){return geometryVersion;}

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    private Mesh mesh;
    private float waterHeight = Float.NaN;
    public float waterHeight() { return waterHeight; }
    private World world;
    private ChunkPos position;

    public void attach(World world, ChunkPos position) {
        this.world = world;
        this.position = position;
        notifiedGeometryVersion=Long.MIN_VALUE;
    }

    public ChunkPos position() {
        return position;
    }

    public void markDirty() {
        meshRevision++;
        dirty = true;if(world!=null)world.meshDirty(position,this);
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

        int value = dev.jayms.net.WorldVoxels.encode(color);
        int current = blocks.uniform(x * 16, y * 16, z * 16, 16);
        // -1 denotes mixed geometry, never a uniform cell (even for an encoded -1).
        if (current != -1 && current == value) return;

        if(current==-1)fractionalCells--;
        int i = index(x, y, z);
        terrainEquivalent=false;geometryVersion++;
        blocks.fill(
                x * 16,
                y * 16,
                z * 16,
                x * 16 + 16,
                y * 16 + 16,
                z * 16 + 16,
                value);
        if (dev.jayms.net.Blocks.isModel(color)) models.put(i, color);
        else models.remove(i);
        markDirty();
    }

    public dev.jayms.net.model.SparseVoxelOctree cell(int x, int y, int z) {
        return blocks.region(x * 16, y * 16, z * 16, 16);
    }

    /** Install one complete validated world cell without replaying individual leaves. */
    public void replaceCell(int x,int y,int z,dev.jayms.net.model.SparseVoxelOctree source) {
        if(!inside(x,y,z))throw new IndexOutOfBoundsException("Block outside chunk: "+x+", "+y+", "+z);
        if(source.size()!=16)throw new IllegalArgumentException("World cell resolution must be 16");
        boolean wasFractional=getBlock(x,y,z)==dev.jayms.net.Blocks.PARTIAL;
        blocks.replaceRegion(x*16,y*16,z*16,source);
        int value=blocks.uniform(x*16,y*16,z*16,16);
        boolean nowFractional=value==-1;
        if(wasFractional!=nowFractional)fractionalCells+=nowFractional?1:-1;
        int type=dev.jayms.net.WorldVoxels.decode(value),key=index(x,y,z);
        if(dev.jayms.net.Blocks.isModel(type))models.put(key,type);else models.remove(key);
        terrainEquivalent=false;geometryVersion++;markDirty();
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
        boolean wasFractional=getBlock(x,y,z)==dev.jayms.net.Blocks.PARTIAL;
        int side = 16 >> edit.depth();
        int fx = x * 16 + edit.ix() * side,
                fy = y * 16 + edit.iy() * side,
                fz = z * 16 + edit.iz() * side;
        terrainEquivalent=false;geometryVersion++;
        blocks.fill(
                fx,
                fy,
                fz,
                fx + side,
                fy + side,
                fz + side,
                dev.jayms.net.WorldVoxels.encode(edit));
        boolean nowFractional=getBlock(x,y,z)==dev.jayms.net.Blocks.PARTIAL;
        if(wasFractional!=nowFractional)fractionalCells+=nowFractional?1:-1;
        if (edit.depth() == 0) models.remove(index(x, y, z));
        markDirty();
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
        MeshData data=isEmpty()?null:MeshDataGenerator.generate(this);
        Mesh replacement=data==null?null:new Mesh(data);
        installMesh(replacement,data,meshRevision);
    }

    public boolean installMesh(Mesh replacement,MeshData data,long revision) {
        if(meshRevision!=revision){if(replacement!=null)replacement.close();return false;}
        Mesh old=mesh;mesh=replacement;waterHeight=Float.NaN;
        if(data!=null&&data.surface()!=null)for(int i=0;i<data.vertices().length;i+=9)
            if(data.surface()[i/3+2]==-2&&data.vertices()[i+4]>.5f)
                waterHeight=Float.isNaN(waterHeight)?data.vertices()[i+1]:Math.max(waterHeight,data.vertices()[i+1]);
        dirty=false;if(world!=null)world.meshClean(position,this);
        if(old!=null)old.close();
        return true;
    }

    @Override
    public void close() throws Exception {
        if (mesh != null) mesh.close();
    }
}
