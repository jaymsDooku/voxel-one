package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReadbackFileChangeTest {
    @TempDir Path folder;
    static final RegionReplayWorker.Revision REV=new RegionReplayWorker.Revision(1,2,3);
    Path world()throws Exception {
        var path=folder.resolve("world.dat");var rows=new WorldVoxels.History();
        WorldVoxels.remember(rows,new Protocol.Edit(0,160,0,Blocks.STONE));
        try(var staged=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new dev.jayms.net.model.ModelLibrary(),rows,()->false,n->{})) {
            staged.publish(new Inventory(),20,List.of(),0);
        }return path;
    }
    interface Change {void apply(Path path)throws IOException;}
    void rejects(Change change,String message)throws Exception {
        Path path=world(),spool=Files.createDirectory(folder.resolve("spool"));var changed=new AtomicBoolean();
        IOException error=assertThrows(IOException.class,()->LocalGame.openRegionalReadback(path,spool,REV,()->false,n->{
            if(n>0&&changed.compareAndSet(false,true))try{change.apply(path);}catch(IOException failure){throw new UncheckedIOException(failure);}
        }));
        assertTrue(changed.get());if(message!=null)assertEquals(message,error.getMessage());
        try(var files=Files.list(spool)){assertEquals(0,files.count());}
    }
    @Test void sameSizeInPlaceChangeRejectsAndCleansPrivateSpool()throws Exception {
        rejects(path->{
            var before=Files.getLastModifiedTime(path);
            try(var file=new RandomAccessFile(path.toFile(),"rw")){file.seek(4);file.writeLong(43);}
            Files.setLastModifiedTime(path,FileTime.fromMillis(before.toMillis()+2000));
        },"Saved world changed during regional readback");
    }
    @Test void atomicReplacementIsRejectedOrDeniedWithoutChangingSource()throws Exception {replacement(false);}
    @Test void deniedReplacementKeepsSourceUsableAndCanRetryAfterClose()throws Exception {replacement(true);}
    boolean replacement(boolean injectDenial)throws Exception {
        Path path=world(),spool=Files.createDirectory(folder.resolve("spool")),replacement=folder.resolve("replacement.dat");
        byte[] original=Files.readAllBytes(path);var oldTime=Files.getLastModifiedTime(path);
        Files.write(replacement,original);Files.setLastModifiedTime(replacement,oldTime);
        var attempted=new AtomicBoolean();var denied=new AtomicBoolean();
        RegionReplayWorker.Source source=null;IOException rejected=null;
        try {
            try {
                source=LocalGame.openRegionalReadback(path,spool,REV,()->false,n->{
                    if(n>0&&attempted.compareAndSet(false,true))try {
                        if(injectDenial)throw new AccessDeniedException(path.toString());
                        Files.move(replacement,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                    }catch(AccessDeniedException expected){denied.set(true);}
                    catch(IOException error){throw new UncheckedIOException(error);}
                });
            }catch(IOException error){rejected=error;}
            assertTrue(attempted.get());assertArrayEquals(original,Files.readAllBytes(path));
            if(denied.get()) {
                assertNull(rejected);assertNotNull(source);assertTrue(Files.exists(replacement));
                assertEquals(1,source.spool().size());assertEquals(42,source.terrain().seed);
                String fingerprint=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original));
                assertEquals(fingerprint,source.fingerprint());assertEquals(fingerprint,source.metadata().transfer().readbackFingerprint());
            }else {
                assertNull(source);assertNotNull(rejected);assertFalse(Files.exists(replacement));
                assertEquals("Saved world changed during regional readback",rejected.getMessage());
            }
        }finally{if(source!=null)source.spool().close();}
        try(var files=Files.list(spool)){assertEquals(0,files.count());}
        if(denied.get()) {
            // The denied attempt did not consume the replacement; retry only
            // after the reader is closed. No skip or platform-name branch.
            Files.move(replacement,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            var reopened=LocalGame.openRegionalReadback(path,spool,REV,()->false,n->{});
            try(var owned=reopened.spool()){assertEquals(1,owned.size());assertArrayEquals(original,Files.readAllBytes(path));}
            try(var files=Files.list(spool)){assertEquals(0,files.count());}
        }
        return denied.get();
    }
    @Test void deletedSourceRejectsAndCleansPrivateSpool()throws Exception {rejects(Files::delete,null);}
    @Test void nonRegularSourceRejectsBeforeSpooling()throws Exception {
        var spool=Files.createDirectory(folder.resolve("spool"));
        var error=assertThrows(IOException.class,()->LocalGame.openRegionalReadback(folder,spool,REV,()->false,n->fail()));
        assertEquals("Regional readback requires a regular file",error.getMessage());
        try(var files=Files.list(spool)){assertEquals(0,files.count());}
    }
    @Test void unchangedSourceAndPostOpenReplacementRetainDetachedSnapshot()throws Exception {
        var path=world();var before=Files.readAllBytes(path);var spool=Files.createDirectory(folder.resolve("spool"));
        var source=LocalGame.openRegionalReadback(path,spool,REV,()->false,n->{});
        try(var owned=source.spool()) {
            assertArrayEquals(before,Files.readAllBytes(path));Files.delete(path);
            assertEquals(1,owned.size());assertEquals(42,source.terrain().seed);
            assertFalse(source.metadata().transfer().replayComplete());
        }
        try(var files=Files.list(spool)){assertEquals(0,files.count());}
    }
}
