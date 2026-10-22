package dev.jayms.net.structure;

import dev.jayms.net.*;
import dev.jayms.Chunk;
import dev.jayms.ChunkPos;
import dev.jayms.MeshDataGenerator;
import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Shared offline/server validation and staging. Only Authority.commit may publish. */
public final class StructureEngine {
    public interface Authority extends Source {
        Object lock();
        /** Owner-thread atomic replacement, including durable world state; rollback on IOException. */
        void commit(ModelLibrary staged,Map<Position,SparseVoxelOctree> cells) throws IOException;
    }
    private final int undoCellBudget;
    public StructureEngine(){this(262144);}
    /** History target; retain the newest admitted placement even when it exceeds the target. */
    public StructureEngine(int undoCellBudget){
        if(undoCellBudget<1||undoCellBudget>MAX_CELLS)throw new IllegalArgumentException("Undo history budget");
        this.undoCellBudget=undoCellBudget;
    }
    private final AtomicBoolean busy=new AtomicBoolean();
    private final Map<String,Deque<Record>> undo=new HashMap<>();
    private final Deque<Record> chronology=new ArrayDeque<>();
    private long sequence;
    private int undoCells;
    private record Record(long sequence,String owner,Map<Position,SparseVoxelOctree> before,Map<Position,SparseVoxelOctree> after) {}
    public boolean busy(){return busy.get();}
    public record Footprint(int minX,int minY,int minZ,int width,int height,int depth,int rotation,Position anchor) {}
    public static Footprint footprint(StructurePackage asset,Position anchor,int rotation) {
        checkRotation(rotation);var b=asset.bounds;
        var a=rotate(new Position(b.ax(),b.ay(),b.az()),b.x(),b.z(),rotation);
        return new Footprint(anchor.x()-a.x(),anchor.y()-a.y(),anchor.z()-a.z(),
                rotation%2==0?b.x():b.z(),b.y(),rotation%2==0?b.z():b.x(),rotation,anchor);
    }
    private static void checkRotation(int r){if(r<0||r>3)throw new IllegalArgumentException("Quarter-turn rotation 0..3 required");}
    public static Position rotate(Position p,int width,int depth,int r) {
        checkRotation(r);
        return switch(r) {
            case 0 -> p;
            case 1 -> new Position(depth-1-p.z(),p.y(),p.x());
            case 2 -> new Position(width-1-p.x(),p.y(),depth-1-p.z());
            default -> new Position(p.z(),p.y(),width-1-p.x());
        };
    }
    public static SparseVoxelOctree rotate(SparseVoxelOctree source,int rotation) {
        checkRotation(rotation);int size=source.size();
        // Persistent nodes let immutable snapshots share safely. No rotation is needed
        // for a whole-cell uniform tree, regardless of the requested quarter turn.
        if(rotation==0||source.uniform(0,0,0,size)!=-1)return source.copy().freeze();
        var result=new SparseVoxelOctree(size);
        for(var leaf:source.leaves()) {
            int x=leaf.x(),z=leaf.z(),s=leaf.side();
            int rx=switch(rotation){case 1 -> size-z-s;case 2 -> size-x-s;case 3 -> z;default -> x;};
            int rz=switch(rotation){case 1 -> x;case 2 -> size-z-s;case 3 -> size-x-s;default -> z;};
            result.fill(rx,leaf.y(),rz,rx+s,leaf.y()+s,rz+s,leaf.color());
        }
        return result.freeze();
    }
    public final class Prepared implements AutoCloseable {
        private final Map<Position,SparseVoxelOctree> before,after;
        private final ModelLibrary staged;
        private final long modelRevision;
        private final BooleanSupplier cancel;
        private boolean closed;
        private Prepared(Map<Position,SparseVoxelOctree> after,ModelLibrary staged,long revision,BooleanSupplier cancel,SparseVoxelOctree empty) {
            // prepare supplies an immutable compact map; copying would recreate every key/entry.
            this.after=after;this.before=emptyBeforeImage(this.after,empty);this.staged=staged;modelRevision=revision;this.cancel=cancel;
        }
        public Map<Position,SparseVoxelOctree> cells(){return after;}
        public void commit(String owner,Authority authority) throws IOException {
            try {
                synchronized(authority.lock()) {
                    if(closed)throw new IOException("Structure staging closed");
                    cancelled(cancel);
                    if(authority.models().revision()!=modelRevision)throw new IOException("Model library changed; retry placement");
                    for(var p:after.keySet())if(authority.cell(p.x(),p.y(),p.z()).occupied()!=0)
                        throw new IOException("Destination changed or occupied; nothing placed");
                    validateChunks(after,authority,staged,cancel);
                    cancelled(cancel);authority.commit(staged,after);
                    var records=undo.computeIfAbsent(owner,k->new ArrayDeque<>());
                    var record=new Record(++sequence,owner,before,after);records.addLast(record);chronology.addLast(record);undoCells+=after.size();
                    while(records.size()>8) {var expired=records.removeFirst();chronology.remove(expired);undoCells-=expired.after.size();}
                    while(undoCells>undoCellBudget&&chronology.size()>1) {var expired=chronology.removeFirst();undo.get(expired.owner).remove(expired);undoCells-=expired.after.size();}
                }
            } finally {close();}
        }
        @Override public void close(){if(!closed){closed=true;busy.set(false);}}
    }
    /** Admission rejects occupied destinations, so undo only needs the same keys and frozen air. */
    private static Map<Position,SparseVoxelOctree> emptyBeforeImage(Map<Position,SparseVoxelOctree> after,SparseVoxelOctree empty) {
        return Collections.unmodifiableMap(new AbstractMap<>() {
            @Override public int size(){return after.size();}
            @Override public boolean containsKey(Object key){return after.containsKey(key);}
            @Override public SparseVoxelOctree get(Object key){return after.containsKey(key)?empty:null;}
            @Override public Set<Entry<Position,SparseVoxelOctree>> entrySet() {
                return new AbstractSet<>() {
                    @Override public int size(){return after.size();}
                    @Override public Iterator<Entry<Position,SparseVoxelOctree>> iterator() {
                        var keys=after.keySet().iterator();
                        return new Iterator<>() {
                            @Override public boolean hasNext(){return keys.hasNext();}
                            @Override public Entry<Position,SparseVoxelOctree> next(){return new SimpleImmutableEntry<>(keys.next(),empty);}
                        };
                    }
                };
            }
        });
    }
    public Prepared prepare(StructurePackage asset,Position anchor,int rotation,Source snapshot,
                            BooleanSupplier cancel,IntConsumer progress)throws IOException {
        if(!busy.compareAndSet(false,true))throw new IOException("Another large placement is running");
        boolean complete=false;
        try {
            var box=footprint(asset,anchor,rotation);long revision=snapshot.models().revision();var staged=snapshot.models().copy();
            var modelIds=new int[asset.models.size()];
            for(int i=0;i<modelIds.length;i++) {
                cancelled(cancel);var def=asset.models.get(i);
                modelIds[i]=staged.stageStructureModel(new ModelDefinition(def.name(),rotate(def.voxels(),rotation))).id();
            }
            var cells=new StructurePlacementCells.Builder(box,asset.cells.size());int count=0;
            var empty=new SparseVoxelOctree(16).freeze();
            var uniformGeometry=new HashMap<Integer,SparseVoxelOctree>();
            var instances=new HashMap<Integer,SparseVoxelOctree>();
            for(var cell:asset.cells) {
                cancelled(cancel);var relative=rotate(cell.position(),asset.bounds.x(),asset.bounds.z(),rotation);
                long x=(long)box.minX+relative.x(),y=(long)box.minY+relative.y(),z=(long)box.minZ+relative.z();
                if(x<Integer.MIN_VALUE||x>Integer.MAX_VALUE||y<Integer.MIN_VALUE||y>Integer.MAX_VALUE||z<Integer.MIN_VALUE||z>Integer.MAX_VALUE
                        ||!new Protocol.Edit((int)x,(int)y,(int)z,0).valid())throw new IOException("Destination outside world");
                var p=new Position((int)x,(int)y,(int)z);var old=snapshot.cell(p.x(),p.y(),p.z());
                if(old.occupied()!=0)throw new IOException("Occupied destination cell; empty source space is preserved");
                SparseVoxelOctree next;
                if(cell.model()>=0) {
                    int id=modelIds[cell.model()];next=instances.get(id);
                    if(next==null){next=new SparseVoxelOctree(16);next.fill(0,0,0,16,16,16,WorldVoxels.encode(id));next.freeze();instances.put(id,next);}
                } else {
                    int uniform=cell.geometry().uniform(0,0,0,16);
                    if(uniform==-1)next=rotate(cell.geometry(),rotation);
                    else {next=uniformGeometry.get(uniform);if(next==null){next=rotate(cell.geometry(),rotation);uniformGeometry.put(uniform,next);}}
                }
                cells.add(p,next);if((++count&127)==0)progress.accept(count*100/asset.cells.size());
            }
            var after=cells.build();
            validateChunks(after,snapshot,staged,cancel);
            cancelled(cancel);progress.accept(100);var result=new Prepared(after,staged,revision,cancel,empty);complete=true;return result;
        } finally {if(!complete)busy.set(false);}
    }
    /** Detached CPU meshes conservatively count exposed chunk edges. Never touches GPU state. */
    private static void validateChunks(Map<Position,SparseVoxelOctree> after,Source source,ModelLibrary models,BooleanSupplier cancel)throws IOException {
        var chunks=new HashSet<ChunkPos>();
        for(var p:after.keySet()) {
            chunks.add(ChunkPos.fromBlock(p.x(),p.y(),p.z()));
            if(chunks.size()>256)throw new IOException("Experimental placement exceeds 256 affected chunks");
        }
        var modelQuads=new HashMap<Integer,Integer>();
        for(var position:chunks) {
            cancelled(cancel);var chunk=new Chunk();
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                if((x&7)==0)cancelled(cancel);
                var p=new Position(position.chunkX()*16+x,position.chunkY()*16+y,position.chunkZ()*16+z);
                var tree=after.get(p);
                if(tree==null) {
                    if(source.emptyCell(p.x(),p.y(),p.z()))continue;
                    tree=source.cell(p.x(),p.y(),p.z());
                }
                if(tree.occupied()!=0)chunk.replaceCell(x,y,z,tree);
            }
            long instanceQuads=0;
            for(int id:chunk.models().values()) {
                cancelled(cancel);Integer quads=modelQuads.get(id);
                if(quads==null){var definition=models.get(id);if(definition==null)throw new IOException("Missing world dependency");quads=ModelMesher.mesh(definition.definition().voxels()).quads();modelQuads.put(id,quads);}
                instanceQuads+=quads;if(instanceQuads>262144)throw new IOException("Experimental chunk model-instance surface cap");
            }
            try {MeshDataGenerator.generate(chunk);}
            catch(IllegalStateException failure){throw new IOException("Structure would exceed chunk geometry budget",failure);}
        }
    }
    public void undo(String owner,Authority authority)throws IOException {
        if(!busy.compareAndSet(false,true))throw new IOException("Another large placement is running");
        try {
            synchronized(authority.lock()) {
                var records=undo.get(owner);if(records==null||records.isEmpty())throw new IOException("No structure placement to undo");
                var record=records.peekLast();
                for(var entry:record.after.entrySet()) {
                    var p=entry.getKey();
                    if(!authority.cell(p.x(),p.y(),p.z()).leaves().equals(entry.getValue().leaves()))
                        throw new IOException("Placed cells changed; undo will not overwrite later edits");
                }
                authority.commit(authority.models().copy(),record.before);records.removeLast();chronology.remove(record);undoCells-=record.after.size();
            }
        } finally {busy.set(false);}
    }
}
