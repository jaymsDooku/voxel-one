package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import dev.jayms.net.structure.StructurePackage;

/** Save publication policy. Structure transactions require atomic replacement.
 * Generation guards serialize cooperating publishers in this JVM only. Foreign
 * processes, path aliases and world/sidecar transactions need separate fencing. */
final class WorldSavePublication {
    private WorldSavePublication() {}
    private static final int STRIPES=64;
    static final long MAX_GENERATION_BYTES=512L*1024*1024;
    private static final class Stripe {long revision;}
    private static final Stripe[] LOCKS=new Stripe[STRIPES];
    static {for(int i=0;i<STRIPES;i++)LOCKS[i]=new Stripe();}
    private static Path normalized(Path target){return target.toAbsolutePath().normalize();}
    private static Stripe stripe(Path target){return LOCKS[Math.floorMod(target.hashCode(),STRIPES)];}
    /** A stripe collision can conservatively invalidate an unrelated target.
     * Hashes describe observed bytes; external writers are not excluded. */
    static final class Generation {
        private final Path target;private final long revision,maxBytes;private final FileState file;
        private Generation(Path target,long revision,long maxBytes,FileState file){this.target=target;this.revision=revision;this.maxBytes=maxBytes;this.file=file;}
    }
    private record FileState(boolean exists,long size,Object key,Object modified,Object created,String digest) {}
    static Generation capture(Path target,long maxBytes,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);if(maxBytes<0||maxBytes>MAX_GENERATION_BYTES)throw new IllegalArgumentException("Save generation byte budget");
        StructurePackage.cancelled(cancel);Path path=normalized(target);var lock=stripe(path);
        synchronized(lock){return new Generation(path,lock.revision,maxBytes,observe(path,maxBytes,cancel));}
    }
    /** Bind a decoded source digest to the observed destination generation.
     * A later guarded commit still needs owner/source fences and is JVM-only. */
    static Generation captureMatching(Path target,String expectedDigest,long maxBytes,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(expectedDigest);
        if(!expectedDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Save source digest");
        var generation=capture(target,maxBytes,cancel);
        if(!generation.file.exists()||!expectedDigest.equals(generation.file.digest()))throw new IOException("Save source digest changed");
        return generation;
    }
    private static FileState observe(Path path,long maxBytes,BooleanSupplier cancel)throws IOException {
        StructurePackage.cancelled(cancel);BasicFileAttributes before;
        try{before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
        catch(NoSuchFileException absent){return new FileState(false,0,null,null,null,null);}
        if(!before.isRegularFile()||before.size()>maxBytes)throw new IOException("Save generation file budget/type");
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException failure){throw new IOException("SHA-256 unavailable",failure);}
        long read=0;byte[] buffer=new byte[64*1024];
        try(var input=Files.newInputStream(path)) {
            for(;;) {
                StructurePackage.cancelled(cancel);int count=input.read(buffer);if(count<0)break;
                if(count>maxBytes-read)throw new IOException("Save generation file budget");read+=count;digest.update(buffer,0,count);
            }
        }
        StructurePackage.cancelled(cancel);var after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!after.isRegularFile()||read!=before.size()||before.size()!=after.size()
                ||!Objects.equals(before.fileKey(),after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime())
                ||!before.creationTime().equals(after.creationTime()))throw new IOException("Save changed during generation capture");
        return new FileState(true,read,before.fileKey(),before.lastModifiedTime(),before.creationTime(),HexFormat.of().formatHex(digest.digest()));
    }
    @FunctionalInterface interface Move {
        void apply(Path source, Path target, CopyOption... options) throws IOException;
    }
    static void replace(Path source, Path target, boolean requireAtomic) throws IOException {
        replace(source, target, requireAtomic, Files::move);
    }
    static void replace(Path source, Path target, boolean requireAtomic, Move move) throws IOException {
        var path=normalized(target);var lock=stripe(path);
        synchronized(lock){move(source,target,requireAtomic,move);lock.revision++;}
    }
    /** Atomic replace only if no cooperating publication or observed destination
     * change followed capture. Performs file I/O; use on a worker, not a frame. */
    static void replaceIfUnchanged(Path source,Path target,Generation expected,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(expected);Objects.requireNonNull(cancel);StructurePackage.cancelled(cancel);
        var path=normalized(target);if(!path.equals(expected.target))throw new IOException("Save generation target mismatch");var lock=stripe(path);
        synchronized(lock) {
            if(lock.revision!=expected.revision)throw new IOException("Save publication generation changed");
            if(!observe(path,expected.maxBytes,cancel).equals(expected.file))throw new IOException("Save destination changed");
            StructurePackage.cancelled(cancel);move(source,target,true,Files::move);lock.revision++;
        }
    }
    private static void move(Path source,Path target,boolean requireAtomic,Move move)throws IOException {
        try {
            move.apply(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            if (requireAtomic) throw unsupported;
            move.apply(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
