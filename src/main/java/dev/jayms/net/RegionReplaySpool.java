package dev.jayms.net;

import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.Objects;
import dev.jayms.ChunkPos;
import java.util.zip.*;
import java.util.function.*;

/** Worker-private replay staging, never an authority or durable world format.
 * Holds only paths/counts/checksum, not global edits or a resident section directory. A later
 * invalid world tail must cause the caller to close this uncommitted spool. */
final class RegionReplaySpool implements AutoCloseable {
    // Worst case: one 20-byte header for every 24-byte edit, plus total count.
    static final long MAX_BYTES=4L+LocalGame.MAX_SAVED_EDITS*(20L+RegionReplay.EDIT_BYTES);
    private final Thread owner=Thread.currentThread();
    static final int INDEX_RECORD_BYTES=28;
    static final long MAX_INDEX_BYTES=(long)LocalGame.MAX_SAVED_EDITS*INDEX_RECORD_BYTES;
    private final Path path,index;
    private final int sections;
    private final long indexChecksum;
    private final int records;
    private final long bytes;
    private boolean closed,busy;
    private RegionReplaySpool(Path path,Path index,int records,long bytes,int sections,long indexChecksum){this.path=path;this.index=index;this.records=records;this.bytes=bytes;this.sections=sections;this.indexChecksum=indexChecksum;}

