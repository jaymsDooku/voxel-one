import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;

/** Synthetic-file publication experiment. Not connected to game saves or live state. */
public final class RegionGenerationPrototype {
    static final int MAGIC=0x56524750,MAX_BYTES=RegionDependencyPrototype.MAX_BYTES+24;
    interface Operations {
        void force(FileChannel file)throws IOException;
        void replace(Path stage,Path current)throws IOException;
        default void beforeReadOpen(Path path)throws IOException{}
    }
    static final Operations NIO=new Operations(){
        public void force(FileChannel file)throws IOException{file.force(true);}
        public void replace(Path stage,Path current)throws IOException{Files.move(stage,current,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
    };
    public record Loaded(long generation,RegionDependencyPrototype manifest,RegionWorldLibraryPrototype worldLibrary){
        public Loaded {if((manifest==null)==(worldLibrary==null))throw new IllegalArgumentException("Exactly one generation payload required");}
        public int formatVersion(){return worldLibrary==null?1:2;}
    }
    public record Receipt(long generation,boolean recoveredMoveOutcome){}
    private record Frame(Loaded loaded,byte[] bytes){}
    private final Path root,current,lockFile;
    private final Operations operations;
    public RegionGenerationPrototype(Path root)throws IOException{this(root,NIO);}
    RegionGenerationPrototype(Path root,Operations operations)throws IOException {
        this.root=root.toAbsolutePath().normalize();this.operations=Objects.requireNonNull(operations);
        Files.createDirectories(this.root);current=this.root.resolve("current.region");lockFile=this.root.resolve("commit.lock");
    }
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    private Frame read(Path path,BooleanSupplier cancel)throws IOException {
        check(cancel);
        var attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attributes.isRegularFile())throw new IOException("Not a regular generation file");
        operations.beforeReadOpen(path);check(cancel);byte[] bytes;
        try(var file=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            // Size and bytes must refer to the same opened generation. The path
            // can be atomically replaced between metadata lookup and open.
            long size=file.size();if(size<24||size>MAX_BYTES)throw new IOException("Generation byte budget");
            bytes=new byte[(int)size];
            int offset=0;
            while(offset<bytes.length){check(cancel);int count=file.read(ByteBuffer.wrap(bytes,offset,Math.min(65536,bytes.length-offset)));if(count<1)throw new EOFException("Truncated generation");offset+=count;}
            check(cancel);if(file.read(ByteBuffer.allocate(1))!=-1)throw new IOException("Generation grew during read");
        }
        var crc=new CRC32();for(int i=0;i<bytes.length-4;i+=65536){check(cancel);crc.update(bytes,i,Math.min(65536,bytes.length-4-i));}
        if((int)crc.getValue()!=ByteBuffer.wrap(bytes).getInt(bytes.length-4))throw new IOException("Generation CRC");
        var in=new DataInputStream(new ByteArrayInputStream(bytes,0,bytes.length-4));
        if(in.readInt()!=MAGIC)throw new IOException("Generation format/version");
        int version=in.readInt();if(version!=1&&version!=2)throw new IOException("Generation format/version");
        long generation=in.readLong();int length=in.readInt();
        if(generation<1||length<24||length>RegionDependencyPrototype.MAX_BYTES||length!=in.available())throw new IOException("Generation metadata");
        var payload=in.readNBytes(length);
        Loaded loaded=version==1?new Loaded(generation,RegionDependencyPrototype.decode(payload,cancel),null):new Loaded(generation,null,RegionWorldLibraryPrototype.decode(payload,cancel));check(cancel);
        return new Frame(loaded,bytes);
    }
    public Optional<Loaded> load(BooleanSupplier cancel)throws IOException {
        try{return Optional.of(read(current,cancel).loaded());}
        catch(NoSuchFileException absent){return Optional.empty();}
    }
    private long generation(BooleanSupplier cancel)throws IOException {
        // The synthetic directory is private. Only this store's locked atomic
        // replacements may change current.region; disappearance is not a reset API.
        return load(cancel).map(Loaded::generation).orElse(0L);
    }
    public Staged stage(RegionDependencyPrototype manifest,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(manifest);Objects.requireNonNull(cancel);check(cancel);
        return stagePayload(manifest.encode(cancel),1,false,false,cancel);
    }
    public Staged stage(RegionWorldLibraryPrototype world,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(world);Objects.requireNonNull(cancel);check(cancel);
        return stagePayload(world.encode(cancel),2,world.metadata().isPresent(),world.sidecars().isPresent(),cancel);
    }
    private Staged stagePayload(byte[] payload,int version,boolean hasMetadata,boolean hasSidecars,BooleanSupplier cancel)throws IOException {
        check(cancel);var loaded=load(cancel);
        if(version==1&&loaded.isPresent()&&loaded.orElseThrow().formatVersion()==2)throw new IOException("Generation downgrade would discard library assets");
        if(!hasMetadata&&loaded.isPresent()&&loaded.orElseThrow().worldLibrary()!=null&&loaded.orElseThrow().worldLibrary().metadata().isPresent())throw new IOException("Generation downgrade would discard offline metadata");
        if(!hasSidecars&&loaded.isPresent()&&loaded.orElseThrow().worldLibrary()!=null&&loaded.orElseThrow().worldLibrary().sidecars().isPresent())throw new IOException("Generation downgrade would discard sidecars");
        long expected=loaded.map(Loaded::generation).orElse(0L),next;
        try{next=Math.addExact(expected,1);}catch(ArithmeticException overflow){throw new IOException("Generation exhausted",overflow);}
        var sink=new ByteArrayOutputStream(payload.length+24);var out=new DataOutputStream(sink);
        out.writeInt(MAGIC);out.writeInt(version);out.writeLong(next);out.writeInt(payload.length);out.write(payload);out.flush();
        byte[] prefix=sink.toByteArray();var crc=new CRC32();
        for(int i=0;i<prefix.length;i+=65536){check(cancel);crc.update(prefix,i,Math.min(65536,prefix.length-i));}
        out.writeInt((int)crc.getValue());byte[] bytes=sink.toByteArray();check(cancel);
        Path path=Files.createTempFile(root,".region-stage-",".tmp");
        try {
            try(var file=FileChannel.open(path,StandardOpenOption.WRITE)) {
                int offset=0;while(offset<bytes.length){check(cancel);int count=file.write(ByteBuffer.wrap(bytes,offset,Math.min(65536,bytes.length-offset)));if(count<1)throw new IOException("No staging write progress");offset+=count;}
                check(cancel);operations.force(file);check(cancel);
            }
            return new Staged(path,expected,next,bytes);
        }catch(IOException|RuntimeException failure){try{Files.deleteIfExists(path);}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    public final class Staged implements AutoCloseable {
        final Path path;private final long expected,next;private final byte[] bytes;private boolean closed;
        private Staged(Path path,long expected,long next,byte[] bytes){this.path=path;this.expected=expected;this.next=next;this.bytes=bytes;}
        long expectedGeneration(){return expected;}
        long nextGeneration(){return next;}
        public synchronized Receipt commit(BooleanSupplier cancel)throws IOException {
            Objects.requireNonNull(cancel);if(closed)throw new IOException("Stage closed");check(cancel);boolean attempted=false;
            try(var channel=FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE)) {
                FileLock acquired;
                try{acquired=channel.tryLock();}catch(OverlappingFileLockException busy){throw new IOException("Another prototype commit is active",busy);}
                if(acquired==null)throw new IOException("Another prototype commit is active");
                try(var lock=acquired) {
                    if(generation(cancel)!=expected)throw new IOException("Stale generation");
                    if(!Arrays.equals(read(path,cancel).bytes(),bytes))throw new IOException("Staged bytes changed");
                    check(cancel);attempted=true;
                    // Publication starts here. Cancellation cannot pretend the move
                    // did not happen. No directory fsync/power-loss claim is made.
                    operations.replace(path,current);
                }
            }catch(IOException failure) {
                if(attempted) {
                    try {
                        if(Arrays.equals(read(current,()->false).bytes(),bytes)){closed=true;Files.deleteIfExists(path);return new Receipt(next,true);}
                    }catch(IOException unknown){failure.addSuppressed(unknown);}
                }
                throw failure;
            }
            closed=true;return new Receipt(next,false);
        }
        @Override public synchronized void close()throws IOException {if(!closed){Files.deleteIfExists(path);closed=true;}}
    }
}
