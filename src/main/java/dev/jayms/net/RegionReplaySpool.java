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
 * Holds only paths/counts/checksum between visits, not global edits or a resident section directory. A later
 * invalid world tail must cause the caller to close this uncommitted spool. */
final class RegionReplaySpool implements AutoCloseable {
    // Worst case: one 20-byte header for every 24-byte edit, plus total count.
    static final long MAX_BYTES=4L+LocalGame.MAX_SAVED_EDITS*(20L+RegionReplay.EDIT_BYTES);
    private final Thread owner=Thread.currentThread();
    static final int INDEX_RECORD_BYTES=28;
    static final int SELECTED_DIRECTORY_RECORDS=4096;
    static final long MAX_INDEX_BYTES=(long)LocalGame.MAX_SAVED_EDITS*INDEX_RECORD_BYTES;
    private final Path path,index;
    private final int sections;
    private final long indexChecksum;
    private final int records;
    private final long bytes;
    private boolean closed,busy;
    private Buckets buckets;
    private final java.util.Set<Path> scratchPaths=new java.util.LinkedHashSet<>();
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
     * preserving recurring sections in file order. Up to 4096 matching section records
     * are snapshotted from the validated index; larger selections use a second
     * streaming index pass. Unselected payload integrity
     * is deferred until read; this is not permission to publish a whole world. */
    void visitRegion(ChunkPos region,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,RegionReplay.SectionSink sink)throws IOException {
        visitRegion(region,LocalGame.MAX_SAVED_EDITS,allowed,cancel,sink);
    }
    /** Tighten the selected input-row budget before any payload decode/delivery.
     * Counts source rows, including overwritten and explicit-air rows. */
    void visitRegion(ChunkPos region,int maxRows,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,RegionReplay.SectionSink sink)throws IOException {
        Objects.requireNonNull(region);
        if(maxRows<0||maxRows>LocalGame.MAX_SAVED_EDITS)throw new IllegalArgumentException("Replay region row budget");
        visitSelected(region::equals,maxRows,allowed,cancel,sink);
    }
    /** Private copied-index lookup. Full original index validates once before
     * bucket publication; later reads validate the selected bucket only. */
    void visitIndexedRegion(ChunkPos region,int maxRows,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,RegionReplay.SectionSink sink)throws IOException {
        Objects.requireNonNull(region);
        if(maxRows<0||maxRows>LocalGame.MAX_SAVED_EDITS)throw new IllegalArgumentException("Replay region row budget");
        visitSelected(region::equals,maxRows,allowed,cancel,sink,region);
    }
    private void visitSelected(Predicate<ChunkPos> select,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,RegionReplay.SectionSink sink)throws IOException {
        visitSelected(select,LocalGame.MAX_SAVED_EDITS,allowed,cancel,sink);
    }
    private void visitSelected(Predicate<ChunkPos> select,int maxRows,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,RegionReplay.SectionSink sink)throws IOException {
        visitSelected(select,maxRows,allowed,cancel,sink,null);
    }
    private void visitSelected(Predicate<ChunkPos> select,int maxRows,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,RegionReplay.SectionSink sink,ChunkPos indexed)throws IOException {
        Objects.requireNonNull(allowed);Objects.requireNonNull(cancel);Objects.requireNonNull(sink);
        check();StructurePackage.cancelled(cancel);busy=true;
        try(var data=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            if(data.size()!=bytes||bytes>MAX_BYTES)throw new IOException("Replay spool length changed");
            if(new DataInputStream(Channels.newInputStream(data)).readInt()!=records)throw new IOException("Replay spool count changed");
            if(indexed!=null&&buckets==null) {
                buckets=new Buckets();
                try{buckets.prepare(cancel);}
                catch(IOException|RuntimeException|Error failure) {
                    try{buckets.close();buckets=null;}catch(IOException cleanup){failure.addSuppressed(cleanup);}
                    throw failure;
                }
            }
            // Validate the complete chosen directory before selected delivery.
            long[] selectedRows={0};
            // Snapshot a bounded matching directory only after the full sealed
            // index passes validation. Larger selections retain the streaming path.
            var selected=select==null?null:ByteBuffer.allocate(SELECTED_DIRECTORY_RECORDS*INDEX_RECORD_BYTES);
            boolean[] captured={selected!=null};
            readDirectory(indexed,cancel,entry->{
                if(select==null||select.test(entry.region())) {
                    selectedRows[0]+=entry.count();
                    if(selectedRows[0]>maxRows)throw new IOException("Replay region row budget");
                    if(captured[0]) {
                        if(selected.remaining()<INDEX_RECORD_BYTES){captured[0]=false;selected.clear();}
                        else selected.putLong(entry.offset()).putInt(entry.region().chunkX()).putInt(entry.region().chunkY())
                                .putInt(entry.region().chunkZ()).putInt(entry.count()).putInt((int)entry.checksum());
                    }
                }
            });
            if(select==null) {
                data.position(0);var in=new DataInputStream(new BufferedInputStream(Channels.newInputStream(data),64*1024));
                if(in.readInt()!=records)throw new IOException("Replay spool count changed");
                readIndex(cancel,entry->deliver(entry,RegionReplay.readSection(in,entry.count(),allowed,cancel),cancel,sink));
                if(in.read()!=-1)throw new IOException("Trailing replay spool data");
                StructurePackage.cancelled(cancel);return;
            }
            var frame=ByteBuffer.allocate(20+RegionReplay.MAX_BODY);long[] position={4},decodedRows={0};
            IndexSink delivery=entry->{
                if(!select.test(entry.region()))return;
                // Recheck while consuming: a changed directory must not grow
                // private staging before its final sealed-CRC rejection.
                decodedRows[0]+=entry.count();
                if(decodedRows[0]>maxRows)throw new IOException("Replay region row budget");
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
            };
            if(captured[0]) {
                selected.flip();
                while(selected.hasRemaining()) {
                    StructurePackage.cancelled(cancel);
                    delivery.accept(new IndexEntry(selected.getLong(),new ChunkPos(selected.getInt(),selected.getInt(),selected.getInt()),
                            selected.getInt(),Integer.toUnsignedLong(selected.getInt())));
                }
            }else readDirectory(indexed,cancel,delivery);
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
    private void readDirectory(ChunkPos region,BooleanSupplier cancel,IndexSink sink)throws IOException {
        if(region==null)readIndex(cancel,sink);else buckets.read(region,cancel,sink);
    }
    static final int BUCKETS=32;
    static int bucket(ChunkPos region){return (region.hashCode()*0x9e3779b9)>>>27;}
    /** Worker-owned copied metadata snapshot. No durable/public archive format.
     * Collisions may degenerate to the full metadata cost; storage stays bounded. */
    private final class Buckets implements AutoCloseable {
        private final Path[] paths=new Path[BUCKETS];
        private final int[] counts=new int[BUCKETS],rows=new int[BUCKETS];
        private final long[] checksums=new long[BUCKETS];
        private boolean ready;
        void prepare(BooleanSupplier cancel)throws IOException {
            var channels=new FileChannel[BUCKETS];var outputs=new DataOutputStream[BUCKETS];var crc=new CRC32[BUCKETS];
            Throwable failure=null;
            try {
                readIndex(cancel,entry->{
                    int b=bucket(entry.region());
                    if(paths[b]==null) {
                        paths[b]=Files.createTempFile(index.getParent(),".replay-bucket-"+b+"-",".tmp");
                        channels[b]=FileChannel.open(paths[b],StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);crc[b]=new CRC32();
                        outputs[b]=new DataOutputStream(new BufferedOutputStream(new CheckedOutputStream(Channels.newOutputStream(channels[b]),crc[b]),4096));
                    }
                    var out=outputs[b];out.writeLong(entry.offset());out.writeInt(entry.region().chunkX());out.writeInt(entry.region().chunkY());out.writeInt(entry.region().chunkZ());out.writeInt(entry.count());out.writeInt((int)entry.checksum());
                    counts[b]++;rows[b]+=entry.count();
                });
                for(int b=0;b<BUCKETS;b++)if(outputs[b]!=null) {
                    StructurePackage.cancelled(cancel);outputs[b].flush();
                    if(channels[b].size()!=(long)counts[b]*INDEX_RECORD_BYTES)throw new IOException("Replay bucket length changed");
                    channels[b].force(true);checksums[b]=crc[b].getValue();StructurePackage.cancelled(cancel);
                }
            }catch(IOException|RuntimeException|Error error){failure=error;throw error;}
            finally {
                IOException cleanup=null;
                for(int b=0;b<BUCKETS;b++) {
                    try{if(outputs[b]!=null)outputs[b].close();}catch(IOException error){if(cleanup==null)cleanup=error;else cleanup.addSuppressed(error);}
                    try{if(channels[b]!=null)channels[b].close();}catch(IOException error){if(cleanup==null)cleanup=error;else cleanup.addSuppressed(error);}
                }
                if(cleanup!=null){if(failure!=null)failure.addSuppressed(cleanup);else throw cleanup;}
            }
            ready=true;
        }
        void read(ChunkPos region,BooleanSupplier cancel,IndexSink sink)throws IOException {
            if(!ready)throw new IOException("Replay bucket preparation incomplete; close source");
            StructurePackage.cancelled(cancel);int b=bucket(region);if(counts[b]==0)return;
            long expected=(long)counts[b]*INDEX_RECORD_BYTES;
            try(var channel=FileChannel.open(paths[b],StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
                if(expected>MAX_INDEX_BYTES||channel.size()!=expected)throw new IOException("Replay bucket length changed");
                var crc=new CRC32();var in=new DataInputStream(new BufferedInputStream(new CheckedInputStream(Channels.newInputStream(channel),crc),64*1024));
                long previousEnd=4,total=0;
                for(int i=0;i<counts[b];i++) {
                    StructurePackage.cancelled(cancel);long offset=in.readLong();
                    var p=new ChunkPos(in.readInt(),in.readInt(),in.readInt());int count=in.readInt();long checksum=Integer.toUnsignedLong(in.readInt());
                    var entry=new IndexEntry(offset,p,count,checksum);
                    if(bucket(p)!=b||offset<previousEnd||count<1||count>RegionReplay.MAX_RECORDS||count>records-total||entry.bytes()>bytes-offset)
                        throw new IOException("Replay bucket entry changed");
                    previousEnd=offset+entry.bytes();total+=count;sink.accept(entry);
                }
                StructurePackage.cancelled(cancel);
                if(in.read()!=-1||total!=rows[b]||crc.getValue()!=checksums[b]||channel.size()!=expected)throw new IOException("Replay bucket changed");
            }
        }
        @Override public void close()throws IOException {
            IOException failure=null;ready=false;
            for(int b=0;b<BUCKETS;b++)if(paths[b]!=null) {
                try{Files.deleteIfExists(paths[b]);paths[b]=null;}
                catch(IOException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            }
            if(failure!=null)throw failure;
        }
    }
    /** At most the raw counting file and its final cell-index candidate.
     * Failed deletion remains owned here until explicit cleanup succeeds. */
    final class Scratch implements AutoCloseable {
        private final Path file;private boolean released;
        private Scratch(Path file){this.file=file;}
        Path path(){return file;}
        Path release()throws IOException {
            check();if(released||!scratchPaths.remove(file))throw new IOException("Replay scratch not owned");
            released=true;return file;
        }
        @Override public void close()throws IOException {
            if(Thread.currentThread()!=owner||busy)throw new IOException("Replay scratch worker ownership required");
            if(!released){Files.deleteIfExists(file);scratchPaths.remove(file);released=true;}
        }
    }
    Scratch scratch(Path directory,boolean cells)throws IOException {
        check();if(scratchPaths.size()>=2||(!cells&&!scratchPaths.isEmpty()))throw new IOException("Replay scratch cleanup pending");
        var file=Files.createTempFile(directory,cells?".replay-cells-":".replay-count-",".tmp");
        scratchPaths.add(file);return new Scratch(file);
    }
    @Override public void close()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay spool worker ownership required");
        if(busy)throw new IOException("Replay spool operation already active");
        if(!closed){
            IOException failure=null;
            if(buckets!=null)try{buckets.close();}catch(IOException error){failure=error;}
            for(var iterator=scratchPaths.iterator();iterator.hasNext();) {
                try{Files.deleteIfExists(iterator.next());iterator.remove();}
                catch(IOException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            }
            try{Files.deleteIfExists(path);}catch(IOException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            try{Files.deleteIfExists(index);}catch(IOException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            if(failure!=null)throw failure;closed=true;
        }
    }
}
