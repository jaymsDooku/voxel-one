package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.Objects;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Worker-only ordinary geometry mesh preparation. Custom model references stay
 * in the frozen root; their draw/dependency staging remains separate. Unknown
 * neighbours are conservatively air here. Live neighbour/source/mesh fences
 * are still required before installation. Never creates GL resources. */
final class RegionReplayMesh {
    private RegionReplayMesh() {}
    static RegionReplayChunk.Snapshot prepare(RegionReplayChunk.Snapshot source,BooleanSupplier cancel)throws IOException {
        return prepare(source,List.of(),cancel);
    }
    /** Detached face-neighbour capture only. It supplies mesh visibility, not
     * live neighbour freshness or permission to publish into a World. */
    static RegionReplayChunk.Snapshot prepare(RegionReplayChunk.Snapshot source,
            List<RegionReplayChunk.Snapshot> neighbours,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(source);Objects.requireNonNull(cancel);Objects.requireNonNull(neighbours);
        if(neighbours.size()>6)throw new IllegalArgumentException("Replay mesh neighbour count");
        var roots=new SparseVoxelOctree[6];
        for(var neighbour:neighbours) {
            StructurePackage.cancelled(cancel);Objects.requireNonNull(neighbour);
            long dx=(long)neighbour.region().chunkX()-source.region().chunkX();
            long dy=(long)neighbour.region().chunkY()-source.region().chunkY();
            long dz=(long)neighbour.region().chunkZ()-source.region().chunkZ();
            if(Math.abs(dx)+Math.abs(dy)+Math.abs(dz)!=1)throw new IllegalArgumentException("Replay mesh nonadjacent neighbour");
            int slot=dx!=0?(dx<0?0:1):dy!=0?(dy<0?2:3):(dz<0?4:5);
            if(roots[slot]!=null)throw new IllegalArgumentException("Replay mesh duplicate neighbour");
            if(neighbour.geometry().size()!=256)throw new IllegalArgumentException("Replay neighbour mesh resolution");
            if(neighbour.terrainRevision()!=source.terrainRevision())throw new IOException("Replay mesh terrain revision mismatch");
            roots[slot]=neighbour.geometry().copy().freeze();
        }
        if(source.geometry().size()!=256)throw new IllegalArgumentException("Replay chunk mesh resolution");
        StructurePackage.cancelled(cancel);
        // Constant-root persistent copy: never freeze a caller's mutable wrapper.
        var geometry=source.geometry().copy().freeze();
        var input=new Input(geometry,roots,cancel);MeshData mesh;
        try{mesh=MeshDataGenerator.generate(input);}
        catch(java.util.concurrent.CancellationException cancelled){throw cancelled;}
        catch(IllegalStateException failure){throw new IOException("Replay chunk mesh failed",failure);}
        StructurePackage.cancelled(cancel);
        // MeshData follows existing renderer ownership: CPU arrays are not
        // immutable. No World geometry or readiness is published by this value.
        return new RegionReplayChunk.Snapshot(source.region(),geometry,source.terrainRevision(),
                source.editedCells(),source.stagedCellLeaves(),mesh,source.modelDraws(),null,waterHeight(mesh,cancel));
    }
    /** Same surface convention as Chunk.installMesh, computed on the worker. */
    static float waterHeight(MeshData mesh,BooleanSupplier cancel) {
        float height=Float.NaN;
        if(mesh.surface()!=null)for(int i=0;i<mesh.vertices().length;i+=9) {
            StructurePackage.cancelled(cancel);
            if(mesh.surface()[i/3+2]==-2&&mesh.vertices()[i+4]>.5f)
                height=Float.isNaN(height)?mesh.vertices()[i+1]:Math.max(height,mesh.vertices()[i+1]);
        }
        StructurePackage.cancelled(cancel);return height;
    }
    private static final class Input extends Chunk {
        private final SparseVoxelOctree root;private final SparseVoxelOctree[] neighbours;private final BooleanSupplier cancel;
        Input(SparseVoxelOctree root,SparseVoxelOctree[] neighbours,BooleanSupplier cancel){this.root=root;this.neighbours=neighbours;this.cancel=cancel;}
        private void check(){StructurePackage.cancelled(cancel);}
        private static boolean cellInside(int x,int y,int z){return x>=0&&x<16&&y>=0&&y<16&&z>=0&&z<16;}
        @Override public int getBlock(int x,int y,int z){check();return cellInside(x,y,z)?WorldVoxels.decode(root.uniform(x*16,y*16,z*16,16)):0;}
        @Override public int neighbor(int x,int y,int z){
            check();if(cellInside(x,y,z))return getBlock(x,y,z);
            int outside=(x<0||x>=16?1:0)+(y<0||y>=16?1:0)+(z<0||z>=16?1:0);
            if(outside!=1||x< -1||x>16||y< -1||y>16||z< -1||z>16)return 0;
            int slot=x<0?0:x>=16?1:y<0?2:y>=16?3:z<0?4:5;
            var neighbour=neighbours[slot];
            return neighbour==null?0:WorldVoxels.decode(neighbour.uniform(Math.floorMod(x,16)*16,Math.floorMod(y,16)*16,Math.floorMod(z,16)*16,16));
        }
        @Override public int value(int x,int y,int z){check();return x>=0&&x<256&&y>=0&&y<256&&z>=0&&z<256?root.get(x,y,z):0;}
        @Override public int material(int x,int y,int z){return WorldVoxels.decode(value(x,y,z));}
        @Override public SparseVoxelOctree cell(int x,int y,int z){check();return root.region(x*16,y*16,z*16,16);}
        @Override public boolean isEmpty(){check();return root.isEmpty();}
    }
}
