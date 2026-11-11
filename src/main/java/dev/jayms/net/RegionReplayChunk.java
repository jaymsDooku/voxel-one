package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Private worker-only CPU geometry assembly. Not mesh readiness, live world
 * installation, global replay order or save authority. Source/model generation
 * and owner publication fences remain the caller's responsibility. */
final class RegionReplayChunk {
    static final int MAX_CELL_LEAVES=131_072;
    record Snapshot(ChunkPos region,SparseVoxelOctree geometry,long terrainRevision,
                    int editedCells,int stagedCellLeaves,dev.jayms.MeshData mesh,RegionReplayModelDraws.Draws modelDraws) {
        Snapshot(ChunkPos region,SparseVoxelOctree geometry,long terrainRevision,int editedCells,int stagedCellLeaves,dev.jayms.MeshData mesh) {
            this(region,geometry,terrainRevision,editedCells,stagedCellLeaves,mesh,null);
        }
        Snapshot(ChunkPos region,SparseVoxelOctree geometry,long terrainRevision,int editedCells,int stagedCellLeaves) {
            this(region,geometry,terrainRevision,editedCells,stagedCellLeaves,null);
        }
    }
    private RegionReplayChunk() {}

    /** At most one 4097-row cell page plus one detached 256-cubed root is
     * retained during assembly. Admission sums non-air cell leaves before
     * installing each cell; it is conservative across octree merging. Cached
     * disk data keeps its independent cache budgets. No render arrays or GL
     * objects are created. These are logical bounds, not measured heap/time. */
    static Snapshot load(RegionReplayCache cache,ChunkPos region,Terrain terrain,
            int maxCellLeaves,BooleanSupplier cancel)throws IOException {
        return load(cache,region,terrain,maxCellLeaves,cancel,n->{});
    }
    /** Worker-local progress counts completely staged cells, including implicit
     * terrain. Even 4096 is not a successful result until final checks pass. */
    static Snapshot load(RegionReplayCache cache,ChunkPos region,Terrain terrain,
            int maxCellLeaves,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Objects.requireNonNull(cache);Objects.requireNonNull(region);
        Objects.requireNonNull(terrain);Objects.requireNonNull(cancel);Objects.requireNonNull(progress);
        if(maxCellLeaves<0||maxCellLeaves>MAX_CELL_LEAVES)
            throw new IllegalArgumentException("Replay chunk geometry budget");
        checkRegion(region);
        long ox=16L*region.chunkX(),oy=16L*region.chunkY(),oz=16L*region.chunkZ();
        long revision=terrain.geometryRevision();
        if((revision&1)!=0)throw new IOException("Replay terrain changed");
        StructurePackage.cancelled(cancel);progress.accept(0);
        var geometry=new SparseVoxelOctree(256);int edited=0,leaves=0;
        for(int key=0;key<4096;key++) {
            StructurePackage.cancelled(cancel);checkTerrain(terrain,revision);
            int x=key&15,z=(key>>>4)&15,y=key>>>8;
            var page=cache.load(region,new RegionReplayPage.CellRange(key,key+1),terrain,
                    RegionReplayPage.MAX_ROWS,cancel);
            var position=new WorldVoxels.CellPosition((int)ox+x,(int)oy+y,(int)oz+z);
            var image=page.cells().get(position);SparseVoxelOctree cell;
            if(image!=null){cell=image.tree();edited++;}
            else {
                cell=new SparseVoxelOctree(16);
                cell.fill(0,0,0,16,16,16,WorldVoxels.encode(terrain.block(position.x(),position.y(),position.z())));
            }
            int count=cell.leafCount();
            if(count>maxCellLeaves-leaves)throw new IOException("Replay chunk geometry budget");
            leaves+=count;StructurePackage.cancelled(cancel);checkTerrain(terrain,revision);
            geometry.replaceRegion(x*16,y*16,z*16,cell);
            progress.accept(key+1);
        }
        StructurePackage.cancelled(cancel);checkTerrain(terrain,revision);
        return new Snapshot(region,geometry.freeze(),revision,edited,leaves);
    }
    static void checkRegion(ChunkPos region)throws IOException {
        long ox=16L*region.chunkX(),oy=16L*region.chunkY(),oz=16L*region.chunkZ();
        if(ox<Integer.MIN_VALUE||oy<Integer.MIN_VALUE||oz<Integer.MIN_VALUE
                ||ox+15>Integer.MAX_VALUE||oy+15>Integer.MAX_VALUE||oz+15>Integer.MAX_VALUE
                ||!new Protocol.Edit((int)ox,(int)oy,(int)oz,0).valid()
                ||!new Protocol.Edit((int)ox+15,(int)oy+15,(int)oz+15,0).valid())
            throw new IOException("Replay chunk outside world");
    }
    private static void checkTerrain(Terrain terrain,long revision)throws IOException {
        if(terrain.geometryRevision()!=revision)throw new IOException("Replay terrain changed");
    }
}
