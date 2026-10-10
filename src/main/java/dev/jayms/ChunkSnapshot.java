package dev.jayms;

import dev.jayms.net.WorldVoxels;
import dev.jayms.net.model.SparseVoxelOctree;
import java.util.HashMap;
import java.util.Map;

/** Persistent octree roots and immutable border cells. No live world access on a mesh worker. */
public final class ChunkSnapshot extends Chunk {
    private record Cell(int x,int y,int z) {}
    private final SparseVoxelOctree root;
    private final Map<Cell,SparseVoxelOctree> borders = new HashMap<>();
    private final Map<ChunkPos,SparseVoxelOctree> neighbors = new HashMap<>();
    private final boolean empty,sealed;
    public final long revision;

    public ChunkSnapshot(World world,ChunkPos pos,Chunk chunk){this(world,pos,chunk,false);}
    public ChunkSnapshot(World world,ChunkPos pos,Chunk chunk,boolean sealed) {
        this.sealed=sealed;root=chunk.snapshot();empty=chunk.isEmpty();revision=chunk.meshRevision();
        for(Face face:Face.values()) {
            var p=new ChunkPos(pos.chunkX()+face.dx(),pos.chunkY()+face.dy(),pos.chunkZ()+face.dz());
            Chunk adjacent=world.getLoadedChunks().get(p);
            if(adjacent!=null)neighbors.put(new ChunkPos(face.dx(),face.dy(),face.dz()),adjacent.snapshot());
            // Full-cell traversal only needs loaded neighbors. Fractional surfaces also use
            // authoritative fallback border cells, captured on the owning world thread.
            for(int a=0;a<16;a++)for(int b=0;b<16;b++) {
                int x=face.dx()==0?a:face.dx()<0?-1:16;
                int y=face.dy()==0?(face.dx()==0?b:a):face.dy()<0?-1:16;
                int z=face.dz()==0?b:face.dz()<0?-1:16;
                if(face.dz()!=0){x=a;y=b;}
                if(face.dy()!=0){x=a;z=b;}
                borders.put(new Cell(x,y,z),world.cell(pos.chunkX()*16+x,pos.chunkY()*16+y,pos.chunkZ()*16+z).copy().freeze());
            }
        }
    }
    @Override public boolean isEmpty(){return empty;}
    @Override public int getBlock(int x,int y,int z) {
        return x<0||y<0||z<0||x>=16||y>=16||z>=16?0:WorldVoxels.decode(root.uniform(x*16,y*16,z*16,16));
    }
    @Override public int neighbor(int x,int y,int z) {
        if(x>=0&&y>=0&&z>=0&&x<16&&y<16&&z<16)return getBlock(x,y,z);
        var tree=neighbors.get(new ChunkPos(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16)));
        int neighbor=tree==null?0:WorldVoxels.decode(tree.uniform(Math.floorMod(x,16)*16,Math.floorMod(y,16)*16,Math.floorMod(z,16)*16,16));
        int own=getBlock(clamp(x,15),clamp(y,15),clamp(z,15));
        return sealed&&opaque(own)&&opaque(neighbor)?0:neighbor;
    }
    @Override public int value(int x,int y,int z) {
        if(x>=0&&y>=0&&z>=0&&x<256&&y<256&&z<256)return root.get(x,y,z);
        var cell=borders.get(new Cell(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16)));
        int raw=cell==null?0:cell.get(Math.floorMod(x,16),Math.floorMod(y,16),Math.floorMod(z,16));
        int own=root.get(clamp(x,255),clamp(y,255),clamp(z,255));
        return sealed&&opaque(WorldVoxels.decode(own))&&opaque(WorldVoxels.decode(raw))?0:raw;
    }
    private static int clamp(int value,int max){return Math.max(0,Math.min(max,value));}
    private static boolean opaque(int type){return type!=0&&type!=dev.jayms.net.Blocks.PARTIAL&&type!=dev.jayms.net.Blocks.WATER&&type!=dev.jayms.net.Blocks.GLASS&&!dev.jayms.net.Blocks.isModel(type);}
    @Override public SparseVoxelOctree cell(int x,int y,int z){return root.region(x*16,y*16,z*16,16);}
    @Override public SparseVoxelOctree snapshot(){return root;}
}
