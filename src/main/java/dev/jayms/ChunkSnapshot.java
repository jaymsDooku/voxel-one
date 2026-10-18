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
    private final boolean empty,sealed,fractional;
    private int captured;
    private static final Face[] FACES=Face.values();
    public final long revision;

    public ChunkSnapshot(World world,ChunkPos pos,Chunk chunk){this(world,pos,chunk,false);}
    public ChunkSnapshot(World world,ChunkPos pos,Chunk chunk,boolean sealed) {
        this(world,pos,chunk,sealed,false);
    }
    private ChunkSnapshot(World world,ChunkPos pos,Chunk chunk,boolean sealed,boolean deferred) {
        this.sealed=sealed;root=chunk.snapshot();empty=chunk.isEmpty();fractional=chunk.hasFractional();revision=chunk.meshRevision();
        for(Face face:FACES) {
            Chunk adjacent=world.getLoadedChunks().get(new ChunkPos(pos.chunkX()+face.dx(),pos.chunkY()+face.dy(),pos.chunkZ()+face.dz()));
            if(adjacent!=null)neighbors.put(new ChunkPos(face.dx(),face.dy(),face.dz()),adjacent.snapshot());
        }
        if(!fractional)captured=1536;
        if(!deferred)pump(world,pos,Long.MAX_VALUE,1536);
    }
    static ChunkSnapshot begin(World world,ChunkPos pos,Chunk chunk,boolean sealed){return new ChunkSnapshot(world,pos,chunk,sealed,true);}
    boolean complete(){return captured==1536;}
    /** Only partial cells on an outer face can sample a fine border. Check each unit's deadline. */
    boolean pump(World world,ChunkPos pos,long deadline,int cells) {
        int end=Math.min(1536,captured+cells);
        while(captured<end&&System.nanoTime()<deadline) {
            int i=captured++,a=(i%256)/16,b=i%16;Face face=FACES[i/256];
            int x=face.dx()==0?a:face.dx()<0?-1:16;
            int y=face.dy()==0?(face.dx()==0?b:a):face.dy()<0?-1:16;
            int z=face.dz()==0?b:face.dz()<0?-1:16;
            if(face.dz()!=0){x=a;y=b;}if(face.dy()!=0){x=a;z=b;}
            if(getBlock(clamp(x,15),clamp(y,15),clamp(z,15))!=dev.jayms.net.Blocks.PARTIAL)continue;
            borders.put(new Cell(x,y,z),world.cell(pos.chunkX()*16+x,pos.chunkY()*16+y,pos.chunkZ()*16+z).copy().freeze());
        }
        return complete();
    }
    @Override public boolean hasFractional(){return fractional;}
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
