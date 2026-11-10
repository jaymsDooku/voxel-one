package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

/** Private synchronous worker cache, not a live world archive. The caller owns
 * the source spool and must fence model/terrain/authority revisions separately.
 * No index handle escapes. Returned pages are detached from cache lifetime. */
final class RegionReplayCache implements AutoCloseable {
    static final int MAX_REGIONS=4;
    static final long MAX_BYTES=RegionReplayCells.MAX_BYTES;
    private final Thread owner=Thread.currentThread();
    private final Path directory;
    private final RegionReplaySpool source;
    private final Predicate<Protocol.Edit> allowed;
    private final int maxRegions,maxRegionRows;
    private final long maxBytes,reservation;
    private final LinkedHashMap<ChunkPos,RegionReplayCells> entries=new LinkedHashMap<>(4,.75f,true);
    private long bytes;
    private boolean busy,closed;

    RegionReplayCache(Path directory,RegionReplaySpool source,int maxRegions,long maxBytes,
            int maxRegionRows,Predicate<Protocol.Edit> allowed) {
        this.directory=Objects.requireNonNull(directory);this.source=Objects.requireNonNull(source);
        this.allowed=Objects.requireNonNull(allowed);
        if(maxRegions<1||maxRegions>MAX_REGIONS||maxBytes<0||maxBytes>MAX_BYTES||
                maxRegionRows<0||maxRegionRows>LocalGame.MAX_SAVED_EDITS)
            throw new IllegalArgumentException("Replay cache budget");
        reservation=(long)maxRegionRows*RegionReplayCells.RECORD_BYTES;
        if(reservation>maxBytes)throw new IllegalArgumentException("Replay cache build reservation");
        this.maxRegions=maxRegions;this.maxBytes=maxBytes;this.maxRegionRows=maxRegionRows;
    }
    private void check()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay cache worker ownership required");
        if(closed)throw new IOException("Replay cache closed");
        if(busy)throw new IOException("Replay cache operation already active");
    }
    int retainedRegions()throws IOException{check();return entries.size();}
    long retainedBytes()throws IOException{check();return bytes;}
    /** Reserve a complete possible build before writing. Eviction is allowed
     * even if a later build/read fails. Failed deletion retains accounting and
     * the handle, so a later close/request can retry without leaking ownership.
     * Limits cover copied logical data bytes, not source files or JVM overhead. */
    RegionReplayPage load(ChunkPos region,RegionReplayPage.CellRange range,Terrain terrain,
            int maxRows,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(region);Objects.requireNonNull(range);Objects.requireNonNull(terrain);Objects.requireNonNull(cancel);
        if(range.size()>RegionReplayPage.MAX_CELLS||maxRows<0||maxRows>RegionReplayPage.MAX_ROWS)
            throw new IllegalArgumentException("Replay cache page budget");
        check();StructurePackage.cancelled(cancel);busy=true;
        try {
            var index=entries.get(region);
            if(index==null) {
                while(entries.size()>=maxRegions||bytes>maxBytes-reservation) {
                    StructurePackage.cancelled(cancel);evictFirst();
                }
                // The build enforces maxRegionRows during both traversals.
                index=RegionReplayCells.build(directory,source,region,maxRegionRows,allowed,cancel);
                long size;
                try {size=index.encodedBytes();}
                catch(IOException|RuntimeException|Error failure) {
                    try{index.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;
                }
                entries.put(region,index);bytes+=size;
            }
            return RegionReplayPage.loadIndexed(index,range,terrain,maxRows,allowed,cancel);
        }finally{busy=false;}
    }
    private void evictFirst()throws IOException {
        var iterator=entries.entrySet().iterator();var entry=iterator.next();
        long size=entry.getValue().encodedBytes();entry.getValue().close();
        iterator.remove();bytes-=size;
    }
    @Override public void close()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay cache worker ownership required");
        if(busy)throw new IOException("Replay cache operation already active");
        if(closed)return;
        // A failed deletion leaves this cache open and retryable. Already
        // removed roots stay removed; no forgotten file is counted as closed.
        while(!entries.isEmpty())evictFirst();closed=true;
    }
}
