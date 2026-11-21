import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;

/** Private migration coordinator. Caller must own the save directory and share one monitor/revision with writers. */
public final class RegionOfflineBundlePrototype {
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    private static void owner(Object monitor)throws IOException{if(!Thread.holdsLock(monitor))throw new IOException("Source owner monitor required");}
    private static byte[] read(Path path,int maximum,boolean required,BooleanSupplier cancel)throws IOException {
        check(cancel);BasicFileAttributes attributes;
        try{attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
        catch(NoSuchFileException absent){if(required)throw absent;return null;}
        if(!attributes.isRegularFile())throw new IOException("Source must be a regular file");
        try(var file=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            long size=file.size();if(size<0||size>maximum)throw new IOException("Source file byte budget");byte[] bytes=new byte[(int)size];int offset=0;
            while(offset<bytes.length){check(cancel);int count=file.read(ByteBuffer.wrap(bytes,offset,Math.min(65536,bytes.length-offset)));if(count<1)throw new EOFException("Source truncated during read");offset+=count;}
            check(cancel);if(file.read(ByteBuffer.allocate(1))!=-1)throw new IOException("Source grew during read");return bytes;
        }
    }
    /** Caller-held monitor is required before any source file is read. Does not open arbitrary sidecar paths. */
    public static Snapshot capture(Path base,Object monitor,LongSupplier revision,BooleanSupplier cancel)throws IOException {
        return capture(base,monitor,revision,cancel,false);
    }
    /** Explicit server codec selection; source monitor/revision and fixed-path rules are identical. */
    public static Snapshot captureServer(Path base,Object monitor,LongSupplier revision,BooleanSupplier cancel)throws IOException {
        return capture(base,monitor,revision,cancel,true);
    }
    private static Snapshot capture(Path base,Object monitor,LongSupplier revision,BooleanSupplier cancel,boolean server)throws IOException {
        Objects.requireNonNull(base);Objects.requireNonNull(monitor);Objects.requireNonNull(revision);Objects.requireNonNull(cancel);owner(monitor);check(cancel);
        long expected=revision.getAsLong();if(expected<0)throw new IOException("Invalid source revision");
        var absolute=base.toAbsolutePath().normalize();var paths=List.of(absolute,absolute.resolveSibling(absolute.getFileName()+".city"),absolute.resolveSibling(absolute.getFileName()+".city.history"));
        byte[][] bytes=readAll(paths,cancel);var result=new Snapshot(paths,bytes,monitor,revision,expected,server);result.validate(cancel);return result;
    }
    private static byte[][] readAll(List<Path> paths,BooleanSupplier cancel)throws IOException {
        var base=read(paths.get(0),RegionOfflineImportPrototype.MAX_BYTES,true,cancel);var city=read(paths.get(1),RegionSidecarsPrototype.MAX_CONTENT,false,cancel);
        var history=read(paths.get(2),RegionSidecarsPrototype.MAX_CONTENT-(city==null?0:city.length),false,cancel);
        if(history!=null&&history.length==0)throw new IOException("Empty history sidecar must not be dropped");
        if(city==null&&history!=null)throw new IOException("Orphan city history must not be dropped");
        return new byte[][]{base,city,history};
    }
    public static final class Snapshot {
        private final List<Path> paths;private final byte[][] bytes;private final Object monitor;private final LongSupplier revision;private final long expected;private final boolean server;
        private Snapshot(List<Path> paths,byte[][] bytes,Object monitor,LongSupplier revision,long expected,boolean server){this.server=server;this.paths=paths;this.bytes=bytes;this.monitor=monitor;this.revision=revision;this.expected=expected;}
        private void validate(BooleanSupplier cancel)throws IOException {
            owner(monitor);check(cancel);if(revision.getAsLong()!=expected)throw new IOException("Source revision changed");
            var current=readAll(paths,cancel);for(int i=0;i<3;i++){check(cancel);if(!Arrays.equals(bytes[i],current[i]))throw new IOException("Source bundle changed");}
            if(revision.getAsLong()!=expected)throw new IOException("Source revision changed");check(cancel);
        }
        /** Parse and force only private candidate bytes; may run outside the source monitor. */
        public Staged stage(RegionGenerationPrototype destination,BooleanSupplier cancel)throws IOException {
            Objects.requireNonNull(destination);Objects.requireNonNull(cancel);check(cancel);
            var candidate=server?RegionServerImportPrototype.decode(bytes[0],cancel):RegionOfflineImportPrototype.decode(bytes[0],cancel);
            if(bytes[1]!=null)candidate=candidate.withSidecars(RegionSidecarsPrototype.capture(bytes[1],bytes[2],cancel),cancel);
            return new Staged(this,destination.stage(candidate,cancel));
        }
    }
    public static final class Staged implements AutoCloseable {
        private final Snapshot source;private final RegionGenerationPrototype.Staged file;private boolean closed;
        private Staged(Snapshot source,RegionGenerationPrototype.Staged file){this.source=source;this.file=file;}
        /** Caller must again hold the same source monitor. No legacy source files are replaced. */
        public synchronized RegionGenerationPrototype.Receipt commit(BooleanSupplier cancel)throws IOException {
            if(closed)throw new IOException("Bundle stage closed");Objects.requireNonNull(cancel);source.validate(cancel);
            var receipt=file.commit(cancel);closed=true;return receipt;
        }
        @Override public synchronized void close()throws IOException{closed=true;file.close();}
    }
}
