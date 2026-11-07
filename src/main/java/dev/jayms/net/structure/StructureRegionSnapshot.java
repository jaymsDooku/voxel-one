package dev.jayms.net.structure;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.IOException;
import java.util.*;
import java.util.function.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Worker-built region source. Only bounded owner calls read live world state. */
public final class StructureRegionSnapshot {
    private StructureRegionSnapshot(){}
    public static Source capture(World world,Position origin,int width,int height,int depth,StructureOwnerQueue owner,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        new Bounds(width,height,depth,0,0,0);cancelled(cancel);
        long endX=(long)origin.x()+width-1,endY=(long)origin.y()+height-1,endZ=(long)origin.z()+depth-1;
        if(endX>Integer.MAX_VALUE||endY>Integer.MAX_VALUE||endZ>Integer.MAX_VALUE||!new Protocol.Edit(origin.x(),origin.y(),origin.z(),0).valid()||!new Protocol.Edit((int)endX,(int)endY,(int)endZ,0).valid())throw new IOException("Structure snapshot outside world");
        var plan=owner.call(world::beginStructureSnapshot,cancel);var sources=new HashMap<ChunkPos,Source>();
        int minX=Math.floorDiv(origin.x(),16),minY=Math.floorDiv(origin.y(),16),minZ=Math.floorDiv(origin.z(),16);
        int maxX=Math.floorDiv((int)endX,16),maxY=Math.floorDiv((int)endY,16),maxZ=Math.floorDiv((int)endZ,16);
        int total=(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1),done=0;ModelLibrary definitions=null;
        var batch=new ArrayList<ChunkPos>(128);progress.accept(0);cancelled(cancel);
        for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
            cancelled(cancel);batch.add(new ChunkPos(x,y,z));done++;
            if(batch.size()==128||done==total) {
                var positions=List.copyOf(batch);var part=owner.call(()->plan.batch(positions),cancel);
                for(var position:positions)sources.put(position,part);definitions=part.models();batch.clear();
                progress.accept((int)(100L*sources.size()/total));cancelled(cancel);
            }
        }
        owner.call(()->{plan.validate();return null;},cancel);cancelled(cancel);
        var models=Objects.requireNonNull(definitions);var terrain=plan.terrain();long generation=plan.terrainRevision();
        return new Source() {
            private void check(){cancelled(cancel);if(terrain.geometryRevision()!=generation)throw new ConcurrentModificationException("Terrain changed during structure snapshot");}
            private Source source(ChunkPos p){check();var source=sources.get(p);if(source==null)throw new IllegalArgumentException("Read outside structure snapshot");return source;}
            public SparseVoxelOctree cell(int x,int y,int z){var result=source(ChunkPos.fromBlock(x,y,z)).cell(x,y,z);check();return result;}
            public boolean emptyCell(int x,int y,int z){boolean result=source(ChunkPos.fromBlock(x,y,z)).emptyCell(x,y,z);check();return result;}
            public boolean emptyChunk(ChunkPos p){boolean result=source(p).emptyChunk(p);check();return result;}
            public Object chunkStamp(ChunkPos p){check();var source=sources.get(p);return source==null?null:source.chunkStamp(p);}
            public ModelLibrary models(){check();return models;}
        };
    }
}