    static RegionReplaySpool stage(Path directory,DataInputStream source,
            Predicate<Protocol.Edit> allowed,BooleanSupplier cancel)throws IOException {
        return stage(directory,source,allowed,cancel,MAX_BYTES,count->{});
    }
    /** The byte budget may only tighten the existing format limit. */
    static RegionReplaySpool stage(Path directory,DataInputStream source,
            Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,long budget)throws IOException {
        return stage(directory,source,allowed,cancel,budget,count->{});
    }
    static RegionReplaySpool stage(Path directory,DataInputStream source,
            Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,long budget,IntConsumer progress)throws IOException {
        Objects.requireNonNull(directory);Objects.requireNonNull(source);
        Objects.requireNonNull(allowed);Objects.requireNonNull(cancel);Objects.requireNonNull(progress);
        if(budget<4||budget>MAX_BYTES)throw new IllegalArgumentException("Replay spool byte budget");
        StructurePackage.cancelled(cancel);
        Path temp=Files.createTempFile(directory,".replay-spool-",".tmp");
        Path index=null;
        try {
            index=Files.createTempFile(directory,".replay-index-",".tmp");
            var indexCRC=new CRC32();int[] sectionCount={0};
            progress.accept(0);StructurePackage.cancelled(cancel);
            int[] count={0};long[] size={4};
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE);
                var indexChannel=FileChannel.open(index,StandardOpenOption.WRITE);
                var directoryOut=new DataOutputStream(new BufferedOutputStream(new CheckedOutputStream(Channels.newOutputStream(indexChannel),indexCRC),64*1024));
                var out=new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel),64*1024))) {
                out.writeInt(0);
                var buffer=new ByteArrayOutputStream(RegionReplay.MAX_BODY);
                var body=new DataOutputStream(buffer);
                RegionReplay.visit(source,allowed,cancel,(region,section)->{
                    long length=20L+section.size()*RegionReplay.EDIT_BYTES;
                    if(length>budget-size[0])throw new IOException("Replay spool byte budget");
                    buffer.reset();
                    for(var edit:section){
                        StructurePackage.cancelled(cancel);edit.write(body);
                        if((++count[0]&127)==0){progress.accept(count[0]);StructurePackage.cancelled(cancel);}
                    }
                    StructurePackage.cancelled(cancel);
                    long crc=RegionReplay.writeSection(out,region,section.size(),buffer);
                    directoryOut.writeLong(size[0]);directoryOut.writeInt(region.chunkX());directoryOut.writeInt(region.chunkY());directoryOut.writeInt(region.chunkZ());
                    directoryOut.writeInt(section.size());directoryOut.writeInt((int)crc);sectionCount[0]++;
                    size[0]+=length;
                });
                StructurePackage.cancelled(cancel);out.flush();directoryOut.flush();
                var header=ByteBuffer.allocate(4).putInt(count[0]);header.flip();
                long position=0;
                while(header.hasRemaining()) {
                    StructurePackage.cancelled(cancel);int n=channel.write(header,position);
                    if(n<=0)throw new IOException("Replay spool header write made no progress");position+=n;
                }
                StructurePackage.cancelled(cancel);channel.force(true);StructurePackage.cancelled(cancel);indexChannel.force(true);StructurePackage.cancelled(cancel);
            }
            StructurePackage.cancelled(cancel);progress.accept(count[0]);StructurePackage.cancelled(cancel);
            return new RegionReplaySpool(temp,index,count[0],size[0],sectionCount[0],indexCRC.getValue());
        }catch(IOException|RuntimeException|Error failure) {
            try{Files.deleteIfExists(temp);}catch(IOException cleanup){failure.addSuppressed(cleanup);}
            if(index!=null)try{Files.deleteIfExists(index);}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;
        }
    }
    int size()throws IOException{check();return records;}
    long encodedBytes()throws IOException{check();return bytes;}
    private void check()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay spool worker ownership required");
        if(closed)throw new IOException("Replay spool closed");
        if(busy)throw new IOException("Replay spool operation already active");
    }
    /** Every delivery is private staging. Full traversal rechecks every section.
     * Sink failure/cancellation allows retry; publish only after complete success. */
    void visit(Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,RegionReplay.SectionSink sink)throws IOException {
        visitSelected(null,allowed,cancel,sink);
    }
    /** Selective worker read. Reads index metadata and only matching payloads,
     * preserving recurring sections in file order. Unselected payload integrity
     * is deferred until read; this is not permission to publish a whole world. */
    void visitRegion(ChunkPos region,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,RegionReplay.SectionSink sink)throws IOException {
        Objects.requireNonNull(region);visitSelected(region::equals,allowed,cancel,sink);
    }
    private void visitSelected(Predicate<ChunkPos> select,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,RegionReplay.SectionSink sink)throws IOException {
        Objects.requireNonNull(allowed);Objects.requireNonNull(cancel);Objects.requireNonNull(sink);
        check();StructurePackage.cancelled(cancel);busy=true;
        try(var data=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            if(data.size()!=bytes||bytes>MAX_BYTES)throw new IOException("Replay spool length changed");
            if(new DataInputStream(Channels.newInputStream(data)).readInt()!=records)throw new IOException("Replay spool count changed");
            // Validate the entire sealed directory before any selected delivery.
            readIndex(cancel,entry->{});
            if(select==null) {
                data.position(0);var in=new DataInputStream(new BufferedInputStream(Channels.newInputStream(data),64*1024));
                if(in.readInt()!=records)throw new IOException("Replay spool count changed");
                readIndex(cancel,entry->deliver(entry,RegionReplay.readSection(in,entry.count(),allowed,cancel),cancel,sink));
                if(in.read()!=-1)throw new IOException("Trailing replay spool data");
                StructurePackage.cancelled(cancel);return;
            }
            var frame=ByteBuffer.allocate(20+RegionReplay.MAX_BODY);long[] position={4};
            readIndex(cancel,entry->{
                if(!select.test(entry.region()))return;
                StructurePackage.cancelled(cancel);
                if(position[0]!=entry.offset())data.position(entry.offset());
                frame.clear().limit((int)entry.bytes());
                while(frame.hasRemaining()) {
                    StructurePackage.cancelled(cancel);if(data.read(frame)<1)throw new EOFException("Truncated replay spool section");
                }
                position[0]=entry.offset()+entry.bytes();
                var in=new DataInputStream(new ByteArrayInputStream(frame.array(),0,frame.limit()));
                var section=RegionReplay.readSection(in,entry.count(),allowed,cancel);
                if(in.read()!=-1)throw new IOException("Trailing replay spool section");
                deliver(entry,section,cancel,sink);
            });
            StructurePackage.cancelled(cancel);
            if(data.size()!=bytes)throw new IOException("Replay spool length changed");
        }finally{busy=false;}
    }
    private static void deliver(IndexEntry entry,RegionReplay.Section section,BooleanSupplier cancel,RegionReplay.SectionSink sink)throws IOException {
        if(!section.region().equals(entry.region())||section.rows().size()!=entry.count()||section.checksum()!=entry.checksum())throw new IOException("Replay spool section changed");
        StructurePackage.cancelled(cancel);sink.accept(section.region(),section.rows());StructurePackage.cancelled(cancel);
    }
    private record IndexEntry(long offset,ChunkPos region,int count,long checksum) {
        long bytes(){return 20L+(long)count*RegionReplay.EDIT_BYTES;}
    }
    @FunctionalInterface private interface IndexSink {void accept(IndexEntry entry)throws IOException;}
    private void readIndex(BooleanSupplier cancel,IndexSink sink)throws IOException {
        long expectedBytes=(long)sections*INDEX_RECORD_BYTES;
        try(var channel=FileChannel.open(index,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            if(expectedBytes>MAX_INDEX_BYTES||channel.size()!=expectedBytes)throw new IOException("Replay index length changed");
            var crc=new CRC32();var in=new DataInputStream(new BufferedInputStream(new CheckedInputStream(Channels.newInputStream(channel),crc),64*1024));
            long offset=4,total=0;
            for(int i=0;i<sections;i++) {
                StructurePackage.cancelled(cancel);long actualOffset=in.readLong();
                var region=new ChunkPos(in.readInt(),in.readInt(),in.readInt());int count=in.readInt();long checksum=Integer.toUnsignedLong(in.readInt());
                if(actualOffset!=offset||count<1||count>RegionReplay.MAX_RECORDS||count>records-total)throw new IOException("Replay index section budget");
                var entry=new IndexEntry(actualOffset,region,count,checksum);offset+=entry.bytes();total+=count;
                if(offset>bytes)throw new IOException("Replay index offset budget");sink.accept(entry);
            }
            StructurePackage.cancelled(cancel);
            if(in.read()!=-1||offset!=bytes||total!=records||crc.getValue()!=indexChecksum)throw new IOException("Replay index changed");
            StructurePackage.cancelled(cancel);
        }
    }
    @Override public void close()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay spool worker ownership required");
        if(busy)throw new IOException("Replay spool operation already active");
        if(!closed){
            IOException failure=null;
            try{Files.deleteIfExists(path);}catch(IOException error){failure=error;}
            try{Files.deleteIfExists(index);}catch(IOException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            if(failure!=null)throw failure;closed=true;
        }
    }
}
