package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.CloseSequence;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Synchronous worker-owned capture from one validated regional saved snapshot.
 * Does not create a World, consume playable metadata, save, or edit the source.
 * Construct/use/close on the same worker. Failed cleanup stays owned for an
 * explicit close retry; queries never retry filesystem operations. */
public final class SavedRegionCapture implements AutoCloseable {
    public enum Phase {OPENING,CAPTURING,CLEANING,DONE}
    public record Stats(int checkedRows,int visitedChunks,int cellReads,int peakCachedRegions,long peakCachedBytes) {}
    public record Progress(Phase phase,int percent,Stats stats) {}
    private final Thread owner=Thread.currentThread();
    private final Path file,directory;
    private final String name,expectedFingerprint;
    private final StructurePackage.Bounds bounds;
    private final StructurePackage.Position origin;
    private RegionReplayWorker.Source source;
    private RegionReplayCache cache;
    private Path ownedDirectory;
    private boolean busy,attempted,closed;
    private int checkedRows,visitedChunks,cellReads,peakRegions;private long peakBytes;

    public SavedRegionCapture(Path file,Path directory,String name,StructurePackage.Bounds bounds,StructurePackage.Position origin)throws IOException {
        this(file,directory,name,bounds,origin,"");
    }
    /** Optional validated snapshot identity, for capture matching a read-only view. */
    public SavedRegionCapture(Path file,Path directory,String name,StructurePackage.Bounds bounds,StructurePackage.Position origin,String expectedFingerprint)throws IOException {
        if(expectedFingerprint==null||!expectedFingerprint.isEmpty()&&!expectedFingerprint.matches("[0-9a-f]{64}"))throw new IOException("Capture snapshot identity required");
        this.expectedFingerprint=expectedFingerprint;
        this.file=Objects.requireNonNull(file).toAbsolutePath().normalize();this.directory=Objects.requireNonNull(directory).toAbsolutePath().normalize();
        this.name=name;this.bounds=Objects.requireNonNull(bounds);this.origin=Objects.requireNonNull(origin);
        if(name==null||!name.matches("[A-Za-z0-9 _-]{1,48}"))throw new IOException("Use 1-48 letters, digits, spaces, underscores or dashes");
        long x=(long)origin.x()+bounds.x()-1,y=(long)origin.y()+bounds.y()-1,z=(long)origin.z()+bounds.z()-1;
        if(x>Integer.MAX_VALUE||y>Integer.MAX_VALUE||z>Integer.MAX_VALUE||!new Protocol.Edit(origin.x(),origin.y(),origin.z(),0).valid()||!new Protocol.Edit((int)x,(int)y,(int)z,0).valid())throw new IOException("Capture outside world");
    }
    private void owner()throws IOException {if(Thread.currentThread()!=owner)throw new IOException("Saved region capture worker required");}
    public Stats stats()throws IOException {owner();return new Stats(checkedRows,visitedChunks,cellReads,peakRegions,peakBytes);}
    public boolean cleanupPending()throws IOException {owner();return ownedDirectory!=null||cache!=null||source!=null;}
    /** Owned child only, for a durable internal cleanup checkpoint after failure.
     * Never delete the caller's container or unrelated files recursively. */
    public Path ownedDirectory()throws IOException {owner();return ownedDirectory;}
    private void sample()throws IOException {peakRegions=Math.max(peakRegions,cache.retainedRegions());peakBytes=Math.max(peakBytes,cache.retainedBytes());}
    private void progress(Consumer<Progress> progress,Phase phase,int percent){progress.accept(new Progress(phase,percent,new Stats(checkedRows,visitedChunks,cellReads,peakRegions,peakBytes)));}

