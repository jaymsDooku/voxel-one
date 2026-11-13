package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.CRC32;

/** One worker-owned, temporary region index, not durable storage or live authority.
 * Build fully validates the matching region. Reads validate requested copied
 * cells; later corruption of other cells is deferred until those cells are read.
 * Callers still own model/terrain revisions and publication fences. */
final class RegionReplayCells implements AutoCloseable {
    static final int RECORD_BYTES=4+RegionReplay.EDIT_BYTES;
    static final int SCATTER_PAGE_BYTES=64*1024,SCATTER_PAGES=4;
    static final long MAX_BYTES=(long)LocalGame.MAX_SAVED_EDITS*RECORD_BYTES;
    static final int METADATA_PRIMITIVE_BYTES=4096*4+4097*8+4096*8;
    private final Thread owner=Thread.currentThread();
    private final Path path;
    private final ChunkPos region;
    private final int[] counts;
    private final long[] starts,checksums;
    private final int records;
    private boolean busy,closed;

    private RegionReplayCells(Path path,ChunkPos region,int[] counts,long[] starts,long[] checksums,int records) {
        this.path=path;this.region=region;this.counts=counts;this.starts=starts;this.checksums=checksums;this.records=records;
    }
    private static int key(Protocol.Edit edit){return (edit.x()&15)|((edit.z()&15)<<4)|((edit.y()&15)<<8);}
    /** One source traversal into a bounded checksummed row scratch, then
     * stable counting scatter. Scratch ownership remains with source on failed
     * cleanup; the final root transfers only after both validation passes. */
    static RegionReplayCells build(Path directory,RegionReplaySpool source,ChunkPos region,int maxRows,
            Predicate<Protocol.Edit> allowed,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(directory);Objects.requireNonNull(source);Objects.requireNonNull(region);
        Objects.requireNonNull(allowed);Objects.requireNonNull(cancel);
        if(maxRows<0||maxRows>LocalGame.MAX_SAVED_EDITS)throw new IllegalArgumentException("Replay cell-index row budget");
        StructurePackage.cancelled(cancel);
        int[] counts=new int[4096],total={0};var sealed=new CRC32();
        try(var raw=source.scratch(directory,false)) {
            try(var channel=FileChannel.open(raw.path(),StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
                    var out=new DataOutputStream(new BufferedOutputStream(new java.util.zip.CheckedOutputStream(
                            java.nio.channels.Channels.newOutputStream(channel),sealed),64*1024))) {
                source.visitIndexedRegion(region,maxRows,allowed,cancel,(p,rows)->{
                    for(var edit:rows) {
                        StructurePackage.cancelled(cancel);
                        if(total[0]>=maxRows)throw new IOException("Replay cell-index row budget");
                        out.writeInt(total[0]++);edit.write(out);counts[key(edit)]++;
                    }
                });
                out.flush();StructurePackage.cancelled(cancel);
                if(channel.size()!=(long)total[0]*RECORD_BYTES)throw new IOException("Replay counting file length changed");
            }
            var starts=new long[4097];var crc=new CRC32[4096];
            for(int i=0;i<4096;i++) {
                StructurePackage.cancelled(cancel);starts[i+1]=starts[i]+(long)counts[i]*RECORD_BYTES;
                if(counts[i]>0)crc[i]=new CRC32();
            }
            if(starts[4096]>MAX_BYTES)throw new IOException("Replay cell-index byte budget");
            try(var candidate=source.scratch(directory,true)) {
                var written=new int[4096];var record=ByteBuffer.allocate(RECORD_BYTES);var verified=new CRC32();
                try(var input=FileChannel.open(raw.path(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);
                        var in=new DataInputStream(new BufferedInputStream(new java.util.zip.CheckedInputStream(
                                java.nio.channels.Channels.newInputStream(input),verified),64*1024));
                        var channel=FileChannel.open(candidate.path(),StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
                    if(input.size()!=starts[4096])throw new IOException("Replay counting file length changed");
                    var scatter=new Scatter(channel,starts[4096],cancel);
                    for(int ordinal=0;ordinal<total[0];ordinal++) {
                        StructurePackage.cancelled(cancel);in.readFully(record.array());record.clear();
                        if(record.getInt()!=ordinal)throw new IOException("Replay counting file order changed");
                        var edit=new Protocol.Edit(record.getInt(),record.getInt(),record.getInt(),record.getInt(),
                                Byte.toUnsignedInt(record.get()),Byte.toUnsignedInt(record.get()),Byte.toUnsignedInt(record.get()),Byte.toUnsignedInt(record.get()),record.getInt());
                        int cell=key(edit);
                        if(!edit.valid()||!ChunkPos.fromBlock(edit.x(),edit.y(),edit.z()).equals(region)||written[cell]>=counts[cell])
                            throw new IOException("Replay counting file changed");
                        crc[cell].update(record.array(),0,RECORD_BYTES);record.clear();
                        long position=starts[cell]+(long)written[cell]++*RECORD_BYTES;
                        scatter.write(position,record.array());
                    }
                    if(in.read()!=-1||input.size()!=starts[4096]||verified.getValue()!=sealed.getValue()
                            ||!Arrays.equals(counts,written)||channel.size()!=starts[4096])throw new IOException("Replay counting file changed");
                    scatter.flush();StructurePackage.cancelled(cancel);channel.force(true);StructurePackage.cancelled(cancel);
                }
                var checksums=new long[4096];
                for(int i=0;i<4096;i++){StructurePackage.cancelled(cancel);checksums[i]=crc[i]==null?0:crc[i].getValue();}
                // Delete the transient source before relinquishing the candidate.
                raw.close();StructurePackage.cancelled(cancel);
                var result=new RegionReplayCells(candidate.path(),region,counts,starts,checksums,total[0]);candidate.release();return result;
            }
        }
    }
    /** Worker-local bounded write-back pages. Reuse evicted arrays; records
     * may cross a page boundary. No file or buffer escapes before validation. */
    private static final class Scatter {
        private final FileChannel channel;private final long bytes;private final BooleanSupplier cancel;
        private final LinkedHashMap<Long,byte[]> pages=new LinkedHashMap<>(SCATTER_PAGES,.75f,true);
        Scatter(FileChannel channel,long bytes,BooleanSupplier cancel)throws IOException {
            this.channel=channel;this.bytes=bytes;this.cancel=cancel;
            if(bytes<0||bytes>MAX_BYTES)throw new IOException("Replay scatter byte budget");
            if(bytes>0)writeFully(ByteBuffer.wrap(new byte[1]),bytes-1);
        }
        private int length(long page){return (int)Math.min(SCATTER_PAGE_BYTES,bytes-page*SCATTER_PAGE_BYTES);}
        private void writeFully(ByteBuffer buffer,long position)throws IOException {
            while(buffer.hasRemaining()) {
                StructurePackage.cancelled(cancel);int n=channel.write(buffer,position);
                if(n<=0)throw new IOException("Replay cell-index write made no progress");position+=n;
            }
        }
        private void flush(long page,byte[] data)throws IOException {
            writeFully(ByteBuffer.wrap(data,0,length(page)),page*SCATTER_PAGE_BYTES);
        }
        private byte[] page(long page)throws IOException {
            var data=pages.get(page);if(data!=null)return data;
            if(pages.size()==SCATTER_PAGES) {
                var iterator=pages.entrySet().iterator();var first=iterator.next();
                flush(first.getKey(),first.getValue());data=first.getValue();iterator.remove();
            }else data=new byte[SCATTER_PAGE_BYTES];
            var buffer=ByteBuffer.wrap(data,0,length(page));long position=page*SCATTER_PAGE_BYTES;
            while(buffer.hasRemaining()) {
                StructurePackage.cancelled(cancel);int n=channel.read(buffer,position);
                if(n<=0)throw new EOFException("Truncated replay scatter page");position+=n;
            }
            pages.put(page,data);return data;
        }
        void write(long position,byte[] record)throws IOException {
            if(position<0||record.length>bytes-position)throw new IOException("Replay scatter position");
            for(int offset=0;offset<record.length;) {
                StructurePackage.cancelled(cancel);long page=position/SCATTER_PAGE_BYTES;int within=(int)(position%SCATTER_PAGE_BYTES);
                var data=page(page);int count=Math.min(record.length-offset,length(page)-within);
                System.arraycopy(record,offset,data,within,count);offset+=count;position+=count;
            }
        }
        void flush()throws IOException {for(var entry:pages.entrySet())flush(entry.getKey(),entry.getValue());pages.clear();}
    }
    private void check()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay cell-index worker ownership required");
        if(closed)throw new IOException("Replay cell-index closed");
        if(busy)throw new IOException("Replay cell-index operation already active");
    }
    ChunkPos region()throws IOException{check();return region;}
    int size()throws IOException{check();return records;}
    long encodedBytes()throws IOException{check();return starts[4096];}
    private record Row(int ordinal,Protocol.Edit edit) {}

    /** Admits selected source-row count from sealed in-memory metadata before
     * payload I/O or policy callbacks. Holds at most 4097 selected records.
     * All selected cell CRCs pass before any policy callback. Source policy
     * was checked for the whole matching region during build; read rechecks
     * only selected rows. Cross-cell order is restored by original ordinal. */
    List<Protocol.Edit> readWindow(RegionReplayPage.CellRange range,int maxRows,
            Predicate<Protocol.Edit> allowed,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(range);Objects.requireNonNull(allowed);Objects.requireNonNull(cancel);
        if(range.size()>RegionReplayPage.MAX_CELLS||maxRows<0||maxRows>RegionReplayPage.MAX_ROWS)
            throw new IllegalArgumentException("Replay cell-index window budget");
        check();StructurePackage.cancelled(cancel);
        long byteCount=starts[range.endExclusive()]-starts[range.firstInclusive()];
        int count=(int)(byteCount/RECORD_BYTES);
        if(count>maxRows)throw new IOException("Replay cell-index selected row budget");
        busy=true;
        try(var channel=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            if(channel.size()!=starts[4096])throw new IOException("Replay cell-index length changed");
            var payload=ByteBuffer.allocate((int)byteCount);long position=starts[range.firstInclusive()];
            while(payload.hasRemaining()) {
                StructurePackage.cancelled(cancel);int n=channel.read(payload,position);
                if(n<=0)throw new EOFException("Truncated replay cell-index window");position+=n;
            }
            var crc=new CRC32();int offset=0;
            for(int cell=range.firstInclusive();cell<range.endExclusive();cell++) {
                StructurePackage.cancelled(cancel);int length=counts[cell]*RECORD_BYTES;
                crc.reset();crc.update(payload.array(),offset,length);offset+=length;
                if(crc.getValue()!=checksums[cell])throw new IOException("Replay cell-index checksum changed");
            }
            payload.flip();var rows=new Row[count];int next=0;
            for(int cell=range.firstInclusive();cell<range.endExclusive();cell++) {
                int previous=-1;
                for(int i=0;i<counts[cell];i++) {
                    StructurePackage.cancelled(cancel);int ordinal=payload.getInt();
                    var edit=new Protocol.Edit(payload.getInt(),payload.getInt(),payload.getInt(),payload.getInt(),
                            Byte.toUnsignedInt(payload.get()),Byte.toUnsignedInt(payload.get()),Byte.toUnsignedInt(payload.get()),Byte.toUnsignedInt(payload.get()),payload.getInt());
                    if(ordinal<=previous||ordinal>=records||!edit.valid()||key(edit)!=cell||!ChunkPos.fromBlock(edit.x(),edit.y(),edit.z()).equals(region))
                        throw new IOException("Invalid replay cell-index record");
                    previous=ordinal;
                    if(!allowed.test(edit))throw new IOException("Disallowed replay cell-index edit");
                    rows[next++]=new Row(ordinal,edit);
                }
            }
            StructurePackage.cancelled(cancel);Arrays.sort(rows,Comparator.comparingInt(Row::ordinal));
            var result=new ArrayList<Protocol.Edit>(count);int previous=-1;
            for(var row:rows) {
                StructurePackage.cancelled(cancel);
                if(row.ordinal()<=previous)throw new IOException("Duplicate replay cell-index ordinal");
                previous=row.ordinal();result.add(row.edit());
            }
            StructurePackage.cancelled(cancel);
            if(channel.size()!=starts[4096])throw new IOException("Replay cell-index length changed");
            return List.copyOf(result);
        }finally{busy=false;}
    }
    @Override public void close()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay cell-index worker ownership required");
        if(busy)throw new IOException("Replay cell-index operation already active");
        if(!closed){Files.deleteIfExists(path);closed=true;}
    }
}
