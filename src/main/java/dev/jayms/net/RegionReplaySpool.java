package dev.jayms.net;

import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.Objects;
import java.util.function.*;

/** Worker-private replay staging, never an authority or durable world format.
 * Holds only a path/count, not global edits or a section directory. A later
 * invalid world tail must cause the caller to close this uncommitted spool. */
final class RegionReplaySpool implements AutoCloseable {
    // Worst case: one 20-byte header for every 24-byte edit, plus total count.
    static final long MAX_BYTES=4L+LocalGame.MAX_SAVED_EDITS*(20L+RegionReplay.EDIT_BYTES);
    private final Thread owner=Thread.currentThread();
    private final Path path;
    private final int records;
    private final long bytes;
    private boolean closed,busy;
    private RegionReplaySpool(Path path,int records,long bytes){this.path=path;this.records=records;this.bytes=bytes;}

    static RegionReplaySpool stage(Path directory,DataInputStream source,
            Predicate<Protocol.Edit> allowed,BooleanSupplier cancel)throws IOException {
        return stage(directory,source,allowed,cancel,MAX_BYTES);
    }
    /** The byte budget may only tighten the existing format limit. */
    static RegionReplaySpool stage(Path directory,DataInputStream source,
            Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,long budget)throws IOException {
        Objects.requireNonNull(directory);Objects.requireNonNull(source);
        Objects.requireNonNull(allowed);Objects.requireNonNull(cancel);
        if(budget<4||budget>MAX_BYTES)throw new IllegalArgumentException("Replay spool byte budget");
        StructurePackage.cancelled(cancel);
        Path temp=Files.createTempFile(directory,".replay-spool-",".tmp");
        try {
            int[] count={0};long[] size={4};
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE);
                var out=new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel),64*1024))) {
                out.writeInt(0);
                var buffer=new ByteArrayOutputStream(RegionReplay.MAX_BODY);
                var body=new DataOutputStream(buffer);
                RegionReplay.visit(source,allowed,cancel,(region,section)->{
                    long length=20L+section.size()*RegionReplay.EDIT_BYTES;
                    if(length>budget-size[0])throw new IOException("Replay spool byte budget");
                    buffer.reset();
                    for(var edit:section){StructurePackage.cancelled(cancel);edit.write(body);}
                    StructurePackage.cancelled(cancel);
                    RegionReplay.section(out,region,section.size(),buffer);
                    count[0]+=section.size();size[0]+=length;
                });
                StructurePackage.cancelled(cancel);out.flush();
                var header=ByteBuffer.allocate(4).putInt(count[0]);header.flip();
                long position=0;
                while(header.hasRemaining()) {
                    StructurePackage.cancelled(cancel);int n=channel.write(header,position);
                    if(n<=0)throw new IOException("Replay spool header write made no progress");position+=n;
                }
                StructurePackage.cancelled(cancel);channel.force(true);StructurePackage.cancelled(cancel);
            }
            StructurePackage.cancelled(cancel);
            return new RegionReplaySpool(temp,count[0],size[0]);
        }catch(IOException|RuntimeException|Error failure) {
            try{Files.deleteIfExists(temp);}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;
        }
    }
    int size()throws IOException{check();return records;}
    long encodedBytes()throws IOException{check();return bytes;}
    private void check()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay spool worker ownership required");
        if(closed)throw new IOException("Replay spool closed");
        if(busy)throw new IOException("Replay spool operation already active");
    }
    /** Every delivery is still private staging. Checks the sealed length/count,
     * each section again, and exact EOF. Sink failure/cancellation allows retry.
     * Caller must not retain unbounded sections or publish a partial traversal. */
    void visit(Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,RegionReplay.SectionSink sink)throws IOException {
        Objects.requireNonNull(allowed);Objects.requireNonNull(cancel);Objects.requireNonNull(sink);
        check();StructurePackage.cancelled(cancel);busy=true;
        try(var channel=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            if(channel.size()!=bytes||bytes>MAX_BYTES)throw new IOException("Replay spool length changed");
            var input=Channels.newInputStream(channel);
            var header=new DataInputStream(input);
            if(header.readInt()!=records)throw new IOException("Replay spool count changed");
            channel.position(0);
            var in=new DataInputStream(new BufferedInputStream(input,64*1024));
            RegionReplay.visit(in,allowed,cancel,sink);
            StructurePackage.cancelled(cancel);
            if(in.read()!=-1)throw new IOException("Trailing replay spool data");
            StructurePackage.cancelled(cancel);
        }finally{busy=false;}
    }
    @Override public void close()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay spool worker ownership required");
        if(busy)throw new IOException("Replay spool operation already active");
        if(!closed){Files.deleteIfExists(path);closed=true;}
    }
}