    public StructurePackage capture(BooleanSupplier cancel,Consumer<Progress> progress)throws IOException {
        owner();Objects.requireNonNull(cancel);Objects.requireNonNull(progress);if(busy||closed||attempted)throw new IOException("Saved region capture unavailable or consumed");
        attempted=true;busy=true;boolean cleanupAttempted=false;
        try {
            StructurePackage.cancelled(cancel);progress(progress,Phase.OPENING,0);StructurePackage.cancelled(cancel);
            Files.createDirectories(directory);ownedDirectory=Files.createTempDirectory(directory,".structure-capture-");
            source=LocalGame.openRegionalReadback(file,ownedDirectory,new RegionReplayWorker.Revision(1,0,0),cancel,n->{checkedRows=n;progress(progress,Phase.OPENING,0);});
            if(!expectedFingerprint.isEmpty()&&!expectedFingerprint.equals(source.fingerprint()))throw new IOException("Capture source differs from viewed snapshot");
            var models=source.metadata().sourceModelSnapshot();cache=new RegionReplayCache(ownedDirectory,source.spool(),2,4L*1024*1024,32768,source.allowed());
            var adapter=new StructurePackage.Source() {
                private int lastX,lastY,lastZ;private SparseVoxelOctree last;
                public ModelLibrary models(){return models;}
                public boolean emptyChunk(ChunkPos chunk) {
                    visitedChunks++;
                    try {boolean outside=chunk.chunkY()*16L>Terrain.MAX_Y||chunk.chunkY()*16L+15<Terrain.MIN_Y;boolean empty=outside&&cache.emptyRegion(chunk,cancel);sample();return empty;}
                    catch(IOException failure){throw new UncheckedIOException(failure);}
                }
                public boolean emptyCell(int x,int y,int z){return cell(x,y,z).occupied()==0;}
                public SparseVoxelOctree cell(int x,int y,int z) {
                    if(last!=null&&x==lastX&&y==lastY&&z==lastZ)return last;
                    try {
                        StructurePackage.cancelled(cancel);int key=(x&15)|((z&15)<<4)|((y&15)<<8);
                        var page=cache.load(ChunkPos.fromBlock(x,y,z),new RegionReplayPage.CellRange(key,key+1),source.terrain(),RegionReplayPage.MAX_ROWS,cancel);
                        var image=page.cells().get(new WorldVoxels.CellPosition(x,y,z));SparseVoxelOctree tree;
                        if(image==null){tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(source.terrain().block(x,y,z)));tree.freeze();}else tree=image.tree();
                        lastX=x;lastY=y;lastZ=z;last=tree;cellReads++;sample();return tree;
                    }catch(IOException failure){throw new UncheckedIOException(failure);}
                }
            };
            progress(progress,Phase.CAPTURING,25);StructurePackage.cancelled(cancel);StructurePackage result;
            try{result=StructurePackage.capturePartitioned(name,bounds,origin,adapter,cancel,n->progress(progress,Phase.CAPTURING,25+n*70/100));}
            catch(UncheckedIOException failure){throw failure.getCause();}
            StructurePackage.cancelled(cancel);progress(progress,Phase.CLEANING,95);StructurePackage.cancelled(cancel);
            cleanupAttempted=true;cleanup();progress(progress,Phase.DONE,100);StructurePackage.cancelled(cancel);return result;
        }catch(IOException|RuntimeException|Error failure) {
            if(!cleanupAttempted)try{cleanup();}catch(IOException|RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}throw failure;
        }finally{busy=false;}
    }
    private void cleanup()throws IOException {
        var cleanup=new CloseSequence();
        cleanup.run(()->{if(cache!=null){cache.close();cache=null;}});
        cleanup.run(()->{if(source!=null){source.spool().close();source=null;}});
        cleanup.run(()->{if(cache==null&&source==null&&ownedDirectory!=null){Files.delete(ownedDirectory);ownedDirectory=null;}});
        try{cleanup.finish();}catch(IOException|RuntimeException|Error failure){throw failure;}catch(Exception failure){throw new IOException("Saved region cleanup failed",failure);}
    }
    @Override public void close()throws IOException {owner();if(busy)throw new IOException("Saved region capture close reentry");closed=true;cleanup();}
}
