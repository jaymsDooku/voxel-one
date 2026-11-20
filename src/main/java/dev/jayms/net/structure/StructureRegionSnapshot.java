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
        return guarded(plan,sources,Objects.requireNonNull(definitions),cancel);
    }
    /** Capture complete destination chunks touched by occupied asset cells.
     * Bounds still preflight as a whole; missing chunks remain unknown. */
    public static Source captureOccupied(World world,StructurePackage asset,Position anchor,int rotation,StructureOwnerQueue owner,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Objects.requireNonNull(asset);Objects.requireNonNull(anchor);Objects.requireNonNull(cancel);Objects.requireNonNull(progress);cancelled(cancel);
        var box=StructureEngine.footprint(asset,anchor,rotation);var origin=new Position(box.minX(),box.minY(),box.minZ());
        long endX=(long)box.minX()+box.width()-1,endY=(long)box.minY()+box.height()-1,endZ=(long)box.minZ()+box.depth()-1;
        if(endX>Integer.MAX_VALUE||endY>Integer.MAX_VALUE||endZ>Integer.MAX_VALUE||!new Protocol.Edit(origin.x(),origin.y(),origin.z(),0).valid()||!new Protocol.Edit((int)endX,(int)endY,(int)endZ,0).valid())throw new IOException("Structure snapshot outside world");
        var positions=new AbstractCollection<Position>() {
            public int size(){return asset.cells.size();}
            public Iterator<Position> iterator(){var input=asset.occupiedPositions().iterator();return new Iterator<>() {
                public boolean hasNext(){return input.hasNext();}
                public Position next(){var p=StructureEngine.rotate(input.next(),asset.bounds.x(),asset.bounds.z(),rotation);return new Position(origin.x()+p.x(),origin.y()+p.y(),origin.z()+p.z());}
            };}
        };
        return captureCells(world,positions,owner,cancel,progress);
    }
    /** Bounded worker grouping for placement/undo keys. This grants no partial
     * World edit or save authority. Owner batches remain at most 128 chunks. */
    public static Source captureCells(World world,Collection<Position> positions,StructureOwnerQueue owner,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Objects.requireNonNull(world);Objects.requireNonNull(positions);Objects.requireNonNull(owner);Objects.requireNonNull(cancel);Objects.requireNonNull(progress);owner.requireWorker();cancelled(cancel);
        int count=positions.size();if(count<1||count>MAX_CELLS)throw new IllegalArgumentException("Structure snapshot cell budget");
        // A maximum 1024x256x1024 footprint, unaligned on all axes, intersects
        // at most 65x17x65 chunks. General callers retain the same bounded cap.
        var chunks=new HashSet<ChunkPos>();int seen=0;progress.accept(0);cancelled(cancel);
        for(var p:positions) {
            cancelled(cancel);Objects.requireNonNull(p);if(++seen>count||!new Protocol.Edit(p.x(),p.y(),p.z(),0).valid())throw new IOException("Structure snapshot positions changed or outside world");
            chunks.add(ChunkPos.fromBlock(p.x(),p.y(),p.z()));if(chunks.size()>65*17*65)throw new IOException("Structure snapshot chunk budget");
            if((seen&1023)==0){progress.accept((int)(10L*seen/count));cancelled(cancel);}
        }
        if(seen!=count||positions.size()!=count)throw new ConcurrentModificationException("Structure snapshot positions changed");
        var sorted=new ArrayList<>(chunks);sorted.sort((a,b)->{cancelled(cancel);int d=Integer.compare(a.chunkY(),b.chunkY());if(d==0)d=Integer.compare(a.chunkZ(),b.chunkZ());return d==0?Integer.compare(a.chunkX(),b.chunkX()):d;});cancelled(cancel);
        var plan=owner.call(world::beginStructureSnapshot,cancel);var sources=new HashMap<ChunkPos,Source>();ModelLibrary definitions=null;
        for(int first=0;first<sorted.size();first+=128) {
            cancelled(cancel);var batch=List.copyOf(sorted.subList(first,Math.min(first+128,sorted.size())));var part=owner.call(()->plan.batch(batch),cancel);
            for(var position:batch)sources.put(position,part);definitions=part.models();progress.accept(10+(int)(90L*sources.size()/sorted.size()));cancelled(cancel);
        }
        owner.call(()->{plan.validate();return null;},cancel);cancelled(cancel);
        return guarded(plan,sources,Objects.requireNonNull(definitions),cancel);
    }
    private static Source guarded(World.StructureSnapshotPlan plan,Map<ChunkPos,Source> sources,ModelLibrary models,BooleanSupplier cancel) {
        var terrain=plan.terrain();long generation=plan.terrainRevision();
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
