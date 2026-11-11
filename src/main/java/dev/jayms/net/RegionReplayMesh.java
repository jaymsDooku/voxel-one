package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Worker-only ordinary geometry mesh preparation. Custom model references stay
 * in the frozen root; their draw/dependency staging remains separate. Unknown
 * neighbours are conservatively air here. Live neighbour/source/mesh fences
 * are still required before installation. Never creates GL resources. */
final class RegionReplayMesh {
    private RegionReplayMesh() {}
    static RegionReplayChunk.Snapshot prepare(RegionReplayChunk.Snapshot source,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(source);Objects.requireNonNull(cancel);
        if(source.geometry().size()!=256)throw new IllegalArgumentException("Replay chunk mesh resolution");
        StructurePackage.cancelled(cancel);
        // Constant-root persistent copy: never freeze a caller's mutable wrapper.
        var geometry=source.geometry().copy().freeze();
        var input=new Input(geometry,cancel);MeshData mesh;
        try{mesh=MeshDataGenerator.generate(input);}
        catch(java.util.concurrent.CancellationException cancelled){throw cancelled;}
        catch(IllegalStateException failure){throw new IOException("Replay chunk mesh failed",failure);}
        StructurePackage.cancelled(cancel);
        // MeshData follows existing renderer ownership: CPU arrays are not
        // immutable. No World geometry or readiness is published by this value.
        return new RegionReplayChunk.Snapshot(source.region(),geometry,source.terrainRevision(),
                source.editedCells(),source.stagedCellLeaves(),mesh,source.modelDraws());
    }
    private static final class Input extends Chunk {
        private final SparseVoxelOctree root;private final BooleanSupplier cancel;
        Input(SparseVoxelOctree root,BooleanSupplier cancel){this.root=root;this.cancel=cancel;}
        private void check(){StructurePackage.cancelled(cancel);}
        private static boolean cellInside(int x,int y,int z){return x>=0&&x<16&&y>=0&&y<16&&z>=0&&z<16;}
        @Override public int getBlock(int x,int y,int z){check();return cellInside(x,y,z)?WorldVoxels.decode(root.uniform(x*16,y*16,z*16,16)):0;}
        @Override public int neighbor(int x,int y,int z){return getBlock(x,y,z);}
        @Override public int value(int x,int y,int z){check();return x>=0&&x<256&&y>=0&&y<256&&z>=0&&z<256?root.get(x,y,z):0;}
        @Override public int material(int x,int y,int z){return WorldVoxels.decode(value(x,y,z));}
        @Override public SparseVoxelOctree cell(int x,int y,int z){check();return root.region(x*16,y*16,z*16,16);}
        @Override public boolean isEmpty(){check();return root.isEmpty();}
    }
}
