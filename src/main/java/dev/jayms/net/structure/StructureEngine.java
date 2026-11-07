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
        /** Side-effect-free publication staging, called before the engine acquires lock().
         * The returned commit runs under lock after authoritative revalidation. */
        default Commit stageCommit(ModelLibrary staged,Map<Position,SparseVoxelOctree> cells,long modelRevision,BooleanSupplier cancel)throws IOException {
            cancelled(cancel);return ()->commit(staged,cells);
        }
        /** Trusted engine-only before-images: same keys as cells, detached/frozen, and
         * authoritatively verified before apply under lock. Never accept wire before-images.
         * Default dispatch preserves existing server/custom staging implementations. */
        default Commit stageCommit(ModelLibrary staged,Map<Position,SparseVoxelOctree> cells,Map<Position,SparseVoxelOctree> beforeImages,long modelRevision,BooleanSupplier cancel)throws IOException {
            return stageCommit(staged,cells,modelRevision,cancel);
        }
    }
    @FunctionalInterface public interface Commit extends AutoCloseable {
        void apply() throws IOException;
        @Override default void close()throws IOException {}
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
        checkRotation(rotation);return source.rotatedY(rotation);
    }
    public final class Prepared implements AutoCloseable {
        private final Map<Position,SparseVoxelOctree> before,after;
        private final ModelLibrary staged;
        private final long modelRevision;
        private final Map<ChunkPos,Object> validatedChunks;
        private final int affectedChunkCount;
        private final BooleanSupplier cancel;
        private boolean closed;
        private Prepared(Map<Position,SparseVoxelOctree> after,ModelLibrary staged,long revision,BooleanSupplier cancel,SparseVoxelOctree empty,Map<ChunkPos,Object> validatedChunks,int affectedChunkCount) {
            // prepare supplies an immutable compact map; copying would recreate every key/entry.
            this.after=after;this.before=emptyBeforeImage(this.after,empty);this.staged=staged;modelRevision=revision;this.cancel=cancel;this.validatedChunks=validatedChunks;this.affectedChunkCount=affectedChunkCount;
        }
        public Map<Position,SparseVoxelOctree> cells(){return after;}
        /** Independent maps over immutable definitions for worker save staging. */
        public ModelLibrary modelSnapshot(){return staged.copy();}
        public void commit(String owner,Authority authority) throws IOException {
            try {
                if(closed)throw new IOException("Structure staging closed");
                cancelled(cancel);
                try(var publication=authority.stageCommit(staged,after,before,modelRevision,cancel)) {
                    synchronized(authority.lock()) {
                        if(closed)throw new IOException("Structure staging closed");
                        cancelled(cancel);
                        if(authority.models().revision()!=modelRevision)throw new IOException("Model library changed; retry placement");
                        // prepare proved every destination empty. Reuse that proof only when every
                        // affected chunk still has its trusted stamp under this same authority lock.
                        if(!destinationsUnchanged(authority)) {
                            int checked=0;
                            for(var p:after.keySet()) {
                                if((checked++&127)==0)cancelled(cancel);
                                if(authority.cell(p.x(),p.y(),p.z()).occupied()!=0)
                                    throw new IOException("Destination changed or occupied; nothing placed");
                            }
                        }
                        validateChunks(after,authority,staged,cancel,validatedChunks);
                        cancelled(cancel);publication.apply();
                        var records=undo.computeIfAbsent(owner,k->new ArrayDeque<>());
                        var record=new Record(++sequence,owner,before,after);records.addLast(record);chronology.addLast(record);undoCells+=after.size();
                        while(records.size()>8) {var expired=records.removeFirst();chronology.remove(expired);undoCells-=expired.after.size();}
                        while(undoCells>undoCellBudget&&chronology.size()>1) {var expired=chronology.removeFirst();undo.get(expired.owner).remove(expired);undoCells-=expired.after.size();}
                    }
                }
            } finally {close();}
        }
        private boolean destinationsUnchanged(Authority authority) {
            if(validatedChunks.size()!=affectedChunkCount)return false;
            for(var entry:validatedChunks.entrySet()) {
                cancelled(cancel);
                var current=authority.chunkStamp(entry.getKey());
                if(current==null||!entry.getValue().equals(current))return false;
            }
            return true;
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
            var affectedChunks=new HashSet<ChunkPos>();
            var empty=new SparseVoxelOctree(16).freeze();
            var uniformGeometry=new StructureUniformCells(asset.cells.size()-asset.instanceCount,cancel);
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
                    if(uniform==-1)next=rotate(cell.geometry(),rotation).packedCellCopy();
                    else {next=uniformGeometry.get(uniform);if(next==null){next=rotate(cell.geometry(),rotation);uniformGeometry.put(uniform,next);}}
                }
                affectedChunks.add(ChunkPos.fromBlock(p.x(),p.y(),p.z()));
                if(affectedChunks.size()>256)throw new IOException("Experimental placement exceeds 256 affected chunks");
                cells.add(p,next);if((++count&127)==0)progress.accept(count*100/asset.cells.size());
            }
            var after=cells.build(cancel);
            var validated=validateChunks(after,snapshot,staged,cancel,Map.of());
            cancelled(cancel);progress.accept(100);var result=new Prepared(after,staged,revision,cancel,empty,validated,affectedChunks.size());complete=true;return result;
        } finally {if(!complete)busy.set(false);}
    }
    /** Detached CPU meshes conservatively count exposed chunk edges. Never touches GPU state. */
    private static Map<ChunkPos,Object> validateChunks(Map<Position,SparseVoxelOctree> after,Source source,ModelLibrary models,BooleanSupplier cancel,Map<ChunkPos,Object> reusable)throws IOException {
        var chunks=new HashSet<ChunkPos>();
        for(var p:after.keySet()) {
            chunks.add(ChunkPos.fromBlock(p.x(),p.y(),p.z()));
            if(chunks.size()>256)throw new IOException("Experimental placement exceeds 256 affected chunks");
        }
        var modelQuads=new HashMap<Integer,Integer>();var validated=new HashMap<ChunkPos,Object>();
        for(var position:chunks) {
            cancelled(cancel);var stamp=source.chunkStamp(position);
            if(stamp!=null&&stamp.equals(reusable.get(position))){validated.put(position,stamp);continue;}
            var chunk=new Chunk();
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
                if(quads==null){var definition=models.get(id);if(definition==null)throw new IOException("Missing world dependency");quads=ModelMesher.count(definition.definition().voxels());modelQuads.put(id,quads);}
                instanceQuads+=quads;if(instanceQuads>262144)throw new IOException("Experimental chunk model-instance surface cap");
            }
            try {MeshDataGenerator.count(chunk);}
            catch(IllegalStateException failure){throw new IOException("Structure would exceed chunk geometry budget",failure);}
            if(stamp!=null&&stamp.equals(source.chunkStamp(position)))validated.put(position,stamp);
        }
        return Map.copyOf(validated);
    }
    /** Reserve the newest undo record without touching the live world. Worker callers must close it. */
    public UndoPrepared beginUndo(String owner,BooleanSupplier cancel)throws IOException {
        if(!busy.compareAndSet(false,true))throw new IOException("Another large placement is running");
        boolean complete=false;
        try {
            cancelled(cancel);var records=undo.get(owner);
            if(records==null||records.isEmpty())throw new IOException("No structure placement to undo");
            var result=new UndoPrepared(records.peekLast(),cancel);complete=true;return result;
        } finally {if(!complete)busy.set(false);}
    }
    /** Detached undo staging. Only commit may publish or remove the reserved history record. */
    public final class UndoPrepared implements AutoCloseable {
        private final Record record;
        private final BooleanSupplier cancel;
        private ModelLibrary staged;
        private long modelRevision;
        private Map<ChunkPos,Object> proofs=Map.of();
        private int affectedChunks;
        private boolean closed,validated;
        private UndoPrepared(Record record,BooleanSupplier cancel){this.record=record;this.cancel=cancel;}
        private void open()throws IOException {if(closed)throw new IOException("Structure undo staging closed");cancelled(cancel);}
        public Map<Position,SparseVoxelOctree> cells(){return record.before;}
        /** Worker-only scan of immutable keys; no owner-thread bounds scan. */
        public Footprint footprint()throws IOException {
            open();int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
            int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
            for(var p:record.after.keySet()) {
                cancelled(cancel);minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());minZ=Math.min(minZ,p.z());
                maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());maxZ=Math.max(maxZ,p.z());
            }
            return new Footprint(minX,minY,minZ,maxX-minX+1,maxY-minY+1,maxZ-minZ+1,0,new Position(minX,minY,minZ));
        }
        public void validate(Source snapshot,IntConsumer progress)throws IOException {
            open();if(validated)throw new IOException("Structure undo already validated");
            modelRevision=snapshot.models().revision();staged=snapshot.models().copy();
            var chunks=new HashSet<ChunkPos>();var captured=new HashMap<ChunkPos,Object>();int count=0;
            progress.accept(0);cancelled(cancel);
            for(var entry:record.after.entrySet()) {
                cancelled(cancel);var p=entry.getKey();var chunk=ChunkPos.fromBlock(p.x(),p.y(),p.z());
                if(chunks.add(chunk)){var stamp=snapshot.chunkStamp(chunk);if(stamp!=null)captured.put(chunk,stamp);}
                if(!snapshot.cell(p.x(),p.y(),p.z()).sameValues(entry.getValue()))
                    throw new IOException("Placed cells changed; undo will not overwrite later edits");
                if((++count&127)==0)progress.accept((int)(100L*count/record.after.size()));
            }
            for(var entry:captured.entrySet()) {
                cancelled(cancel);
                if(!entry.getValue().equals(snapshot.chunkStamp(entry.getKey())))throw new IOException("World changed during undo validation; retry undo");
            }
            cancelled(cancel);progress.accept(100);cancelled(cancel);
            proofs=Map.copyOf(captured);affectedChunks=chunks.size();validated=true;
        }
        public ModelLibrary modelSnapshot()throws IOException {open();if(!validated)throw new IOException("Structure undo not validated");return staged.copy();}
        private boolean unchanged(Authority authority) {
            if(proofs.size()!=affectedChunks)return false;
            for(var entry:proofs.entrySet()){cancelled(cancel);if(!entry.getValue().equals(authority.chunkStamp(entry.getKey())))return false;}
            return true;
        }
        public void commit(Authority authority)throws IOException {
            try {
                open();if(!validated)throw new IOException("Structure undo not validated");
                try(var publication=authority.stageCommit(staged,record.before,record.after,modelRevision,cancel)) {
                    synchronized(authority.lock()) {
                        open();var records=undo.get(record.owner);
                        if(records==null||records.peekLast()!=record)throw new IOException("Undo history changed; retry undo");
                        if(authority.models().revision()!=modelRevision)throw new IOException("Model library changed; retry undo");
                        if(!unchanged(authority))for(var entry:record.after.entrySet()) {
                            cancelled(cancel);var p=entry.getKey();
                            if(!authority.cell(p.x(),p.y(),p.z()).sameValues(entry.getValue()))
                                throw new IOException("Placed cells changed; undo will not overwrite later edits");
                        }
                        cancelled(cancel);publication.apply();records.removeLast();chronology.remove(record);undoCells-=record.after.size();
                    }
                }
            } finally {close();}
        }
        @Override public void close(){if(!closed){closed=true;busy.set(false);}}
    }
    public void undo(String owner,Authority authority)throws IOException {undo(owner,authority,()->false);}
    public void undo(String owner,Authority authority,BooleanSupplier cancel)throws IOException {
        if(!busy.compareAndSet(false,true))throw new IOException("Another large placement is running");
        try {
            Record record;ModelLibrary staged;long revision;
            synchronized(authority.lock()) {
                cancelled(cancel);
                var records=undo.get(owner);if(records==null||records.isEmpty())throw new IOException("No structure placement to undo");
                record=records.peekLast();revision=authority.models().revision();staged=authority.models().copy();
            }
            try(var publication=authority.stageCommit(staged,record.before,record.after,revision,cancel)) {
                synchronized(authority.lock()) {
                    cancelled(cancel);
                    if(authority.models().revision()!=revision)throw new IOException("Model library changed; retry undo");
                    int checked=0;
                    for(var entry:record.after.entrySet()) {
                        if((checked++&127)==0)cancelled(cancel);
                        var p=entry.getKey();
                        if(!authority.cell(p.x(),p.y(),p.z()).sameValues(entry.getValue()))
                            throw new IOException("Placed cells changed; undo will not overwrite later edits");
                    }
                    cancelled(cancel);publication.apply();
                    undo.get(owner).removeLast();chronology.remove(record);undoCells-=record.after.size();
                }
            }
        } finally {busy.set(false);}
    }
}
